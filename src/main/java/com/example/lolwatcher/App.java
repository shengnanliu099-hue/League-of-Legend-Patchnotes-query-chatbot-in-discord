package com.example.lolwatcher;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public class App {
    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) throws Exception {
        Config config = Config.fromEnv();

        JDA jda = JDABuilder.createDefault(config.discordToken())
                .enableIntents(GatewayIntent.GUILD_MESSAGES)
                .setActivity(Activity.watching("LoL version updates"))
                .build()
                .awaitReady();

        LoLVersionClient versionClient = new LoLVersionClient(config.versionsUrl());
        PbeVersionClient pbeVersionClient = new PbeVersionClient(config.pbeVersionUrl());
        VpbeWikiClient vpbeWikiClient = new VpbeWikiClient(config.vpbeWikiUrl());
        PatchNotesClient patchNotesClient = new PatchNotesClient(config.livePatchNotesUrl(), "https://www.leagueoflegends.com/en-us/rss.xml");
        GuildLiveChannelStore guildLiveChannelStore = new GuildLiveChannelStore(config.guildChannelsFile());
        VersionStateStore stateStore = new VersionStateStore(config.stateFile());
        VersionStateStore pbeStateStore = new VersionStateStore(config.pbeStateFile());
        UpdateNotifierService notifierService = new UpdateNotifierService(
                jda,
                config,
                versionClient,
                pbeVersionClient,
                vpbeWikiClient,
                patchNotesClient,
                guildLiveChannelStore,
                stateStore,
                pbeStateStore
        );
        jda.addEventListener(new SlashCommandListener(notifierService));
        registerSlashCommands(jda);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down bot...");
            notifierService.stop();
            jda.shutdown();
        }));

        notifierService.start();
        log.info("Bot is ready as {}", jda.getSelfUser().getAsTag());

        // Keep process alive for scheduled checks until interrupted.
        new CountDownLatch(1).await();
    }

    private static void registerSlashCommands(JDA jda) {
        jda.getGuilds().forEach(guild -> guild
                .updateCommands()
                .addCommands(
                        Commands.slash(SlashCommandListener.COMMAND_LOL_CHECK_LIVE, "Manually check LIVE server updates"),
                        Commands.slash(SlashCommandListener.COMMAND_LOL_CHECK_PBE, "Manually check PBE build updates"),
                        Commands.slash(SlashCommandListener.COMMAND_SET_LIVE_CHANNEL, "Set this server's LIVE update channel")
                                .addOption(OptionType.CHANNEL, "channel", "Channel for LIVE auto notifications", true, false)
                                .setGuildOnly(true)
                                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER)),
                        Commands.slash(SlashCommandListener.COMMAND_CLEAR_LIVE_CHANNEL, "Clear this server's LIVE update channel")
                                .setGuildOnly(true)
                                .setDefaultPermissions(net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions.enabledFor(net.dv8tion.jda.api.Permission.MANAGE_SERVER))
                )
                .queue(
                        cmds -> log.info("Registered {} slash commands in guild {}", cmds.size(), guild.getName()),
                        err -> log.warn("Failed to register slash commands in guild {}", guild.getName(), err)
                ));
    }
}
