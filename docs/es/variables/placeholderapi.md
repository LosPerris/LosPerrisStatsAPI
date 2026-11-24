# Variables: PlaceholderAPI

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** Cualquier plugin compatible con PlaceholderAPI  
**Sintaxis:** `%nombre%`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `%losperrisapi_kills%` | Asesinatos PvP | `%losperrisapi_kills%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_deaths%` | Muertes | `%losperrisapi_deaths%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_mob_kills%` | Mobs eliminados | `%losperrisapi_mob_kills%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_time%` | Horas jugadas (formato 1.25h) | `%losperrisapi_time%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_blocks_broken%` | Bloques rotos | `%losperrisapi_blocks_broken%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_blocks_placed%` | Bloques colocados (suma de las estadísticas vanilla de uso de bloques) | `%losperrisapi_blocks_placed%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_ores_mined%` | Minerales extraídos (todos los tipos de mena) | `%losperrisapi_ores_mined%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_jumps%` | Saltos | `%losperrisapi_jumps%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_distance_walked%` | Distancia caminada (formato 1.25km) | `%losperrisapi_distance_walked%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_fish_caught%` | Peces pescados | `%losperrisapi_fish_caught%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_trades%` | Comercios con aldeanos | `%losperrisapi_trades%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_damage_dealt%` | Daño causado en corazones (formato 12.5❤) | `%losperrisapi_damage_dealt%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_damage_taken%` | Daño recibido en corazones (formato 12.5❤) | `%losperrisapi_damage_taken%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_language%` | Idioma que usa el plugin con el jugador (es, en...) | `%losperrisapi_language%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_top_<type>_<position>_<name|value>%` | Nombre o valor de una posición del ranking. Tipos: kills, deaths, mob_kills, time, blocks_broken, blocks_placed, ores_mined, jumps, distance_walked, fish_caught, trades, damage_dealt, damage_taken. Posición 1-100. | `%losperrisapi_top_time_1_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_<weekly|monthly>_<type>_<position>_<name|value>%` | Posición del ranking semanal o mensual. Solo las estadísticas de stats en config.yml → periods. Posición 1-100. Requiere el módulo periods. | `%losperrisapi_monthly_time_1_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_<weekly|monthly>_<type>%` | Lo que lleva el jugador en el periodo actual. Solo las estadísticas de stats en config.yml → periods. Posición 1-100. Requiere el módulo periods. | `%losperrisapi_weekly_kills%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_achievements_obtained%` | Logros obtenidos por el jugador (requiere el módulo achievements) | `%losperrisapi_achievements_obtained%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_achievements_total%` | Número total de logros | `%losperrisapi_achievements_total%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_achievements_percent%` | Porcentaje de logros obtenidos (0-100) | `%losperrisapi_achievements_percent%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_<type>%` | Lo que lleva el jugador en la temporada (módulo seasons) | `%losperrisapi_season_kills%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_rank_<type>%` | Puesto del jugador en la temporada (o «-») | `%losperrisapi_season_rank_kills%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_<type>_<position>_<name|value>%` | Nombre o valor de una posición del ranking de temporada | `%losperrisapi_season_kills_1_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_<position>_<name|value>%` | Nombre o valor de una posición del evento en curso («---»/0 sin evento) | `%losperrisapi_event_1_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_name%` | Nombre de la temporada actual (módulo seasons) | `%losperrisapi_season_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_number%` | Número de la temporada actual | `%losperrisapi_season_number%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_time_left%` | Tiempo hasta que termina la temporada (∞ si es manual) | `%losperrisapi_season_time_left%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_season_days_left%` | Días hasta que termina la temporada (∞ si es manual) | `%losperrisapi_season_days_left%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_title%` | Título activo con su formato (titles.format) o vacío (módulo titles) | `%losperrisapi_title%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_title_name%` | Nombre del título activo, sin formato | `%losperrisapi_title_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_title_id%` | Id del título activo (settings/titles.yml) | `%losperrisapi_title_id%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_titles_unlocked%` | Títulos conseguidos por el jugador | `%losperrisapi_titles_unlocked%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_titles_total%` | Títulos que existen | `%losperrisapi_titles_total%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_active%` | true si hay un evento en curso (módulo events) | `%losperrisapi_event_active%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_name%` | Nombre del evento en curso | `%losperrisapi_event_name%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_stat%` | Estadística del evento | `%losperrisapi_event_stat%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_time%` | Tiempo que le queda al evento | `%losperrisapi_event_time%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_score%` | Puntuación del jugador en el evento | `%losperrisapi_event_score%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_event_rank%` | Puesto del jugador en el evento (o «-») | `%losperrisapi_event_rank%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_discord_linked%` | true si el jugador vinculó su cuenta de Discord (módulo discord-bot) | `%losperrisapi_discord_linked%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_completed%` | Misiones completadas en total (módulo quests) | `%losperrisapi_quests_completed%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_daily_done%` | Misiones diarias completadas hoy | `%losperrisapi_quests_daily_done%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_daily_total%` | Misiones diarias activas | `%losperrisapi_quests_daily_total%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_daily_reset%` | Tiempo hasta que cambian las diarias | `%losperrisapi_quests_daily_reset%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_weekly_done%` | Misiones semanales completadas esta semana | `%losperrisapi_quests_weekly_done%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_weekly_total%` | Misiones semanales activas | `%losperrisapi_quests_weekly_total%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_quests_weekly_reset%` | Tiempo hasta que cambian las semanales | `%losperrisapi_quests_weekly_reset%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_streak_current%` | Racha de conexión actual en días (módulo streaks) | `%losperrisapi_streak_current%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_streak_best%` | Mejor racha de conexión | `%losperrisapi_streak_best%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_streak_next_reward%` | Día de racha de la próxima recompensa (o «-») | `%losperrisapi_streak_next_reward%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_playtime_active%` | Horas de juego activo, sin AFK (módulo playtime) | `%losperrisapi_playtime_active%` | Cualquier plugin con PlaceholderAPI |
| `%losperrisapi_playtime_next%` | Tiempo activo que falta para la próxima recompensa | `%losperrisapi_playtime_next%` | Cualquier plugin con PlaceholderAPI |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.
