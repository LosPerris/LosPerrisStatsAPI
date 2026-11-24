# Variables by context

> Generated automatically from `core/text/Variables.java`. [Versión en español](../../es/variables/README.md)

| Context | File | Syntax | Where |
|---|---|---|---|
| Language messages | [lang.md](lang.md) | `{x}` | lang/es.yml, lang/en.yml (and any lang/<code>.yml) |
| Milestone messages | [milestones.md](milestones.md) | `%x%` | settings/milestones.yml: message, broadcast, title, actionbar |
| Rewards | [rewards.md](rewards.md) | `%x%` | config.yml → periods and settings/<extra>.yml: rewards in milestones, quests, periods, streaks, playtime, seasons and events (and presets.<id>.rewards in events) |
| Discord | [discord-webhook.md](discord-webhook.md) | `{x}` | settings/discord.yml → discord-webhook.title |
| Scoreboard | [scoreboard.md](scoreboard.md) | `%x%` | settings/scoreboard.yml → title, lines and event-lines |
| PlaceholderAPI | [placeholderapi.md](placeholderapi.md) | `%x%` | Any PlaceholderAPI-compatible plugin |
| Color codes | [colors.md](colors.md) | `&` | Messages, milestones, scoreboard, prefix and web dashboard announcements |
| Quest objectives | [quests.md](quests.md) | `a:b` | settings/quests.yml: objective |
| Seasons | [seasons.md](seasons.md) | `{x}` | settings/seasons.yml → name-format |
| Titles | [titles.md](titles.md) | `{x}` | settings/titles.yml → format (title names accept & colors) |
| Discord bot (chat bridge) | [discord-bot.md](discord-bot.md) | `{x}` | settings/discord.yml → discord-bot.bridge.to-discord-format and bridge.to-minecraft-format |
