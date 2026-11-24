# Variables: Scoreboard

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** settings/scoreboard.yml → title, lines y event-lines  
**Sintaxis:** `%nombre%`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `%player_name%` | Nombre del jugador | `"   %player_name%"` | title, lines |
| `%kills%` | Asesinatos PvP | `"%kills%"` | title, lines |
| `%deaths%` | Muertes | `"%deaths%"` | title, lines |
| `%mob_kills%` | Mobs eliminados | `"%mob_kills%"` | title, lines |
| `%time%` | Horas jugadas (formato 1.25h) | `"%time%"` | title, lines |
| `%blocks_broken%` | Bloques rotos | `"%blocks_broken%"` | title, lines |
| `%blocks_placed%` | Bloques colocados (suma de las estadísticas vanilla de uso de bloques) | `"%blocks_placed%"` | title, lines |
| `%ores_mined%` | Minerales extraídos (todos los tipos de mena) | `"%ores_mined%"` | title, lines |
| `%jumps%` | Saltos | `"%jumps%"` | title, lines |
| `%distance_walked%` | Distancia caminada (formato 1.25km) | `"%distance_walked%"` | title, lines |
| `%fish_caught%` | Peces pescados | `"%fish_caught%"` | title, lines |
| `%trades%` | Comercios con aldeanos | `"%trades%"` | title, lines |
| `%damage_dealt%` | Daño causado en corazones (formato 12.5❤) | `"%damage_dealt%"` | title, lines |
| `%damage_taken%` | Daño recibido en corazones (formato 12.5❤) | `"%damage_taken%"` | title, lines |
| `%top_<type>_<position>_<name|value>%` | Nombre o valor de una posición del ranking. Tipos: kills, deaths, mob_kills, time, blocks_broken, blocks_placed, ores_mined, jumps, distance_walked, fish_caught, trades, damage_dealt, damage_taken. Posición 1-100. | `"%top_kills_1_name%: %top_kills_1_value%"` | title, lines |
| `%<weekly|monthly>_<type>_<position>_<name|value>%` | Nombre o valor de una posición del ranking semanal o mensual. Solo las estadísticas de stats en config.yml → periods. Posición 1-100. Requiere el módulo periods. | `"%weekly_kills_1_name%"` | title, lines |
| `%<weekly|monthly>_<type>%` | Lo que lleva el jugador en el periodo actual. Solo las estadísticas de stats en config.yml → periods. Posición 1-100. Requiere el módulo periods. | `"Semana: %weekly_kills%"` | title, lines |
| `%achievements_obtained%` | Logros obtenidos por el jugador (requiere el módulo achievements) | `%achievements_obtained%` | title, lines |
| `%achievements_total%` | Número total de logros | `%achievements_total%` | title, lines |
| `%achievements_percent%` | Porcentaje de logros obtenidos (0-100) | `%achievements_percent%` | title, lines |
| `%season_<type>%` | Lo que lleva el jugador en la temporada (módulo seasons) | `%season_kills%` | title, lines |
| `%season_rank_<type>%` | Puesto del jugador en la temporada (o «-») | `%season_rank_kills%` | title, lines |
| `%season_<type>_<position>_<name|value>%` | Nombre o valor de una posición del ranking de temporada | `%season_kills_1_name%` | title, lines |
| `%event_<position>_<name|value>%` | Nombre o valor de una posición del evento en curso («---»/0 sin evento) | `%event_1_name%` | title, lines |
| `%season_name%` | Nombre de la temporada actual (módulo seasons) | `%season_name%` | title, lines |
| `%season_number%` | Número de la temporada actual | `%season_number%` | title, lines |
| `%season_time_left%` | Tiempo hasta que termina la temporada (∞ si es manual) | `%season_time_left%` | title, lines |
| `%season_days_left%` | Días hasta que termina la temporada (∞ si es manual) | `%season_days_left%` | title, lines |
| `%title%` | Título activo con su formato (titles.format) o vacío (módulo titles) | `%title%` | title, lines |
| `%title_name%` | Nombre del título activo, sin formato | `%title_name%` | title, lines |
| `%title_id%` | Id del título activo (settings/titles.yml) | `%title_id%` | title, lines |
| `%titles_unlocked%` | Títulos conseguidos por el jugador | `%titles_unlocked%` | title, lines |
| `%titles_total%` | Títulos que existen | `%titles_total%` | title, lines |
| `%event_active%` | true si hay un evento en curso (módulo events) | `%event_active%` | title, lines |
| `%event_name%` | Nombre del evento en curso | `%event_name%` | title, lines |
| `%event_stat%` | Estadística del evento | `%event_stat%` | title, lines |
| `%event_time%` | Tiempo que le queda al evento | `%event_time%` | title, lines |
| `%event_score%` | Puntuación del jugador en el evento | `%event_score%` | title, lines |
| `%event_rank%` | Puesto del jugador en el evento (o «-») | `%event_rank%` | title, lines |
| `%discord_linked%` | true si el jugador vinculó su cuenta de Discord (módulo discord-bot) | `%discord_linked%` | title, lines |
| `%quests_completed%` | Misiones completadas en total (módulo quests) | `%quests_completed%` | title, lines |
| `%quests_daily_done%` | Misiones diarias completadas hoy | `%quests_daily_done%` | title, lines |
| `%quests_daily_total%` | Misiones diarias activas | `%quests_daily_total%` | title, lines |
| `%quests_daily_reset%` | Tiempo hasta que cambian las diarias | `%quests_daily_reset%` | title, lines |
| `%quests_weekly_done%` | Misiones semanales completadas esta semana | `%quests_weekly_done%` | title, lines |
| `%quests_weekly_total%` | Misiones semanales activas | `%quests_weekly_total%` | title, lines |
| `%quests_weekly_reset%` | Tiempo hasta que cambian las semanales | `%quests_weekly_reset%` | title, lines |
| `%streak_current%` | Racha de conexión actual en días (módulo streaks) | `%streak_current%` | title, lines |
| `%streak_best%` | Mejor racha de conexión | `%streak_best%` | title, lines |
| `%streak_next_reward%` | Día de racha de la próxima recompensa (o «-») | `%streak_next_reward%` | title, lines |
| `%playtime_active%` | Horas de juego activo, sin AFK (módulo playtime) | `%playtime_active%` | title, lines |
| `%playtime_next%` | Tiempo activo que falta para la próxima recompensa | `%playtime_next%` | title, lines |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.

Con PlaceholderAPI instalado también se aceptan sus placeholders (`%vault_eco_balance%`, ...).
