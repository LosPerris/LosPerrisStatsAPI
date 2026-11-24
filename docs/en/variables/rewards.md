# Variables: Rewards

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** config.yml → periods and settings/<extra>.yml: rewards in milestones, quests, periods, streaks, playtime, seasons and events (and presets.<id>.rewards in events)  
**Syntax:** `%name%`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `%player%` | Name of the player who receives the reward | `"give %player% diamond 1"` | rewards (console commands) |
| `%money:<cantidad>%` | Special prefix (not a variable): deposits money with Vault | `"money:500"` | rewards (whole line) |
| `%item:<MATERIAL>:<cantidad>%` | Special prefix (not a variable): gives an item; what does not fit is dropped | `"item:DIAMOND:3"` | rewards (whole line) |
| `%command:<comando>%` | Optional prefix (not a variable): console command, same as a line without a prefix | `"command:give %player% emerald 2"` | rewards (whole line) |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.
