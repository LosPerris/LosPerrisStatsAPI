# Variables: Discord

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** settings/discord.yml → discord-webhook.title  
**Sintaxis:** `{nombre}`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `{player}` | Jugador que completó el hito | `"🏆 {player}"` | discord.title |
| `{stat}` | Estadística del hito (clave interna, ej. mob_kills) | `"{stat} completado"` | discord.title |
| `{value}` | Umbral alcanzado | `"¡{value} alcanzado!"` | discord.title |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.
