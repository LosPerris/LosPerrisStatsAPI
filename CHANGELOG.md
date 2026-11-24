# Changelog

Español: [REGISTRO_DE_CAMBIOS.md](REGISTRO_DE_CAMBIOS.md)

## 1.6.0 — Discord status panel

### Changes
- Discord bot: `/panel` posts a configurable status embed (online bar, registered players, TPS, IP, version, uptime)
  that auto-refreshes; buttons open ephemeral Online/Tops/Info embeds.
- `panel.embed` and `custom-commands` in `settings/discord.yml`; improved `/stats` (player required), `/top` (`amount`),
  `/online` and `/staff` embeds.
- Removed built-in account linking (`/link`, `/unlink`, `/lpa discord link`). Old `link:` keys still load harmlessly;
  `data/discord_links.json` is kept. Roles sync only with DiscordSRV.
- Docs and links point to https://github.com/LosPerris/LosPerrisStatsAPI

### Behavior changes
- `/stats` without a player name no longer looks up a linked Discord account.
- Slash set no longer includes `/link` / `/unlink`.

### Automatic migration
- Module merge adds `panel` / `custom-commands` keys. No `config-version` bump required.

## 1.5.1 — evolution on the web profile

### Changes
- Web profile: with LosPerrisExtras 1.3 and its `evolution` module on, an "Evolution" card shows the current rank,
  the next one with its percentage, a bar per requirement and the next reward.

### Behavior changes
- None. LosPerrisExtras 1.3 also works with 1.5 (without this card).

## 1.5 — addons integrated into the core

### Changes
- **Command API** (`api.commands()`): an addon adds `/lpa` subcommands with name, aliases, permission, handler, tab
  completion and help; they show in `/lpa help` and tab completion and are removed with `unregisterAll(owner)`.
- **Addon API** (`api.addons()`): an addon's modules join the core module system: `/lpa modules` (list, detail,
  `enable`/`disable`), the dashboard module list and `/lpa reload` (which also reloads the addon).
- An addon's switches are written to the core `config.yml` when missing, off, under `# ===== <Addon> =====`, without
  touching existing values. If the addon is removed, the lines stay and are ignored.
- `/lpa version` lists the installed addons and their version.
- Web profile: quests with several objectives show a small bar per objective.
- Performance: the profile of a player with no plugin data no longer walks every player who ever joined on each
  request (name index, rebuilt at most every 5 minutes).
- Cleanup: unused code and imports, module lookups safe from web threads.

### Behavior changes
- `LosPerrisApi.VERSION` becomes 2.

### Automatic migration
- None in the core (config v10). LosPerrisExtras 1.2 moves its switches into the core `config.yml` on first start.

## 1.4 — core and LosPerrisExtras addon (config v10)

### Changes
- **The core goes back to its original features, improved:** statistics and leaderboards, HTTP API and web dashboard
  (with chat stream, public page, web console and heatmap), Discord (webhook and bot), milestones with Vault rewards,
  scoreboard, AFK, menus, PlaceholderAPI, JSON/MySQL/PostgreSQL storage, weekly/monthly leaderboards, history, backups
  and update notices.
- **What was added later moves to the [LosPerrisExtras](https://github.com/LosPerris/LosPerrisExtras) addon:** quests,
  streaks, active playtime, titles, seasons, events, holograms, moderation and alerts. It has its own `/lpx` command,
  folder (`plugins/LosPerrisExtras/`) and `config.yml`.
- **Achievements merge into milestones:** the gallery with rarity is now `gallery:` in `settings/milestones.yml`
  (milestones stay off by default because they hand out rewards). Placeholders `%losperrisapi_achievements_*%`, the
  "Achievements" page and `/api/achievements` keep working.
- **Public Java API for addons** (`me.gordi.mineapi.api.LosPerrisApi`): HTTP routes, profile fields, menu pages,
  Discord notices, player tag, event mode and the internal events. Documented in API_REFERENCE §7.
- `config.yml` switches are grouped as core, web, and server and data (175 lines on a fresh install).
- `/lpa modules` and `/lpa help` only list core modules; the console says which modules are on (or "none").

### Behavior changes
- The addon modules' commands move from `/lpa` to `/lpx` (`/lpx quests`, `/lpx season`, `/lpx title`, `/lpx event`,
  `/lpx mod`, `/lpx holo`). Permissions keep their names.
- Without the addon (or with its module off), `/api/quests`, `/api/seasons`, `/api/events`, `/api/titles` and their
  admin routes answer `404`.
- Fix: `/api/titles` (and other translated texts) without `?lang=` no longer fails with 500.

### Automatic migration (from v9)
- Before touching anything it saves everything to `backups/migration-v10-<date>.zip`.
- Moves the addon modules' `settings/<id>.yml` and their data (`data/seasons/`, `data/events/`, `data/holograms.json`,
  `data/sanctions/`) to `plugins/LosPerrisExtras/`; the addon imports their switches on first start. Nothing is
  deleted without a copy.
- `achievements` becomes `gallery:` in `milestones.yml` (on if it was on).
- In `lang/`, the "Extras · web" and "Extras · server" categories become "Web" and "Server and data" unless you had changed them.

## 1.3-V3 — phase 6 (config v9: clean root and settings/ folder)

### Changes
- **Only `config.yml` stays at the root** (`config-version: 9`, ≈185 lines): `general`, the switches split into
  **core** (stats, web, discord-webhook, discord-bot, periods, history, placeholders, gui, backups, updates) and
  **extras**, and the `stats`, `periods`, `history`, `backups` and `updates` sections.
- **`settings/` folder:** `web.yml`, `discord.yml` (`discord-webhook` and `discord-bot` sections), `database.yml` and one
  `settings/<id>.yml` per extra. An extra's file is created the first time it is enabled; a disabled one is never opened.
- **`server_data/` is renamed to `data/`.** The other folders: `lang/` and `backups/`.
- **Docs are no longer copied to the server** (the old `docs/` folder is deleted). Comments point to `/lpa help`,
  `/lpa modules <id>` and the published guide.
- **New ids:** `public` → `public-page` and `console` → `web-console` (the old ones still work as aliases).
- **Readable durations:** `5m`, `1h`, `1d`… Old plain numbers are still accepted in their previous unit.
- **Consistent stat names** matching `stats.tracked` (`player_kills`, `play_time`…); `kills`, `time`, `hours` still work
  as aliases.
- **Defaults:** web, web console, heatmap, history, moderation, alerts, scoreboard, holograms and afk on; public page
  and Discord off; the progression extras (milestones, achievements, titles, events, quests, streaks, playtime,
  seasons) now default to **off**.
- **Dependency warning:** on startup and `/lpa reload`, one console line when a setting does nothing because the module
  it needs is off (e.g. `moderation.discord: true` without `discord-webhook`).
- The ES and EN templates have exactly the same keys (checked by a test).
- `/lpa modules` groups core and extras and shows the right file (`settings/web.yml`, `config.yml → stats`…).

### Automatic migration (from v8, v7, v6 or v5)
- Before touching anything it saves every source file to `backups/migration-v9-<date>.zip`.
- Keeps your values and secrets (api-key, token, webhook, rewards, lists…); extras you never touched get no file until
  you enable them.
- Moves `server_data/` → `data/`, `database/database.yml` → `settings/database.yml` and deletes `docs/`.
- **Rolling back:** stop the server, delete `config.yml` and `settings/`, rename `data/` to `server_data/`, unzip the
  backup into the plugin folder and go back to the previous JAR.

## 1.3-V2 — phase 5c (config v8: own files for the big modules)

### Changes
- **`config.yml` goes from ~990 to 359 lines** (`config-version: 8`): general settings, the `modules` list and the
  small modules' sections, each with a one-line title linking to the guide (`# ── stats · Statistics · guide: GUIDE.md#stats ──`).
- **Own files for the big modules**, with their detailed comments: `web.yml`, `discord.yml` (`discord-webhook` and
  `discord-bot` sections), `events.yml` and `scoreboard.yml`.
- **`milestones.yml`, `quests.yml` and `titles.yml`** now hold the module's settings on top and the list below.
- **Big LosPerrisStats banner** at the top of `config.yml` and of every own file.
- A disabled module's file is never opened; if it is missing it is created from the template when the module is enabled.
- Own files are edited per top-level block, so comments are kept. `/lpa modules enable|disable` still changes only that
  line of `config.yml`.
- `/lpa modules <module>` shows the right file (`web.yml`, `discord.yml → discord-bot`, `config.yml → stats`…).
- The guide has one anchor per module (`GUIDE.md#<id>`) and says where each module's settings are.

### Automatic migration (from v7, v6 or v5)
- Reads everything first; if anything cannot be read it changes nothing and says so.
- Saves every source file to `backups/migration-v8-<date>.zip`, writes the module files and `config.yml` last (if anything
  fails it runs again on the next start) and removes `modules/`.
- Every value is kept (api-key, tokens, webhooks, presets, rewards, lists…).
- **Going back:** stop the server, delete `config.yml`, `web.yml`, `discord.yml`, `events.yml`, `scoreboard.yml`,
  `milestones.yml`, `quests.yml` and `titles.yml`, put the previous JAR back and unzip the backup into the plugin folder.

## 1.3-V2 — phase 5b (single config.yml)

### Changes
- **One ordered `config.yml` again** (`config-version: 7`) instead of 20 files: short header → `general` →
  the grouped `modules` on/off list → one section per module (`web:`, `events:`, `discord-bot:`…) in category order.
  Each section starts with a box: what the module does, its commands, permissions, variables doc and how to enable it.
- **Lists stay as their own files:** `milestones.yml`, `quests.yml` and `titles.yml` (plus `lang/`) hold only the
  long lists; the settings of those modules are in their `config.yml` section.
- **Performance:** at startup only `config-version`, `general` and `modules` are parsed. A module's section is parsed
  the first time the module is enabled; a disabled module's section is never parsed. Writing a section (new api-key,
  new keys, `/lpa modules enable|disable`) changes only that block, keeping every comment and the rest of the file.
- `/lpa modules <module>` now shows `Settings: config.yml → <id>` (and the list file for milestones, quests and titles).
- Messages, guide, READMEs and docs point to `config.yml → <id>` instead of `modules/<id>.yml`.
- Backups (`include-configs`) save the plugin folder's `.yml` files plus `lang/` and `database/`.

### Automatic migration (from v6 or v5)
- On the first start the plugin reads everything first; if any file cannot be read it changes nothing and says so.
- It then saves every file involved (`config.yml`, `milestones.yml`, `quests.yml`, `titles.yml` and `modules/*.yml`),
  with their paths, to `backups/migration-v7-<date>.zip`, writes the lists and the new `config.yml`, and finally
  removes `modules/` (it is complete in the zip).
- Values are kept (api-key, tokens, webhooks, presets, rewards…). Comments are the new template's; yours are in the zip.
- **Rollback:** stop the server, delete `config.yml`, `milestones.yml`, `quests.yml` and `titles.yml`, put the previous
  JAR back and unzip `backups/migration-v7-<date>.zip` into the plugin folder.

## 1.3-V2 — phase 5

### New
- **One file per module:** `config.yml` is now short (language, time zone, database and the
  `modules: <id>: true|false` list) and each module has its own `modules/<id>.yml` with a header that explains
  what it does, how to enable it, its commands and permissions. A disabled module does not read its file.
- **`/lpa modules enable|disable <module>`:** turns a module on or off without restarting. Only that line of
  `config.yml` changes (comments are kept), turning one off also turns off the ones that depend on it, and a
  module whose dependency is off cannot be turned on. `/lpa modules <module>` shows the detail: what it does,
  dependencies, file, commands, permissions and, if it fails to start, the reason.
- **New help:** `/lpa help` groups the commands by category, only shows the ones you can use and they are
  clickable. The admin help is in `/lpa help admin` (clickable link at the end) and a module's help in
  `/lpa help <module>`.
- **Per-module permissions**, all children of `losperris.admin`: `losperris.reload`, `losperris.modules`,
  `losperris.modules.toggle`, `losperris.timings`, `losperris.stats.reset`, `losperris.gui.admin`,
  `losperris.periods.admin`, `losperris.seasons.admin`, `losperris.events.admin`, `losperris.titles.admin`,
  `losperris.holograms.admin`, `losperris.moderation`, `losperris.backups.admin`, `losperris.alerts.notify` and
  `losperris.updates.notify`.
- **Admin guide** in Spanish and English (`docs/es/GUIA.md`, `docs/en/GUIDE.md`), also copied to the server in
  `plugins/LosPerrisStatsAPI/docs/`.

### Changes
- **Automatic v5 → v6 migration** (`config-version: 6`): on the first start `config.yml`, `milestones.yml`,
  `quests.yml` and `titles.yml` are saved to `backups/migration-v6-<date>.zip` and then `config.yml` is split into `modules/<id>.yml`. `milestones.yml`,
  `quests.yml` and `titles.yml` move to `modules/`. Your values are kept and an existing module file is never
  overwritten.
- **Going back:** the previous JAR does not understand the new format. Stop the server, put the previous JAR back
  and unzip `backups/migration-v6-<date>.zip` over the plugin folder.
- The `discord` and `discordbot` modules are renamed `discord-webhook` and `discord-bot`; `/lpa modules` still
  accepts the old names.
- Admin commands ask for their own permission instead of `losperris.admin`. Anyone with `losperris.admin` (or OP)
  notices nothing; if you give individual permissions with LuckPerms, check the table in the guide (§4).
- All documentation lives in `docs/` (before, also in `src/main/resources/docs/` and the root) and there is a root
  `README.md`.
- Renamed packages: `hook` → `core.hook`, `discord` → `discordwebhook` (`DiscordWebhookModule`),
  `discord.bot` → `discordbot` and `publicpage` → `publicweb`.

### Fixes
- The `modules/playtime.yml` header said "every X minutes"; it is hours.
- The `/lpa title` usage in the help said `[id|clear]`; the right command is `/lpa title set <id>`.
- Language files from earlier versions: texts whose format changed (`help.line`, which showed "- {value}" in the
  help, and the `/lpa modules` header) are replaced with the new text on startup; the console says which ones.
- File names such as `events.yml` or `GUIDE.md` in clickable chat lines no longer open a fake web link.
- New tests: every catalog module appears in both guides with its file, commands and permissions; every
  `modules/<id>.yml` has a complete header; documentation links point to files that exist.

## 1.3-V2 — phase 4

### New
- **Seasons** (`seasons` module): monthly, weekly, `<N>d` or manual. They count gains since the start, give rewards
  per rank when they end and save the top in the hall of fame. `/lpa season [top <type>|hall]`, web page and
  `/lpa menu` page, `season_*` placeholders.
- **Titles** (`titles` module, `titles.yml` with 13 titles): by statistic, by finishing in a season top or by
  permission. Players choose the active one (`/lpa title`, menu or web) and `%title%` is shown in chat, TAB and scoreboard.
- **Events** (`events` module): short competitions on one statistic (`/lpa event start mineria`), with a live
  leaderboard on the web, the scoreboard (`scoreboard.event-lines`) and a bossbar; rewards and announcement at the end, history.
- **Leaderboard holograms** (`holograms` module): `/lpa holo create` with DecentHolograms or FancyHolograms (optional);
  all-time, weekly, monthly, season or event leaderboards.
- **Two-way Discord bot** (`discordbot` module): `/stats`, `/top`, `/online`, `/link`, `/unlink` and `/staff`, chat
  bridge with anti-mention protection, code-based linking, linked/milestone/top roles. Uses DiscordSRV when installed;
  otherwise a library-free embedded bot (~60 KB). Guide in `docs/en/DISCORD_SETUP.md`.
- `/api/seasons`, `/api/events`, `/api/titles`, `/api/discord` endpoints and admin endpoints to end the season and
  start or stop events; public sections `seasons`, `events` and `titles`.

### Changes
- `config.yml` moves to `config-version: 5` (new keys only; automatic migration with backup). `seasons` and
  `discordbot` ship off because they hand out rewards or need a token.
- The bot token can come from the `LPA_DISCORD_TOKEN` environment variable and is never written to the log or the API.
- The profile (`/api/player`) includes the active title.

### Fixes
- Unused imports removed in `gui`, `periods`, `quests` and `titles`.

## 1.3-V2 — phase 3

### New
- **Web console** (`console` module): live console over SSE in Admin and commands filtered by
  `console.allow`/`console.deny`, with IPs, api-key and sensitive patterns hidden and a lines-per-second cap.
- **Moderation** (`moderation` module): `warn`, `kick`, `mute`, `unmute`, `ban` and `unban` with duration (`10m`, `1h30m`,
  `perm`) and history, from `/lpa mod` or the dashboard profile; optional staff and Discord notice.
- **Alerts** (`alerts` module): low TPS, high memory and database down, with sustain time and cooldown; notices on the
  web, in-game and on Discord (also when they are resolved).
- **Activity heatmap** per weekday and hour (`heatmap` module) on the overview.
- **Daily zip backups** (`backups` module) at `backups.time`; the last 7 are kept, catch-up if the server was off and
  `/lpa backup now|list`.
- **Update notices** in the background (`updates` module: GitHub, Modrinth or Spigot) for admins and on the dashboard.
- **CSV export** of every player from Admin (`/api/admin/export.csv`).
- **Daily and weekly quests** (`quests` module, `quests.yml`) with rewards, a page in `/lpa menu`, `/lpa quests`,
  placeholders and a profile card.
- **Login streaks** (`streaks` module) with per-day rewards and a configurable cycle.
- **Active-playtime rewards** (`playtime` module) that do not count AFK time.
- **Pop-up toasts** on the dashboard (joins, alerts, milestones, quests) that can be turned on or off per type.

### Changed
- `config.yml` moves to `config-version: 4` (new keys only; automatic migration with backup). `quests`, `streaks` and
  `playtime` arrive off because they hand out rewards.
- Shared rewards in `core/reward` (milestones, periods, quests, streaks and active playtime): `command:`, `item:` and
  `money:` with the same format everywhere.
- Admin audit log in `server_data/audit.log` (`web.audit-log`).
- Discord notices without a player (alerts, moderation) no longer show an empty head.
- Toasts have no coloured side stripe, like the cards and the sidebar.

### Fixed
- Version comparison ordered `2.0` and `2.0-beta` the wrong way round; the final release now beats the beta.
- The rewards `item:minecraft:diamond` and `command:give ...` were misread (the literal `command:give` was run).
- The default `console.deny` patterns (`op .*`) blocked nothing; they are now `op *` and both forms are accepted.
- Locale-independent case handling (`Locale.ROOT`) in sanctions and the console.

## 1.3-V2 — phase 2

### New
- **Daily history** per player (`history` module) and a 30-day chart on the dashboard profile.
- **Weekly and monthly leaderboards** (`periods` module): automatic reset, winners announced in-game (in each
  player's language) and on Discord, optional rewards per position and an archive of closes. Manual close with
  `/lpa period end <weekly|monthly>` or `POST /api/admin/period/end`.
- **New statistics:** blocks placed, ores per type, fish, villager trades, damage dealt and taken, and deaths by
  cause.
- **Read-only public page and API** (`public` module, off by default), with configurable sections, per-IP limit
  and CORS. Admin stays protected.
- **Compare two players** on the dashboard (`#/compare/A/B`).
- **Achievements gallery** with rarity (% of players) and common → legendary tiers (`achievements` module).
- **Revamped `/lpa menu`:** ores, deaths, achievements and period leaderboard pages.
- **Bilingual documentation:** API reference, variables, architecture and this changelog in Spanish and English.

### Changed
- `config.yml` moves to `config-version: 3` (new keys only; automatic migration with backup).
- Dashboard leaderboard with an all-time / weekly / monthly selector.
- New placeholders: `%losperrisapi_weekly_*%`, `%losperrisapi_monthly_*%`, `%losperrisapi_achievements_*%` and the
  new statistics.
- The `/` and `/dashboard` redirects keep the query string (`?public`).

### Fixed
- "Log out" no longer wraps onto two lines in the sidebar.
- Paper 1.21.1 compatibility (enchantments looked up by key instead of renamed constants).
- The profile no longer shows "#1 of N" or a 100% percentile for stats whose value is 0 (everyone used to tie for first).
- `/lpa timings reset` left the list empty forever (already scheduled tasks kept writing to measurements that had been removed); it now zeroes the counters instead.

## 1.3-V2 — phase 1

### New
- Modular architecture: `core/` plus modules toggled with `modules.<id>.enabled`.
- Spanish and English in-game (per player) and on the dashboard.
- Central variable registry with console warnings and generated documentation.
- `/lpa timings` and `/lpa modules`.

### Changed
- Data indexed by UUID, with migration and backup.
- Config migration v1 → v2.

### Fixed
- General bug and performance review (I/O off the main thread, precomputed rankings).
