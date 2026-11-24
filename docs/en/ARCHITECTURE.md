# LosPerrisStatsAPI architecture

Español: [ARQUITECTURA.md](../es/ARQUITECTURA.md)

This document explains how the plugin is organized, how to add a new module and which
performance rules you must follow. Read it before touching the code.

**Core idea:** the plugin has a small **core** (`core/`) and a list of **modules**
(`module/*`). Each module is turned on or off with `modules.<id>: true|false` in `config.yml` (or with
`/lpa modules enable|disable <id>`) and keeps its options where `ConfigLayout.place(id)` says: **core** modules in their `config.yml` section (`<id>:`) or in
`settings/web.yml` and `settings/discord.yml` (one section per Discord module), and each **extra** in `settings/<id>.yml`.
The database lives in `settings/database.yml` and the data in `data/`. In `settings/milestones.yml` settings come first and the list below.
A disabled module registers no listeners, tasks, commands or threads, and its settings are not even parsed (its file is not opened): it costs nothing.

---

## 1. Project tree

```
src-review/
├── pom.xml                         Maven, single module (Java 17, Spigot API 1.20.1)
├── README.md                       what it is, where to start and folder table (ES/EN)
├── CHANGELOG.md                    changes per version (Spanish: REGISTRO_DE_CAMBIOS.md)
├── docs/                           ALL documentation, one single place
│   └── es|en/
│       ├── GUIDE.md                quick guide: one section per module
│       ├── API_REFERENCE.md        commands, permissions, placeholders, HTTP API
│       ├── DISCORD_SETUP.md        Discord bot
│       ├── ARCHITECTURE.md         this document (Spanish: ARQUITECTURA.md)
│       └── variables/              variables per context (GENERATED, see §5)
└── src/
    ├── main/java/me/gordi/mineapi/
    │   ├── MineAPI.java            entry point: starts the core and registers the modules
    │   ├── api/                    public API for addons: LosPerrisApi, WebApi, MenuApi, MenuItems, DiscordApi, PlayerTag, CommandApi, AddonApi
    │   ├── core/                   core; never depends on module/*
    │   │   ├── command/            /lpa: CommandRouter, SubCommand, CoreCommands, HelpCatalog/HelpView (help), ModulesView
    │   │   ├── config/             ConfigService (config.yml + settings/), ConfigLayout (blocks and place(id)), ConfigMigrator (v1 → v9 with backup), DependencyWarnings, ModuleToggleWriter
    │   │   ├── data/               PlayerDataService (cache per UUID), UuidMigration, listener
    │   │   ├── event/              internal EventBus + events (StatChanged, PlayerReset, ...)
    │   │   ├── hook/               optional integrations (PlaceholderAPI, Vault)
    │   │   ├── i18n/               I18n (server language and per-player language), Lang
    │   │   ├── log/                DebugLog
    │   │   ├── module/             Module, AbstractModule, ModuleManager, ModuleCatalog (single module list), Perms
    │   │   ├── placeholder/        Placeholders: per-prefix providers registered by modules
    │   │   ├── reward/             Rewards: shared rewards (command, item, money)
    │   │   ├── scheduler/          TaskScheduler (owned tasks with timings)
    │   │   ├── storage/            JSON / MySQL / PostgreSQL behind StorageProvider
    │   │   ├── text/               Template, Text, Csv, central variable registry and its validator
    │   │   └── time/               Durations (10m, 1h30m, perm) and Zones
    │   └── module/                 one module = one folder = one id
    │       ├── stats/  afk/  milestones/ (+ achievements gallery)
    │       ├── periods/
    │       ├── scoreboard/  gui/  placeholders/
    │       ├── web/                HTTP API and dashboard (http/, routes/)
    │       ├── publicweb/          `public-page` module: read-only public API and page (`public` is a Java reserved word)
    │       ├── console/            `web-console` module
    │       ├── heatmap/  history/
    │       ├── backups/  updates/
    │       ├── discordwebhook/     `discord-webhook` module: rankings to a webhook
    │       └── discordbot/         `discord-bot` module: GatewayClient, DiscordRest, SlashCommands, Mentions, LinkCodes/LinkStore, RoleRules, DiscordSrvHook
    ├── main/resources/
    │   ├── plugin.yml                      commands and permissions (every admin one is a child of losperris.admin)
    │   ├── defaults/es|en/config.yml       general + switches + core sections (one-line title)
    │   ├── defaults/es|en/settings/*.yml   web, discord, database and one <id>.yml per extra (short header + settings [+ list])
    │   ├── lang/es.yml, lang/en.yml        messages (none are hard-coded)
    │   ├── database/                       SQL schemas
    │   └── web/                            dashboard: html, css, js, i18n.js (ES/EN)
    └── test/java/me/gordi/mineapi/         JUnit 5 tests (same tree as main)
```

**Tree rules:**

- `core/` imports nothing from `module/`.
- A module only talks to another one through the `EventBus` or a declared dependency.
- Each module lives entirely in its folder. To remove it, delete the folder and its
  `register(...)` line.
- Tests mirror the package path they test.

**Addons:** what was added after the original plugin (quests, streaks, active playtime, titles, seasons, events,
holograms, moderation and alerts) lives in the **LosPerrisExtras** addon (`me.gordi.losperrisextras`,
`depend: [LosPerrisStatsAPI]`), which only uses `me.gordi.mineapi.api` and the module base classes. The core does
not know the addon: the addon's routes, menu pages and placeholders are registered with an owner and removed when
it is disabled (`LosPerrisApi.unregisterAll`). Since 1.5 its subcommands join the `/lpa` `CommandRouter`
(`CommandApi`), its catalog is added to the core one (`CommandCatalog`), its languages extend the root `I18n` and its
modules are added to the `ModuleManager` (`AddonApi`, `AddonRegistry`), with the switches in the core `config.yml`
(`ModuleToggleWriter.addGroup`).

---

## 2. Modules

| id             | Depends on | What it does                                               | config.yml switch                     |
|----------------|------------|------------------------------------------------------------|------------------------------------|
| `stats`        | (none)     | Counts kills, deaths, hours, blocks broken/placed, ores per type, jumps, distance, fish, trades, damage and death causes; rankings | `modules.stats`            |
| `afk`          | (none)     | Marks AFK players; AFK hours do not count                  | `modules.afk`              |
| `milestones`   | `stats`    | Milestones, messages and rewards (`settings/milestones.yml`)                | `modules.milestones` (off) |
| `scoreboard`   | `stats`    | Sidebar scoreboard (`/lpa sb`)                             | `modules.scoreboard`       |
| `gui`          | `stats`    | Stats and admin menus                                      | `modules.gui`              |
| `placeholders` | `stats`    | `%losperrisapi_*%` for PlaceholderAPI (only if installed)  | `modules.placeholders`     |
| `discord-webhook`| `stats`    | Sends rankings to a webhook                                | `modules.discord-webhook` (off) |
| `history`      | `stats`    | Daily snapshot of each player (`/api/player/history`)      | `modules.history`          |
| `periods`      | `stats`    | Weekly/monthly leaderboards, automatic close, announcement and rewards | `modules.periods`          |
| `web`          | `stats`    | HTTP API and dashboard (`/dashboard`)                      | `modules.web`              |
| `public-page`  | `web`      | `/api/public/*` and `/?public`, read-only, no api-key       | `modules.public-page` (off) |
| `web-console`  | `web`      | Live web console (SSE) and commands filtered by allow/deny | `modules.web-console`      |
| `heatmap`      | (none)     | Average players online per weekday and hour                | `modules.heatmap`          |
| `backups`      | (none)     | Daily zip backup of the data; keeps the last `keep`        | `modules.backups`          |
| `updates`      | (none)     | Checks for new versions (GitHub, Modrinth or Spigot) in the background | `modules.updates`          |
| `discord-bot`  | `stats`    | Discord bot: slash commands, chat bridge, linking, roles and staff (DiscordSRV or embedded) | `modules.discord-bot` (off) |

**Behavior:**

- **Startup order:** the order of the `register(...)` calls in `MineAPI`. If a dependency is
  missing, the module does **not** start and a console warning is shown.
- **Errors:** if `enable()` throws, only that module goes down. For example, if the web port
  is taken, `web` is disabled and everything else keeps working.
- **`/lpa reload`:** rereads the config and languages. Enables or disables the modules whose
  switch changed and reloads the ones still active. All of it live, without a restart.
- **`/lpa modules [id]`:** shows each module's state grouped by category (● on, ○ off,
  ⚠ enabled with a problem, with the reason). With an id it shows the detail: file, commands,
  permissions and dependencies. Every name is clickable in chat.
- **`/lpa modules enable|disable <id>`** (permission `losperris.admin.modules.toggle`): changes only
  the `modules.<id>` line of `config.yml` (comments are kept, atomic write) and applies the change
  live with `ModuleManager.sync()`, without reloading the other modules. Turning a module off also
  turns off the ones that depend on it; a module whose dependency is off cannot be turned on.
  A disabled module's config is released from memory.
- **Help:** `HelpCatalog` is the single list of commands (module, permission, admin or not).
  `HelpView` builds `/lpa help` (only commands of enabled modules the sender may use, grouped by
  category) and `ChatOut` sends it with hover text and click in game, or as plain text in the
  console. A test checks every command and permission has its text in ES and EN.

---

## 3. Configuration changes (v1 → … → v6 → v7 → v8)

The `ConfigMigrator` updates `config.yml` by itself the first time the new version starts:

1. Saves a copy to `backups/config-<date>.yml`.
2. Moves old keys: `scoreboard.enabled` becomes `modules.scoreboard.enabled` and
   `discord.enabled` becomes `modules.discord.enabled`.
3. Adds the new keys with their defaults: `modules.*`, `language`,
   `per-player-language` and `web.history-*`, `web.admin-*`.
4. Writes the current version (`config-version: 3`). Later startups do nothing.

Data is migrated too. `server_data/all_stats.json` became indexed by UUID (format 2).
A copy is saved to `backups/all_stats-pre-uuid-<date>.json` before migrating. If a player had
no UUID, one is derived and corrected on their next join. Duplicates are merged. The existing
API (`/api/all`, `/api/stats?name=`) answers exactly as before.

**v2 → v3 (phase 2):** keys are only added (`modules.history|periods|achievements|public`, the
`periods`, `achievements` and `public` sections, the new statistics in the scoreboard and dashboard). No existing
value changes and `public` arrives disabled. The new statistics are read from vanilla `Statistic`, so each player
gets their historical value back the next time they join (until then it shows 0). Only death causes start counting
from the update. No data migration is needed.

**v3 → v4 (phase 3):** also only adds keys, with the same prior backup in `backups/config-<date>.yml`: the
`console`, `moderation`, `alerts`, `heatmap`, `backups` and `updates` modules (on) and `quests`, `streaks` and
`playtime` (off, because they hand out rewards), plus the `console`, `moderation`, `alerts`, `heatmap`, `backups`,
`updates`, `streaks` and `playtime-rewards` sections. `quests.yml` is created separately the first time. Streaks and
active playtime are stored as internal player statistics (`login_streak`, `best_login_streak`, `active_playtime`);
no data migration is needed.

**v4 → v5 (phase 4):** again only adds keys, with a prior backup in `backups/config-<date>.yml`: the `titles`,
`events` and `holograms` modules (on; with no event or hologram plugin they cost nothing) and `seasons` and
`discordbot` (off: they hand out rewards or need a token), the `seasons`, `titles`, `events`, `holograms` and
`discordbot` sections, and `scoreboard.event-lines`. `titles.yml` is created separately the first time. The season
starts when the module is enabled; titles players already meet are given on their next join. No data migration is needed.

**v5 → v6 (phase 5):** the long `config.yml` is split into a short `config.yml` (general settings and
`modules.<id>: true|false`) plus one file per module in `modules/<id>.yml`; `milestones.yml`, `quests.yml` and
`titles.yml` move to `modules/`. Before touching anything, `config.yml`, `milestones.yml`, `quests.yml` and `titles.yml` are saved to
`backups/migration-v6-<date>.zip`
and an existing module file is never overwritten. The `discord` and `discordbot` ids become `discord-webhook` and
`discord-bot` (the old ones are still accepted). User values are kept; only new keys are added. A disabled module
does not read its file. To go back to the previous version, restore that zip.

**v6 → v7 (phase 5b):** twenty files were too many. Everything goes back to a single ordered `config.yml`: short header, `general`, the grouped `modules` list and one section per module (`web:`, `events:`…) in category order, each with a box that explains what it does, its commands, permissions and variables. The long lists stay as their own files (`milestones.yml`, `quests.yml`, `titles.yml`, in the plugin folder). At startup `ConfigService` only parses the head (`config-version`, `general`, `modules`); a module's section is parsed the first time the module is enabled (`ConfigLayout` splits the text into blocks without parsing the YAML). Writes (new api-key, new keys, `/lpa modules enable|disable`) change only that block; the rest of the file stays byte-identical. The migration (from v5 or v6) reads everything first; if anything is unreadable it changes nothing. It then saves every file involved, with its path, to `backups/migration-v7-<date>.zip`, writes the lists and the new `config.yml` and finally removes `modules/`. To go back: delete the new files and unzip the backup.

**v7 → v8 (phase 5c):** a single `config.yml` was reaching almost a thousand lines. Now `config.yml` (≈360 lines) holds the general settings, the switches and the small modules' sections, each with a one-line title linking to the guide (`# ── stats · Statistics · guide: GUIDE.md#stats ──`). Big modules have their own file with the LosPerrisStats banner and their detailed box: `web.yml`, `discord.yml` (`discord-webhook` and `discord-bot` sections), `events.yml`, `scoreboard.yml`, and `milestones.yml`, `quests.yml`, `titles.yml` with settings on top and the list below. `ConfigLayout.place(id)` says where each module lives; a disabled module's file is never opened. Own files are edited per top-level block (`ConfigLayout.replaceTop`), so comments are kept. The migration (from v5, v6 or v7) reads everything first, saves every source file to `backups/migration-v8-<date>.zip`, writes the module files and `config.yml` last (if anything fails it runs again on the next start) and removes `modules/`.

**v8 → v9 (phase 6):** the root is clean: only `config.yml` (≈185 lines: `general`, the switches grouped into core and extras, and the `stats`, `periods`, `history`, `backups` and `updates` sections). `settings/` holds `web.yml`, `discord.yml` (`discord-webhook` and `discord-bot` sections), `database.yml` and one `settings/<id>.yml` per extra, with a short header linking to the guide; an extra's file is created the first time it is enabled. `server_data/` becomes `data/` (`DataDir`) and the documentation is no longer copied to the server (`docs/` is deleted); comments point to `/lpa help` and the `Links.DOCS` URL. The `public` and `console` ids become `public-page` and `web-console`. Durations are written `5m`, `1h`… (`Durations.seconds` still accepts the old plain number in the old unit) and stats use the `stats.tracked` name (`player_kills`, `play_time`; `StatType.parse` accepts the aliases). The migration (from v5, v6, v7 or v8) first saves every source file to `backups/migration-v9-<date>.zip`, keeps values and secrets, does not create files for untouched extras and writes `config.yml` last. On startup and on `/lpa reload`, `DependencyWarnings` reports in one line the settings that do nothing because the module they need is off (a template `true` waiting for Discord is not reported).

**v9 → v10 (1.4):** quests, streaks, active playtime, titles, seasons, events, holograms, moderation and alerts move
to the LosPerrisExtras addon, and `achievements` merges into `milestones` (`gallery:` in `settings/milestones.yml`).
`ConfigService` (`ExtrasSplit`) first saves everything to `backups/migration-v10-<date>.zip`, **moves** the
`settings/<id>.yml` files and the data (`data/seasons/`, `data/events/`, `data/holograms.json`, `data/sanctions/`) to
`plugins/LosPerrisExtras/` and leaves their switches there for the addon to import. `config.yml` is written last.
Those modules' commands move from `/lpa` to `/lpx`; their endpoints and placeholders stay with the addon installed.

The core's permissions, placeholders and HTTP endpoints remain **unchanged**.

---

## 4. Languages (i18n)

**Server:**

- Messages live in `lang/<code>.yml`; `es` and `en` are included. You can add another language
  by copying one of them (for example, `lang/pt.yml`).
- `language: es` in `config.yml` sets the default language.
- With `per-player-language: true`, each player gets messages in their Minecraft client
  language. They can pick another one with `/lpa lang <es|en|auto>`.
- If a text is missing, it is looked up in this order: player language → default language → `es`.

**In code:**

- Write `i18n().send(sender, "key", "player", name)`, with variables as name/value pairs.
- Never hard-code text.

**Dashboard:**

- `web/i18n.js` holds the ES and EN dictionaries. The sidebar button switches language.
- The choice is stored in the browser. If the user never chose, the server language is used.
- ES and EN keys must be identical.

---

## 5. Variables: central registry

`core/text/Variables.java` is the **single source of truth**. It declares the variables each
context accepts (`lang`, `milestones`, `rewards`, `scoreboard`, `discord`,
`placeholderapi`...). Two things come out of that registry:

- **The console warning:** when files load, `VariableValidator` warns about every unknown
  variable with the file, key and variable. It runs once per load, never during gameplay.
- **The documentation:** `docs/es/variables/*.md` and `docs/en/variables/*.md` (in the repository; the plugin
  no longer copies them to the server) must match the registry. Every description
  is declared in both languages (`Localized`). The `VariableDocsTest` test fails if they do not
  match. To regenerate them run:

  ```
  mvn test -Ddocs.update=true
  ```

**To add a variable:**

1. Declare it in `Variables` (context, name, Spanish and English description, and example).
2. Use it in the code.
3. Regenerate the docs with the command above.

---

## 6. How to add a module

Example: a `streaks` module (login streaks).

1. **Create the folder** `module/streaks/` and the class:

   ```java
   package me.gordi.mineapi.module.streaks;

   public final class StreaksModule extends AbstractModule {
       public StreaksModule(MineAPI plugin) {
           super(plugin, "streaks", "stats");           // id and dependencies
       }

       @Override
       protected void onEnable() {
           listen(new StreaksListener(this));             // unregistered automatically
           subscribe(PlayerDataReadyEvent.class, this::onReady);
           asyncTimer("save", 60_000, 60_000, this::save); // shows up in /lpa timings
           command(SubCommand.of("streak").playerOnly()
                   .executes((s, a) -> show((Player) s)).build());
       }

       @Override
       protected void onDisable() {
           // release ONLY what did not go through the helpers above (files, connections...)
       }
   }
   ```

2. **Register it** in `MineAPI` with `modules.register(new StreaksModule(this));`.
3. **Catalog:** add the module's line to `ModuleCatalog` (category, file, commands,
   permissions). `/lpa modules`, the migration and the guide test all come from there.
4. **Configuration:**
   - Add `streaks: false` to the `modules:` block of `defaults/es|en/config.yml`.
   - An extra (the usual case) keeps its settings in `defaults/es|en/settings/streaks.yml`, with the short header
     (`# LosPerrisStatsAPI · … (settings/streaks.yml)`, `modules.streaks: true` and the link `{{DOCS_URL}}/en/GUIDE.md#streaks`).
     A core module goes in a `config.yml` section with its one-line title and in `ConfigLayout.CORE_SECTIONS`.
     Durations are written `5m`/`1h` and read with `Durations.seconds`. Read them with `config()`.
   - If old keys must move, add the step to `ConfigMigrator` and bump `config-version`.
5. **Texts and help:**
   - Put the keys in `lang/es.yml` and `lang/en.yml`, with the same keys in both.
   - If you use new variables, declare them in `Variables` (§5).
   - For each command, add its `HelpCatalog` entry and `help.cmd.<key>.usage|desc|info`,
     plus `module-info.streaks.name|desc`. If it has its own permission, declare it in `Perms` and
     as a child of `losperris.admin` in `plugin.yml`.
   - Add its section to `docs/es/GUIA.md` and `docs/en/GUIDE.md` (a test requires it).
6. **Tests:** create `src/test/java/me/gordi/mineapi/module/streaks/`. Test pure logic without
   Bukkit: move calculations into stateless classes, like `Leaderboards` or `StatsQueries`.
7. **Dashboard (optional):** add new routes in `module/web/routes` and the keys in both
   dictionaries of `web/i18n.js`.

**What NOT to do:**

- Call `Bukkit.getScheduler()` or `registerEvents` directly. Use the `AbstractModule`
  helpers; otherwise the module would not be cleaned up when disabled.
- Keep state in `static` fields.
- Read another module by its class. Use events or the declared dependency.

---

## 7. Performance rules

1. **No I/O on the main thread.** Files, database, HTTP and Discord always go through
   `scheduler().async(...)` or `asyncTimer`. The main thread only reads the cache.
2. **In-memory cache per UUID** (`PlayerDataService`):
   - The main thread only reads and modifies the cache.
   - To save, a JSON copy is made on the main thread (fast) and the write happens in the
     background. The database or disk never blocks a tick.
3. **Cheap frequent events:**
   - Statistics come from vanilla `Statistic`, which the server already counts. The plugin
     does not listen to `PlayerMoveEvent` or other per-tick events.
   - If a new module needs one of those events, it must exit as early as possible (for example,
     no block change, nothing) and must not allocate memory per event.
   - Use `EventBus.hasSubscribers(...)` before building an internal event nobody listens to.
4. **Precomputed rankings:** `Leaderboards` is recomputed asynchronously every so often.
   Commands, placeholders, scoreboard and web read the finished result. Never sort all
   players inside a command or placeholder.
5. **Snapshots for the web:** once per second the main thread copies the data only it can read
   (`web:snapshot`). HTTP requests read that copy and never call the Bukkit API from other threads.
6. **Diff-based scoreboard:** only lines that changed are rewritten.
7. **Templates compile once**, on load (`Template`). Sending a message only substitutes
   values; no regular expressions on the hot path.
8. **A disabled module costs nothing:** it leaves no listeners, tasks or threads. The
   `ModuleManager` guarantees it on disable.
9. **Every task has a name** (`module:task`), so all of them show up in
   `/lpa timings` (average, max and runs).
10. **Phase 2 modules:**
   - `history` keeps one snapshot per day per player in the background; it listens to no game events.
   - `periods` computes gains as the difference with the snapshot taken at the start of the period and
     sorts asynchronously every `periods.update-interval` seconds.
   - the achievements gallery recomputes rarity every `gallery.update-interval`, never per request.
   - The new statistics (placed, ores, fish, trades, damage) are read from vanilla `Statistic`;
     only death causes use a listener, and only on `PlayerDeathEvent`.
   - The public page reuses the same caches as the private API and limits requests per IP.

---

## 8. How to measure (profiling)

Use a **Paper 1.21.1** test server with [spark](https://spark.lucko.me/) installed.

1. **Prepare the load.** Connect bots, for example with mineflayer: `node stay.js Bot1 "hello"`.
   To simulate a real server use 10 to 50 bots that move, chat, break blocks and die.
2. **Measure the plugin:**
   - `/lpa timings` shows the cost of each plugin task. As a reference, in this phase's test
     the most expensive main-thread task, `scoreboard:update`, stayed under 0.1 ms on average.
   - `/lpa timings reset` zeroes the counters before each measurement.
3. **Measure the server:**
   - `/spark tps` and `/spark health` give TPS, tick duration (median and p95), CPU and memory.
   - `/spark profiler start` and, after a few minutes under load, `/spark profiler stop` produce
     the full profile. Look for `me.gordi.mineapi` in the `Server thread` tree.
4. **Compare before and after** each change, with the same number of bots and for the same
   time. Every change to a frequent event must come with this measurement.
5. **Recognize problems:**
   - Ticks over 50 ms.
   - A `sync` task with a max of several ms.
   - Memory that keeps rising and does not drop after several garbage collections.

---

## 9. Build and test

```
mvn clean package                 # compiles, runs the tests and builds target/LosPerrisStatsAPI-<version>.jar
mvn test                          # tests only (JUnit 5)
mvn test -Ddocs.update=true       # regenerates docs/es|en/variables from the registry
```

The JAR bundles HikariCP and the PostgreSQL driver relocated under `me.gordi.mineapi.libs.*`,
so they do not clash with other plugins.

---

## 10. Next phases

Each of these phases comes in as a new module or as an extension of an existing one.
The core does not need to change, except to register new variable contexts:

Already done in phase 2: extended statistics, daily history, weekly/monthly leaderboards,
public page, compare players, achievements gallery and new menus. In phase 3: web console,
moderation, alerts, heatmap, daily backups, update notices, CSV export, quests, streaks and
active-playtime rewards. In phase 4: seasons, titles based on statistics, leaderboard holograms, two-way Discord
bot and events with temporary leaderboards.

Ideas for later:

- team tournaments
- rewards shop on the web
- per-world statistics
