# Registro de cambios

English: [CHANGELOG.md](CHANGELOG.md)

## 1.6.0 — Panel de estado en Discord

### Cambios
- Bot Discord: `/panel` publica un embed de estado configurable (barra online, registrados, TPS, IP, versión, uptime)
  con auto-refresh; botones con embeds ephemeral (Online/Tops/Info).
- `panel.embed` y `custom-commands` en `settings/discord.yml`; `/stats` (jugador obligatorio), `/top` (`amount`),
  `/online` y `/staff` mejorados.
- Eliminada la vinculación propia (`/link`, `/unlink`, `/lpa discord link`). Las claves `link:` antiguas no rompen;
  `data/discord_links.json` se conserva. Roles solo con DiscordSRV.
- Enlaces de docs: https://github.com/LosPerris/LosPerrisStatsAPI

### Cambios de comportamiento
- `/stats` sin nombre ya no usa cuenta vinculada.
- Los slash ya no incluyen `/link` / `/unlink`.

### Migración automática
- El merge del módulo añade `panel` / `custom-commands`. No sube `config-version`.

## 1.5.1 — evolución en el perfil web

### Cambios
- Perfil web: con LosPerrisExtras 1.3 y su módulo `evolution` activo, una tarjeta «Evolución» muestra el rango
  actual, el siguiente con su porcentaje, una barra por requisito y la próxima recompensa.

### Cambios de comportamiento
- Ninguno. LosPerrisExtras 1.3 también funciona con la 1.5 (sin esta tarjeta).

## 1.5 — addons integrados en el núcleo

### Cambios
- **API de comandos** (`api.commands()`): un addon añade subcomandos a `/lpa` con nombre, alias, permiso, ejecución,
  autocompletado y ayuda; salen en `/lpa help` y en el autocompletado, y se quitan con `unregisterAll(owner)`.
- **API de addons** (`api.addons()`): los módulos de un addon entran en el sistema de módulos del núcleo:
  `/lpa modules` (lista, detalle, `enable`/`disable`), la lista de módulos del dashboard y `/lpa reload` (que también
  recarga el addon).
- Los interruptores de un addon se escriben en `config.yml` del núcleo si faltan, apagados, bajo
  `# ===== <Addon> =====`, sin tocar los valores que ya estén. Si se quita el addon, las líneas se quedan sin efecto.
- `/lpa version` muestra los addons instalados y su versión.
- Perfil web: las misiones con varios objetivos muestran una barra pequeña por objetivo.
- Rendimiento: el perfil de un jugador sin datos del plugin ya no recorre todos los jugadores que han entrado alguna
  vez en cada petición (índice de nombres, renovado como mucho cada 5 minutos).
- Limpieza: código y importaciones sin uso, búsquedas de módulos seguras desde los hilos de la web.

### Cambios de comportamiento
- `LosPerrisApi.VERSION` pasa a 2.

### Migración automática
- Ninguna en el núcleo (config v10). LosPerrisExtras 1.2 pasa sus interruptores a `config.yml` del núcleo la primera vez.

## 1.4 — núcleo y addon LosPerrisExtras (config v10)

### Cambios
- **El núcleo vuelve a sus funciones originales, mejoradas:** estadísticas y rankings, API HTTP y dashboard web (con
  chat en vivo, página pública, consola web y mapa de actividad), Discord (webhook y bot), hitos con recompensas de
  Vault, scoreboard, AFK, menús, PlaceholderAPI, almacenamiento JSON/MySQL/PostgreSQL, tops semanal/mensual,
  historial, copias de seguridad y aviso de actualizaciones.
- **Lo que se añadió después pasa al addon [LosPerrisExtras](https://github.com/LosPerris/LosPerrisExtras):** misiones,
  rachas, tiempo activo, títulos, temporadas, eventos, hologramas, moderación y alertas. Tiene su propio comando `/lpx`,
  su carpeta (`plugins/LosPerrisExtras/`) y su `config.yml`.
- **Los logros se unen a los hitos:** la galería con rareza ahora es `gallery:` en `settings/milestones.yml` (los hitos
  siguen apagados por defecto porque dan recompensas). Los placeholders `%losperrisapi_achievements_*%`, la página
  «Logros» y `/api/achievements` siguen funcionando.
- **API Java pública para addons** (`me.gordi.mineapi.api.LosPerrisApi`): rutas HTTP, campos del perfil, páginas del
  menú, avisos de Discord, etiqueta del jugador, modo evento y los eventos internos. Documentada en API_REFERENCE §7.
- Los interruptores de `config.yml` se agrupan en núcleo, web y servidor y datos (175 líneas en una instalación nueva).
- `/lpa modules` y `/lpa help` solo muestran módulos del núcleo; la consola dice qué módulos están activos (o «ninguno»).

### Cambios de comportamiento
- Los comandos de los módulos del addon pasan de `/lpa` a `/lpx` (`/lpx quests`, `/lpx season`, `/lpx title`,
  `/lpx event`, `/lpx mod`, `/lpx holo`). Los permisos mantienen su nombre.
- Sin el addon (o con su módulo apagado), `/api/quests`, `/api/seasons`, `/api/events`, `/api/titles` y sus rutas de
  administración responden `404`.
- Corrección: `/api/titles` (y otros textos traducidos) sin `?lang=` ya no falla con 500.

### Migración automática (desde v9)
- Antes de tocar nada guarda todo en `backups/migration-v10-<fecha>.zip`.
- Mueve los `settings/<id>.yml` de los módulos del addon y sus datos (`data/seasons/`, `data/events/`,
  `data/holograms.json`, `data/sanctions/`) a `plugins/LosPerrisExtras/`; el addon importa sus interruptores al
  arrancar por primera vez. No se borra nada sin copia.
- `achievements` pasa a `gallery:` en `milestones.yml` (activa si lo estaba).
- En `lang/`, las categorías «Extras · web» y «Extras · servidor» pasan a «Web» y «Servidor y datos» si no las había cambiado.

## 1.3-V3 — fase 6 (config v9: raíz limpia y carpeta settings/)

### Cambios
- **En la raíz solo queda `config.yml`** (`config-version: 9`, ≈185 líneas): `general`, los interruptores separados en
  **núcleo** (stats, web, discord-webhook, discord-bot, periods, history, placeholders, gui, backups, updates) y **extras**,
  y las secciones `stats`, `periods`, `history`, `backups` y `updates`.
- **Carpeta `settings/`:** `web.yml`, `discord.yml` (secciones `discord-webhook` y `discord-bot`), `database.yml` y un
  `settings/<id>.yml` por cada extra. El archivo de un extra se crea la primera vez que se activa; uno apagado no se abre.
- **`server_data/` pasa a llamarse `data/`.** Las demás carpetas: `lang/` y `backups/`.
- **La documentación ya no se copia al servidor** (se borra la antigua carpeta `docs/`). Los comentarios remiten a
  `/lpa help`, `/lpa modules <id>` y a la guía publicada.
- **Ids nuevos:** `public` → `public-page` y `console` → `web-console` (los antiguos siguen funcionando como alias).
- **Duraciones legibles:** `5m`, `1h`, `1d`… Los números antiguos se siguen aceptando en su unidad de antes.
- **Nombres de estadística coherentes** con `stats.tracked` (`player_kills`, `play_time`…); `kills`, `time`, `hours`
  siguen valiendo como alias.
- **Valores por defecto:** web, consola web, heatmap, history, moderation, alerts, scoreboard, holograms y afk
  activados; página pública y Discord apagados; los extras de progresión (milestones, achievements, titles, events,
  quests, streaks, playtime, seasons) ahora vienen **apagados**.
- **Aviso de dependencias:** al arrancar y con `/lpa reload`, una sola línea en la consola si un ajuste no hace nada
  porque el módulo que necesita está apagado (p. ej. `moderation.discord: true` sin `discord-webhook`).
- Las plantillas ES y EN tienen exactamente las mismas claves (lo comprueba un test).
- `/lpa modules` agrupa en núcleo y extras y muestra el archivo correcto (`settings/web.yml`, `config.yml → stats`…).

### Migración automática (desde v8, v7, v6 o v5)
- Antes de tocar nada guarda todos los archivos de origen en `backups/migration-v9-<fecha>.zip`.
- Conserva sus valores y secretos (api-key, token, webhook, recompensas, listas…); los extras que nunca tocó no
  generan archivo hasta que los active.
- Mueve `server_data/` → `data/`, `database/database.yml` → `settings/database.yml` y borra `docs/`.
- **Volver atrás:** pare el servidor, borre `config.yml` y `settings/`, renombre `data/` a `server_data/`, descomprima
  el zip en la carpeta del plugin y vuelva al JAR anterior.

## 1.3-V2 — fase 5c (config v8: archivos propios para los módulos grandes)

### Cambios
- **`config.yml` pasa de ~990 a 359 líneas** (`config-version: 8`): ajustes generales, la lista `modules` y las secciones
  de los módulos pequeños, cada una con un título de una línea que enlaza a la guía (`# ── stats · Estadísticas · guía: GUIA.md#stats ──`).
- **Archivos propios para los módulos grandes**, con sus comentarios detallados: `web.yml`, `discord.yml` (secciones
  `discord-webhook` y `discord-bot`), `events.yml` y `scoreboard.yml`.
- **`milestones.yml`, `quests.yml` y `titles.yml`** llevan ahora los ajustes del módulo arriba y la lista debajo.
- **Banner grande de LosPerrisStats** al principio de `config.yml` y de cada archivo propio.
- El archivo de un módulo apagado no se abre nunca; si falta, se crea desde la plantilla al activar el módulo.
- Los archivos propios se editan por bloques de primer nivel: se conservan los comentarios. `/lpa modules enable|disable`
  sigue cambiando solo esa línea de `config.yml`.
- `/lpa modules <módulo>` muestra el archivo correcto (`web.yml`, `discord.yml → discord-bot`, `config.yml → stats`…).
- La guía tiene un ancla por módulo (`GUIA.md#<id>`) y dice dónde están los ajustes de cada uno.

### Migración automática (desde v7, v6 o v5)
- Lo lee todo primero; si algo no se puede leer, no cambia nada y lo indica.
- Guarda todos los archivos de origen en `backups/migration-v8-<fecha>.zip`, escribe los archivos de módulo y por último
  `config.yml` (si algo falla, se repite en el siguiente arranque) y retira `modules/`.
- Se conservan todos los valores (api-key, tokens, webhooks, presets, recompensas, listas…).
- **Volver atrás:** pare el servidor, borre `config.yml`, `web.yml`, `discord.yml`, `events.yml`, `scoreboard.yml`,
  `milestones.yml`, `quests.yml` y `titles.yml`, vuelva a poner el JAR anterior y descomprima el zip en la carpeta del plugin.

## 1.3-V2 — fase 5b (config.yml único)

### Cambios
- **De nuevo un único `config.yml` ordenado** (`config-version: 7`) en vez de 20 archivos: cabecera corta → `general` →
  la lista agrupada `modules` (activar/apagar) → una sección por módulo (`web:`, `events:`, `discord-bot:`…) en el orden
  de las categorías. Cada sección empieza con un recuadro: qué hace el módulo, sus comandos, permisos, variables y cómo activarlo.
- **Las listas siguen siendo archivos propios:** `milestones.yml`, `quests.yml` y `titles.yml` (además de `lang/`) solo
  tienen las listas largas; los ajustes de esos módulos están en su sección de `config.yml`.
- **Rendimiento:** al arrancar solo se analizan `config-version`, `general` y `modules`. La sección de un módulo se analiza
  la primera vez que se activa; la de un módulo apagado no se analiza nunca. Al escribir una sección (api-key nueva, claves
  nuevas, `/lpa modules enable|disable`) solo cambia ese bloque: se conservan los comentarios y el resto del archivo.
- `/lpa modules <módulo>` muestra ahora `Ajustes: config.yml → <id>` (y el archivo de la lista en milestones, quests y titles).
- Mensajes, guía, READMEs y documentación apuntan a `config.yml → <id>` en vez de `modules/<id>.yml`.
- Las copias (`include-configs`) guardan los `.yml` de la carpeta del plugin y además `lang/` y `database/`.

### Migración automática (desde v6 o v5)
- En el primer arranque el plugin lo lee todo antes; si algún archivo no se puede leer, no cambia nada y lo indica.
- Después guarda todos los archivos implicados (`config.yml`, `milestones.yml`, `quests.yml`, `titles.yml` y `modules/*.yml`),
  con sus rutas, en `backups/migration-v7-<fecha>.zip`, escribe las listas y el `config.yml` nuevo y por último retira
  `modules/` (está entera en el zip).
- Se conservan sus valores (api-key, tokens, webhooks, presets, recompensas…). Los comentarios son los de la plantilla nueva; los suyos están en el zip.
- **Volver atrás:** pare el servidor, borre `config.yml`, `milestones.yml`, `quests.yml` y `titles.yml`, vuelva a poner el
  JAR anterior y descomprima `backups/migration-v7-<fecha>.zip` en la carpeta del plugin.

## 1.3-V2 — fase 5

### Novedades
- **Un archivo por módulo:** `config.yml` queda corto (idioma, zona horaria, base de datos y la lista
  `modules: <id>: true|false`) y cada módulo tiene su `modules/<id>.yml` con una cabecera que explica qué hace,
  cómo activarlo, sus comandos y permisos. Un módulo apagado no lee su archivo.
- **`/lpa modules enable|disable <módulo>`:** activa o apaga un módulo sin reiniciar. Solo cambia esa línea de
  `config.yml` (los comentarios se conservan), al apagar uno se apagan también los que dependen de él y no deja
  activar uno cuya dependencia está apagada. `/lpa modules <módulo>` muestra el detalle: qué hace, dependencias,
  archivo, comandos, permisos y, si no arranca, el motivo.
- **Ayuda nueva:** `/lpa help` agrupa los comandos por categoría, solo muestra los que usted puede usar y se puede
  hacer clic en ellos. La ayuda de administración está en `/lpa help admin` (enlace clicable al final) y la de un
  módulo en `/lpa help <módulo>`.
- **Permisos por módulo**, todos hijos de `losperris.admin`: `losperris.reload`, `losperris.modules`,
  `losperris.modules.toggle`, `losperris.timings`, `losperris.stats.reset`, `losperris.gui.admin`,
  `losperris.periods.admin`, `losperris.seasons.admin`, `losperris.events.admin`, `losperris.titles.admin`,
  `losperris.holograms.admin`, `losperris.moderation`, `losperris.backups.admin`, `losperris.alerts.notify` y
  `losperris.updates.notify`.
- **Guía de administración** en español e inglés (`docs/es/GUIA.md`, `docs/en/GUIDE.md`), también copiada al
  servidor en `plugins/LosPerrisStatsAPI/docs/`.

### Cambios
- **Migración automática v5 → v6** (`config-version: 6`): al primer arranque se guardan `config.yml`, `milestones.yml`,
  `quests.yml` y `titles.yml` en `backups/migration-v6-<fecha>.zip` y después se reparte `config.yml` en `modules/<id>.yml`. `milestones.yml`,
  `quests.yml` y `titles.yml` pasan a `modules/`. Se conservan sus valores y nunca se sobrescribe un archivo de
  módulo que ya exista.
- **Volver atrás:** el JAR anterior no entiende el formato nuevo. Pare el servidor, ponga el JAR anterior y
  descomprima `backups/migration-v6-<fecha>.zip` sobre la carpeta del plugin.
- Los módulos `discord` y `discordbot` pasan a llamarse `discord-webhook` y `discord-bot`; `/lpa modules` sigue
  aceptando los nombres antiguos.
- Los comandos de administración piden su permiso propio en vez de `losperris.admin`. Quien tenga
  `losperris.admin` (o sea OP) no nota nada; si da permisos sueltos con LuckPerms, revise la tabla de la guía (§4).
- Toda la documentación está en `docs/` (antes también en `src/main/resources/docs/` y en la raíz) y hay un
  `README.md` en la raíz.
- Paquetes renombrados: `hook` → `core.hook`, `discord` → `discordwebhook` (`DiscordWebhookModule`),
  `discord.bot` → `discordbot` y `publicpage` → `publicweb`.

### Correcciones
- La cabecera de `modules/playtime.yml` decía «cada X minutos»; son horas.
- El uso de `/lpa title` en la ayuda indicaba `[id|clear]`; el comando correcto es `/lpa title set <id>`.
- Archivos de idioma de versiones anteriores: los textos cuyo formato cambió (`help.line`, que mostraba «- {value}» en
  la ayuda, y la cabecera de `/lpa modules`) se sustituyen por el texto nuevo al arrancar; la consola indica cuáles.
- Los nombres de archivo como `events.yml` o `GUIA.md` en las líneas de chat con clic ya no abren un enlace web falso.
- Tests nuevos: cada módulo del catálogo aparece en las dos guías con su archivo, comandos y permisos; cada
  `modules/<id>.yml` tiene la cabecera completa; los enlaces de la documentación apuntan a archivos que existen.

## 1.3-V2 — fase 4

### Novedades
- **Temporadas** (módulo `seasons`): mensuales, semanales, de `<N>d` o manuales. Cuentan lo ganado desde el inicio,
  entregan premios por puesto al terminar y guardan el top en el salón de la fama. `/lpa season [top <tipo>|hall]`,
  página en la web y en `/lpa menu`, placeholders `season_*`.
- **Títulos** (módulo `titles`, `titles.yml` con 13 títulos): por estadística, por quedar en el top de una temporada o
  por permiso. El jugador elige el activo (`/lpa title`, menú o web) y `%title%` se muestra en chat, TAB y scoreboard.
- **Eventos** (módulo `events`): competiciones cortas sobre una estadística (`/lpa event start mineria`), con ranking en
  vivo en la web, el scoreboard (`scoreboard.event-lines`) y una bossbar; premios y anuncio al terminar, historial.
- **Hologramas de ranking** (módulo `holograms`): `/lpa holo create` con DecentHolograms o FancyHolograms (opcionales);
  tops totales, semanales, mensuales, de temporada o del evento.
- **Bot de Discord bidireccional** (módulo `discordbot`): `/stats`, `/top`, `/online`, `/link`, `/unlink` y `/staff`,
  puente de chat con protección anti-menciones, vinculación por código, roles por vinculación, hitos y top. Usa
  DiscordSRV si está instalado; si no, un bot integrado sin librerías (~60 KB). Guía en `docs/es/DISCORD_SETUP.md`.
- Endpoints `/api/seasons`, `/api/events`, `/api/titles`, `/api/discord` y de administración para terminar la
  temporada y empezar o terminar eventos; secciones públicas `seasons`, `events` y `titles`.

### Cambios
- `config.yml` pasa a `config-version: 5` (solo claves nuevas; migración automática con copia). `seasons` y
  `discordbot` llegan apagados porque dan recompensas o necesitan token.
- El token del bot puede venir de la variable de entorno `LPA_DISCORD_TOKEN` y nunca se escribe en el registro ni en la API.
- El perfil (`/api/player`) incluye el título activo.

### Correcciones
- Importaciones sin uso eliminadas en `gui`, `periods`, `quests` y `titles`.

## 1.3-V2 — fase 3

### Novedades
- **Consola web** (módulo `console`): consola en vivo por SSE en Admin y envío de comandos filtrados por
  `console.allow`/`console.deny`, con IPs, api-key y patrones sensibles ocultos y límite de líneas por segundo.
- **Moderación** (módulo `moderation`): `warn`, `kick`, `mute`, `unmute`, `ban` y `unban` con duración (`10m`, `1h30m`,
  `perm`) e historial, desde `/lpa mod` o desde el perfil del dashboard; aviso opcional a staff y a Discord.
- **Alertas** (módulo `alerts`): TPS bajo, memoria alta y base de datos caída, con tiempo de espera y cooldown; avisos en
  la web, en el juego y en Discord (también cuando se resuelven).
- **Mapa de calor** de actividad por día y hora (módulo `heatmap`) en el resumen.
- **Copias diarias** en zip (módulo `backups`) a la hora de `backups.time`; se guardan las 7 últimas, recuperación si el
  servidor estuvo apagado y `/lpa backup now|list`.
- **Aviso de actualizaciones** en segundo plano (módulo `updates`: GitHub, Modrinth o Spigot) para admins y en el dashboard.
- **Exportar CSV** de todos los jugadores desde Admin (`/api/admin/export.csv`).
- **Misiones** diarias y semanales (módulo `quests`, `quests.yml`) con recompensas, página en `/lpa menu`, `/lpa quests`,
  placeholders y tarjeta en el perfil.
- **Rachas de conexión** (módulo `streaks`) con premios por día y ciclo configurable.
- **Recompensas por tiempo activo** (módulo `playtime`) que no cuentan el tiempo AFK.
- **Avisos emergentes** en el dashboard (entradas, alertas, hitos, misiones) que se activan o desactivan por tipo.

### Cambios
- `config.yml` pasa a `config-version: 4` (solo claves nuevas; migración automática con copia). `quests`, `streaks` y
  `playtime` llegan apagados porque dan recompensas.
- Recompensas compartidas en `core/reward` (hitos, periodos, misiones, rachas y tiempo activo): `command:`, `item:` y
  `money:` con el mismo formato en todas partes.
- Registro de auditoría de administración en `server_data/audit.log` (`web.audit-log`).
- Los avisos de Discord sin jugador (alertas, moderación) ya no muestran una cabeza vacía.
- Avisos emergentes sin franja de color lateral, igual que las tarjetas y la barra lateral.

### Correcciones
- La comparación de versiones trataba `2.0` y `2.0-beta` como distintas en el orden equivocado; ahora la versión
  final gana a la beta.
- Las recompensas `item:minecraft:diamond` y `command:give ...` no se leían bien (se ejecutaba `command:give` literal).
- Los patrones por defecto de `console.deny` (`op .*`) no bloqueaban nada; ahora son `op *` y se aceptan ambos formatos.
- Mayúsculas y minúsculas independientes del idioma del sistema (`Locale.ROOT`) en sanciones y consola.

## 1.3-V2 — fase 2

### Novedades
- **Historial diario** por jugador (módulo `history`) y gráfica de 30 días en el perfil del dashboard.
- **Tops semanal y mensual** (módulo `periods`): reinicio automático, anuncio de ganadores en el juego (en el idioma
  de cada jugador) y en Discord, premios opcionales por puesto y archivo de cierres. Cierre manual con
  `/lpa period end <weekly|monthly>` o `POST /api/admin/period/end`.
- **Nuevas estadísticas:** bloques colocados, minerales por tipo, peces, comercios con aldeanos, daño causado y
  recibido, y muertes por causa.
- **Página y API públicas** de solo lectura (módulo `public`, apagado por defecto), con secciones configurables,
  límite por IP y CORS. La administración sigue protegida.
- **Comparar dos jugadores** en el dashboard (`#/compare/A/B`).
- **Galería de logros** con rareza (% de jugadores) y categorías común → legendario (módulo `achievements`).
- **`/lpa menu` renovado:** páginas de minerales, muertes, logros y tops por periodo.
- **Documentación bilingüe:** referencia de la API, variables, arquitectura y este registro en español e inglés.

### Cambios
- `config.yml` pasa a `config-version: 3` (solo claves nuevas; migración automática con copia).
- Ranking del dashboard con selector total / semanal / mensual.
- Nuevos placeholders: `%losperrisapi_weekly_*%`, `%losperrisapi_monthly_*%`, `%losperrisapi_achievements_*%` y las
  estadísticas nuevas.
- Las redirecciones `/` y `/dashboard` conservan la consulta (`?public`).

### Correcciones
- "Cerrar sesión" ya no se parte en dos líneas en la barra lateral.
- Compatibilidad con Paper 1.21.1 (encantamientos por clave en lugar de constantes renombradas).
- El perfil ya no muestra "#1 de N" ni percentil 100 % en estadísticas con valor 0 (antes todos empataban en primer puesto).
- `/lpa timings reset` dejaba la lista vacía para siempre (las tareas ya programadas seguían escribiendo en mediciones que se habían quitado); ahora pone los contadores a cero.

## 1.3-V2 — fase 1

### Novedades
- Arquitectura modular: núcleo `core/` y módulos activables con `modules.<id>.enabled`.
- Idiomas español e inglés en el juego (por jugador) y en el dashboard.
- Registro central de variables con avisos en consola y documentación generada.
- `/lpa timings` y `/lpa modules`.

### Cambios
- Datos indexados por UUID, con migración y copia de seguridad.
- Migración de configuración v1 → v2.

### Correcciones
- Revisión general de errores y rendimiento (E/S fuera del hilo principal, rankings precalculados).
