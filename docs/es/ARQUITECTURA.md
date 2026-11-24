# Arquitectura de LosPerrisStatsAPI

English: [ARCHITECTURE.md](../en/ARCHITECTURE.md)

Este documento le explica cómo está organizado el plugin, cómo añadir un módulo nuevo y qué
reglas de rendimiento debe respetar. Léalo antes de tocar el código.

**Idea central:** el plugin tiene un **núcleo** pequeño (`core/`) y una lista de **módulos**
(`module/*`). Cada módulo se activa o desactiva con `modules.<id>: true|false` en `config.yml` (o con
`/lpa modules enable|disable <id>`) y guarda sus opciones donde indica `ConfigLayout.place(id)`: los del **núcleo** en su sección de `config.yml` (`<id>:`) o en
`settings/web.yml` y `settings/discord.yml` (una sección para cada módulo de Discord), y cada **extra** en `settings/<id>.yml`.
La base de datos está en `settings/database.yml` y los datos en `data/`. En `settings/milestones.yml` los ajustes van arriba y la lista debajo.
Un módulo desactivado no registra listeners, tareas, comandos ni hilos, y sus ajustes ni se analizan (su archivo ni se abre): no consume nada.

---

## 1. Árbol del proyecto

```
src-review/
├── pom.xml                         Maven, un solo módulo (Java 17, Spigot API 1.20.1)
├── README.md                       qué es, por dónde empezar y tabla de carpetas (ES/EN)
├── REGISTRO_DE_CAMBIOS.md          cambios por versión (inglés: CHANGELOG.md)
├── docs/                           TODA la documentación, un solo sitio
│   └── es|en/
│       ├── GUIA.md                 guía rápida: un apartado por módulo
│       ├── API_REFERENCE.md        comandos, permisos, placeholders, API HTTP
│       ├── DISCORD_SETUP.md        bot de Discord
│       ├── ARQUITECTURA.md         este documento (inglés: ARCHITECTURE.md)
│       └── variables/              variables por contexto (GENERADA, ver §5)
└── src/
    ├── main/java/me/gordi/mineapi/
    │   ├── MineAPI.java            punto de entrada: arranca el núcleo y registra los módulos
    │   ├── api/                    API pública para addons: LosPerrisApi, WebApi, MenuApi, MenuItems, DiscordApi, PlayerTag, CommandApi, AddonApi
    │   ├── core/                   núcleo; nunca depende de module/*
    │   │   ├── command/            /lpa: CommandRouter, SubCommand, CoreCommands, HelpCatalog/HelpView (ayuda), ModulesView
    │   │   ├── config/             ConfigService (config.yml + settings/), ConfigLayout (bloques y place(id)), ConfigMigrator (v1 → v9 con copia), DependencyWarnings, ModuleToggleWriter
    │   │   ├── data/               PlayerDataService (caché por UUID), UuidMigration, listener
    │   │   ├── event/              EventBus interno + eventos (StatChanged, PlayerReset, ...)
    │   │   ├── hook/               integraciones opcionales (PlaceholderAPI, Vault)
    │   │   ├── i18n/               I18n (idioma del servidor y de cada jugador), Lang
    │   │   ├── log/                DebugLog
    │   │   ├── module/             Module, AbstractModule, ModuleManager, ModuleCatalog (lista única de módulos), Perms
    │   │   ├── placeholder/        Placeholders: proveedores por prefijo que registran los módulos
    │   │   ├── reward/             Rewards: recompensas compartidas (comando, item, dinero)
    │   │   ├── scheduler/          TaskScheduler (tareas con dueño y timings)
    │   │   ├── storage/            JSON / MySQL / PostgreSQL detrás de StorageProvider
    │   │   ├── text/               Template, Text, Csv, registro central de variables y su validador
    │   │   └── time/               Durations (10m, 1h30m, perm) y Zones
    │   └── module/                 un módulo = una carpeta = un id
    │       ├── stats/  afk/  milestones/ (+ galería de logros)
    │       ├── periods/
    │       ├── scoreboard/  gui/  placeholders/
    │       ├── web/                API HTTP y dashboard (http/, routes/)
    │       ├── publicweb/          módulo `public-page`: API y página pública de solo lectura (`public` es palabra reservada en Java)
    │       ├── console/            módulo `web-console`
    │       ├── heatmap/  history/
    │       ├── backups/  updates/
    │       ├── discordwebhook/     módulo `discord-webhook`: rankings a un webhook
    │       └── discordbot/         módulo `discord-bot`: GatewayClient, DiscordRest, SlashCommands, Mentions, LinkCodes/LinkStore, RoleRules, DiscordSrvHook
    ├── main/resources/
    │   ├── plugin.yml                      comandos y permisos (todos los de admin cuelgan de losperris.admin)
    │   ├── defaults/es|en/config.yml       general + interruptores + secciones del núcleo (título de una línea)
    │   ├── defaults/es|en/settings/*.yml   web, discord, database y un <id>.yml por extra (cabecera corta + ajustes [+ lista])
    │   ├── lang/es.yml, lang/en.yml        mensajes (ninguno queda escrito en el código)
    │   ├── database/                       esquemas SQL
    │   └── web/                            dashboard: html, css, js, i18n.js (ES/EN)
    └── test/java/me/gordi/mineapi/         pruebas JUnit 5 (mismo árbol que main)
```

**Reglas del árbol:**

- `core/` no importa nada de `module/`.
- Un módulo solo habla con otro a través del `EventBus` o de una dependencia declarada.
- Cada módulo vive entero en su carpeta. Para quitarlo basta con borrar la carpeta y su línea
  de `register(...)`.
- Las pruebas copian la ruta del paquete que prueban.

**Addons:** lo que se añadió después del plugin original (misiones, rachas, tiempo activo, títulos, temporadas,
eventos, hologramas, moderación y alertas) vive en el addon **LosPerrisExtras** (`me.gordi.losperrisextras`,
`depend: [LosPerrisStatsAPI]`), que solo usa `me.gordi.mineapi.api` y las clases base de módulo. El núcleo no
conoce al addon: las rutas, páginas del menú y placeholders del addon se registran con un dueño y se quitan al
apagarlo (`LosPerrisApi.unregisterAll`). Desde la 1.5 sus subcomandos entran en el `CommandRouter` de `/lpa`
(`CommandApi`), su catálogo se suma al del núcleo (`CommandCatalog`), sus idiomas extienden el `I18n` raíz y sus
módulos se añaden al `ModuleManager` (`AddonApi`, `AddonRegistry`), con los interruptores en `config.yml` del núcleo
(`ModuleToggleWriter.addGroup`).

---

## 2. Módulos

| id             | Depende de | Qué hace                                                   | Interruptor en config.yml                |
|----------------|------------|------------------------------------------------------------|------------------------------------|
| `stats`        | (ninguno)  | Cuenta kills, muertes, horas, bloques rotos/colocados, menas por tipo, saltos, distancia, peces, comercios, daño y causas de muerte; rankings | `modules.stats`            |
| `afk`          | (ninguno)  | Marca jugadores AFK; las horas AFK no cuentan              | `modules.afk`              |
| `milestones`   | `stats`    | Hitos, mensajes y recompensas (`settings/milestones.yml`)                   | `modules.milestones` (apagado) |
| `scoreboard`   | `stats`    | Scoreboard lateral (`/lpa sb`)                             | `modules.scoreboard`       |
| `gui`          | `stats`    | Menús de estadísticas y de administración                  | `modules.gui`              |
| `placeholders` | `stats`    | `%losperrisapi_*%` para PlaceholderAPI (solo si está instalado) | `modules.placeholders`     |
| `discord-webhook`| `stats`    | Envía rankings a un webhook                                | `modules.discord-webhook` (apagado) |
| `history`      | `stats`    | Foto diaria de cada jugador (`/api/player/history`)        | `modules.history`          |
| `periods`      | `stats`    | Tops semanal/mensual, cierre automático, anuncio y premios | `modules.periods`          |
| `web`          | `stats`    | API HTTP y dashboard (`/dashboard`)                        | `modules.web`              |
| `public-page`  | `web`      | `/api/public/*` y `/?public`, solo lectura, sin api-key     | `modules.public-page` (apagado) |
| `web-console`  | `web`      | Consola web en vivo (SSE) y comandos filtrados por allow/deny | `modules.web-console`      |
| `heatmap`      | (ninguno)  | Media de conectados por día de la semana y hora            | `modules.heatmap`          |
| `backups`      | (ninguno)  | Copia diaria en zip de los datos; guarda las últimas `keep` | `modules.backups`          |
| `updates`      | (ninguno)  | Comprueba versiones nuevas (GitHub, Modrinth o Spigot) en segundo plano | `modules.updates`          |
| `discord-bot`  | `stats`    | Bot de Discord: comandos de barra, puente de chat, vinculación, roles y staff (DiscordSRV o integrado) | `modules.discord-bot` (apagado) |

**Comportamiento:**

- **Orden de arranque:** el que siguen los `register(...)` en `MineAPI`. Si falta una
  dependencia, el módulo **no** arranca y se avisa en consola.
- **Errores:** si `enable()` lanza una excepción, solo cae ese módulo. Por ejemplo, si el puerto
  web está ocupado se desactiva `web` y el resto sigue funcionando.
- **`/lpa reload`:** relee la configuración y los idiomas. Activa o desactiva los módulos cuyo
  interruptor cambió y recarga los que siguen activos. Todo ocurre en caliente, sin reiniciar.
- **`/lpa modules [id]`:** muestra el estado de cada módulo agrupado por categoría (● activo,
  ○ apagado, ⚠ activado con un problema, con el motivo). Con un id muestra el detalle: archivo,
  comandos, permisos y dependencias. Cada nombre se puede pulsar en el chat.
- **`/lpa modules enable|disable <id>`** (permiso `losperris.admin.modules.toggle`): cambia solo la
  línea `modules.<id>` de `config.yml` (conserva los comentarios, escritura atómica) y aplica el
  cambio en vivo con `ModuleManager.sync()`, sin recargar los demás módulos. Al apagar un módulo
  también se apagan los que dependen de él; no se puede encender uno cuya dependencia está apagada.
  La configuración de un módulo apagado se libera de memoria.
- **Ayuda:** `HelpCatalog` es la lista única de comandos (módulo, permiso, si es de admin).
  `HelpView` construye `/lpa help` (solo comandos de módulos activos y con permiso, agrupados por
  categoría) y `ChatOut` la envía con texto al pasar el ratón y clic en el juego, o en texto plano en
  consola. Un test comprueba que cada comando y permiso tiene su texto en ES y EN.

---

## 3. Cambios de configuración (v1 → … → v6 → v7 → v8)

El `ConfigMigrator` actualiza `config.yml` solo, la primera vez que arranca la versión nueva:

1. Guarda una copia en `backups/config-<fecha>.yml`.
2. Mueve las claves antiguas: `scoreboard.enabled` pasa a `modules.scoreboard.enabled` y
   `discord.enabled` pasa a `modules.discord.enabled`.
3. Añade las claves nuevas con sus valores por defecto: `modules.*`, `language`,
   `per-player-language` y `web.history-*`, `web.admin-*`.
4. Marca la versión actual (`config-version: 3`). En los arranques siguientes no vuelve a hacer nada.

Los datos también se migran. `server_data/all_stats.json` pasaba a estar indexado por UUID
(formato 2). Antes de migrar se guarda una copia en `backups/all_stats-pre-uuid-<fecha>.json`.
Si un jugador no tenía UUID, se deduce uno y se corrige en su próxima conexión. Los duplicados
se unen. La API existente (`/api/all`, `/api/stats?name=`) responde igual que antes.

**v2 → v3 (fase 2):** solo se añaden claves (`modules.history|periods|achievements|public`, secciones
`periods`, `achievements` y `public`, las nuevas estadísticas del scoreboard y del dashboard). No se cambia ningún
valor existente y `public` llega apagado. Las estadísticas nuevas se leen de las `Statistic` de vanilla, así que
cada jugador recupera su valor histórico la próxima vez que entra (hasta entonces muestra 0). Solo las causas de
muerte empiezan a contarse desde la actualización. No hace falta migrar datos.

**v3 → v4 (fase 3):** también solo añade claves, con la misma copia previa en `backups/config-<fecha>.yml`: los
módulos `console`, `moderation`, `alerts`, `heatmap`, `backups` y `updates` (activados) y `quests`, `streaks` y
`playtime` (apagados, porque dan recompensas), más las secciones `console`, `moderation`, `alerts`, `heatmap`,
`backups`, `updates`, `streaks` y `playtime-rewards`. `quests.yml` se crea aparte la primera vez. Las rachas y el
tiempo activo se guardan como estadísticas internas del jugador (`login_streak`, `best_login_streak`,
`active_playtime`); no hace falta migrar datos.

**v4 → v5 (fase 4):** de nuevo solo añade claves, con copia previa en `backups/config-<fecha>.yml`: los módulos
`titles`, `events` y `holograms` (activados; sin evento ni plugin de hologramas no consumen nada) y `seasons` y
`discordbot` (apagados: dan recompensas o necesitan token), las secciones `seasons`, `titles`, `events`, `holograms`
y `discordbot`, y `scoreboard.event-lines`. `titles.yml` se crea aparte la primera vez. La temporada empieza al
activar el módulo; los títulos que ya cumplen los jugadores se dan en su próxima conexión. No hace falta migrar datos.

**v5 → v6 (fase 5):** el `config.yml` largo se reparte en un `config.yml` corto (ajustes generales y
`modules.<id>: true|false`) más un archivo por módulo en `modules/<id>.yml`; `milestones.yml`, `quests.yml` y
`titles.yml` pasan a `modules/`. Antes de tocar nada se guardan `config.yml`, `milestones.yml`, `quests.yml` y `titles.yml` en
`backups/migration-v6-<fecha>.zip` y
nunca se sobrescribe un archivo de módulo que ya exista. Los ids `discord` y `discordbot` pasan a `discord-webhook`
y `discord-bot` (los antiguos se siguen aceptando). Los valores del usuario se conservan; solo se añaden las claves
nuevas. Un módulo apagado no lee su archivo. Para volver a la versión anterior hay que restaurar ese zip.

**v6 → v7 (fase 5b):** veinte archivos eran demasiados. Todo vuelve a un único `config.yml` ordenado: cabecera corta, `general`, la lista `modules` agrupada y una sección por módulo (`web:`, `events:`…) en el orden de las categorías, cada una con un recuadro que explica qué hace, sus comandos, permisos y variables. Las listas largas siguen siendo archivos propios (`milestones.yml`, `quests.yml`, `titles.yml`, en la carpeta del plugin). `ConfigService` solo analiza al arrancar la cabecera (`config-version`, `general`, `modules`); la sección de un módulo se analiza la primera vez que el módulo se activa (`ConfigLayout` parte el texto en bloques sin analizar el YAML). Al escribir (api-key nueva, claves nuevas, `/lpa modules enable|disable`) solo cambia ese bloque; el resto del archivo queda igual byte a byte. La migración (desde v5 o v6) lee primero todo; si algo no se puede leer no cambia nada. Después guarda todos los archivos implicados, con su ruta, en `backups/migration-v7-<fecha>.zip`, escribe las listas y el `config.yml` nuevo y por último retira `modules/`. Para volver atrás: borre los archivos nuevos y descomprima el zip.

**v7 → v8 (fase 5c):** un único `config.yml` llegaba a casi mil líneas. Ahora `config.yml` (≈360 líneas) guarda lo general, los interruptores y las secciones de los módulos pequeños, cada una con un título de una línea que enlaza a la guía (`# ── stats · Estadísticas · guía: GUIA.md#stats ──`). Los módulos grandes tienen archivo propio con el banner de LosPerrisStats y su recuadro detallado: `web.yml`, `discord.yml` (secciones `discord-webhook` y `discord-bot`), `events.yml`, `scoreboard.yml`, y `milestones.yml`, `quests.yml`, `titles.yml` con los ajustes arriba y la lista debajo. `ConfigLayout.place(id)` dice dónde vive cada módulo; el archivo de un módulo apagado no se abre. Los archivos propios se editan por bloques de primer nivel (`ConfigLayout.replaceTop`), así que se conservan los comentarios. La migración (desde v5, v6 o v7) lo lee todo primero, guarda todos los archivos de origen en `backups/migration-v8-<fecha>.zip`, escribe los archivos de módulo y por último `config.yml` (si algo falla, se repite en el siguiente arranque) y retira `modules/`.

**v8 → v9 (fase 6):** la raíz queda limpia: solo `config.yml` (≈185 líneas: `general`, los interruptores agrupados en núcleo y extras, y las secciones de `stats`, `periods`, `history`, `backups` y `updates`). `settings/` guarda `web.yml`, `discord.yml` (secciones `discord-webhook` y `discord-bot`), `database.yml` y un `settings/<id>.yml` por cada extra, con una cabecera corta que enlaza a la guía; el archivo de un extra se crea la primera vez que se activa. `server_data/` pasa a `data/` (`DataDir`) y la documentación ya no se copia al servidor (se borra `docs/`); los comentarios remiten a `/lpa help` y a la URL de `Links.DOCS`. Los ids `public` y `console` pasan a `public-page` y `web-console`. Las duraciones se escriben `5m`, `1h`… (`Durations.seconds` sigue aceptando el número antiguo en la unidad de antes) y las estadísticas usan el nombre de `stats.tracked` (`player_kills`, `play_time`; `StatType.parse` acepta los alias). La migración (desde v5, v6, v7 o v8) guarda primero todos los archivos de origen en `backups/migration-v9-<fecha>.zip`, conserva valores y secretos, no crea los archivos de extras que no se tocaron y termina con `config.yml`. Al arrancar y con `/lpa reload`, `DependencyWarnings` avisa en una línea de los ajustes que no hacen nada porque su módulo necesario está apagado (un `true` de plantilla que espera a Discord no se avisa).

**v9 → v10 (1.4):** misiones, rachas, tiempo activo, títulos, temporadas, eventos, hologramas, moderación y alertas pasan
al addon LosPerrisExtras, y `achievements` se une a `milestones` (`gallery:` en `settings/milestones.yml`). `ConfigService`
(`ExtrasSplit`) guarda antes todo en `backups/migration-v10-<fecha>.zip`, **mueve** los `settings/<id>.yml` y los datos
(`data/seasons/`, `data/events/`, `data/holograms.json`, `data/sanctions/`) a `plugins/LosPerrisExtras/` y deja allí sus
interruptores para que el addon los importe. `config.yml` se escribe el último. Los comandos de esos módulos pasan de
`/lpa` a `/lpx`; sus endpoints y placeholders se mantienen con el addon instalado.

Los permisos, placeholders y endpoints HTTP del núcleo se mantienen **sin cambios**.

---

## 4. Idiomas (i18n)

**Servidor:**

- Los mensajes están en `lang/<código>.yml`; se incluyen `es` y `en`. Puede añadir otro
  idioma copiando uno de ellos (por ejemplo, `lang/pt.yml`).
- `language: es` en `config.yml` define el idioma por defecto.
- Con `per-player-language: true`, cada jugador recibe los mensajes en el idioma de su cliente
  de Minecraft. Puede fijar otro con `/lpa lang <es|en|auto>`.
- Si falta un texto, se busca en este orden: idioma del jugador → idioma por defecto → `es`.

**En el código:**

- Se escribe `i18n().send(sender, "clave", "player", nombre)`, con las variables en parejas
  nombre/valor.
- Nunca se escribe el texto directamente en el código.

**Dashboard:**

- `web/i18n.js` contiene los diccionarios ES y EN. El botón del menú lateral cambia el idioma.
- La elección se guarda en el navegador. Si el usuario no ha elegido, se usa el idioma del
  servidor.
- Las claves de ES y EN deben ser idénticas.

---

## 5. Variables: registro central

`core/text/Variables.java` es la **única fuente de verdad**. Ahí se declaran las variables que
admite cada contexto (`lang`, `milestones`, `rewards`, `scoreboard`, `discord`,
`placeholderapi`...). De ese registro salen dos cosas:

- **El aviso en consola:** al cargar los archivos, `VariableValidator` avisa de cada variable
  desconocida con el archivo, la clave y la variable. Lo hace una sola vez por carga, nunca
  mientras se juega.
- **La documentación:** `docs/es/variables/*.md` y `docs/en/variables/*.md` (en el repositorio; el plugin
  ya no las copia al servidor) deben coincidir con el registro. Cada descripción
  se declara en los dos idiomas (`Localized`). La prueba `VariableDocsTest` falla si
  no coinciden. Para regenerarla ejecute:

  ```
  mvn test -Ddocs.update=true
  ```

**Para añadir una variable:**

1. Declárela en `Variables` (contexto, nombre, descripción en español e inglés y ejemplo).
2. Úsela en el código.
3. Regenere la documentación con el comando de arriba.

---

## 6. Cómo añadir un módulo

Ejemplo: un módulo `streaks` (rachas de conexión).

1. **Cree la carpeta** `module/streaks/` y la clase:

   ```java
   package me.gordi.mineapi.module.streaks;

   public final class StreaksModule extends AbstractModule {
       public StreaksModule(MineAPI plugin) {
           super(plugin, "streaks", "stats");           // id y dependencias
       }

       @Override
       protected void onEnable() {
           listen(new StreaksListener(this));             // se desregistra solo
           subscribe(PlayerDataReadyEvent.class, this::onReady);
           asyncTimer("save", 60_000, 60_000, this::save); // aparece en /lpa timings
           command(SubCommand.of("streak").playerOnly()
                   .executes((s, a) -> show((Player) s)).build());
       }

       @Override
       protected void onDisable() {
           // libere SOLO lo que no pasó por las ayudas de arriba (archivos, conexiones...)
       }
   }
   ```

2. **Regístrelo** en `MineAPI` con `modules.register(new StreaksModule(this));`.
3. **Catálogo:** añada la línea del módulo a `ModuleCatalog` (categoría, archivo, comandos,
   permisos). De ahí salen `/lpa modules`, la migración y el test de la guía.
4. **Configuración:**
   - Añada `streaks: false` al bloque `modules:` de `defaults/es|en/config.yml`.
   - Un extra (lo normal) lleva sus ajustes en `defaults/es|en/settings/streaks.yml`, con la cabecera corta
     (`# LosPerrisStatsAPI · … (settings/streaks.yml)`, `modules.streaks: true` y el enlace `{{DOCS_URL}}/es/GUIA.md#streaks`).
     Un módulo del núcleo va en una sección de `config.yml` con su título de una línea y en `ConfigLayout.CORE_SECTIONS`.
     Las duraciones se escriben `5m`/`1h` y se leen con `Durations.seconds`. Léalas con `config()`.
   - Si hay que mover claves antiguas, añada el paso a `ConfigMigrator` y suba `config-version`.
5. **Textos y ayuda:**
   - Ponga las claves en `lang/es.yml` y `lang/en.yml`, con las mismas claves en los dos.
   - Si usa variables nuevas, declárelas en `Variables` (§5).
   - Para cada comando, añada su entrada en `HelpCatalog` y `help.cmd.<clave>.usage|desc|info`,
     más `module-info.streaks.name|desc`. Si tiene permiso propio, declárelo en `Perms` y como
     hijo de `losperris.admin` en `plugin.yml`.
   - Añada su apartado en `docs/es/GUIA.md` y `docs/en/GUIDE.md` (un test lo exige).
6. **Pruebas:** cree `src/test/java/me/gordi/mineapi/module/streaks/`. Pruebe la lógica pura
   sin Bukkit: separe los cálculos en clases sin estado, como `Leaderboards` o `StatsQueries`.
7. **Dashboard (opcional):** añada rutas nuevas en `module/web/routes` y las claves en ambos
   diccionarios de `web/i18n.js`.

**Lo que NO debe hacer:**

- Llamar a `Bukkit.getScheduler()` o `registerEvents` directamente. Use las ayudas de
  `AbstractModule`; si no, el módulo no se limpiaría al desactivarse.
- Guardar estado en campos `static`.
- Leer otro módulo por su clase. Use eventos o la dependencia declarada.

---

## 7. Reglas de rendimiento

1. **Nada de E/S en el hilo principal.** Los archivos, la base de datos, HTTP y Discord van
   siempre por `scheduler().async(...)` o `asyncTimer`. El hilo principal solo lee la caché.
2. **Caché en memoria por UUID** (`PlayerDataService`):
   - El hilo principal solo lee y modifica la caché.
   - Para guardar, se hace una copia del JSON en el hilo principal (rápido) y la escritura va
     en segundo plano. La base de datos o el disco nunca bloquean un tick.
3. **Eventos frecuentes baratos:**
   - Las estadísticas salen de las `Statistic` de vanilla, que el servidor ya cuenta. El plugin
     no escucha `PlayerMoveEvent` ni otros eventos por tick.
   - Si un módulo nuevo necesita uno de esos eventos, debe salir lo antes posible (por ejemplo,
     sin cambio de bloque, nada) y no debe reservar memoria por evento.
   - Use `EventBus.hasSubscribers(...)` antes de construir un evento interno que nadie escucha.
4. **Rankings precalculados:** `Leaderboards` se recalcula de forma asíncrona cada cierto
   tiempo. Los comandos, placeholders, scoreboard y la web leen el resultado ya hecho. Nunca
   ordene a todos los jugadores dentro de un comando o placeholder.
5. **Instantáneas para la web:** el hilo principal copia una vez por segundo los datos que
   solo él puede leer (`web:snapshot`). Las peticiones HTTP leen esa copia y nunca llaman a la
   API de Bukkit desde otros hilos.
6. **Scoreboard con diferencias:** solo se reescriben las líneas que cambiaron.
7. **Las plantillas se compilan una vez**, al cargar (`Template`). Al enviar un mensaje solo se
   sustituyen los valores; no hay expresiones regulares en caliente.
8. **Un módulo desactivado no cuesta nada:** no deja listeners, tareas ni hilos. El
   `ModuleManager` lo garantiza al desactivar.
9. **Todas las tareas tienen nombre** (`módulo:tarea`), así que todas aparecen en
   `/lpa timings` (media, máximo y ejecuciones).
10. **Módulos de la fase 2:**
   - `history` guarda una foto al día por jugador en segundo plano; no escucha eventos de juego.
   - `periods` calcula ganancias como diferencia con la foto del inicio del periodo y ordena de
     forma asíncrona cada `periods.update-interval` segundos.
   - la galería de logros recalcula la rareza cada `gallery.update-interval`, nunca por petición.
   - Las estadísticas nuevas (colocados, menas, peces, comercios, daño) se leen de `Statistic` de vanilla;
     solo las causas de muerte usan un listener, y solo en `PlayerDeathEvent`.
   - La página pública reutiliza las mismas cachés que la API privada y limita peticiones por IP.

---

## 8. Cómo medir (profiling)

Use un servidor de pruebas **Paper 1.21.1** con [spark](https://spark.lucko.me/) instalado.

1. **Prepare la carga.** Conecte bots, por ejemplo con mineflayer: `node stay.js Bot1 "hola"`.
   Para simular un servidor real use entre 10 y 50 bots que se muevan, hablen, rompan bloques
   y mueran.
2. **Mida el plugin:**
   - `/lpa timings` muestra el coste de cada tarea del plugin. Como referencia, en la prueba de
     esta fase la tarea más cara del hilo principal, `scoreboard:update`, quedó por debajo de
     0,1 ms de media.
   - `/lpa timings reset` pone los contadores a cero antes de cada medición.
3. **Mida el servidor:**
   - `/spark tps` y `/spark health` dan los TPS, la duración de los ticks (mediana y p95), la
     CPU y la memoria.
   - `/spark profiler start` y, tras unos minutos con carga, `/spark profiler stop` generan el
     perfil completo. Busque `me.gordi.mineapi` en el árbol del hilo `Server thread`.
4. **Compare antes y después** de cada cambio, con el mismo número de bots y durante el mismo
   tiempo. Todo cambio en un evento frecuente debe venir acompañado de esta medición.
5. **Reconozca los problemas:**
   - Ticks de más de 50 ms.
   - Una tarea `sync` con un máximo de varios ms.
   - Memoria que sube sin bajar después de varias recolecciones de basura.

---

## 9. Compilar y probar

```
mvn clean package                 # compila, ejecuta las pruebas y genera target/LosPerrisStatsAPI-<versión>.jar
mvn test                          # solo las pruebas (JUnit 5)
mvn test -Ddocs.update=true       # regenera docs/es|en/variables desde el registro
```

El JAR incluye HikariCP y el driver de PostgreSQL reubicados en `me.gordi.mineapi.libs.*`, así
que no chocan con otros plugins.

---

## 10. Próximas fases

Cada una de estas fases entra como un módulo nuevo o como una ampliación de uno existente.
No hace falta tocar el núcleo, salvo para registrar nuevos contextos de variables:

Ya hechas en la fase 2: estadísticas ampliadas, historial diario, tops semanal/mensual,
página pública, comparar jugadores, galería de logros y nuevos menús. En la fase 3: consola web,
moderación, alertas, mapa de calor, copias diarias, aviso de actualizaciones, exportación CSV,
misiones, rachas y recompensas por tiempo activo. En la fase 4: temporadas, títulos según estadísticas, hologramas
de ranking, bot de Discord bidireccional y eventos con tops temporales.

Ideas para más adelante:

- torneos por equipos
- tienda de recompensas en la web
- estadísticas por mundo
