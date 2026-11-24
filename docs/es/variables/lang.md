# Variables: Mensajes de idioma

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** lang/es.yml, lang/en.yml (y cualquier lang/<código>.yml)  
**Sintaxis:** `{nombre}`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `{player}` | Nombre del jugador que recibe o provoca el mensaje | `"&a¡Hola {player}!"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{target}` | Jugador sobre el que se actúa (view, reset, admin...) o material/criatura de un objetivo de misión | `"Estadísticas de {target} reseteadas."` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{value}` | Valor numérico ya formateado | `"Valor actual: {value}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{stat}` | Nombre traducido de la estadística | `"{stat}: {value}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{type}` | Categoría de ranking escrita por el usuario | `"TOP 10 {type}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{position}` | Posición en un ranking | `"#{position} {player} - {value}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{usage}` | Sintaxis correcta del comando | `"Uso: {usage}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{version}` | Versión del plugin | `"Versión: {version}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{authors}` | Autores del plugin | `"Autores: {authors}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{language}` | Código de idioma (es, en...) | `"Idioma cambiado a {language}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{languages}` | Idiomas disponibles separados por comas | `"Disponibles: {languages}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{module}` | Identificador de un módulo | `"Módulo {module} desactivado"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{modules}` | Lista de módulos | `"Activos: {modules}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{count}` | Cantidad genérica | `"{count} entradas"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{amount}` | Cantidad de dinero ya formateada por la economía | `"+{amount} recibido"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{task}` | Nombre interno de una tarea (timings) | `"{task}: {avg} ms"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{avg}` | Tiempo medio en milisegundos | `"{task}: {avg} ms"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{max}` | Tiempo máximo en milisegundos | `"máx {max} ms"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{runs}` | Número de ejecuciones | `"{runs} ejecuciones"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{ratio}` | Ratio ya formateado (KDR) | `"KDR: {ratio}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{period}` | Nombre traducido del periodo (semanal, mensual) | `"Ranking {period}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{time}` | Tiempo restante ya formateado (2d 4h) | `"Termina en {time}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{rank}` | Posición del jugador en un ranking (o «-») | `"Puesto #{rank}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{rarity}` | Porcentaje de jugadores que tienen el logro | `"{rarity}% de los jugadores"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{tier}` | Rareza traducida (común, raro, épico...) | `"Rareza: {tier}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{obtained}` | Logros obtenidos | `"{obtained}/{total}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{total}` | Total (logros, jugadores...) | `"{obtained}/{total}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{page}` | Número de página | `"Página {page}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{days}` | Días (tiempo restante) | `"{days}d {hours}h"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{hours}` | Horas (tiempo restante) | `"{hours}h {minutes}m"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{minutes}` | Minutos (tiempo restante) | `"{hours}h {minutes}m"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{quest}` | Nombre traducido de la misión | `"Misión completada: {quest}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{progress}` | Progreso actual ya formateado | `"{progress}/{goal}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{goal}` | Objetivo ya formateado | `"{progress}/{goal}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{reward}` | Recompensa en texto (o «-») | `"Recompensa: {reward}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{duration}` | Duración ya formateada (o ∞ si es permanente) | `"Silenciado ({duration})"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{reason}` | Motivo de una sanción | `"Motivo: {reason}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{action}` | Acción de moderación (kick, mute, ban...) | `"{action} aplicado"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{by}` | Quién aplicó la sanción | `"(por {by})"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{date}` | Fecha y hora (AAAA-MM-DD HH:mm) | `"{date} {action}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{size}` | Tamaño de archivo ya formateado | `"{value} ({size})"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{threshold}` | Umbral configurado de una alerta | `"TPS {value} (umbral {threshold})"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{url}` | Enlace (p. ej. a la versión nueva) | `"Descargar: {url}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{season}` | Nombre de la temporada | `"Termina la {season}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{number}` | Número genérico (temporada, jugadores, hologramas...) | `"{number} conectados"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{title}` | Nombre de un título o encabezado | `"Nuevo título: {title}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{event}` | Nombre del evento | `"¡Empieza {event}!"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{score}` | Puntuación del jugador en el evento | `"Llevas {score}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{source}` | Origen de un ranking (total, semanal, temporada...) | `"{stat} ({source})"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{world}` | Nombre de un mundo | `"{world} {x} {y} {z}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{x}` | Coordenada X | `"{world} {x} {y} {z}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{y}` | Coordenada Y | `"{world} {x} {y} {z}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{z}` | Coordenada Z | `"{world} {x} {y} {z}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{code}` | Código de vinculación de Discord | `"Use /link {code}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{user}` | Nombre de un usuario de Discord | `"Vinculado con {user}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{permission}` | Nombre de un permiso | `"Permiso: {permission}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{file}` | Archivo de configuración | `"Ajustes: {file}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{category}` | Categoría de un módulo (traducida) | `"▸ {category}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{description}` | Descripción traducida | `"{description}"` | Mensajes que la mencionan (ver comentarios del archivo) |
| `{command}` | Comando listo para escribir | `"Clic: {command}"` | Mensajes que la mencionan (ver comentarios del archivo) |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.
