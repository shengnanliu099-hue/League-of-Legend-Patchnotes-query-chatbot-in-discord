package com.example.lolwatcher;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.Permission;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CompletableFuture;

public class SlashCommandListener extends ListenerAdapter {
    public static final String COMMAND_LOL_CHECK_LIVE = "lolcheck_live";
    public static final String COMMAND_LOL_CHECK_PBE = "lolcheck_pbe";
    public static final String COMMAND_SET_LIVE_CHANNEL = "set_live_channel";
    public static final String COMMAND_CLEAR_LIVE_CHANNEL = "clear_live_channel";

    private final UpdateNotifierService notifierService;

    public SlashCommandListener(UpdateNotifierService notifierService) {
        this.notifierService = notifierService;
    }

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event) {
        String name = event.getName();
        if (COMMAND_LOL_CHECK_LIVE.equals(name)) {
            handleAsync(event, notifierService::triggerManualLiveCheck);
            return;
        }

        if (COMMAND_LOL_CHECK_PBE.equals(name)) {
            handleAsync(event, notifierService::triggerManualPbeCheck);
            return;
        }

        if (COMMAND_SET_LIVE_CHANNEL.equals(name)) {
            handleSetLiveChannel(event);
            return;
        }

        if (COMMAND_CLEAR_LIVE_CHANNEL.equals(name)) {
            handleClearLiveChannel(event);
        }
    }

    private void handleAsync(@NotNull SlashCommandInteractionEvent event, java.util.function.Supplier<String> job) {
        event.deferReply(true).queue();
        CompletableFuture
                .supplyAsync(job)
                .thenAccept(message -> event.getHook().editOriginal(message).queue())
                .exceptionally(ex -> {
                    String detail = ex.getMessage() == null ? "unknown error" : ex.getMessage();
                    event.getHook().editOriginal("Manual check failed: " + detail).queue();
                    return null;
                });
    }

    private void handleSetLiveChannel(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("This command can only be used inside a server.").setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need `Manage Server` permission to use this command.").setEphemeral(true).queue();
            return;
        }

        OptionMapping channelOpt = event.getOption("channel");
        if (channelOpt == null || channelOpt.getType() != OptionType.CHANNEL) {
            event.reply("Missing required `channel` option.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        long channelId = channelOpt.getAsChannel().getIdLong();
        try {
            String message = notifierService.bindLiveChannel(guildId, channelId);
            event.reply(message).setEphemeral(true).queue();
        } catch (Throwable ex) {
            event.reply("Failed to bind channel: " + ex.getMessage()).setEphemeral(true).queue();
        }
    }

    private void handleClearLiveChannel(@NotNull SlashCommandInteractionEvent event) {
        if (!event.isFromGuild() || event.getGuild() == null) {
            event.reply("This command can only be used inside a server.").setEphemeral(true).queue();
            return;
        }

        if (event.getMember() == null || !event.getMember().hasPermission(Permission.MANAGE_SERVER)) {
            event.reply("You need `Manage Server` permission to use this command.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        String message = notifierService.unbindLiveChannel(guildId);
        event.reply(message).setEphemeral(true).queue();
    }
}
