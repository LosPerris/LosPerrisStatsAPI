# 📚 Referencia de la API - LosPerrisStatsAPI 1.5

Comandos, placeholders, API HTTP, página pública, dashboard y API Java para addons. English: `docs/en/API_REFERENCE.md`.
Misiones, temporadas, títulos, eventos, rachas, tiempo activo, hologramas, moderación y alertas están en el addon
**LosPerrisExtras** (sus comandos también van en `/lpa`): vea su [referencia](https://github.com/LosPerris/LosPerrisExtras/blob/main/docs/es/API_REFERENCE.md).
La lista completa de variables (con ejemplos y dónde se usan) está en [variables/](variables/README.md).

---

## 🎮 1. Comandos del juego
**Comando principal:** `/losperrisapi` o `/lpa`. Los subcomandos de un módulo desactivado no existen.

### 👤 A) Jugadores
| Comando | Descripción |
| :--- | :--- |
| `/lpa menu` | Panel de estadísticas (posición en el ranking, ganancia semanal, minerales, muertes, logros y tops). |
| `/lpa top <tipo>` | Top 10 de una estadística (`kills`, `deaths`, `mob_kills`, `time`, `blocks_broken`, `blocks_placed`, `ores_mined`, `jumps`, `distance_walked`, `fish_caught`, `trades`, `damage_dealt`, `damage_taken`). |
| `/lpa period [weekly\|monthly]` | Top semanal o mensual actual, lo que usted lleva y cuánto falta para el cierre. |
| `/lpa sb` | Activa/desactiva el scoreboard lateral (`losperris.scoreboard`). |
| `/lpa lang [es\|en\|auto]` | Ver o cambiar su idioma. |
| `/lpa discord link\|unlink\|status` | Código para vincular su cuenta de Discord (`/link <código>` en Discord), desvincular o ver el estado (módulo `discord-bot`). |
| `/lpa help [módulo\|admin]` · `/lpa version` | Ayuda agrupada y clicable (solo lo que usted puede usar), la de un módulo o la de administración; información del plugin y de los addons instalados. |

### 🛠️ B) Administradores
| Comando | Permiso | Descripción |
| :--- | :--- | :--- |
| `/lpa view <jugador>` | `losperris.inspect` | Ver el panel de otro jugador. |
| `/lpa admin <jugador>` | `losperris.gui.admin` | Editor GUI de estadísticas. |
| `/lpa reset <jugador>` | `losperris.stats.reset` | Resetear todas sus estadísticas. |
| `/lpa period end <weekly\|monthly>` | `losperris.periods.admin` | Cerrar el periodo ya: anuncia ganadores, entrega premios y empieza otro. |
| `/lpa reload` | `losperris.reload` | Recargar configuración, idiomas y módulos. |
| `/lpa modules [módulo]` | `losperris.modules` | Estado de cada módulo, también los de los addons (activo, apagado, falta algo, error) o el detalle de uno: qué hace, de qué depende, archivo, comandos y permisos. |
| `/lpa modules enable\|disable <módulo>` | `losperris.modules.toggle` | Activar o apagar un módulo en caliente. Cambia solo esa línea de `config.yml` (se conservan los comentarios); al apagar uno se apagan también los que dependen de él. |
| `/lpa timings [reset]` | `losperris.timings` | Tiempo medio/máximo de cada tarea del plugin. |
| `/lpa backup <now\|list>` | `losperris.backups.admin` | Copia de seguridad inmediata o lista de copias (módulo `backups`). |

---

## 🧩 2. Placeholders (PlaceholderAPI)
Prefijo `%losperrisapi_...%`. Dentro del scoreboard se usan sin prefijo (`%kills%`).

### 📊 A) Estadísticas del jugador
`kills`, `deaths`, `mob_kills`, `time`, `blocks_broken`, `blocks_placed`, `ores_mined`, `jumps`, `distance_walked`,
`fish_caught`, `trades`, `damage_dealt`, `damage_taken`, `language`. Ejemplo: `%losperrisapi_ores_mined%`.

### 🏆 B) Ranking / TOP
- Total: `%losperrisapi_top_<tipo>_<pos>_<name|value>%` → `%losperrisapi_top_kills_1_name%`
- Semanal/mensual: `%losperrisapi_weekly_<tipo>_<pos>_<name|value>%`, `%losperrisapi_monthly_time_1_value%`
- Lo que lleva el jugador en el periodo: `%losperrisapi_weekly_kills%`
- Logros: `%losperrisapi_achievements_obtained%`, `_total`, `_percent`

### 💬 C) Discord
- Discord: `%losperrisapi_discord_linked%` (bot integrado)

> [!NOTE]
> Posiciones 1–10. Los tops semanal/mensual solo existen para las estadísticas de `periods.stats`.
> Los placeholders de un módulo desactivado devuelven vacío.

---

## 🌐 3. API HTTP (Web)
**BASE URL:** `http://TU_IP:25908`

### 🔒 Conexión y seguridad
La seguridad está **activada por defecto** (`security.enabled: true`). Si `security.api-key` está vacía, el plugin
genera una al arrancar, la guarda en `settings/web.yml` (`security.api-key`) y la muestra en la consola.

1. **Header (recomendado):** `Authorization: Bearer TU_API_KEY`
2. **Query:** `?key=TU_API_KEY` (solo lectura; se desactiva con `security.allow-query-key: false`).

### 📡 Endpoints de lectura (GET)
| Endpoint | Descripción |
| :--- | :--- |
| `/api/players` | Jugadores online (`name`, `uuid`, `world`, `ping`, `health`, `food`, `level`, `afk`, `session_seconds`…). |
| `/api/stats?name=X` | Datos de un jugador (incluye `ores` por tipo y `death_causes`). 400 nombre inválido, 404 si no existe. |
| `/api/top?stat=X&limit=N` | Ranking total (`limit` 1–100). |
| `/api/summary` | Resumen global: totales, medias, máximos, actividad, servidor, top 5 y `modules` activos. Caché 3 s. |
| `/api/profile?name=X` | Perfil: `stats`, `ranks` (posición por estadística; 0 = sin clasificar porque el valor es 0), `milestones`, `online`, `session`. |
| `/api/players/list?q=&sort=&order=&page=&size=` | Directorio paginado (`sort`: `name`, `last_updated` o una estadística). |
| `/api/history?since=EPOCH_MS` | Serie del servidor (online, TPS, totales). |
| `/api/player/history?name=X&days=30` | Historial diario del jugador `{days:[{date, values:{stat: valor}}]}` (módulo `history`). |
| `/api/periods?limit=10&name=X` | Tops `weekly`/`monthly`: `enabled`, `start`, `end`, `stats`, `boards`, `last` (último cierre) y, con `name`, `player` (ganancia y puesto). |
| `/api/achievements?name=X` | Logros con `owners`, `percent` y `tier` (`common`…`legendary`); con `name`, los obtenidos por el jugador. |
| `/api/heatmap` | Mapa de actividad `{days[7][24], max, weeks, peak{day,hour}, timezone}`: media de conectados por día (lunes = 0) y hora; `-1` = sin datos (módulo `heatmap`). |
| `/api/discord` | Estado del bot `{mode, state, connected, ping, bot, bridge, links, roles, last_role_sync, failure}`. Nunca incluye el token y nunca es público. |
| `/api/all` | Volcado global (sin IPs; caché 5 s). |
| `/api/chat/stream` · `/api/chat/history?limit=N` | Chat en vivo (SSE) e historial. |

Un módulo desactivado responde `404 {"error": "Module '<id>' disabled"}`. Los endpoints de LosPerrisExtras
(`/api/quests`, `/api/seasons`, `/api/events`, `/api/titles` y los de administración de temporadas, eventos y
moderación) solo existen con el addon y su módulo activos; si no, responden `404`. Con el addon, el perfil añade
`quests` y `title`.

### 🛡️ API de administración (`/api/admin/...`)
Requisitos: `web.admin-enabled: true`, seguridad activa y api-key. Solo header Bearer; las acciones van por POST con JSON (≤ 8 KB),
10 fallos por IP en 5 min → 429. Las acciones se registran como `[WebAdmin]`.

| Método | Endpoint | Cuerpo | Efecto |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/check` | — | `{"ok": true}` si la clave vale. |
| `GET` | `/api/admin/log` | — | Últimas acciones. |
| `POST` | `/api/admin/stat` | `{"player","stat","value","mode":"set"\|"add"}` | Edita una estadística editable (las calculadas, como `blocks_placed` u `ores_mined`, devuelven 400). |
| `POST` | `/api/admin/reset` | `{"player"}` | Igual que `/lpa reset`. |
| `POST` | `/api/admin/period/end` | `{"type":"weekly"\|"monthly"}` | Cierra el periodo (202). 400 si está desactivado, 409 si ya se está cerrando. |
| `POST` | `/api/admin/leaderboard/refresh` · `/save` · `/reload` | `{}` | Recalcular, guardar, recargar. |
| `POST` | `/api/admin/broadcast` | `{"message"}` | Anuncio (1–256 caracteres, códigos `&`). |
| `GET` · `POST` | `/api/admin/backups` · `/api/admin/backups/run` | — · `{}` | Estado `{backups[], keep, next_run, running, last}`; `run` responde 202 o 409 si ya hay una en curso. |
| `GET` · `POST` | `/api/admin/updates` · `/api/admin/updates/check` | — · `{}` | `{configured, source, current, latest?, url?, update_available, checked_at?}`; `check` comprueba ya (404 sin `updates.source`). |
| `GET` | `/api/admin/export.csv` | — | Todos los jugadores en CSV UTF-8 (sin IP; celdas peligrosas para Excel neutralizadas). |

```bash
curl -X POST http://TU_IP:25908/api/admin/period/end \
  -H "Authorization: Bearer TU_API_KEY" -H "Content-Type: application/json" -d '{"type":"weekly"}'
```

### 🖥️ Consola web (`/api/admin/console`)
Módulo `web-console`. Mismos requisitos que la administración (header Bearer).

| Método | Endpoint | Efecto |
| :--- | :--- | :--- |
| `GET` | `/api/admin/console` | `{lines[{t, l, m}], max_clients}`: últimas `console.buffer-lines` líneas. |
| `GET` | `/api/admin/console/stream` | Líneas nuevas por SSE (`data: {t, l, m}`). 503 si hay `console.max-clients` conexiones. |
| `POST` | `/api/admin/console/command` | `{"command"}` (≤ 256). Pasa por `console.deny` (siempre gana) y `console.allow`; 403 si se bloquea. Todo queda en el registro. |

Las líneas se limpian (colores, IPs con `console.hide-ips`, la api-key y los patrones de `console.redact`) y se limitan a
`console.max-lines-per-second`. Patrones de `allow`/`deny`: `list` (exacto), `say *` (con o sin argumentos), `eco*`.

### 🌍 API y página pública (`/api/public/...`)
Desactivada por defecto (`modules.public-page: false`). Al activarla se abren copias **de solo lectura y sin
api-key** de los endpoints elegidos en `public.sections`; la administración, el chat y `/api/all` nunca son públicos.

| Sección | Endpoints |
| :--- | :--- |
| `summary` | `/api/public/summary`, `/api/public/players` |
| `leaderboards` | `/api/public/top` |
| `players` | `/api/public/players/list` |
| `profiles` | `/api/public/profile`, `/api/public/stats` |
| `periods` | `/api/public/periods` |
| `achievements` | `/api/public/achievements` |
| `history` | `/api/public/player/history` |

`/api/public/info` devuelve `{public, language, title, sections}`. Límite `public.rate-limit` peticiones/minuto por IP
(429 + `Retry-After`). CORS con `public.cors-origin`. La página: `http://TU_IP:25908/?public`.

### 📊 Dashboard web
`http://TU_IP:25908/` (privado, con api-key) o `/?public` (público). En español e inglés (botón ES/EN).
- **Resumen:** estado del servidor, totales de las 13 estadísticas, gráficas y top 5.
- **Ranking:** total, semanal o mensual (con la cuenta atrás del cierre).
- **Jugadores** y **Perfil:** puesto por estadística, percentil, hitos, historial de 30 días, minerales por tipo,
  causas de muerte, tops semanal/mensual y galería de logros con rareza.
- **Comparar:** `#/compare/A/B`, estadística por estadística con quién gana cada una.
- **Chat** y **Admin** (solo en el privado): incluye cerrar el top semanal/mensual.
- **Mapa de calor** en el resumen; consola, copias, actualizaciones, exportar CSV y estado del bot de Discord en Admin;
  avisos emergentes (entradas, hitos) que cada navegador activa o desactiva en la página de chat.
- Las pestañas de misiones, temporadas, eventos, moderación y alertas aparecen solo con LosPerrisExtras (`summary.modules`).

---

## 📋 4. Scoreboard
Nativo y configurable en `scoreboard.yml`. Admite las estadísticas, `%top_…%`, `%weekly_…%`, `%monthly_…%` y los de logros.
Los jugadores lo ocultan con `/lpa sb`.
Con un evento en curso (addon LosPerrisExtras) se muestran además las líneas de `scoreboard.event-lines`.

---

## 🎯 5. Hitos y tops por periodo
- **Hitos** (`settings/milestones.yml`, apagados por defecto): recompensas (comandos y dinero con Vault), fuegos, sonidos y Discord.
- **Galería de logros** (`gallery:` en `milestones.yml`; antes el módulo `achievements`): cada nivel de hito es un logro;
  su rareza es el % de jugadores que lo tiene (`gallery.tiers`). Necesita los hitos activos.
- **Tops semanal/mensual** (módulo `periods`): reinicio automático (`periods.week-start` y `timezone`; el mes empieza el día 1), anuncio de los
  `periods.announce.top` primeros en el juego y en Discord, premios opcionales (`periods.rewards`) y archivo de cierres.
- **Bot de Discord** (módulo `discord-bot`): `/stats`, `/top`, `/online`, vinculación, roles y `/staff`. Guía: `docs/es/DISCORD_SETUP.md`.

---

## 📂 6. Almacenamiento
`settings/database.yml`: JSON, MySQL o Supabase (PostgreSQL). Historial diario en `data/player_history/`, periodos y su archivo en `data/periods/`.
Mapa de calor en `data/heatmap.json`, vínculos de Discord en `data/discord_links.json` y copias diarias en `backups/data/` (se guardan `backups.keep`).
Los datos de LosPerrisExtras (temporadas, eventos, hologramas, sanciones) están en `plugins/LosPerrisExtras/data/`.

---

## 🧩 7. API Java para addons
Para plugins que dependen del núcleo (`depend: [LosPerrisStatsAPI]`). Paquete `me.gordi.mineapi.api`; el ejemplo
completo es LosPerrisExtras. Todo lo que un addon registra con un `owner` se quita solo con `unregisterAll(owner)`
(los módulos que extienden `BaseModule` lo hacen al apagarse).

```java
LosPerrisApi api = LosPerrisApi.get();          // null si el núcleo no está activo
api.events().subscribe(this, StatChangedEvent.class, e -> { ... });
api.web().read(this, "quests", null, r -> WebApi.Reply.ok(json));   // GET /api/quests
api.discord().notice("Título", "Texto", "Jugador");                // no hace nada sin webhook
```

| Método | Para qué |
| :--- | :--- |
| `get()` · `core()` · `data()` · `modules()` · `rewards()` · `stats()` | Instancia, plugin, datos de jugadores, módulos del núcleo, recompensas (comandos y Vault) y el módulo de estadísticas. |
| `events()` | Bus interno: `subscribe(owner, Tipo.class, handler)`. Eventos: `StatChangedEvent`, `MilestoneReachedEvent`, `PlayerDataReadyEvent`, `PlayerResetEvent`, `CoreReloadedEvent`. |
| `isAfk(player)` | Si el jugador está AFK (false sin el módulo `afk`). |
| `web()` (`WebApi`) | `read(owner, ruta, secciónPública, handler)` → `GET /api/<ruta>`; `admin(owner, "GET"\|"POST", ruta, handler)` → `/api/admin/<ruta>`; `profile(owner, contribuidor)` añade campos a `/api/profile`; `flag(owner, id, BooleanSupplier)` en `summary.modules`; `hideDataKeys`, `notify`, `invalidate`. Respuestas con `WebApi.Reply.ok/of/error`. |
| `menus()` (`MenuApi`) | `page(owner, Page)` añade una página a `/lpa menu` (botón propio; casillas 9–44 libres, `paginate` pone las flechas); `open(viewer, target, id, página)`. Ayudas en `MenuItems`. |
| `discord()` (`DiscordApi`) | `available()`, `notice`, `alert`, `send`: avisos por el webhook del núcleo. |
| `playerTag(owner, PlayerTag)` | Texto junto al nombre (título activo) en el perfil web y en el bot de Discord. |
| `eventMode(owner, BooleanSupplier)` | Mientras devuelva true, el scoreboard usa `scoreboard.event-lines`. |
| `commands()` (`CommandApi`) | `register(owner, SubCommand, HelpCatalog.Entry...)` añade un subcomando a `/lpa` (nombre, alias, permiso, ejecución y autocompletado con `SubCommand.of(...)`) y sus líneas de `/lpa help`; `help(owner, entrada)` solo ayuda; `texts(owner, I18n)` sus idiomas (claves que el núcleo no tiene); `executor()` para enlazar un alias propio (p. ej. `/lpx`). |
| `addons()` (`AddonApi`) | `register(plugin, Addon)`: sus módulos entran en el sistema del núcleo (`/lpa modules`, `/lpa reload`, dashboard) y sus interruptores se escriben en `config.yml` del núcleo. Ver abajo. |
| `unregisterAll(owner)` | Quita rutas, páginas, etiquetas, modo evento, comandos, ayuda, idiomas y módulos de ese dueño. |

Un addon con módulos implementa `Addon`: `modules()` (en orden de dependencias; pueden depender de módulos del
núcleo), `catalog()` (grupos, nombre, archivo, comandos y ayuda de cada módulo para `/lpa help` y `/lpa modules`),
`texts()` (sus idiomas) y, opcionalmente, `initialSwitches()`, `reload()` y `forget(módulo)`.

```java
api.addons().register(this, addon);   // config.yml del núcleo, si faltan:
// modules:
//   ...
//   # ===== LosPerrisExtras =====
//   quests: false         # Misiones → LosPerrisExtras/settings/quests.yml
```

Los interruptores que falten se añaden en `false` (o con `initialSwitches()`) sin tocar los que ya estén; si el addon
se quita, sus líneas se quedan pero no se usan. Los ajustes de cada módulo siguen en la carpeta del addon.

Los placeholders de un addon se registran en el mismo prefijo `%losperrisapi_...%`. `LosPerrisApi.VERSION` sube si la
API cambia de forma incompatible (es 2 en la 1.5); como el compilador la copia en el addon, para pedir una versión
mínima del núcleo compare `api.core().getDescription().getVersion()`.

---

## 🚀 Novedades 1.5
- 🧩 Los addons se integran en el núcleo: sus comandos van en `/lpa` (con su ayuda y autocompletado), sus módulos en
  `/lpa modules`, `/lpa reload` y el dashboard, y sus interruptores en `config.yml` del núcleo (sección 7).
- 🎯 Las misiones con varios objetivos muestran una barra pequeña por objetivo en el perfil web.

## 🚀 Novedades 1.4
- 🧩 Los extras añadidos después del plugin original pasan al addon **LosPerrisExtras** (`/lpx`). El núcleo conserva
  las funciones originales (estadísticas, rankings, API HTTP, dashboard, Discord, hitos, scoreboard, AFK, menú) y sus mejoras.
- 🏅 Los logros se unen a los hitos (`gallery:` en `milestones.yml`).
- 🔌 API Java pública para addons (sección 7).

## 🚀 Novedades 1.3
- 🗓️ Historial diario por jugador y tops semanal/mensual con reinicio y anuncio de ganadores.
- 📈 Nuevas estadísticas: bloques colocados, minerales por tipo, peces, comercios, daño causado/recibido y muertes por causa.
- 🌍 Página y API públicas de solo lectura; comparar jugadores; galería de logros con rareza.
- 🖥️ Nuevo `/lpa menu` con varias páginas y dashboard en español e inglés.
- 🛡️ **Fase 3:** consola web, moderación, alertas (web, juego y Discord), mapa de calor, copias diarias, aviso de
  actualizaciones, exportar CSV, misiones diarias/semanales, rachas de conexión y recompensas por tiempo activo.
- 🏅 **Fase 4:** temporadas con salón de la fama, títulos por estadísticas, hologramas de ranking, bot de Discord
  bidireccional (comandos, chat, vinculación y roles) y eventos con ranking temporal en vivo.
- 🧩 **Fase 5:** `config.yml` corto con los módulos pequeños y un archivo propio para cada módulo grande (el de un módulo apagado no se abre), `/lpa modules enable|disable` en caliente, ayuda
  agrupada y clicable, permisos por módulo bajo `losperris.admin` y guía de administración ([GUIA.md](GUIA.md)).

---
© 2026 LosPerrisStatsAPI • [Website](https://losperris.site)
