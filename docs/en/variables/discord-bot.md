# Variables: Discord bot (chat bridge)

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** settings/discord.yml → discord-bot.bridge.to-discord-format and bridge.to-minecraft-format  
**Syntax:** `{name}`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `{player}` | Player writing in game | `"**{player}**: {message}"` | settings/discord.yml → discord-bot.bridge.to-discord-format |
| `{user}` | Discord server nickname (or global name) | `"&9[Discord] &b{user}&7: &f{message}"` | settings/discord.yml → discord-bot.bridge.to-minecraft-format |
| `{message}` | Message: without mass mentions towards Discord; without colors or codes towards the game | `"{player}: {message}"` | to-discord-format, to-minecraft-format |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.
