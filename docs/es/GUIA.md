# Guía rápida de LosPerrisStatsAPI

English: [GUIDE.md](../en/GUIDE.md)

Esta guía le explica cómo poner en marcha el plugin y qué hace cada módulo. Para los detalles técnicos vea la [referencia de la API](API_REFERENCE.md) y las [variables](variables/README.md).

> **Misiones, temporadas, títulos, eventos, rachas, tiempo activo, hologramas, moderación y alertas** ya no están en este plugin: están en el addon **LosPerrisExtras**. Con el addon instalado, sus comandos, módulos e interruptores se usan igual que los del núcleo (`/lpa`, `/lpa modules`, `config.yml`). Vea su [guía](https://github.com/LosPerris/LosPerrisExtras/blob/main/docs/es/GUIA.md).

## 1. Primeros 5 minutos

1. Copie el JAR en `plugins/` y arranque el servidor. Se crean `config.yml`, la carpeta `settings/` (`web.yml`, `discord.yml`, `database.yml` y un archivo por cada módulo activo que lo necesite), `lang/` y `data/`.
2. Abra el dashboard en `http://<ip>:25908/dashboard` (puerto en `settings/web.yml`, `port`). La api-key se genera la primera vez: aparece en la consola y queda guardada en `settings/web.yml` (`security.api-key`).
3. Escriba `/lpa modules` para ver qué está activo, agrupado por categorías. Pulse un módulo para ver su detalle.
4. Active o apague módulos con `/lpa modules enable|disable <módulo>` (o en `config.yml` y luego `/lpa reload`).
5. Escriba `/lpa help` para ver los comandos que tiene disponibles.

## 2. Dónde está cada cosa

Carpeta `plugins/LosPerrisStatsAPI/` del servidor:

| Carpeta o archivo | Para qué sirve |
|---|---|
| `config.yml` | El único archivo de la raíz: idioma, zona horaria, qué módulos están activos (`modules:`, en tres grupos: núcleo, web y servidor y datos) y las secciones del núcleo (`stats`, `periods`, `history`, `backups`, `updates`, `advanced`). Empieza con el banner de LosPerrisStats y un recuadro «EMPIECE AQUÍ» |
| `settings/web.yml` | Módulo `web`: puerto, seguridad, api-key, CORS |
| `settings/discord.yml` | Módulos `discord-webhook` y `discord-bot`, una sección cada uno (token, webhook, roles, puente de chat) |
| `settings/database.yml` | Conexión a MySQL (opcional, ver [Base de datos](#base-de-datos)) |
| `settings/<módulo>.yml` | Un archivo por módulo con ajustes propios (`afk.yml`, `scoreboard.yml`, `milestones.yml`…). Se crea al activar el módulo; el de un módulo apagado no se abre |
| `lang/` | Textos del juego (`es.yml`, `en.yml`). Puede añadir otro idioma copiando uno |
| `data/` | Los datos de los jugadores (antes `server_data/`). No los edite con el servidor encendido |
| `backups/` | Copias automáticas y las copias de la migración (`migration-v10-<fecha>.zip`) |

Esta guía ya no se copia al servidor: está en internet, enlazada desde `config.yml`, desde la cabecera de cada archivo y desde `/lpa help`.

**Tiempos:** los ajustes de tiempo se escriben con unidad: `"30s"`, `"5m"`, `"1h"`, `"1d"`. Un número solo sigue valiendo como antes (en la unidad que tenía esa clave).

**Estadísticas:** en las listas (`periods.stats`, hitos) se usan los mismos nombres que en `stats.tracked` (`player_kills`, `play_time`…). Los nombres cortos antiguos (`kills`, `time`) siguen funcionando.

<a id="base-de-datos"></a>

**Base de datos:** por defecto todo se guarda en archivos dentro de `data/` y no hace falta configurar nada. Para usar MySQL, rellene `settings/database.yml` (host, puerto, base, usuario, contraseña y el `pool` de conexiones) y reinicie el servidor.

## 3. Activar y apagar módulos

- **Desde el juego o la consola:** `/lpa modules enable <módulo>` o `/lpa modules disable <módulo>` (permiso `losperris.modules.toggle`). El cambio se guarda en `config.yml` sin tocar sus comentarios y se aplica al momento, sin recargar los demás módulos.
- **Grupos:** núcleo (`stats`, `web`, Discord, `milestones`, `scoreboard`, `afk`, `gui`, `placeholders`), web (`public-page`, `web-console`, `heatmap`) y servidor y datos (`periods`, `history`, `backups`, `updates`). Los módulos con ajustes propios tienen su archivo `settings/<módulo>.yml`, que se crea la primera vez que lo activa. `milestones` viene apagado porque da recompensas.
- **A mano:** cambie `modules.<módulo>: true|false` en `config.yml` y escriba `/lpa reload`.
- **Addons:** los módulos de un addon (por ejemplo LosPerrisExtras) aparecen en `/lpa modules` y en el dashboard, se recargan con `/lpa reload` y sus interruptores se añaden a `config.yml` en su propio grupo, apagados, sin tocar los que ya tenga:

  ```yaml
  modules:
    # ...módulos del núcleo...

    # ===== LosPerrisExtras =====
    quests: false         # Misiones → LosPerrisExtras/settings/quests.yml
    titles: false         # Títulos → LosPerrisExtras/settings/titles.yml
  ```

  Si quita el addon, esas líneas se quedan pero no hacen nada. Los ajustes de cada módulo siguen en la carpeta del addon.
- Al apagar un módulo también se apagan los que dependen de él (por ejemplo, apagar `web` apaga `public-page` y `web-console`). No se puede encender un módulo si su dependencia está apagada.
- Un módulo apagado no consume nada: sus ajustes no se leen (su archivo propio ni se abre) y no registra tareas, eventos o comandos.
- `/lpa modules` marca con ● los activos, con ○ los apagados y con ⚠ los activados que tienen un problema (por ejemplo, falta un plugin), con el motivo.

## 4. Permisos

`losperris.admin` (por defecto, los OP) incluye todos los permisos de administración. Puede dar solo uno concreto a su staff:

| Permiso | Permite |
|---|---|
| `losperris.stats.reset` | `/lpa reset <jugador>` |
| `losperris.inspect` | `/lpa view <jugador>` |
| `losperris.gui.admin` | `/lpa admin <jugador>` |
| `losperris.periods.admin` | `/lpa period end <weekly\|monthly>` |
| `losperris.backups.admin` | `/lpa backup [now\|list]` |
| `losperris.modules` | `/lpa modules [módulo]` |
| `losperris.modules.toggle` | `/lpa modules enable\|disable <módulo>` |
| `losperris.reload` | `/lpa reload` |
| `losperris.timings` | `/lpa timings [reset]` |
| `losperris.updates.notify` | Recibir el aviso de versión nueva al entrar |
| `losperris.scoreboard` | Ver el scoreboard y usar `/lpa sb` (todos los jugadores por defecto) |

## 5. Base

<a id="stats"></a>

### Estadísticas (`stats`)

Es la base de casi todo: cuenta kills, muertes, horas, bloques, menas, saltos, distancia, peces, comercios y daño, y precalcula los rankings en segundo plano.

- Activar: `modules.stats: true` en `config.yml` o `/lpa modules enable stats` (por defecto: activo)
- Ajustes: `config.yml → stats` — `tracked`: qué estadísticas se registran; `leaderboard-cache`: cada cuánto se recalculan los rankings
- Comandos: `/lpa top <tipo>` (jugador), `/lpa reset <jugador>` (admin)
- Permisos: `losperris.stats.reset`

<a id="afk"></a>

### AFK (`afk`)

Marca a los jugadores inactivos. Mientras están AFK no suman horas activas, hitos ni recompensas.

- Activar: `modules.afk: true` en `config.yml` o `/lpa modules enable afk` (por defecto: activo)
- Ajustes: `settings/afk.yml` — `timeout`: tiempo sin moverse para pasar a AFK (por ejemplo "5m")

## 6. Hitos

<a id="milestones"></a>

### Hitos (`milestones`)

Mensajes, efectos y recompensas cuando un jugador llega a un valor (por ejemplo 100 kills).

- Activar: `modules.milestones: true` en `config.yml` o `/lpa modules enable milestones` (por defecto: apagado)
- Necesita: el módulo `stats`
- Ajustes: `settings/milestones.yml` — `stats`: los hitos de cada estadística y sus recompensas; `sound / firework / title`: efectos al conseguirlo; `gallery`: galería de logros
- Galería de logros (antes el módulo `achievements`): cada hito es un logro con su rareza (% de jugadores que lo tiene, umbrales en `gallery.tiers`); página «Logros» en `/lpa menu` y en la web, y `%losperrisapi_achievements_obtained|total|percent%`
- Lista: al final de `settings/milestones.yml`, debajo de los ajustes
- Variables: [milestones.md](variables/milestones.md), [rewards.md](variables/rewards.md)


## 7. Tops por periodo

<a id="periods"></a>

### Tops semanal y mensual (`periods`)

Rankings de la semana y del mes. Al cerrar cada periodo anuncia a los ganadores y reparte premios.

- Activar: `modules.periods: true` en `config.yml` o `/lpa modules enable periods` (por defecto: activo)
- Necesita: el módulo `stats`
- Ajustes: `config.yml → periods` — `weekly / monthly`: qué periodos están activos; `rewards`: premios por puesto
- Comandos: `/lpa period [weekly|monthly]` (jugador), `/lpa period end <weekly|monthly>` (admin)
- Permisos: `losperris.periods.admin`
- Variables: [rewards.md](variables/rewards.md)


## 8. En el juego

<a id="scoreboard"></a>

### Scoreboard (`scoreboard`)

Scoreboard lateral con estadísticas y tops. Cada jugador puede ocultarlo con `/lpa sb`.

- Activar: `modules.scoreboard: true` en `config.yml` o `/lpa modules enable scoreboard` (por defecto: activo)
- Necesita: el módulo `stats`
- Ajustes: `settings/scoreboard.yml` — `lines`: las líneas y sus variables; `event-lines`: líneas durante un evento de LosPerrisExtras
- Comandos: `/lpa sb` (jugador)
- Permisos: `losperris.scoreboard`
- Variables: [scoreboard.md](variables/scoreboard.md)

<a id="gui"></a>

### Menús (`gui`)

Menús de inventario: estadísticas propias (`/lpa menu`), de otro jugador (`/lpa view`) y de administración (`/lpa admin`). No tiene archivo propio.

- Activar: `modules.gui: true` en `config.yml` o `/lpa modules enable gui` (por defecto: activo)
- Necesita: el módulo `stats`
- Ajustes: no tiene ajustes propios
- Comandos: `/lpa menu` (jugador), `/lpa view <jugador>` (admin), `/lpa admin <jugador>` (admin)
- Permisos: `losperris.inspect`, `losperris.gui.admin`

<a id="placeholders"></a>

### PlaceholderAPI (`placeholders`)

Expansión `%losperrisapi_...%` para PlaceholderAPI. Necesita PlaceholderAPI; sin él aparece como ⚠. No tiene archivo propio.

- Activar: `modules.placeholders: true` en `config.yml` o `/lpa modules enable placeholders` (por defecto: activo)
- Necesita: el módulo `stats`
- Requiere: PlaceholderAPI
- Ajustes: no tiene ajustes propios
- Variables: [placeholderapi.md](variables/placeholderapi.md)

## 9. Web

<a id="web"></a>

### Web (`web`)

API HTTP y dashboard web en `http://<ip>:<puerto>/dashboard`. La api-key de administración se genera sola la primera vez.

- Activar: `modules.web: true` en `config.yml` o `/lpa modules enable web` (por defecto: activo)
- Necesita: el módulo `stats`
- Ajustes: `settings/web.yml` — `port / bind-address`: dónde escucha; `security`: api-key, intentos fallidos y CORS

<a id="public-page"></a>

### Página pública (`public-page`)

Página y API públicas de solo lectura, sin api-key (`/?public`, `/api/public/*`). Viene apagado.

- Activar: `modules.public-page: true` en `config.yml` o `/lpa modules enable public-page` (por defecto: apagado)
- Necesita: el módulo `web`
- Ajustes: `settings/public-page.yml` — `sections`: qué secciones se publican; `rate-limit`: peticiones por IP

<a id="web-console"></a>

### Consola web (`web-console`)

Consola del servidor en vivo dentro del dashboard, solo para administradores, con comandos filtrados.

- Activar: `modules.web-console: true` en `config.yml` o `/lpa modules enable web-console` (por defecto: activo)
- Necesita: el módulo `web`
- Ajustes: `settings/web-console.yml` — `allow / deny`: qué comandos se permiten; `hide-ips`: ocultar IPs en el registro

<a id="heatmap"></a>

### Mapa de actividad (`heatmap`)

Mapa de calor del dashboard: media de jugadores conectados por día de la semana y hora.

- Activar: `modules.heatmap: true` en `config.yml` o `/lpa modules enable heatmap` (por defecto: activo)
- Ajustes: `settings/heatmap.yml` — `weeks`: semanas que se guardan

<a id="history"></a>

### Historial (`history`)

Guarda una foto diaria de las estadísticas de cada jugador para las gráficas de progreso.

- Activar: `modules.history: true` en `config.yml` o `/lpa modules enable history` (por defecto: activo)
- Necesita: el módulo `stats`
- Ajustes: `config.yml → history` — `retention-days`: días que se guardan

## 10. Servidor y datos

<a id="backups"></a>

### Copias de seguridad (`backups`)

Copia diaria en zip de los datos del plugin; guarda las últimas `keep`.

- Activar: `modules.backups: true` en `config.yml` o `/lpa modules enable backups` (por defecto: activo)
- Ajustes: `config.yml → backups` — `time`: hora de la copia; `keep`: cuántas copias guardar
- Comandos: `/lpa backup [now|list]` (admin)
- Permisos: `losperris.backups.admin`

<a id="updates"></a>

### Actualizaciones (`updates`)

Comprueba en segundo plano si hay una versión nueva y avisa a los administradores.

- Activar: `modules.updates: true` en `config.yml` o `/lpa modules enable updates` (por defecto: activo)
- Ajustes: `config.yml → updates` — `source / id`: dónde buscar (GitHub, Modrinth o Spigot)
- Permisos: `losperris.updates.notify`

## 11. Discord

<a id="discord-webhook"></a>

### Webhook de Discord (`discord-webhook`)

Envía hitos y ganadores a un canal de Discord mediante un webhook. Viene apagado; sin `webhook-url` válida aparece como ⚠.

- Activar: `modules.discord-webhook: true` en `config.yml` o `/lpa modules enable discord-webhook` (por defecto: apagado)
- Necesita: el módulo `stats`
- Ajustes: `settings/discord.yml → discord-webhook` — `webhook-url`: la URL del webhook
- Variables: [discord-webhook.md](variables/discord-webhook.md)

<a id="discord-bot"></a>

### Bot de Discord (`discord-bot`)

Bot de Discord bidireccional: `/stats`, `/top`, `/online`, puente de chat, vinculación de cuentas y roles. Usa DiscordSRV si está instalado; si no, necesita un token. Viene apagado.

- Activar: `modules.discord-bot: true` en `config.yml` o `/lpa modules enable discord-bot` (por defecto: apagado)
- Necesita: el módulo `stats`
- Requiere: DiscordSRV (opcional)
- Ajustes: `settings/discord.yml → discord-bot` — `mode`: DiscordSRV o integrado; `token`: token del bot (o la variable LPA_DISCORD_TOKEN)
- Comandos: `/lpa discord [link|unlink|status]` (jugador)
- Variables: [discord-bot.md](variables/discord-bot.md)

## 12. Actualizar desde una versión anterior

### Desde la 1.4 a la 1.5

Cambie el JAR (y el de LosPerrisExtras por la 1.2, que necesita esta versión). No hay migración del núcleo: al
arrancar, el addon pasa sus interruptores a `config.yml` del núcleo con el valor que tenían. Sus comandos pasan de
`/lpx` a `/lpa` (`/lpx` sigue funcionando por ahora).

### Desde la 1.3 (config-version 9) a la 1.4 (config-version 10)

1. Pare el servidor, cambie el JAR y, si usaba misiones, temporadas, títulos, eventos, rachas, tiempo activo, hologramas, moderación o alertas, ponga también `LosPerrisExtras` en `plugins/`.
2. La primera vez el plugin guarda todo en `backups/migration-v10-<fecha>.zip` y después mueve los ajustes de esos módulos (`settings/<módulo>.yml`) y sus datos (`data/seasons/`, `data/events/`, `data/holograms.json`, `data/sanctions/`) a `plugins/LosPerrisExtras/`. Sus `modules.<id>` pasan al `config.yml` del addon con el mismo valor.
3. El módulo `achievements` desaparece: su configuración pasa a `gallery:` dentro de `settings/milestones.yml` (activa si lo tenía activo).
4. No se pierde nada: sin el addon instalado, los archivos esperan en `plugins/LosPerrisExtras/` hasta que lo instale.

### Desde versiones más antiguas

1. Pare el servidor, cambie el JAR y arránquelo.
2. Si viene de una versión anterior (config-version 8 con `web.yml`, `discord.yml`… en la raíz, 7 con todo en `config.yml`, 6 con `modules/<id>.yml`, o 5 y anteriores con un `config.yml` largo), la primera vez el plugin guarda **todos** los archivos implicados en `backups/migration-v9-<fecha>.zip`, con sus rutas, y después lo reparte en el formato nuevo (config-version 9, y luego 10 como arriba): `config.yml` para lo general y el núcleo, y `settings/` para la web, Discord, la base de datos y cada extra.
3. Se conservan sus valores (api-key, tokens, webhooks, presets, recompensas, listas, módulos activados…). Los comentarios pasan a ser los de la plantilla nueva; los suyos siguen en el zip.
4. Un extra apagado y sin cambios respecto a la plantilla no crea archivo; se crea al activarlo. Si lo tenía personalizado, su archivo se crea igualmente.
5. Algunas claves cambian de nombre y pasan a usar tiempos con unidad (`auto-save-interval: 300` → `auto-save: "5m"`, `interval-minutes` → `update-every`, `check-hours` → `check-every`, `sample-minutes` → `sample-every`; `stats-folder` y `debug` pasan a `advanced`). Los nombres antiguos se siguen aceptando.
6. `server_data/` pasa a `data/`, `database/` a `settings/` y se borra la carpeta `docs/` antigua (la guía ahora es un enlace). Los ids `public` y `console` pasan a `public-page` y `web-console`; `/lpa modules` sigue aceptando los nombres antiguos.
7. `config.yml` se escribe el último: si algo falla, la migración se repite en el próximo arranque. Si un archivo no se puede leer, el plugin no cambia nada y lo dice en la consola.
8. Al arrancar, la consola avisa en una sola línea de los ajustes que no tienen efecto porque el módulo que necesitan está apagado (por ejemplo `periods.announce.discord` con `discord-webhook` apagado).

**Volver atrás:** el JAR anterior no entiende el formato nuevo. Pare el servidor, borre `config.yml` y `settings/`, vuelva a poner el JAR anterior, descomprima `backups/migration-v9-<fecha>.zip` en la carpeta del plugin y cambie el nombre de `data/` a `server_data/`.

## 13. Problemas frecuentes

| `/lpa modules` dice | Qué hacer |
|---|---|
| necesita el módulo <módulo> | Active primero la dependencia (`/lpa modules <módulo>` la muestra). |
| falta PlaceholderAPI | Instale PlaceholderAPI y reinicie. |
| sin token del bot (settings/discord.yml → discord-bot.token) | Ponga el token en `settings/discord.yml → discord-bot.token` (o instale DiscordSRV) y vuelva a activarlo. Vea [DISCORD_SETUP.md](DISCORD_SETUP.md). |
| sin webhook-url válida (settings/discord.yml → discord-webhook.webhook-url) | Ponga una `webhook-url` válida en `settings/discord.yml → discord-webhook` y escriba `/lpa reload`. |
| error al arrancar: … | Revise el error en la consola (por ejemplo, el puerto web ocupado), corríjalo y vuelva a activarlo. |
