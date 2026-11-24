# Variables: Discord

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** settings/discord.yml → discord-webhook.title  
**Syntax:** `{name}`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `{player}` | Player who completed the milestone | `"🏆 {player}"` | discord.title |
| `{stat}` | Milestone statistic (internal key, e.g. mob_kills) | `"{stat} completado"` | discord.title |
| `{value}` | Threshold reached | `"¡{value} alcanzado!"` | discord.title |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.
