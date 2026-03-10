package com.example.lolwatcher;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

public record Config(
        String discordToken,
        Long channelId,
        Duration checkInterval,
        String versionsUrl,
        String pbeVersionUrl,
        String pbePatchNotesUrl,
        String vpbeWikiUrl,
        String livePatchNotesUrl,
        boolean postOnStartupInitialization,
        Path stateFile,
        Path pbeStateFile,
        Path guildChannelsFile
) {
    private static final String DEFAULT_VERSIONS_URL = "https://ddragon.leagueoflegends.com/api/versions.json";
    private static final String DEFAULT_PBE_VERSION_URL = "https://raw.communitydragon.org/pbe/content-metadata.json";
    private static final String DEFAULT_PBE_PATCH_NOTES_URL = "https://www.reddit.com/r/LeaguePBE/new/";
    private static final String DEFAULT_VPBE_WIKI_URL = "https://wiki.leagueoflegends.com/en-us/VPBE";
    private static final String DEFAULT_LIVE_PATCH_NOTES_URL = "https://www.leagueoflegends.com/en-us/news/tags/patch-notes/";

    public static Config fromEnv() {
        Map<String, String> env = System.getenv();

        String token = require(env, "DISCORD_TOKEN");
        Long channelId = parseOptionalLong(env.get("DISCORD_CHANNEL_ID"), "DISCORD_CHANNEL_ID");

        long intervalMinutes = parseLong(env.getOrDefault("CHECK_INTERVAL_MINUTES", "1440"), "CHECK_INTERVAL_MINUTES");
        if (intervalMinutes <= 0) {
            throw new IllegalArgumentException("CHECK_INTERVAL_MINUTES must be > 0");
        }

        String versionsUrl = env.getOrDefault("LOL_VERSIONS_URL", DEFAULT_VERSIONS_URL);
        String pbeVersionUrl = env.getOrDefault("LOL_PBE_VERSION_URL", DEFAULT_PBE_VERSION_URL);
        String pbePatchNotesUrl = env.getOrDefault("LOL_PBE_PATCH_NOTES_URL", DEFAULT_PBE_PATCH_NOTES_URL);
        String vpbeWikiUrl = env.getOrDefault("LOL_VPBE_WIKI_URL", DEFAULT_VPBE_WIKI_URL);
        String livePatchNotesUrl = env.getOrDefault("LOL_LIVE_PATCH_NOTES_URL", DEFAULT_LIVE_PATCH_NOTES_URL);
        boolean postOnStartupInitialization = Boolean.parseBoolean(env.getOrDefault("POST_ON_STARTUP_INITIALIZATION", "false"));
        Path stateFile = Path.of(env.getOrDefault("STATE_FILE", "data/lol-last-version.txt"));
        Path pbeStateFile = Path.of(env.getOrDefault("PBE_STATE_FILE", "data/lol-last-pbe-version.txt"));
        Path guildChannelsFile = Path.of(env.getOrDefault("GUILD_CHANNELS_FILE", "data/guild-live-channels.json"));

        return new Config(
                token,
                channelId,
                Duration.ofMinutes(intervalMinutes),
                versionsUrl,
                pbeVersionUrl,
                pbePatchNotesUrl,
                vpbeWikiUrl,
                livePatchNotesUrl,
                postOnStartupInitialization,
                stateFile,
                pbeStateFile,
                guildChannelsFile
        );
    }

    private static String require(Map<String, String> env, String key) {
        String value = env.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required env var: " + key);
        }
        return value;
    }

    private static long parseLong(String value, String key) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid number for " + key + ": " + value, ex);
        }
    }

    private static Long parseOptionalLong(String value, String key) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseLong(value, key);
    }
}
