package com.example.lolwatcher;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateNotifierService {
    private static final Logger log = LoggerFactory.getLogger(UpdateNotifierService.class);

    private final JDA jda;
    private final Config config;
    private final LoLVersionClient liveVersionClient;
    private final PbeVersionClient pbeVersionClient;
    private final VpbeWikiClient vpbeWikiClient;
    private final PatchNotesClient patchNotesClient;
    private final GuildLiveChannelStore guildChannelStore;
    private final VersionStateStore liveStateStore;
    private final VersionStateStore pbeStateStore;
    private final ScheduledExecutorService scheduler;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean checking = new AtomicBoolean(false);

    public UpdateNotifierService(
            JDA jda,
            Config config,
            LoLVersionClient liveVersionClient,
            PbeVersionClient pbeVersionClient,
            VpbeWikiClient vpbeWikiClient,
            PatchNotesClient patchNotesClient,
            GuildLiveChannelStore guildChannelStore,
            VersionStateStore liveStateStore,
            VersionStateStore pbeStateStore
    ) {
        this.jda = jda;
        this.config = config;
        this.liveVersionClient = liveVersionClient;
        this.pbeVersionClient = pbeVersionClient;
        this.vpbeWikiClient = vpbeWikiClient;
        this.patchNotesClient = patchNotesClient;
        this.guildChannelStore = guildChannelStore;
        this.liveStateStore = liveStateStore;
        this.pbeStateStore = pbeStateStore;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "lol-version-checker"));
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }

        long intervalMs = config.checkInterval().toMillis();
        scheduler.scheduleAtFixedRate(this::safeLiveCheck, 0, intervalMs, TimeUnit.MILLISECONDS);
        log.info("Started LIVE version check every {} minutes", config.checkInterval().toMinutes());
    }

    public void stop() {
        running.set(false);
        scheduler.shutdownNow();
    }

    // Backward compatibility for existing /lolcheck behavior (maps to LIVE check).
    public String triggerManualCheck() {
        return triggerManualLiveCheck();
    }

    public String triggerManualLiveCheck() {
        if (!checking.compareAndSet(false, true)) {
            return "A check is already running. Please try again in a moment.";
        }

        try {
            LiveCheckResult result = checkLiveAndMaybeNotify(false);
            return toManualLiveReply(result);
        } catch (Throwable ex) {
            log.error("Manual LIVE check failed", ex);
            return "Manual LIVE check failed: " + ex.getMessage();
        } finally {
            checking.set(false);
        }
    }

    public String triggerManualPbeCheck() {
        if (!checking.compareAndSet(false, true)) {
            return "A check is already running. Please try again in a moment.";
        }

        try {
            PbeCheckResult result = checkPbe();
            return toManualPbeReply(result);
        } catch (Throwable ex) {
            log.error("Manual PBE check failed", ex);
            return "Manual PBE check failed: " + ex.getMessage();
        } finally {
            checking.set(false);
        }
    }

    public String bindLiveChannel(long guildId, long channelId) {
        resolveChannel(channelId);
        guildChannelStore.setChannel(guildId, channelId);
        return "LIVE update channel bound to <#" + channelId + "> for this server.";
    }

    public String unbindLiveChannel(long guildId) {
        boolean removed = guildChannelStore.removeChannel(guildId);
        if (removed) {
            return "LIVE update channel binding removed for this server.";
        }
        return "No LIVE update channel was configured for this server.";
    }

    private void safeLiveCheck() {
        if (!checking.compareAndSet(false, true)) {
            log.info("Skip scheduled LIVE check because another check is in progress.");
            return;
        }

        try {
            log.info("Running scheduled LIVE check...");
            LiveCheckResult result = checkLiveAndMaybeNotify(config.postOnStartupInitialization());
            log.info(result.logMessage());
            log.info("Scheduled LIVE check finished.");
        } catch (Throwable ex) {
            log.error("Scheduled LIVE check failed", ex);
        } finally {
            checking.set(false);
        }
    }

    private LiveCheckResult checkLiveAndMaybeNotify(boolean allowInitPost) throws IOException, InterruptedException {
        log.info("Fetching latest LIVE version from {}", config.versionsUrl());
        String latest = liveVersionClient.fetchLatestVersion();
        log.info("Latest LIVE version: {}", latest);
        Optional<String> lastOpt = liveStateStore.readLastVersion();
        log.info("Last notified LIVE version: {}", lastOpt.orElse("<none>"));

        if (lastOpt.isPresent() && lastOpt.get().equals(latest)) {
            return LiveCheckResult.noUpdate(latest);
        }

        if (lastOpt.isEmpty()) {
            liveStateStore.saveLastVersion(latest);
            if (!allowInitPost) {
                return LiveCheckResult.initialized(latest, 0);
            }

            String bootstrap = enrichWithPatchSummary(
                    "[LoL LIVE watcher initialized] Current version: `" + latest + "` (" + Instant.now() + ")",
                    latest
            );
            int notified = sendToConfiguredLiveChannels(bootstrap);
            return LiveCheckResult.initialized(latest, notified);
        }

        String previous = lastOpt.get();
        String message = enrichWithPatchSummary(
                "New League of Legends LIVE version detected: `" + previous + "` -> `" + latest + "`",
                latest
        );
        int notified = sendToConfiguredLiveChannels(message);
        liveStateStore.saveLastVersion(latest);
        return LiveCheckResult.updated(previous, latest, notified);
    }

    private PbeCheckResult checkPbe() throws IOException, InterruptedException {
        log.info("Fetching latest PBE version from {}", config.pbeVersionUrl());
        String latest = pbeVersionClient.fetchLatestPbeVersion();
        log.info("Latest PBE version: {}", latest);
        Optional<String> lastOpt = pbeStateStore.readLastVersion();
        log.info("Last recorded PBE version: {}", lastOpt.orElse("<none>"));

        if (lastOpt.isPresent() && lastOpt.get().equals(latest)) {
            return PbeCheckResult.noUpdate(latest);
        }

        pbeStateStore.saveLastVersion(latest);
        if (lastOpt.isEmpty()) {
            return PbeCheckResult.initialized(latest);
        }

        return PbeCheckResult.updated(lastOpt.get(), latest);
    }

    private MessageChannel resolveChannel(long channelId) {
        MessageChannel channel = jda.getChannelById(MessageChannel.class, channelId);
        if (channel != null) {
            return channel;
        }

        if (jda.getGuildChannelById(channelId) != null) {
            throw new IllegalStateException("Channel id " + channelId + " exists but is not message-capable.");
        }

        throw new IllegalStateException(
                "Cannot find channel id " + channelId
                        + ". Check channel ID, bot server membership, and View/Send Messages permission."
                        + " Visible guild count: " + jda.getGuilds().size()
        );
    }

    private int sendToConfiguredLiveChannels(String message) {
        Set<Long> channelIds = new LinkedHashSet<>(guildChannelStore.allChannels());
        if (config.channelId() != null && config.channelId() > 0) {
            channelIds.add(config.channelId());
        }

        int sent = 0;
        for (Long channelId : channelIds) {
            try {
                MessageChannel channel = resolveChannel(channelId);
                channel.sendMessage(message).timeout(20, TimeUnit.SECONDS).complete();
                sent++;
            } catch (Throwable ex) {
                log.warn("Failed to send LIVE notification to channel {}", channelId, ex);
            }
        }
        return sent;
    }

    private String enrichWithPatchSummary(String baseMessage, String latestVersion) {
        Optional<PatchNotesSummary> summaryOpt = patchNotesClient.fetchLatestSummary(latestVersion);
        if (summaryOpt.isEmpty()) {
            return baseMessage;
        }

        PatchNotesSummary summary = summaryOpt.get();
        StringBuilder builder = new StringBuilder(baseMessage);
        builder.append("\n\n")
                .append("Patch Summary (Auto-generated)")
                .append("\n")
                .append("Title: ").append(summary.title());

        for (String line : summary.highlights()) {
            builder.append("\n").append("- ").append(line);
        }

        builder.append("\n").append("Details: ").append(summary.url());
        return truncateDiscordMessage(builder.toString(), 1900);
    }

    private String truncateDiscordMessage(String message, int maxLen) {
        if (message.length() <= maxLen) {
            return message;
        }
        return message.substring(0, Math.max(0, maxLen - 1)).trim() + "…";
    }

    private String toManualLiveReply(LiveCheckResult result) {
        String base = result.manualMessage();
        if (result.type() == LiveResultType.UPDATED) {
            return base;
        }

        Optional<PatchNotesSummary> summaryOpt = patchNotesClient.fetchLatestSummary(result.latest());
        if (summaryOpt.isEmpty()) {
            return base;
        }

        PatchNotesSummary summary = summaryOpt.get();
        StringBuilder builder = new StringBuilder(base);
        builder.append("\n\n")
                .append("Current LIVE Patch Summary (Auto-generated)")
                .append("\n")
                .append("Title: ").append(summary.title());

        for (String line : summary.highlights()) {
            builder.append("\n").append("- ").append(line);
        }

        builder.append("\n").append("Details: ").append(summary.url());
        return truncateDiscordMessage(builder.toString(), 1900);
    }

    private String toManualPbeReply(PbeCheckResult result) {
        StringBuilder builder = new StringBuilder(result.manualMessage(config.pbePatchNotesUrl()));
        Optional<VpbeWikiInfo> infoOpt = vpbeWikiClient.fetchInfo();

        if (infoOpt.isPresent()) {
            VpbeWikiInfo info = infoOpt.get();
            builder.append("\n\n")
                    .append("VPBE Wiki Update")
                    .append("\n")
                    .append("Title: ").append(info.title())
                    .append("\n")
                    .append("Last updated: ").append(info.lastUpdated())
                    .append("\n")
                    .append("Details: ").append(info.url());
        } else {
            builder.append("\n")
                    .append("VPBE Wiki: ").append(config.vpbeWikiUrl());
        }

        return truncateDiscordMessage(builder.toString(), 1900);
    }

    private record LiveCheckResult(LiveResultType type, String previous, String latest, int notifiedChannels) {
        static LiveCheckResult noUpdate(String latest) {
            return new LiveCheckResult(LiveResultType.NO_UPDATE, latest, latest, 0);
        }

        static LiveCheckResult initialized(String latest, int notifiedChannels) {
            return new LiveCheckResult(LiveResultType.INITIALIZED, null, latest, notifiedChannels);
        }

        static LiveCheckResult updated(String previous, String latest, int notifiedChannels) {
            return new LiveCheckResult(LiveResultType.UPDATED, previous, latest, notifiedChannels);
        }

        String logMessage() {
            return switch (type) {
                case NO_UPDATE -> "LIVE: no update detected. Latest version: " + latest;
                case INITIALIZED -> notifiedChannels > 0
                        ? "LIVE: initialized baseline and posted startup message to " + notifiedChannels + " channel(s) for version " + latest
                        : "LIVE: initialized baseline version to " + latest + " (no channel message posted)";
                case UPDATED -> notifiedChannels > 0
                        ? "LIVE: new version detected and notified " + notifiedChannels + " channel(s): " + previous + " -> " + latest
                        : "LIVE: new version detected but no channels are configured: " + previous + " -> " + latest;
            };
        }

        String manualMessage() {
            return switch (type) {
                case NO_UPDATE -> "No new LIVE version yet. Latest LIVE version is still `" + latest + "`.";
                case INITIALIZED -> notifiedChannels > 0
                        ? "Manual LIVE check complete. Baseline set to `" + latest + "` and startup message posted to " + notifiedChannels + " channel(s)."
                        : "Manual LIVE check complete. Baseline set to `" + latest + "`. No channel update was posted.";
                case UPDATED -> notifiedChannels > 0
                        ? "New LIVE version detected: `" + previous + "` -> `" + latest + "`. Notification posted to " + notifiedChannels + " channel(s)."
                        : "New LIVE version detected: `" + previous + "` -> `" + latest + "`, but no LIVE channel is configured. Use `/set_live_channel` first.";
            };
        }
    }

    private enum LiveResultType {
        NO_UPDATE,
        INITIALIZED,
        UPDATED
    }

    private record PbeCheckResult(PbeResultType type, String previous, String latest) {
        static PbeCheckResult noUpdate(String latest) {
            return new PbeCheckResult(PbeResultType.NO_UPDATE, latest, latest);
        }

        static PbeCheckResult initialized(String latest) {
            return new PbeCheckResult(PbeResultType.INITIALIZED, null, latest);
        }

        static PbeCheckResult updated(String previous, String latest) {
            return new PbeCheckResult(PbeResultType.UPDATED, previous, latest);
        }

        String manualMessage(String pbeNotesUrl) {
            String suffix = (pbeNotesUrl == null || pbeNotesUrl.isBlank())
                    ? ""
                    : "\nPBE notes feed: " + pbeNotesUrl;

            return switch (type) {
                case NO_UPDATE -> "No new PBE build yet. Latest PBE build is still `" + latest + "`." + suffix;
                case INITIALIZED -> "Manual PBE check complete. Baseline PBE build set to `" + latest + "`." + suffix;
                case UPDATED -> "New PBE build detected: `" + previous + "` -> `" + latest + "`." + suffix;
            };
        }
    }

    private enum PbeResultType {
        NO_UPDATE,
        INITIALIZED,
        UPDATED
    }
}
