# 📚 API Reference - LosPerrisStatsAPI 1.5

Commands, placeholders, HTTP API, public page, dashboard and Java API for addons. Español: `docs/es/API_REFERENCE.md`.
Quests, seasons, titles, events, streaks, active playtime, holograms, moderation and alerts live in the
**LosPerrisExtras** addon (its commands also live in `/lpa`): see its [reference](https://github.com/LosPerris/LosPerrisExtras/blob/main/docs/en/API_REFERENCE.md).
The full variable list (with examples and where each one is used) is in [variables/](variables/README.md).

---

## 🎮 1. In-game commands
**Main command:** `/losperrisapi` or `/lpa`. Subcommands of a disabled module do not exist.

### 👤 A) Players
| Command | Description |
| :--- | :--- |
| `/lpa menu` | Stats panel (ranking position, weekly gain, ores, deaths, achievements and leaderboards). |
| `/lpa top <type>` | Top 10 of a statistic (`kills`, `deaths`, `mob_kills`, `time`, `blocks_broken`, `blocks_placed`, `ores_mined`, `jumps`, `distance_walked`, `fish_caught`, `trades`, `damage_dealt`, `damage_taken`). |
| `/lpa period [weekly\|monthly]` | Current weekly or monthly leaderboard, your own amount and time left until it closes. |
| `/lpa sb` | Toggle the sidebar scoreboard (`losperris.scoreboard`). |
| `/lpa lang [es\|en\|auto]` | Show or change your language. |
| `/lpa discord link\|unlink\|status` | Code to link your Discord account (`/link <code>` in Discord), unlink or see the status (`discord-bot` module). |
| `/lpa help [module\|admin]` · `/lpa version` | Grouped, clickable help (only what you can use), the help of one module or the admin help; plugin and installed addon information. |

### 🛠️ B) Administrators
| Command | Permission | Description |
| :--- | :--- | :--- |
| `/lpa view <player>` | `losperris.inspect` | Open another player's panel. |
| `/lpa admin <player>` | `losperris.gui.admin` | GUI statistics editor. |
| `/lpa reset <player>` | `losperris.stats.reset` | Reset all of their statistics. |
| `/lpa period end <weekly\|monthly>` | `losperris.periods.admin` | Close the period now: announces winners, gives rewards and starts a new one. |
| `/lpa reload` | `losperris.reload` | Reload config, languages and modules. |
| `/lpa modules [module]` | `losperris.modules` | Status of every module, addon modules included (on, off, missing something, error) or the detail of one: what it does, dependencies, file, commands and permissions. |
| `/lpa modules enable\|disable <module>` | `losperris.modules.toggle` | Turn a module on or off live. Only that line of `config.yml` changes (comments are kept); turning one off also turns off the ones that depend on it. |
| `/lpa timings [reset]` | `losperris.timings` | Average/max time of every plugin task. |
| `/lpa backup <now\|list>` | `losperris.backups.admin` | Back up now or list backups (`backups` module). |

---

## 🧩 2. Placeholders (PlaceholderAPI)
Prefix `%losperrisapi_...%`. Inside the scoreboard they are used without the prefix (`%kills%`).

### 📊 A) Player statistics
`kills`, `deaths`, `mob_kills`, `time`, `blocks_broken`, `blocks_placed`, `ores_mined`, `jumps`, `distance_walked`,
`fish_caught`, `trades`, `damage_dealt`, `damage_taken`, `language`. Example: `%losperrisapi_ores_mined%`.

### 🏆 B) Ranking / TOP
- All-time: `%losperrisapi_top_<type>_<pos>_<name|value>%` → `%losperrisapi_top_kills_1_name%`
- Weekly/monthly: `%losperrisapi_weekly_<type>_<pos>_<name|value>%`, `%losperrisapi_monthly_time_1_value%`
- The player's amount in the period: `%losperrisapi_weekly_kills%`
- Achievements: `%losperrisapi_achievements_obtained%`, `_total`, `_percent`

### 💬 C) Discord
- Discord: `%losperrisapi_discord_linked%` (embedded bot)

> [!NOTE]
> Positions 1–10. Weekly/monthly leaderboards only exist for the statistics in `periods.stats`.
> Placeholders of a disabled module return an empty string.

---

## 🌐 3. HTTP API (Web)
**BASE URL:** `http://YOUR_IP:25908`

### 🔒 Connection and security
Security is **on by default** (`security.enabled: true`). If `security.api-key` is empty, the plugin generates one on
startup, saves it to `settings/web.yml` (`security.api-key`) and prints it to the console.

1. **Header (recommended):** `Authorization: Bearer YOUR_API_KEY`
2. **Query:** `?key=YOUR_API_KEY` (read-only; disable with `security.allow-query-key: false`).

### 📡 Read endpoints (GET)
| Endpoint | Description |
| :--- | :--- |
| `/api/players` | Online players (`name`, `uuid`, `world`, `ping`, `health`, `food`, `level`, `afk`, `session_seconds`…). |
| `/api/stats?name=X` | A player's data (includes `ores` per type and `death_causes`). 400 invalid name, 404 if unknown. |
| `/api/top?stat=X&limit=N` | All-time ranking (`limit` 1–100). |
| `/api/summary` | Global summary: totals, averages, maximums, activity, server, top 5 and enabled `modules`. 3 s cache. |
| `/api/profile?name=X` | Profile: `stats`, `ranks` (position per stat; 0 = unranked because the value is 0), `milestones`, `online`, `session`. |
| `/api/players/list?q=&sort=&order=&page=&size=` | Paged directory (`sort`: `name`, `last_updated` or any statistic). |
| `/api/history?since=EPOCH_MS` | Server series (online, TPS, totals). |
| `/api/player/history?name=X&days=30` | The player's daily history `{days:[{date, values:{stat: value}}]}` (`history` module). |
| `/api/periods?limit=10&name=X` | `weekly`/`monthly` leaderboards: `enabled`, `start`, `end`, `stats`, `boards`, `last` (previous close) and, with `name`, `player` (gain and rank). |
| `/api/achievements?name=X` | Achievements with `owners`, `percent` and `tier` (`common`…`legendary`); with `name`, the ones the player owns. |
| `/api/heatmap` | Activity map `{days[7][24], max, weeks, peak{day,hour}, timezone}`: average players online per weekday (Monday = 0) and hour; `-1` = no data (`heatmap` module). |
| `/api/discord` | Bot status `{mode, state, connected, ping, bot, bridge, links, roles, last_role_sync, failure}`. Never includes the token and is never public. |
| `/api/all` | Global dump (no IPs; 5 s cache). |
| `/api/chat/stream` · `/api/chat/history?limit=N` | Live chat (SSE) and history. |

A disabled module answers `404 {"error": "Module '<id>' disabled"}`. The LosPerrisExtras endpoints
(`/api/quests`, `/api/seasons`, `/api/events`, `/api/titles` and the season, event and moderation admin ones)
only exist with the addon and its module enabled; otherwise they answer `404`. With the addon, the profile adds
`quests` and `title`.

### 🛡️ Admin API (`/api/admin/...`)
Requirements: `web.admin-enabled: true`, security on and an api-key. Bearer header only; actions use POST with JSON (≤ 8 KB),
10 failures per IP in 5 min → 429. Actions are logged as `[WebAdmin]`.

| Method | Endpoint | Body | Effect |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/admin/check` | — | `{"ok": true}` if the key is valid. |
| `GET` | `/api/admin/log` | — | Latest actions. |
| `POST` | `/api/admin/stat` | `{"player","stat","value","mode":"set"\|"add"}` | Edits an editable statistic (computed ones such as `blocks_placed` or `ores_mined` return 400). |
| `POST` | `/api/admin/reset` | `{"player"}` | Same as `/lpa reset`. |
| `POST` | `/api/admin/period/end` | `{"type":"weekly"\|"monthly"}` | Closes the period (202). 400 if disabled, 409 if it is already closing. |
| `POST` | `/api/admin/leaderboard/refresh` · `/save` · `/reload` | `{}` | Recompute, save, reload. |
| `POST` | `/api/admin/broadcast` | `{"message"}` | Announcement (1–256 characters, `&` codes). |
| `GET` · `POST` | `/api/admin/backups` · `/api/admin/backups/run` | — · `{}` | Status `{backups[], keep, next_run, running, last}`; `run` answers 202, or 409 if one is already running. |
| `GET` · `POST` | `/api/admin/updates` · `/api/admin/updates/check` | — · `{}` | `{configured, source, current, latest?, url?, update_available, checked_at?}`; `check` checks now (404 without `updates.source`). |
| `GET` | `/api/admin/export.csv` | — | Every player as UTF-8 CSV (no IPs; cells dangerous for Excel are neutralised). |

```bash
curl -X POST http://YOUR_IP:25908/api/admin/period/end \
  -H "Authorization: Bearer YOUR_API_KEY" -H "Content-Type: application/json" -d '{"type":"weekly"}'
```

### 🖥️ Web console (`/api/admin/console`)
`web-console` module. Same requirements as the admin API (Bearer header).

| Method | Endpoint | Effect |
| :--- | :--- | :--- |
| `GET` | `/api/admin/console` | `{lines[{t, l, m}], max_clients}`: the last `console.buffer-lines` lines. |
| `GET` | `/api/admin/console/stream` | New lines over SSE (`data: {t, l, m}`). 503 when `console.max-clients` streams are open. |
| `POST` | `/api/admin/console/command` | `{"command"}` (≤ 256). Checked against `console.deny` (always wins) and `console.allow`; 403 if blocked. Everything is logged. |

Lines are cleaned (colours, IPs with `console.hide-ips`, the api-key and the `console.redact` patterns) and capped at
`console.max-lines-per-second`. `allow`/`deny` patterns: `list` (exact), `say *` (with or without arguments), `eco*`.

### 🌍 Public API and page (`/api/public/...`)
Off by default (`modules.public-page: false`). When enabled it opens **read-only, key-less** copies of the endpoints
chosen in `public.sections`; admin, chat and `/api/all` are never public.

| Section | Endpoints |
| :--- | :--- |
| `summary` | `/api/public/summary`, `/api/public/players` |
| `leaderboards` | `/api/public/top` |
| `players` | `/api/public/players/list` |
| `profiles` | `/api/public/profile`, `/api/public/stats` |
| `periods` | `/api/public/periods` |
| `achievements` | `/api/public/achievements` |
| `history` | `/api/public/player/history` |

`/api/public/info` returns `{public, language, title, sections}`. Limit of `public.rate-limit` requests/minute per IP
(429 + `Retry-After`). CORS via `public.cors-origin`. The page: `http://YOUR_IP:25908/?public`.

### 📊 Web dashboard
`http://YOUR_IP:25908/` (private, with api-key) or `/?public` (public). In Spanish and English (ES/EN button).
- **Overview:** server status, totals of the 13 statistics, charts and top 5.
- **Leaderboard:** all-time, weekly or monthly (with the countdown to the close).
- **Players** and **Profile:** rank per statistic, percentile, milestones, 30-day history, ores per type,
  death causes, weekly/monthly leaderboards and achievements gallery with rarity.
- **Compare:** `#/compare/A/B`, statistic by statistic with who wins each one.
- **Chat** and **Admin** (private only): includes closing the weekly/monthly leaderboard.
- **Heatmap** on the overview; console, backups, updates, CSV export and Discord bot status in Admin;
  pop-up toasts (joins, milestones) that each browser turns on or off on the chat page.
- The quests, seasons, events, moderation and alerts tabs only appear with LosPerrisExtras (`summary.modules`).

---

## 📋 4. Scoreboard
Native and configurable in `scoreboard.yml`. Supports the statistics, `%top_…%`, `%weekly_…%`, `%monthly_…%` and achievement ones.
Players hide it with `/lpa sb`.

While an event is running (LosPerrisExtras addon), the `scoreboard.event-lines` lines are shown too.
---

## 🎯 5. Milestones and period leaderboards
- **Milestones** (`settings/milestones.yml`, off by default): rewards (commands and money via Vault), fireworks, sounds and Discord.
- **Achievements gallery** (`gallery:` in `milestones.yml`; formerly the `achievements` module): every milestone level is an
  achievement; its rarity is the % of players who own it (`gallery.tiers`). Needs milestones on.
- **Weekly/monthly leaderboards** (`periods` module): automatic reset (`periods.week-start` and `timezone`; months start on day 1), announcement of
  the top `periods.announce.top` in-game and on Discord, optional rewards (`periods.rewards`) and an archive of closes.
- **Discord bot** (`discord-bot` module): `/stats`, `/top`, `/online`, account linking, roles and `/staff`. Guide: `docs/en/DISCORD_SETUP.md`.

---

## 📂 6. Storage
`settings/database.yml`: JSON, MySQL or Supabase (PostgreSQL). Daily history in `data/player_history/`, periods and their archive in `data/periods/`.
The heatmap in `data/heatmap.json`, Discord links in `data/discord_links.json` and daily backups in `backups/data/` (`backups.keep` are kept).
LosPerrisExtras data (seasons, events, holograms, sanctions) lives in `plugins/LosPerrisExtras/data/`.

---

## 🧩 7. Java API for addons
For plugins that depend on the core (`depend: [LosPerrisStatsAPI]`). Package `me.gordi.mineapi.api`; the full example
is LosPerrisExtras. Everything an addon registers with an `owner` is removed with `unregisterAll(owner)` (modules that
extend `BaseModule` do it when they are disabled).

```java
LosPerrisApi api = LosPerrisApi.get();          // null if the core is not enabled
api.events().subscribe(this, StatChangedEvent.class, e -> { ... });
api.web().read(this, "quests", null, r -> WebApi.Reply.ok(json));   // GET /api/quests
api.discord().notice("Title", "Text", "Player");                    // does nothing without a webhook
```

| Method | Purpose |
| :--- | :--- |
| `get()` · `core()` · `data()` · `modules()` · `rewards()` · `stats()` | Instance, plugin, player data, core modules, rewards (commands and Vault) and the statistics module. |
| `events()` | Internal bus: `subscribe(owner, Type.class, handler)`. Events: `StatChangedEvent`, `MilestoneReachedEvent`, `PlayerDataReadyEvent`, `PlayerResetEvent`, `CoreReloadedEvent`. |
| `isAfk(player)` | Whether the player is AFK (false without the `afk` module). |
| `web()` (`WebApi`) | `read(owner, path, publicSection, handler)` → `GET /api/<path>`; `admin(owner, "GET"\|"POST", path, handler)` → `/api/admin/<path>`; `profile(owner, contributor)` adds fields to `/api/profile`; `flag(owner, id, BooleanSupplier)` in `summary.modules`; `hideDataKeys`, `notify`, `invalidate`. Replies via `WebApi.Reply.ok/of/error`. |
| `menus()` (`MenuApi`) | `page(owner, Page)` adds a page to `/lpa menu` (its own button; slots 9–44 are free, `paginate` adds the arrows); `open(viewer, target, id, page)`. Helpers in `MenuItems`. |
| `discord()` (`DiscordApi`) | `available()`, `notice`, `alert`, `send`: notices through the core webhook. |
| `playerTag(owner, PlayerTag)` | Text next to the name (active title) on the web profile and in the Discord bot. |
| `eventMode(owner, BooleanSupplier)` | While it returns true, the scoreboard uses `scoreboard.event-lines`. |
| `commands()` (`CommandApi`) | `register(owner, SubCommand, HelpCatalog.Entry...)` adds a `/lpa` subcommand (name, aliases, permission, handler and tab completion with `SubCommand.of(...)`) and its `/lpa help` lines; `help(owner, entry)` help only; `texts(owner, I18n)` its languages (keys the core does not have); `executor()` to wire an own alias (e.g. `/lpx`). |
| `addons()` (`AddonApi`) | `register(plugin, Addon)`: its modules join the core module system (`/lpa modules`, `/lpa reload`, dashboard) and its switches are written to the core `config.yml`. See below. |
| `unregisterAll(owner)` | Removes that owner's routes, pages, tags, event mode, commands, help, languages and modules. |

An addon with modules implements `Addon`: `modules()` (dependencies first; they may depend on core modules),
`catalog()` (group, name, file, commands and help of each module for `/lpa help` and `/lpa modules`), `texts()` (its
languages) and, optionally, `initialSwitches()`, `reload()` and `forget(module)`.

```java
api.addons().register(this, addon);   // core config.yml, when missing:
// modules:
//   ...
//   # ===== LosPerrisExtras =====
//   quests: false         # Quests → LosPerrisExtras/settings/quests.yml
```

Missing switches are added as `false` (or with `initialSwitches()`) without touching existing ones; if the addon is
removed its lines stay but are ignored. Each module's settings stay in the addon folder.

An addon's placeholders use the same `%losperrisapi_...%` prefix. `LosPerrisApi.VERSION` goes up if the API changes
in an incompatible way (it is 2 in 1.5); since the compiler copies it into the addon, compare
`api.core().getDescription().getVersion()` to require a minimum core version.

---

## 🚀 What's new in 1.5
- 🧩 Addons integrate into the core: their commands live in `/lpa` (with help and tab completion), their modules in
  `/lpa modules`, `/lpa reload` and the dashboard, and their switches in the core `config.yml` (section 7).
- 🎯 Quests with several objectives show a small bar per objective on the web profile.

## 🚀 What's new in 1.4
- 🧩 Extras added after the original plugin move to the **LosPerrisExtras** addon (`/lpx`). The core keeps the original
  features (statistics, leaderboards, HTTP API, dashboard, Discord, milestones, scoreboard, AFK, menu) and their improvements.
- 🏅 Achievements merge into milestones (`gallery:` in `milestones.yml`).
- 🔌 Public Java API for addons (section 7).

## 🚀 What's new in 1.3
- 🗓️ Daily player history and weekly/monthly leaderboards with reset and winner announcements.
- 📈 New statistics: blocks placed, ores per type, fish, villager trades, damage dealt/taken and deaths by cause.
- 🌍 Read-only public page and API; compare players; achievements gallery with rarity.
- 🖥️ New multi-page `/lpa menu` and dashboard in Spanish and English.
- 🛡️ **Phase 3:** web console, moderation, alerts (web, in-game and Discord), heatmap, daily backups, update
  notices, CSV export, daily/weekly quests, login streaks and active-playtime rewards.
- 🏅 **Phase 4:** seasons with hall of fame, titles by statistics, leaderboard holograms, two-way Discord bot
  (commands, chat, linking and roles) and events with live temporary leaderboards.
- 🧩 **Phase 5:** a short `config.yml` with the small modules and an own file for each big module (a disabled module's file is never opened), live `/lpa modules enable|disable`, grouped and
  clickable help, per-module permissions under `losperris.admin` and an admin guide ([GUIDE.md](GUIDE.md)).

---
© 2026 LosPerrisStatsAPI • [Website](https://losperris.site)
