# LosPerrisStatsAPI documentation

Español: [../es/README.md](../es/README.md)

## Contents
- [Admin guide](GUIDE.md): first steps, where every file is, turning modules on and off, permissions, every module with its settings and commands, updating from v5 and common problems.
- [API reference](API_REFERENCE.md): commands, permissions, placeholders, HTTP API, public page, dashboard and Java API for addons.
- The extras (quests, seasons, titles, events…) are documented in the [LosPerrisExtras](https://github.com/LosPerris/LosPerrisExtras) addon.
- [Discord bot](DISCORD_SETUP.md): create the bot, invite it, token, modes (DiscordSRV or embedded), chat bridge, linking, roles, staff and troubleshooting.
- [Variables](variables/README.md): every variable each config text accepts, generated from the central registry (`Variables.java`).
- [Architecture](ARCHITECTURE.md): code layout, modules, performance and how to add a module.
- [Changelog](../../CHANGELOG.md).

## On the server
Since config v9 the plugin no longer copies this documentation to the server (the migration deletes the old `docs/` folder).
The config files link to the published version, and in game `/lpa help` and `/lpa modules <id>` give help and the guide link.

## Maintenance
The `variables/` pages are not edited by hand: if the registry changes, run `mvn test -Ddocs.update=true`.
Every document exists in Spanish and English with the same heading structure; `BilingualDocsTest` checks it.
