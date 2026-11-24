# Variables: Recompensas

> Archivo generado automáticamente desde el registro del plugin (`core/text/Variables.java`). No lo edite: se sobrescribe al arrancar.

**Dónde se usan:** config.yml → periods y settings/<extra>.yml: rewards en milestones, quests, periods, streaks, playtime, seasons y events (y presets.<id>.rewards en events)  
**Sintaxis:** `%nombre%`

| Variable | Descripción | Ejemplo | Válida en |
|---|---|---|---|
| `%player%` | Nombre del jugador que recibe la recompensa | `"give %player% diamond 1"` | rewards (comandos de consola) |
| `%money:<cantidad>%` | Prefijo especial (no es variable): deposita dinero con Vault | `"money:500"` | rewards (línea completa) |
| `%item:<MATERIAL>:<cantidad>%` | Prefijo especial (no es variable): da un objeto; lo que no cabe cae al suelo | `"item:DIAMOND:3"` | rewards (línea completa) |
| `%command:<comando>%` | Prefijo opcional (no es variable): comando de consola, igual que una línea sin prefijo | `"command:give %player% emerald 2"` | rewards (línea completa) |

Si un archivo usa una variable que no está en esta tabla, la consola mostrará un aviso al cargar o al hacer `/lpa reload`.
