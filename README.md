# LoL Version Watcher (Discord Bot)

A Java-based Discord bot that monitors League of Legends version updates.
When a new LIVE version is detected, it automatically posts to configured Discord channel(s).

## Features

- Periodically checks Riot Data Dragon versions API (default: `https://ddragon.leagueoflegends.com/api/versions.json`)
- Persists last notified LIVE/PBE versions locally to avoid duplicate notifications
- Auto-pushes updates only for **LIVE** version changes
- Appends an auto-generated LIVE patch summary (title/highlights/link) to LIVE update messages
- Supports manual slash commands:
  - `/lolcheck_live`: check LIVE version
  - `/lolcheck_pbe`: check PBE build (with VPBE wiki info)
- Supports multi-server channel binding for LIVE auto notifications

## Requirements

- Java 17+
- Maven 3.9+
- A Discord Bot Token

## Configuration (Environment Variables)

- `DISCORD_TOKEN` (required): Discord bot token
- `DISCORD_CHANNEL_ID` (optional): legacy single-channel fallback channel ID
- `CHECK_INTERVAL_MINUTES` (optional): check interval in minutes, default `30`
- `LOL_VERSIONS_URL` (optional): LIVE versions API URL
- `LOL_PBE_VERSION_URL` (optional): PBE version API URL, default `https://raw.communitydragon.org/pbe/content-metadata.json`
- `LOL_PBE_PATCH_NOTES_URL` (optional): PBE notes feed link shown in `/lolcheck_pbe` replies
- `LOL_VPBE_WIKI_URL` (optional): VPBE wiki URL shown in `/lolcheck_pbe` replies
- `LOL_LIVE_PATCH_NOTES_URL` (optional): LIVE patch notes index URL used for auto summary extraction
- `STATE_FILE` (optional): LIVE state file path, default `data/lol-last-version.txt`
- `PBE_STATE_FILE` (optional): PBE state file path, default `data/lol-last-pbe-version.txt`
- `GUILD_CHANNELS_FILE` (optional): per-guild LIVE channel binding file, default `data/guild-live-channels.json`
- `POST_ON_STARTUP_INITIALIZATION` (optional): whether to post initialization message when baseline is first created, default `false`

## Build

```bash
mvn -q -DskipTests package
```

Artifact:

- `target/lol-version-watcher-1.0.0.jar`

## Run

```bash
export DISCORD_TOKEN="your_token"
export CHECK_INTERVAL_MINUTES="30"

java -jar target/lol-version-watcher-1.0.0.jar
```

## Discord Permissions

When inviting the bot, ensure it has at least:

- View Channels
- Send Messages
- Use Application Commands

## Slash Commands

Use these commands in your Discord server:

```text
/lolcheck_live
/lolcheck_pbe
/set_live_channel channel:#your-channel
/clear_live_channel
```

- `/lolcheck_live`: manually checks LIVE version; if no update, still returns the current LIVE patch summary
- `/lolcheck_pbe`: manually checks PBE build; does **not** trigger auto channel push; includes VPBE wiki info
- `/set_live_channel`: sets this server's channel for LIVE auto notifications (requires `Manage Server`)
- `/clear_live_channel`: removes this server's LIVE auto notification binding (requires `Manage Server`)

## Auto Notification Behavior

- Automatic push is triggered only by **LIVE** version changes.
- PBE updates are **manual query only**.
- Each server can bind its own LIVE notification channel using `/set_live_channel`.
