# Variables: Language messages

> File generated automatically from the plugin registry (`core/text/Variables.java`). Do not edit it: it is overwritten on startup.

**Used in:** lang/es.yml, lang/en.yml (and any lang/<code>.yml)  
**Syntax:** `{name}`

| Variable | Description | Example | Valid in |
|---|---|---|---|
| `{player}` | Name of the player who receives or triggers the message | `"&a¡Hola {player}!"` | Messages that mention it (see the file comments) |
| `{target}` | Player being acted on (view, reset, admin...) or the material/mob of a quest objective | `"Estadísticas de {target} reseteadas."` | Messages that mention it (see the file comments) |
| `{value}` | Already formatted numeric value | `"Valor actual: {value}"` | Messages that mention it (see the file comments) |
| `{stat}` | Translated statistic name | `"{stat}: {value}"` | Messages that mention it (see the file comments) |
| `{type}` | Ranking category typed by the user | `"TOP 10 {type}"` | Messages that mention it (see the file comments) |
| `{position}` | Position in a ranking | `"#{position} {player} - {value}"` | Messages that mention it (see the file comments) |
| `{usage}` | Correct command syntax | `"Uso: {usage}"` | Messages that mention it (see the file comments) |
| `{version}` | Plugin version | `"Versión: {version}"` | Messages that mention it (see the file comments) |
| `{authors}` | Plugin authors | `"Autores: {authors}"` | Messages that mention it (see the file comments) |
| `{language}` | Language code (es, en...) | `"Idioma cambiado a {language}"` | Messages that mention it (see the file comments) |
| `{languages}` | Available languages, comma separated | `"Disponibles: {languages}"` | Messages that mention it (see the file comments) |
| `{module}` | Module identifier | `"Módulo {module} desactivado"` | Messages that mention it (see the file comments) |
| `{modules}` | List of modules | `"Activos: {modules}"` | Messages that mention it (see the file comments) |
| `{count}` | Generic amount | `"{count} entradas"` | Messages that mention it (see the file comments) |
| `{amount}` | Money amount already formatted by the economy plugin | `"+{amount} recibido"` | Messages that mention it (see the file comments) |
| `{task}` | Internal task name (timings) | `"{task}: {avg} ms"` | Messages that mention it (see the file comments) |
| `{avg}` | Average time in milliseconds | `"{task}: {avg} ms"` | Messages that mention it (see the file comments) |
| `{max}` | Maximum time in milliseconds | `"máx {max} ms"` | Messages that mention it (see the file comments) |
| `{runs}` | Number of runs | `"{runs} ejecuciones"` | Messages that mention it (see the file comments) |
| `{ratio}` | Already formatted ratio (KDR) | `"KDR: {ratio}"` | Messages that mention it (see the file comments) |
| `{period}` | Translated period name (weekly, monthly) | `"Ranking {period}"` | Messages that mention it (see the file comments) |
| `{time}` | Remaining time, already formatted (2d 4h) | `"Termina en {time}"` | Messages that mention it (see the file comments) |
| `{rank}` | Player position in a ranking (or «-») | `"Puesto #{rank}"` | Messages that mention it (see the file comments) |
| `{rarity}` | Percentage of players who own the achievement | `"{rarity}% de los jugadores"` | Messages that mention it (see the file comments) |
| `{tier}` | Translated rarity tier (common, rare, epic...) | `"Rareza: {tier}"` | Messages that mention it (see the file comments) |
| `{obtained}` | Achievements obtained | `"{obtained}/{total}"` | Messages that mention it (see the file comments) |
| `{total}` | Total (achievements, players...) | `"{obtained}/{total}"` | Messages that mention it (see the file comments) |
| `{page}` | Page number | `"Página {page}"` | Messages that mention it (see the file comments) |
| `{days}` | Days (remaining time) | `"{days}d {hours}h"` | Messages that mention it (see the file comments) |
| `{hours}` | Hours (remaining time) | `"{hours}h {minutes}m"` | Messages that mention it (see the file comments) |
| `{minutes}` | Minutes (remaining time) | `"{hours}h {minutes}m"` | Messages that mention it (see the file comments) |
| `{quest}` | Translated quest name | `"Misión completada: {quest}"` | Messages that mention it (see the file comments) |
| `{progress}` | Current progress, already formatted | `"{progress}/{goal}"` | Messages that mention it (see the file comments) |
| `{goal}` | Goal, already formatted | `"{progress}/{goal}"` | Messages that mention it (see the file comments) |
| `{reward}` | Reward as text (or «-») | `"Recompensa: {reward}"` | Messages that mention it (see the file comments) |
| `{duration}` | Duration, already formatted (or ∞ if permanent) | `"Silenciado ({duration})"` | Messages that mention it (see the file comments) |
| `{reason}` | Reason of a sanction | `"Motivo: {reason}"` | Messages that mention it (see the file comments) |
| `{action}` | Moderation action (kick, mute, ban...) | `"{action} aplicado"` | Messages that mention it (see the file comments) |
| `{by}` | Who applied the sanction | `"(por {by})"` | Messages that mention it (see the file comments) |
| `{date}` | Date and time (YYYY-MM-DD HH:mm) | `"{date} {action}"` | Messages that mention it (see the file comments) |
| `{size}` | File size, already formatted | `"{value} ({size})"` | Messages that mention it (see the file comments) |
| `{threshold}` | Configured alert threshold | `"TPS {value} (umbral {threshold})"` | Messages that mention it (see the file comments) |
| `{url}` | Link (e.g. to the new version) | `"Descargar: {url}"` | Messages that mention it (see the file comments) |
| `{season}` | Season name | `"Termina la {season}"` | Messages that mention it (see the file comments) |
| `{number}` | Generic number (season, players, holograms...) | `"{number} conectados"` | Messages that mention it (see the file comments) |
| `{title}` | Name of a title or heading | `"Nuevo título: {title}"` | Messages that mention it (see the file comments) |
| `{event}` | Event name | `"¡Empieza {event}!"` | Messages that mention it (see the file comments) |
| `{score}` | Player score in the event | `"Llevas {score}"` | Messages that mention it (see the file comments) |
| `{source}` | Ranking source (all time, weekly, season...) | `"{stat} ({source})"` | Messages that mention it (see the file comments) |
| `{world}` | World name | `"{world} {x} {y} {z}"` | Messages that mention it (see the file comments) |
| `{x}` | X coordinate | `"{world} {x} {y} {z}"` | Messages that mention it (see the file comments) |
| `{y}` | Y coordinate | `"{world} {x} {y} {z}"` | Messages that mention it (see the file comments) |
| `{z}` | Z coordinate | `"{world} {x} {y} {z}"` | Messages that mention it (see the file comments) |
| `{code}` | Discord link code | `"Use /link {code}"` | Messages that mention it (see the file comments) |
| `{user}` | Discord user name | `"Vinculado con {user}"` | Messages that mention it (see the file comments) |
| `{permission}` | Permission name | `"Permiso: {permission}"` | Messages that mention it (see the file comments) |
| `{file}` | Configuration file | `"Ajustes: {file}"` | Messages that mention it (see the file comments) |
| `{category}` | Module category (translated) | `"▸ {category}"` | Messages that mention it (see the file comments) |
| `{description}` | Translated description | `"{description}"` | Messages that mention it (see the file comments) |
| `{command}` | Command ready to type | `"Clic: {command}"` | Messages that mention it (see the file comments) |

If a file uses a variable that is not in this table, the console prints a warning on load or on `/lpa reload`.
