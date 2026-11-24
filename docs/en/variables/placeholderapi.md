# Variables: PlaceholderAPI

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** Any PlaceholderAPI-compatible plugin  
**Syntax:** `%name%`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `%losperrisapi_kills%` | PvP kills | `%losperrisapi_kills%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_deaths%` | Deaths | `%losperrisapi_deaths%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_mob_kills%` | Mobs killed | `%losperrisapi_mob_kills%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_time%` | Hours played (format 1.25h) | `%losperrisapi_time%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_blocks_broken%` | Blocks broken | `%losperrisapi_blocks_broken%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_blocks_placed%` | Blocks placed (sum of the vanilla block use statistics) | `%losperrisapi_blocks_placed%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_ores_mined%` | Ores mined (every ore type) | `%losperrisapi_ores_mined%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_jumps%` | Jumps | `%losperrisapi_jumps%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_distance_walked%` | Distance walked (format 1.25km) | `%losperrisapi_distance_walked%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_fish_caught%` | Fish caught | `%losperrisapi_fish_caught%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_trades%` | Villager trades | `%losperrisapi_trades%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_damage_dealt%` | Damage dealt in hearts (format 12.5❤) | `%losperrisapi_damage_dealt%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_damage_taken%` | Damage taken in hearts (format 12.5❤) | `%losperrisapi_damage_taken%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_language%` | Language the plugin uses with the player (es, en...) | `%losperrisapi_language%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_top_<type>_<position>_<name|value>%` | Name or value of a ranking position. Types: kills, deaths, mob_kills, time, blocks_broken, blocks_placed, ores_mined, jumps, distance_walked, fish_caught, trades, damage_dealt, damage_taken. Position 1-100. | `%losperrisapi_top_time_1_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_<weekly|monthly>_<type>_<position>_<name|value>%` | Weekly or monthly ranking position. Only the stats listed in stats in config.yml → periods. Position 1-100. Requires the periods module. | `%losperrisapi_monthly_time_1_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_<weekly|monthly>_<type>%` | The player's amount in the current period. Only the stats listed in stats in config.yml → periods. Position 1-100. Requires the periods module. | `%losperrisapi_weekly_kills%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_achievements_obtained%` | Achievements the player owns (requires the achievements module) | `%losperrisapi_achievements_obtained%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_achievements_total%` | Total number of achievements | `%losperrisapi_achievements_total%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_achievements_percent%` | Percentage of achievements obtained (0-100) | `%losperrisapi_achievements_percent%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_<type>%` | The player's amount this season (seasons module) | `%losperrisapi_season_kills%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_rank_<type>%` | Player position this season (or «-») | `%losperrisapi_season_rank_kills%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_<type>_<position>_<name|value>%` | Name or value of a season ranking position | `%losperrisapi_season_kills_1_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_<position>_<name|value>%` | Name or value of a position in the running event («---»/0 with no event) | `%losperrisapi_event_1_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_name%` | Current season name (seasons module) | `%losperrisapi_season_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_number%` | Current season number | `%losperrisapi_season_number%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_time_left%` | Time until the season ends (∞ if manual) | `%losperrisapi_season_time_left%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_season_days_left%` | Days until the season ends (∞ if manual) | `%losperrisapi_season_days_left%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_title%` | Active title with its format (titles.format) or empty (titles module) | `%losperrisapi_title%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_title_name%` | Active title name, without format | `%losperrisapi_title_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_title_id%` | Active title id (settings/titles.yml) | `%losperrisapi_title_id%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_titles_unlocked%` | Titles the player has unlocked | `%losperrisapi_titles_unlocked%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_titles_total%` | Number of titles | `%losperrisapi_titles_total%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_active%` | true if an event is running (events module) | `%losperrisapi_event_active%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_name%` | Name of the running event | `%losperrisapi_event_name%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_stat%` | Event statistic | `%losperrisapi_event_stat%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_time%` | Time left in the event | `%losperrisapi_event_time%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_score%` | Player score in the event | `%losperrisapi_event_score%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_event_rank%` | Player position in the event (or «-») | `%losperrisapi_event_rank%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_discord_linked%` | true if the player linked their Discord account (discord-bot module) | `%losperrisapi_discord_linked%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_completed%` | Quests completed in total (quests module) | `%losperrisapi_quests_completed%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_daily_done%` | Daily quests completed today | `%losperrisapi_quests_daily_done%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_daily_total%` | Active daily quests | `%losperrisapi_quests_daily_total%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_daily_reset%` | Time until the daily quests change | `%losperrisapi_quests_daily_reset%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_weekly_done%` | Weekly quests completed this week | `%losperrisapi_quests_weekly_done%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_weekly_total%` | Active weekly quests | `%losperrisapi_quests_weekly_total%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_quests_weekly_reset%` | Time until the weekly quests change | `%losperrisapi_quests_weekly_reset%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_streak_current%` | Current login streak in days (streaks module) | `%losperrisapi_streak_current%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_streak_best%` | Best login streak | `%losperrisapi_streak_best%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_streak_next_reward%` | Streak day of the next reward (or «-») | `%losperrisapi_streak_next_reward%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_playtime_active%` | Active play hours, without AFK (playtime module) | `%losperrisapi_playtime_active%` | Any plugin with PlaceholderAPI |
| `%losperrisapi_playtime_next%` | Active time left until the next reward | `%losperrisapi_playtime_next%` | Any plugin with PlaceholderAPI |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.
