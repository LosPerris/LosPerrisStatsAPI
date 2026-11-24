# Variables: Scoreboard

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** settings/scoreboard.yml → title, lines and event-lines  
**Syntax:** `%name%`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `%player_name%` | Player name | `"   %player_name%"` | title, lines |
| `%kills%` | PvP kills | `"%kills%"` | title, lines |
| `%deaths%` | Deaths | `"%deaths%"` | title, lines |
| `%mob_kills%` | Mobs killed | `"%mob_kills%"` | title, lines |
| `%time%` | Hours played (format 1.25h) | `"%time%"` | title, lines |
| `%blocks_broken%` | Blocks broken | `"%blocks_broken%"` | title, lines |
| `%blocks_placed%` | Blocks placed (sum of the vanilla block use statistics) | `"%blocks_placed%"` | title, lines |
| `%ores_mined%` | Ores mined (every ore type) | `"%ores_mined%"` | title, lines |
| `%jumps%` | Jumps | `"%jumps%"` | title, lines |
| `%distance_walked%` | Distance walked (format 1.25km) | `"%distance_walked%"` | title, lines |
| `%fish_caught%` | Fish caught | `"%fish_caught%"` | title, lines |
| `%trades%` | Villager trades | `"%trades%"` | title, lines |
| `%damage_dealt%` | Damage dealt in hearts (format 12.5❤) | `"%damage_dealt%"` | title, lines |
| `%damage_taken%` | Damage taken in hearts (format 12.5❤) | `"%damage_taken%"` | title, lines |
| `%top_<type>_<position>_<name|value>%` | Name or value of a ranking position. Types: kills, deaths, mob_kills, time, blocks_broken, blocks_placed, ores_mined, jumps, distance_walked, fish_caught, trades, damage_dealt, damage_taken. Position 1-100. | `"%top_kills_1_name%: %top_kills_1_value%"` | title, lines |
| `%<weekly|monthly>_<type>_<position>_<name|value>%` | Name or value of a weekly or monthly ranking position. Only the stats listed in stats in config.yml → periods. Position 1-100. Requires the periods module. | `"%weekly_kills_1_name%"` | title, lines |
| `%<weekly|monthly>_<type>%` | The player's amount in the current period. Only the stats listed in stats in config.yml → periods. Position 1-100. Requires the periods module. | `"Semana: %weekly_kills%"` | title, lines |
| `%achievements_obtained%` | Achievements the player owns (requires the achievements module) | `%achievements_obtained%` | title, lines |
| `%achievements_total%` | Total number of achievements | `%achievements_total%` | title, lines |
| `%achievements_percent%` | Percentage of achievements obtained (0-100) | `%achievements_percent%` | title, lines |
| `%season_<type>%` | The player's amount this season (seasons module) | `%season_kills%` | title, lines |
| `%season_rank_<type>%` | Player position this season (or «-») | `%season_rank_kills%` | title, lines |
| `%season_<type>_<position>_<name|value>%` | Name or value of a season ranking position | `%season_kills_1_name%` | title, lines |
| `%event_<position>_<name|value>%` | Name or value of a position in the running event («---»/0 with no event) | `%event_1_name%` | title, lines |
| `%season_name%` | Current season name (seasons module) | `%season_name%` | title, lines |
| `%season_number%` | Current season number | `%season_number%` | title, lines |
| `%season_time_left%` | Time until the season ends (∞ if manual) | `%season_time_left%` | title, lines |
| `%season_days_left%` | Days until the season ends (∞ if manual) | `%season_days_left%` | title, lines |
| `%title%` | Active title with its format (titles.format) or empty (titles module) | `%title%` | title, lines |
| `%title_name%` | Active title name, without format | `%title_name%` | title, lines |
| `%title_id%` | Active title id (settings/titles.yml) | `%title_id%` | title, lines |
| `%titles_unlocked%` | Titles the player has unlocked | `%titles_unlocked%` | title, lines |
| `%titles_total%` | Number of titles | `%titles_total%` | title, lines |
| `%event_active%` | true if an event is running (events module) | `%event_active%` | title, lines |
| `%event_name%` | Name of the running event | `%event_name%` | title, lines |
| `%event_stat%` | Event statistic | `%event_stat%` | title, lines |
| `%event_time%` | Time left in the event | `%event_time%` | title, lines |
| `%event_score%` | Player score in the event | `%event_score%` | title, lines |
| `%event_rank%` | Player position in the event (or «-») | `%event_rank%` | title, lines |
| `%discord_linked%` | true if the player linked their Discord account (discord-bot module) | `%discord_linked%` | title, lines |
| `%quests_completed%` | Quests completed in total (quests module) | `%quests_completed%` | title, lines |
| `%quests_daily_done%` | Daily quests completed today | `%quests_daily_done%` | title, lines |
| `%quests_daily_total%` | Active daily quests | `%quests_daily_total%` | title, lines |
| `%quests_daily_reset%` | Time until the daily quests change | `%quests_daily_reset%` | title, lines |
| `%quests_weekly_done%` | Weekly quests completed this week | `%quests_weekly_done%` | title, lines |
| `%quests_weekly_total%` | Active weekly quests | `%quests_weekly_total%` | title, lines |
| `%quests_weekly_reset%` | Time until the weekly quests change | `%quests_weekly_reset%` | title, lines |
| `%streak_current%` | Current login streak in days (streaks module) | `%streak_current%` | title, lines |
| `%streak_best%` | Best login streak | `%streak_best%` | title, lines |
| `%streak_next_reward%` | Streak day of the next reward (or «-») | `%streak_next_reward%` | title, lines |
| `%playtime_active%` | Active play hours, without AFK (playtime module) | `%playtime_active%` | title, lines |
| `%playtime_next%` | Active time left until the next reward | `%playtime_next%` | title, lines |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.

With PlaceholderAPI installed its placeholders are accepted too (`%vault_eco_balance%`, ...).
