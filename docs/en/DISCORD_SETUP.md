# Discord bot — setup guide

Module `discord-bot`: status panel (`/panel`), `/stats`, `/top`, `/online`, `/staff`, custom commands, chat bridge, and DiscordSRV roles.

> [!NOTE]
> Separate from `discord-webhook` (notices only). Both can run together. Docs: https://github.com/LosPerris/LosPerrisStatsAPI

---

## 1. Create the app and bot
1. Open <https://discord.com/developers/applications> → **New Application**.
2. Under **Bot**, **Reset Token** and copy it (do not share it).
3. Enable **MESSAGE CONTENT INTENT** only if you use the chat bridge.

## 2. Invite the bot
OAuth2 → URL Generator: scopes `bot` + `applications.commands`. Permissions: View Channels, Send Messages, Embed Links, Read Message History; Manage Roles only if you use DiscordSRV roles.

## 3. Configure the plugin
```yaml
modules:
  discord-bot: true
```
In `settings/discord.yml` → `discord-bot:`:
```yaml
token: ""                      # or LPA_DISCORD_TOKEN
guild-id: "123..."             # guild commands (instant)
commands: true
panel:
  refresh-interval: "5m"
  default-channel-id: ""
  server-ip: "play.example.net"
  server-version: "1.21.4"
  embed:
    title: "Server online"
    description: "Server information panel."
    color: "#57F287"
    author-name: "LosPerris"
    footer-text: "LosPerrisStats • next update in {next_update}"
    timestamp: true
```

Embed/footer variables: `{online}`, `{max}`, `{players}`, `{tps}`, `{uptime}`, `{ip}`, `{version}`,
`{refresh}`, `{next_update}`, `{bar}`, `{pct}`, `{status}`. If `server-ip` is empty, the IP field is omitted.

## 4. `/panel`
`/panel channel:#status` posts (or updates) the embed. It auto-refreshes every `refresh-interval`.

Buttons:
| Button | Effect |
| --- | --- |
| Refresh | Edits the public panel |
| Online / Tops / Info | **Ephemeral** embed (only the clicker) |

## 5. Other slash commands
| Command | Use |
| --- | --- |
| `/stats <player>` | Saved stats (player required) |
| `/top <stat> [amount]` | Leaderboard (3–15) |
| `/online` | List + occupancy bar |
| `/staff <command>` | Filtered console (roles in `staff.role-ids`) |

### custom-commands
Up to 15 YAML slash commands (`reply`, `embed`, `options`, `action: stats|online|reply`). Reserved names: panel, stats, top, online, staff.

## 6. Modes
`auto` (DiscordSRV if present), `embedded`, `discordsrv`. Built-in chat bridge only in embedded. Auto roles only with DiscordSRV.

## 7. Replacing the JAR
Old configs still load: `link:` keys are ignored; `data/discord_links.json` is not deleted. After update, restart or `/lpa reload`; slash commands re-register on connect.

## 8. LosPerrisExtras addon
Season/event/alert notices use the **webhook** (`DiscordApi`), not this bot. The active title can appear on `/stats` via `playerTag`.
https://github.com/LosPerris/LosPerrisExtras
