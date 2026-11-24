# Bot de Discord — guía de configuración

Módulo `discord-bot`: panel de estado (`/panel`), `/stats`, `/top`, `/online`, `/staff`, comandos custom, puente de chat y roles vía DiscordSRV.

> [!NOTE]
> Distinto de `discord-webhook` (solo avisos). Ambos pueden convivir. Docs: https://github.com/LosPerris/LosPerrisStatsAPI

---

## 1. Crear la aplicación y el bot
1. Entre en <https://discord.com/developers/applications> → **New Application**.
2. En **Bot**, **Reset Token** y copie el token (no lo comparta).
3. Active **MESSAGE CONTENT INTENT** solo si usa el puente de chat.

## 2. Invitar el bot
OAuth2 → URL Generator: scopes `bot` + `applications.commands`. Permisos: View Channels, Send Messages, Embed Links, Read Message History; Manage Roles solo si usa roles con DiscordSRV.

## 3. Configurar el plugin
```yaml
modules:
  discord-bot: true
```
En `settings/discord.yml` → `discord-bot:`:
```yaml
token: ""                      # o LPA_DISCORD_TOKEN
guild-id: "123..."             # comandos de servidor (aparición inmediata)
commands: true
panel:
  refresh-interval: "5m"
  default-channel-id: ""
  server-ip: "play.ejemplo.net"
  server-version: "1.21.4"
  embed:
    title: "Servidor online"
    description: "Panel de información del servidor."
    color: "#57F287"
    author-name: "LosPerris"
    footer-text: "LosPerrisStats • próxima actualización en {next_update}"
    timestamp: true
```

Variables del embed/footer: `{online}`, `{max}`, `{players}`, `{tps}`, `{uptime}`, `{ip}`, `{version}`,
`{refresh}`, `{next_update}`, `{bar}`, `{pct}`, `{status}`. Si `server-ip` está vacío, el field IP no se muestra.

## 4. `/panel`
`/panel channel:#estado` publica (o actualiza) el embed en ese canal. Se refresca solo cada `refresh-interval`.

Botones:
| Botón | Efecto |
| --- | --- |
| Actualizar | Edita el panel público |
| Online / Tops / Info | Embed **ephemeral** (solo quien pulsa) |

## 5. Otros slash
| Comando | Uso |
| --- | --- |
| `/stats <jugador>` | Stats guardadas (jugador obligatorio) |
| `/top <stat> [amount]` | Ranking (3–15) |
| `/online` | Lista + barra de ocupación |
| `/staff <comando>` | Consola filtrada (roles en `staff.role-ids`) |

### custom-commands
Hasta 15 slash en YAML (`reply`, `embed`, `options`, `action: stats|online|reply`). Nombres reservados: panel, stats, top, online, staff.

## 6. Modos
`auto` (DiscordSRV si hay), `embedded`, `discordsrv`. El puente de chat propio solo en embedded. Roles automáticos solo con DiscordSRV.

## 7. Actualizar el JAR
La config antigua carga: claves `link:` se ignoran; `data/discord_links.json` no se borra. Tras actualizar, reinicie o `/lpa reload` y vuelva a registrar slash (automático al conectar).

## 8. Addon LosPerrisExtras
Avisos de temporadas/eventos/alertas usan el **webhook** (`DiscordApi`), no este bot. El título activo puede verse en `/stats` vía `playerTag`.
https://github.com/LosPerris/LosPerrisExtras
