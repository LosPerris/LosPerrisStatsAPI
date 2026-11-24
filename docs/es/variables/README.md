# Variables por contexto

> Generado automáticamente desde `core/text/Variables.java`. [English version](../../en/variables/README.md)

| Contexto | Archivo | Sintaxis | Dónde |
|---|---|---|---|
| Mensajes de idioma | [lang.md](lang.md) | `{x}` | lang/es.yml, lang/en.yml (y cualquier lang/<código>.yml) |
| Mensajes de hitos | [milestones.md](milestones.md) | `%x%` | settings/milestones.yml: message, broadcast, title, actionbar |
| Recompensas | [rewards.md](rewards.md) | `%x%` | config.yml → periods y settings/<extra>.yml: rewards en milestones, quests, periods, streaks, playtime, seasons y events (y presets.<id>.rewards en events) |
| Discord | [discord-webhook.md](discord-webhook.md) | `{x}` | settings/discord.yml → discord-webhook.title |
| Scoreboard | [scoreboard.md](scoreboard.md) | `%x%` | settings/scoreboard.yml → title, lines y event-lines |
| PlaceholderAPI | [placeholderapi.md](placeholderapi.md) | `%x%` | Cualquier plugin compatible con PlaceholderAPI |
| Códigos de color | [colors.md](colors.md) | `&` | Mensajes, hitos, scoreboard, prefix y anuncios del dashboard web |
| Objetivos de misiones | [quests.md](quests.md) | `a:b` | settings/quests.yml: objective |
| Temporadas | [seasons.md](seasons.md) | `{x}` | settings/seasons.yml → name-format |
| Títulos | [titles.md](titles.md) | `{x}` | settings/titles.yml → format (los nombres de los títulos admiten colores &) |
| Bot de Discord (puente de chat) | [discord-bot.md](discord-bot.md) | `{x}` | settings/discord.yml → discord-bot.bridge.to-discord-format y bridge.to-minecraft-format |
