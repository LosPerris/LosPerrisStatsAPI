# Variables: Milestone messages

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** settings/milestones.yml: message, broadcast, title, actionbar  
**Syntax:** `%name%`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `%player%` | Name of the player who completes the milestone | `"&a¡%player% ha roto 1000 bloques!"` | message, broadcast, title, actionbar |
| `%message%` | Already formatted milestone message | `actionbar: "&e✦ %message% ✦"` | actionbar (global and per milestone) |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.
