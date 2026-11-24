# Variables: Bot de Discord (puente de chat)

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** settings/discord.yml → discord-bot.bridge.to-discord-format y bridge.to-minecraft-format  
**Sintaxis:** `{nombre}`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `{player}` | Jugador que escribe en el juego | `"**{player}**: {message}"` | settings/discord.yml → discord-bot.bridge.to-discord-format |
| `{user}` | Apodo en el servidor de Discord (o nombre global) | `"&9[Discord] &b{user}&7: &f{message}"` | settings/discord.yml → discord-bot.bridge.to-minecraft-format |
| `{message}` | Mensaje: sin menciones masivas hacia Discord; sin colores ni códigos hacia el juego | `"{player}: {message}"` | to-discord-format, to-minecraft-format |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.
