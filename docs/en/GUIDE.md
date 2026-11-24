# LosPerrisStatsAPI quick guide

Español: [GUIA.md](../es/GUIA.md)

This guide explains how to get the plugin running and what each module does. For technical details see the [API reference](API_REFERENCE.md) and the [variables](variables/README.md).

> **Quests, seasons, titles, events, streaks, active playtime, holograms, moderation and alerts** are no longer in this plugin: they live in the **LosPerrisExtras** addon. With the addon installed, its commands, modules and switches work like the core ones (`/lpa`, `/lpa modules`, `config.yml`). See its [guide](https://github.com/LosPerris/LosPerrisExtras/blob/main/docs/en/GUIDE.md).

## 1. First 5 minutes

1. Copy the JAR into `plugins/` and start the server. `config.yml`, the `settings/` folder (`web.yml`, `discord.yml`, `database.yml` and one file per enabled module that needs one), `lang/` and `data/` are created.
2. Open the dashboard at `http://<ip>:25908/dashboard` (port in `settings/web.yml`, `port`). The api-key is generated the first time: it is printed to the console and saved in `settings/web.yml` (`security.api-key`).
3. Type `/lpa modules` to see what is on, grouped by category. Click a module to see its detail.
4. Turn modules on or off with `/lpa modules enable|disable <module>` (or in `config.yml` followed by `/lpa reload`).
5. Type `/lpa help` to see the commands available to you.

## 2. Where everything is

The server's `plugins/LosPerrisStatsAPI/` folder:

| Folder or file | What it is for |
|---|---|
| `config.yml` | The only file at the root: language, time zone, which modules are on (`modules:`, in three groups: core, web, and server and data) and the core sections (`stats`, `periods`, `history`, `backups`, `updates`, `advanced`). It starts with the LosPerrisStats banner and a "START HERE" box |
| `settings/web.yml` | `web` module: port, security, api-key, CORS |
| `settings/discord.yml` | `discord-webhook` and `discord-bot` modules, one section each (token, webhook, roles, chat bridge) |
| `settings/database.yml` | MySQL connection (optional, see [Database](#database)) |
| `settings/<module>.yml` | One file per module with its own settings (`afk.yml`, `scoreboard.yml`, `milestones.yml`…). Created when the module is enabled; a disabled module's file is never opened |
| `lang/` | In-game texts (`es.yml`, `en.yml`). You can add another language by copying one |
| `data/` | Player data (formerly `server_data/`). Do not edit it while the server is running |
| `backups/` | Automatic backups and the migration backups (`migration-v10-<date>.zip`) |

This guide is no longer copied to the server: it is online, linked from `config.yml`, from every file's header and from `/lpa help`.

**Durations:** time settings are written with a unit: `"30s"`, `"5m"`, `"1h"`, `"1d"`. A plain number still works as before (in that key's old unit).

**Statistics:** lists (`periods.stats`, milestones) use the same names as `stats.tracked` (`player_kills`, `play_time`…). The old short names (`kills`, `time`) still work.

<a id="database"></a>

**Database:** by default everything is stored in files under `data/` and nothing needs configuring. To use MySQL, fill in `settings/database.yml` (host, port, database, user, password and the connection `pool`) and restart the server.

## 3. Turning modules on and off

- **From the game or console:** `/lpa modules enable <module>` or `/lpa modules disable <module>` (permission `losperris.modules.toggle`). The change is saved to `config.yml` without touching its comments and applied at once, without reloading the other modules.
- **Groups:** core (`stats`, `web`, Discord, `milestones`, `scoreboard`, `afk`, `gui`, `placeholders`), web (`public-page`, `web-console`, `heatmap`) and server and data (`periods`, `history`, `backups`, `updates`). Modules with their own settings have a `settings/<module>.yml`, created the first time you enable them. `milestones` ships off because it hands out rewards.
- **By hand:** change `modules.<module>: true|false` in `config.yml` and type `/lpa reload`.
- **Addons:** an addon's modules (LosPerrisExtras, for example) show in `/lpa modules` and on the dashboard, reload with `/lpa reload`, and their switches are added to `config.yml` in their own group, off, without touching the ones you already have:

  ```yaml
  modules:
    # ...core modules...

    # ===== LosPerrisExtras =====
    quests: false         # Quests → LosPerrisExtras/settings/quests.yml
    titles: false         # Titles → LosPerrisExtras/settings/titles.yml
  ```

  If you remove the addon those lines stay but do nothing. Each module's settings stay in the addon folder.
- Turning a module off also turns off the ones that depend on it (for example, turning `web` off turns `public-page` and `web-console` off). A module cannot be turned on while its dependency is off.
- A disabled module costs nothing: its settings are not read (its own file is not even opened) and it registers no tasks, events or commands.
- `/lpa modules` marks enabled modules with ●, disabled ones with ○ and enabled ones with a problem (for example, a missing plugin) with ⚠, with the reason.

## 4. Permissions

`losperris.admin` (OPs by default) includes every admin permission. You can give your staff just one of them:

| Permission | Allows |
|---|---|
| `losperris.stats.reset` | `/lpa reset <player>` |
| `losperris.inspect` | `/lpa view <player>` |
| `losperris.gui.admin` | `/lpa admin <player>` |
| `losperris.periods.admin` | `/lpa period end <weekly\|monthly>` |
| `losperris.backups.admin` | `/lpa backup [now\|list]` |
| `losperris.modules` | `/lpa modules [module]` |
| `losperris.modules.toggle` | `/lpa modules enable\|disable <module>` |
| `losperris.reload` | `/lpa reload` |
| `losperris.timings` | `/lpa timings [reset]` |
| `losperris.updates.notify` | Receive the new-version notice on join |
| `losperris.scoreboard` | See the scoreboard and use `/lpa sb` (every player by default) |

## 5. Base

<a id="stats"></a>

### Statistics (`stats`)

The base for almost everything: counts kills, deaths, hours, blocks, ores, jumps, distance, fish, trades and damage, and precomputes the rankings in the background.

- Enable: `modules.stats: true` in `config.yml` or `/lpa modules enable stats` (default: on)
- Settings: `config.yml → stats` — `tracked`: which statistics are tracked; `leaderboard-cache`: how often rankings are recomputed
- Commands: `/lpa top <type>` (player), `/lpa reset <player>` (admin)
- Permissions: `losperris.stats.reset`

<a id="afk"></a>

### AFK (`afk`)

Marks idle players. While AFK they earn no active hours, milestones or rewards.

- Enable: `modules.afk: true` in `config.yml` or `/lpa modules enable afk` (default: on)
- Settings: `settings/afk.yml` — `timeout`: time without moving before going AFK (for example "5m")

## 6. Milestones

<a id="milestones"></a>

### Milestones (`milestones`)

Messages, effects and rewards when a player reaches a value (for example 100 kills).

- Enable: `modules.milestones: true` in `config.yml` or `/lpa modules enable milestones` (default: off)
- Needs: the `stats` module
- Settings: `settings/milestones.yml` — `stats`: the milestones per statistic and their rewards; `sound / firework / title`: effects when reached; `gallery`: achievements gallery
- Achievements gallery (formerly the `achievements` module): every milestone is an achievement with its rarity (% of players who own it, thresholds in `gallery.tiers`); "Achievements" page in `/lpa menu` and on the web, and `%losperrisapi_achievements_obtained|total|percent%`
- List: at the end of `settings/milestones.yml`, below the settings
- Variables: [milestones.md](variables/milestones.md), [rewards.md](variables/rewards.md)


## 7. Period leaderboards

<a id="periods"></a>

### Weekly and monthly tops (`periods`)

Weekly and monthly rankings. When each period closes it announces the winners and hands out rewards.

- Enable: `modules.periods: true` in `config.yml` or `/lpa modules enable periods` (default: on)
- Needs: the `stats` module
- Settings: `config.yml → periods` — `weekly / monthly`: which periods are active; `rewards`: rewards per rank
- Commands: `/lpa period [weekly|monthly]` (player), `/lpa period end <weekly|monthly>` (admin)
- Permissions: `losperris.periods.admin`
- Variables: [rewards.md](variables/rewards.md)


## 8. In game

<a id="scoreboard"></a>

### Scoreboard (`scoreboard`)

Sidebar scoreboard with statistics and tops. Each player can hide it with `/lpa sb`.

- Enable: `modules.scoreboard: true` in `config.yml` or `/lpa modules enable scoreboard` (default: on)
- Needs: the `stats` module
- Settings: `settings/scoreboard.yml` — `lines`: the lines and their variables; `event-lines`: lines during a LosPerrisExtras event
- Commands: `/lpa sb` (player)
- Permissions: `losperris.scoreboard`
- Variables: [scoreboard.md](variables/scoreboard.md)

<a id="gui"></a>

### Menus (`gui`)

Inventory menus: own statistics (`/lpa menu`), another player's (`/lpa view`) and admin (`/lpa admin`). It has no file of its own.

- Enable: `modules.gui: true` in `config.yml` or `/lpa modules enable gui` (default: on)
- Needs: the `stats` module
- Settings: no settings of its own
- Commands: `/lpa menu` (player), `/lpa view <player>` (admin), `/lpa admin <player>` (admin)
- Permissions: `losperris.inspect`, `losperris.gui.admin`

<a id="placeholders"></a>

### PlaceholderAPI (`placeholders`)

`%losperrisapi_...%` expansion for PlaceholderAPI. Needs PlaceholderAPI; without it it shows as ⚠. It has no file of its own.

- Enable: `modules.placeholders: true` in `config.yml` or `/lpa modules enable placeholders` (default: on)
- Needs: the `stats` module
- Requires: PlaceholderAPI
- Settings: no settings of its own
- Variables: [placeholderapi.md](variables/placeholderapi.md)

## 9. Web

<a id="web"></a>

### Web (`web`)

HTTP API and web dashboard at `http://<ip>:<port>/dashboard`. The admin api-key is generated automatically the first time.

- Enable: `modules.web: true` in `config.yml` or `/lpa modules enable web` (default: on)
- Needs: the `stats` module
- Settings: `settings/web.yml` — `port / bind-address`: where it listens; `security`: api-key, failed attempts and CORS

<a id="public-page"></a>

### Public page (`public-page`)

Read-only public page and API, without an api-key (`/?public`, `/api/public/*`). Ships off.

- Enable: `modules.public-page: true` in `config.yml` or `/lpa modules enable public-page` (default: off)
- Needs: the `web` module
- Settings: `settings/public-page.yml` — `sections`: which sections are published; `rate-limit`: requests per IP

<a id="web-console"></a>

### Web console (`web-console`)

Live server console inside the dashboard, admins only, with filtered commands.

- Enable: `modules.web-console: true` in `config.yml` or `/lpa modules enable web-console` (default: on)
- Needs: the `web` module
- Settings: `settings/web-console.yml` — `allow / deny`: which commands are allowed; `hide-ips`: hide IPs in the log

<a id="heatmap"></a>

### Activity heatmap (`heatmap`)

Dashboard heat map: average players online per weekday and hour.

- Enable: `modules.heatmap: true` in `config.yml` or `/lpa modules enable heatmap` (default: on)
- Settings: `settings/heatmap.yml` — `weeks`: weeks kept

<a id="history"></a>

### History (`history`)

Saves a daily snapshot of each player's statistics for the progress charts.

- Enable: `modules.history: true` in `config.yml` or `/lpa modules enable history` (default: on)
- Needs: the `stats` module
- Settings: `config.yml → history` — `retention-days`: days kept

## 10. Server and data

<a id="backups"></a>

### Backups (`backups`)

Daily zip backup of the plugin data; keeps the last `keep`.

- Enable: `modules.backups: true` in `config.yml` or `/lpa modules enable backups` (default: on)
- Settings: `config.yml → backups` — `time`: backup time; `keep`: how many backups to keep
- Commands: `/lpa backup [now|list]` (admin)
- Permissions: `losperris.backups.admin`

<a id="updates"></a>

### Updates (`updates`)

Checks for a new version in the background and tells the admins.

- Enable: `modules.updates: true` in `config.yml` or `/lpa modules enable updates` (default: on)
- Settings: `config.yml → updates` — `source / id`: where to look (GitHub, Modrinth or Spigot)
- Permissions: `losperris.updates.notify`

## 11. Discord

<a id="discord-webhook"></a>

### Discord webhook (`discord-webhook`)

Sends milestones and winners to a Discord channel through a webhook. Ships off; without a valid `webhook-url` it shows as ⚠.

- Enable: `modules.discord-webhook: true` in `config.yml` or `/lpa modules enable discord-webhook` (default: off)
- Needs: the `stats` module
- Settings: `settings/discord.yml → discord-webhook` — `webhook-url`: the webhook URL
- Variables: [discord-webhook.md](variables/discord-webhook.md)

<a id="discord-bot"></a>

### Discord bot (`discord-bot`)

Two-way Discord bot: `/stats`, `/top`, `/online`, chat bridge, account linking and roles. Uses DiscordSRV when installed; otherwise it needs a token. Ships off.

- Enable: `modules.discord-bot: true` in `config.yml` or `/lpa modules enable discord-bot` (default: off)
- Needs: the `stats` module
- Requires: DiscordSRV (optional)
- Settings: `settings/discord.yml → discord-bot` — `mode`: DiscordSRV or embedded; `token`: bot token (or the LPA_DISCORD_TOKEN variable)
- Commands: `/lpa discord [link|unlink|status]` (player)
- Variables: [discord-bot.md](variables/discord-bot.md)

## 12. Updating from an older version

### From 1.4 to 1.5

Swap the JAR (and LosPerrisExtras for 1.2, which needs this version). There is no core migration: on start the addon
moves its switches into the core `config.yml` with their current values. Its commands move from `/lpx` to `/lpa`
(`/lpx` still works for now).

### From 1.3 (config-version 9) to 1.4 (config-version 10)

1. Stop the server, replace the JAR and, if you used quests, seasons, titles, events, streaks, active playtime, holograms, moderation or alerts, also put `LosPerrisExtras` in `plugins/`.
2. The first time, the plugin saves everything to `backups/migration-v10-<date>.zip` and then moves those modules' settings (`settings/<module>.yml`) and data (`data/seasons/`, `data/events/`, `data/holograms.json`, `data/sanctions/`) to `plugins/LosPerrisExtras/`. Their `modules.<id>` go to the addon's `config.yml` with the same value.
3. The `achievements` module is gone: its settings move to `gallery:` inside `settings/milestones.yml` (on if it was on).
4. Nothing is lost: without the addon installed, the files wait in `plugins/LosPerrisExtras/` until you install it.

### From older versions

1. Stop the server, replace the JAR and start it.
2. If you come from an older version (config-version 8 with `web.yml`, `discord.yml`… at the root, 7 with everything in `config.yml`, 6 with `modules/<id>.yml`, or 5 and older with a long `config.yml`), the first time the plugin saves **every** file involved to `backups/migration-v9-<date>.zip`, with their paths, and then splits it into the new format (config-version 9, then 10 as above): `config.yml` for general settings and the core, and `settings/` for web, Discord, the database and each extra.
3. Your values are kept (api-key, tokens, webhooks, presets, rewards, lists, enabled modules…). Comments become the new template's; yours remain in the zip.
4. A disabled extra that matches the template gets no file; it is created when you enable it. If you had customized it, its file is created anyway.
5. Some keys are renamed and now use durations with a unit (`auto-save-interval: 300` → `auto-save: "5m"`, `interval-minutes` → `update-every`, `check-hours` → `check-every`, `sample-minutes` → `sample-every`; `stats-folder` and `debug` move to `advanced`). The old names are still accepted.
6. `server_data/` becomes `data/`, `database/` moves to `settings/` and the old `docs/` folder is deleted (the guide is now a link). The `public` and `console` ids become `public-page` and `web-console`; `/lpa modules` still accepts the old names.
7. `config.yml` is written last: if anything fails, the migration runs again on the next start. If a file cannot be read, the plugin changes nothing and says so in the console.
8. On start, the console warns in a single line about settings that have no effect because the module they need is off (for example `periods.announce.discord` with `discord-webhook` off).

**Going back:** the previous JAR does not understand the new format. Stop the server, delete `config.yml` and `settings/`, put the previous JAR back, unzip `backups/migration-v9-<date>.zip` into the plugin folder and rename `data/` back to `server_data/`.

## 13. Common problems

| `/lpa modules` says | What to do |
|---|---|
| needs module <module> | Enable the dependency first (`/lpa modules <module>` shows it). |
| PlaceholderAPI missing | Install PlaceholderAPI and restart. |
| no bot token (settings/discord.yml → discord-bot.token) | Put the token in `settings/discord.yml → discord-bot.token` (or install DiscordSRV) and enable it again. See [DISCORD_SETUP.md](DISCORD_SETUP.md). |
| no valid webhook-url (settings/discord.yml → discord-webhook.webhook-url) | Put a valid `webhook-url` in `settings/discord.yml → discord-webhook` and type `/lpa reload`. |
| failed to start: … | Check the error in the console (for example, the web port is taken), fix it and enable it again. |
