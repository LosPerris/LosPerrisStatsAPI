# Variables: Mensajes de hitos

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** settings/milestones.yml: message, broadcast, title, actionbar  
**Sintaxis:** `%nombre%`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `%player%` | Nombre del jugador que completa el hito | `"&a¡%player% ha roto 1000 bloques!"` | message, broadcast, title, actionbar |
| `%message%` | Mensaje del hito ya formateado | `actionbar: "&e✦ %message% ✦"` | actionbar (global y por hito) |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.
