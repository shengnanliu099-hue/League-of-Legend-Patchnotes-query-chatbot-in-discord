# LoL Version Watcher (Discord Bot)

一个 Java Discord 机器人：定时检查《英雄联盟》版本号是否更新，有更新就推送到指定频道。

## 功能

- 定时请求 Riot Data Dragon 版本接口（默认：`https://ddragon.leagueoflegends.com/api/versions.json`）
- 本地持久化上次已通知版本，避免重复发送
- 检测到新版本时自动发送频道消息
- 新版本消息会自动附带补丁内容摘要（从官方补丁文章提取）
- 支持 Discord Slash 命令手动查询：
  - `/lolcheck_live`：查询正式服更新
  - `/lolcheck_pbe`：查询 PBE 构建更新

## 环境要求

- Java 17+
- Maven 3.9+
- 一个 Discord Bot Token
- 目标频道 ID（`DISCORD_CHANNEL_ID`）

## 配置

通过环境变量配置：

- `DISCORD_TOKEN`：必填，机器人 token
- `DISCORD_CHANNEL_ID`：可选，兼容旧版的单频道推送 ID（数字）
- `CHECK_INTERVAL_MINUTES`：可选，检查间隔（分钟），默认 `30`
- `LOL_VERSIONS_URL`：可选，版本接口地址，默认 Riot Data Dragon
- `LOL_PBE_VERSION_URL`：可选，PBE 版本接口，默认 `https://raw.communitydragon.org/pbe/content-metadata.json`
- `LOL_PBE_PATCH_NOTES_URL`：可选，PBE 参考链接（展示在 `/lolcheck_pbe` 回复中）
- `LOL_VPBE_WIKI_URL`：可选，VPBE Wiki 链接（`/lolcheck_pbe` 会附带其页面更新信息）
- `LOL_LIVE_PATCH_NOTES_URL`：可选，正式服补丁列表入口（用于自动摘要抓取）
- `PBE_STATE_FILE`：可选，PBE 状态文件路径，默认 `data/lol-last-pbe-version.txt`
- `GUILD_CHANNELS_FILE`：可选，多服务器 LIVE 频道绑定文件，默认 `data/guild-live-channels.json`
- `POST_ON_STARTUP_INITIALIZATION`：可选，首次无历史状态时是否发初始化消息，默认 `false`
- `STATE_FILE`：可选，状态文件路径，默认 `data/lol-last-version.txt`

## 构建

```bash
mvn -q -DskipTests package
```

产物：`target/lol-version-watcher-1.0.0.jar`

## 运行

```bash
export DISCORD_TOKEN="你的token"
export DISCORD_CHANNEL_ID="123456789012345678"
export CHECK_INTERVAL_MINUTES="30"

java -jar target/lol-version-watcher-1.0.0.jar
```

## Discord 机器人权限

把机器人邀请到服务器时，确保至少有：

- View Channels
- Send Messages
- Use Application Commands（斜杠命令）

如果频道没找到，程序会报错并打印日志。

## 手动触发

机器人在线后，在服务器输入：

```text
/lolcheck_live
/lolcheck_pbe
/set_live_channel channel:#your-channel
/clear_live_channel
```

- `/lolcheck_live`：立即检查正式服版本；即使没有新版本，也会返回当前版本补丁摘要与链接。
- `/lolcheck_pbe`：立即检查 PBE 构建版本，只返回查询结果，不会触发自动频道推送；并附带 VPBE Wiki 更新信息。
- `/set_live_channel`：为当前服务器设置 LIVE 自动推送频道（需要 Manage Server 权限）。
- `/clear_live_channel`：清除当前服务器的 LIVE 自动推送频道（需要 Manage Server 权限）。
