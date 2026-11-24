package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.data.DataDir;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.storage.JsonFiles;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.module.console.CommandFilter;
import me.gordi.mineapi.module.stats.Leaderboards;
import me.gordi.mineapi.module.stats.StatSnapshot;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Bot Discord embebido (o DiscordSRV): {@code /panel}, stats/top/online/staff, custom-commands y puente.
 * Sin vinculación propia; roles solo si hay DiscordSRV.
 */
public final class DiscordBotModule extends AbstractModule implements Listener {

    public static final String ID = "discord-bot";
    static final String FILE = DataDir.NAME + "/discord_links.json";
    static final String PANEL_FILE = DataDir.NAME + "/discord_panel.json";
    static final String TOKEN_ENV = "LPA_DISCORD_TOKEN";
    private static final int EMBED_COLOR = 0x57F287;

    public enum Mode { EMBEDDED, DISCORDSRV }

    private Mode mode;
    private DiscordSrvHook hook;
    private DiscordRest rest;
    private GatewayClient gateway;
    private ThreadPoolExecutor out;
    private LinkStore store;
    private RoleRules rules;
    private CommandFilter staffFilter;
    private Set<String> staffRoles;
    private StatsModule stats;
    private ServerPanel panel;
    private CustomCommands customCommands;
    private File file;
    private String guild;
    private boolean registerCommands;
    private boolean bridge;
    private String bridgeChannel;
    private String toDiscord;
    private String toMinecraft;
    private int maxLength;
    private boolean joinQuit;
    private long startedAt;
    private volatile String appId;
    private volatile String botName;
    private volatile String lastError;
    private volatile long lastRoleSync;
    private final Set<Integer> warned = new HashSet<>();

    public DiscordBotModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    public String problem() {
        return isEnabled() && gateway == null && mode != Mode.DISCORDSRV ? "no-token" : null;
    }

    @Override
    protected void onEnable() {
        startedAt = System.currentTimeMillis();
        stats = plugin.modules().get(StatsModule.class);
        ConfigurationSection c = config();
        String token = token(c.getString("token", ""));
        guild = id(c.getString("guild-id", ""));
        registerCommands = c.getBoolean("commands", true);
        String wanted = c.getString("mode", "auto").toLowerCase(Locale.ROOT);
        hook = "embedded".equals(wanted) ? null : DiscordSrvHook.find(Bukkit.getPluginManager().getPlugin("DiscordSRV"), log()::warning);
        if ("discordsrv".equals(wanted) && hook == null) {
            log().warning("mode = discordsrv pero DiscordSRV no está activo: se usa el bot integrado.");
        }
        mode = hook != null ? Mode.DISCORDSRV : Mode.EMBEDDED;

        bridgeChannel = id(c.getString("bridge.channel-id", ""));
        bridge = mode == Mode.EMBEDDED && c.getBoolean("bridge.enabled", false) && bridgeChannel != null;
        if (c.getBoolean("bridge.enabled", false) && mode == Mode.DISCORDSRV) {
            log().info("Discord: el puente de chat lo hace DiscordSRV; el de este plugin queda desactivado.");
        }
        toDiscord = c.getString("bridge.to-discord-format", "**{player}**: {message}");
        toMinecraft = Text.color(c.getString("bridge.to-minecraft-format", "&9[Discord] &b{user}&7: &f{message}"));
        maxLength = Math.max(16, Math.min(2000, c.getInt("bridge.max-length", 256)));
        joinQuit = c.getBoolean("bridge.join-quit", true);

        rules = RoleRules.parse(id(c.getString("roles.linked", "")), values(c, "roles.milestones"), values(c, "roles.top"), log()::warning);
        staffRoles = new LinkedHashSet<>();
        for (String r : c.getStringList("staff.role-ids")) if (id(r) != null) staffRoles.add(id(r));
        staffFilter = new CommandFilter(c.getStringList("staff.commands-allow"), c.getStringList("staff.commands-deny"));

        file = new File(plugin.getDataFolder(), FILE);
        store = LinkStore.fromJson(JsonFiles.read(file));
        customCommands = new CustomCommands(c.getMapList("custom-commands"));
        panel = new ServerPanel(c.getConfigurationSection("panel"), new File(plugin.getDataFolder(), PANEL_FILE),
                this::countPlayers, () -> startedAt, this::onlineNames, Bukkit::getBukkitVersion);

        command(SubCommand.of("discord")
                .executes((s, a) -> sendStatus(s))
                .completes((s, a) -> List.of("status"))
                .build());

        if (token == null) {
            log().info("Discord: sin token (settings/discord.yml → discord-bot.token o " + TOKEN_ENV + "): el bot no se conecta."
                    + (mode == Mode.DISCORDSRV ? " Los vínculos de DiscordSRV siguen disponibles para roles." : ""));
            return;
        }
        out = new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(256), r -> {
            Thread t = new Thread(r, "LosPerris-DiscordOut");
            t.setDaemon(true);
            return t;
        }, new ThreadPoolExecutor.DiscardOldestPolicy());
        rest = new DiscordRest(c.getString("api-url", "https://discord.com/api/v10"), token, plugin.getDescription().getVersion());
        gateway = new GatewayClient(token, GatewayProtocol.intents(bridge), c.getString("gateway-url", "wss://gateway.discord.gg"),
                new Handler(), msg -> log().warning(msg));
        gateway.start();
        if (bridge) listen(this);

        if (!rules.isEmpty() && mode == Mode.DISCORDSRV) {
            if (guild == null) {
                log().warning("Discord: hay roles configurados pero falta guild-id; no se asignarán.");
            } else {
                long every;
                try {
                    every = Math.max(60_000L, me.gordi.mineapi.core.time.Durations.parse(c.getString("roles.sync-interval", "10m")));
                } catch (IllegalArgumentException e) {
                    every = 600_000L;
                }
                asyncTimer("discord-roles", 30_000L, every, () -> out.execute(this::syncRoles));
            }
        }

        asyncTimer("discord-panel", panel.refreshMs(), panel.refreshMs(), () -> out.execute(this::refreshPanelQuiet));
        log().info("Discord: modo " + mode.name().toLowerCase(Locale.ROOT) + (bridge ? ", puente activo" : "")
                + (hook != null ? " (DiscordSRV " + hook.version() + ")" : "") + ".");
    }

    @Override
    protected void onDisable() {
        if (bridge && joinQuit && gateway != null && gateway.status() == GatewayClient.Status.READY) {
            post(Text.strip(i18n().trDefault("discordbot.bridge.stopped")));
        }
        if (gateway != null) gateway.stop();
        if (out != null) {
            out.shutdown();
            try {
                if (!out.awaitTermination(3, TimeUnit.SECONDS)) out.shutdownNow();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        save();
        gateway = null;
        rest = null;
        out = null;
    }

    private static String token(String configured) {
        String t = configured == null ? "" : configured.trim();
        if (t.isEmpty()) {
            String env = System.getenv(TOKEN_ENV);
            t = env == null ? "" : env.trim();
        }
        return t.isEmpty() || t.startsWith("PON_") || t.startsWith("YOUR_") ? null : t;
    }

    static String id(String raw) {
        String s = raw == null ? "" : raw.trim();
        return s.matches("\\d{5,25}") ? s : null;
    }

    private static Map<String, Object> values(ConfigurationSection c, String path) {
        ConfigurationSection s = c.getConfigurationSection(path);
        return s == null ? Map.of() : s.getValues(false);
    }

    private void save() {
        if (store == null || !store.isDirty()) return;
        try {
            JsonFiles.write(file, store.toJson(), true);
        } catch (IOException e) {
            log().warning("No se pudo guardar " + FILE + ": " + e.getMessage());
        }
    }

    private <T> T onMain(Callable<T> task) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get(2, TimeUnit.SECONDS);
    }

    private String plain(String lang, String key, Object... vars) {
        return Text.strip(i18n().tr(lang, key, vars));
    }

    private static String escape(String name) {
        return name.replaceAll("([_*~`|\\\\>])", "\\\\$1");
    }

    private int countPlayers() {
        AtomicInteger n = new AtomicInteger();
        plugin.data().forEach(o -> n.incrementAndGet());
        return n.get();
    }

    private List<String> onlineNames() {
        try {
            return onMain(() -> {
                List<String> l = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) l.add(p.getName());
                return l;
            });
        } catch (Exception e) {
            return List.of();
        }
    }

    private void sendStatus(CommandSender s) {
        GatewayClient g = gateway;
        String state = g == null ? i18n().tr(s, "discordbot.no-token") : g.status().name().toLowerCase(Locale.ROOT);
        i18n().send(s, "discordbot.status", "value", mode.name().toLowerCase(Locale.ROOT), "title", state);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        String msg = Mentions.toDiscord(Text.strip(e.getMessage()), maxLength);
        post(toDiscord.replace("{player}", escape(e.getPlayer().getName())).replace("{message}", msg));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        if (joinQuit) post(Text.strip(i18n().trDefault("discordbot.bridge.join", "player", escape(e.getPlayer().getName()))));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (joinQuit) post(Text.strip(i18n().trDefault("discordbot.bridge.quit", "player", escape(e.getPlayer().getName()))));
    }

    private void post(String content) {
        ThreadPoolExecutor ex = out;
        DiscordRest r = rest;
        if (ex == null || r == null || content.isBlank()) return;
        ex.execute(() -> {
            try {
                DiscordRest.Response res = r.sendMessage(bridgeChannel, content.length() > 2000 ? content.substring(0, 2000) : content);
                if (!res.ok()) warnOnce(res.code(), "Discord: no se pudo enviar al canal del puente (HTTP " + res.code() + ").");
            } catch (IOException e) {
                lastError = e.getClass().getSimpleName();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    private void warnOnce(int code, String msg) {
        lastError = "HTTP " + code;
        synchronized (warned) {
            if (warned.add(code)) log().warning(msg);
        }
    }

    private final class Handler implements GatewayClient.Handler {
        @Override
        public void onReady(JsonObject d) {
            JsonObject user = d.has("user") ? d.getAsJsonObject("user") : new JsonObject();
            botName = user.has("username") ? user.get("username").getAsString() : "?";
            appId = d.has("application") ? d.getAsJsonObject("application").get("id").getAsString()
                    : user.has("id") ? user.get("id").getAsString() : null;
            log().info("Discord: conectado como " + botName + ".");
            if (registerCommands && appId != null) out.execute(DiscordBotModule.this::registerCommands);
            if (bridge && joinQuit) post(Text.strip(i18n().trDefault("discordbot.bridge.started")));
            out.execute(DiscordBotModule.this::refreshPanelQuiet);
        }

        @Override
        public void onDispatch(String type, JsonObject d) {
            if ("INTERACTION_CREATE".equals(type)) {
                int t = d.has("type") ? d.get("type").getAsInt() : 0;
                if (t == 2 || t == 3) scheduler().async(() -> interaction(d));
            } else if ("MESSAGE_CREATE".equals(type) && bridge) {
                fromDiscord(d);
            }
        }
    }

    private void registerCommands() {
        boolean staff = !staffRoles.isEmpty();
        JsonArray defs = SlashCommands.build((lang, key) -> plain(lang, key), i18n().defaultLanguage(), staff, customCommands);
        String target = mode == Mode.DISCORDSRV ? null : guild;
        try {
            DiscordRest.Response r = rest.registerCommands(appId, target, defs);
            if (!r.ok()) warnOnce(r.code(), "Discord: no se pudieron registrar los comandos (HTTP " + r.code() + ").");
        } catch (IOException e) {
            lastError = e.getClass().getSimpleName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void fromDiscord(JsonObject d) {
        if (!d.has("author") || d.getAsJsonObject("author").has("bot") && d.getAsJsonObject("author").get("bot").getAsBoolean()) return;
        if (!bridgeChannel.equals(str(d, "channel_id"))) return;
        JsonObject author = d.getAsJsonObject("author");
        JsonArray mentions = d.has("mentions") && d.get("mentions").isJsonArray() ? d.getAsJsonArray("mentions") : null;
        String text = Mentions.toMinecraft(str(d, "content"), mentions, maxLength);
        if (text.isEmpty()) return;
        String name = Mentions.displayName(author, d.has("member") ? d.getAsJsonObject("member") : null);
        if (name == null || name.isBlank()) name = "?";
        else if (name.length() > 32) name = name.substring(0, 32);
        String line = toMinecraft.replace("{user}", Mentions.toMinecraft(name, null, 32)).replace("{message}", text);
        scheduler().sync(() -> Bukkit.broadcastMessage(line));
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private void interaction(JsonObject d) {
        int type = d.get("type").getAsInt();
        String id = str(d, "id");
        String token = str(d, "token");
        String lang = i18n().normalize(SlashCommands.clientLocale(d));
        try {
            if (type == 3) {
                component(d, id, token, lang);
                return;
            }
            JsonObject data = d.has("data") ? d.getAsJsonObject("data") : new JsonObject();
            JsonObject member = d.has("member") ? d.getAsJsonObject("member") : null;
            JsonObject user = member != null && member.has("user") ? member.getAsJsonObject("user")
                    : d.has("user") ? d.getAsJsonObject("user") : new JsonObject();
            String name = str(data, "name");
            Reply reply = switch (name == null ? "" : name) {
                case "stats" -> stats(lang, SlashCommands.option(data, "player"));
                case "top" -> top(lang, SlashCommands.option(data, "stat"), SlashCommands.optionInt(data, "amount"));
                case "online" -> online(lang);
                case "panel" -> panelCommand(lang, SlashCommands.channelId(data, "channel"));
                case "staff" -> staff(lang, user, member, SlashCommands.option(data, "command"));
                default -> custom(lang, name, data);
            };
            DiscordRest.Response r = rest.reply(id, token, reply.text(), reply.embed(), reply.components(), reply.ephemeral());
            if (!r.ok()) warnOnce(r.code(), "Discord: no se pudo responder a un comando (HTTP " + r.code() + ").");
        } catch (Exception e) {
            log().log(Level.FINE, "Discord: error en interacción", e);
            try {
                rest.reply(id, token, plain(lang, "discordbot.error"), null, true);
            } catch (Exception ignored) {
                // ya se registró lastError abajo si falla
            }
        }
    }

    private void component(JsonObject d, String id, String token, String lang) throws Exception {
        JsonObject data = d.has("data") ? d.getAsJsonObject("data") : new JsonObject();
        String customId = str(data, "custom_id");
        if (!ServerPanel.isPanelButton(customId)) {
            rest.reply(id, token, plain(lang, "discordbot.unknown-command"), null, true);
            return;
        }
        if (ServerPanel.BTN_REFRESH.equals(customId)) {
            PanelPayload p = buildPanel(lang);
            DiscordRest.Response r = rest.updateMessage(id, token, null, p.embed(), p.components());
            if (r.ok() && panel.channelId() != null) panel.remember(panel.channelId(), panel.messageId());
            else if (!r.ok()) warnOnce(r.code(), "Discord: no se pudo actualizar el panel (HTTP " + r.code() + ").");
            return;
        }
        Reply reply = switch (customId) {
            case ServerPanel.BTN_ONLINE -> online(lang);
            case ServerPanel.BTN_TOPS -> topsEphemeral(lang);
            case ServerPanel.BTN_INFO -> infoEphemeral(lang);
            default -> new Reply(plain(lang, "discordbot.unknown-command"), null, null, true);
        };
        rest.reply(id, token, reply.text(), reply.embed(), reply.ephemeral());
    }

    private record Reply(String text, JsonObject embed, JsonArray components, boolean ephemeral) {
        Reply(String text, JsonObject embed, boolean ephemeral) {
            this(text, embed, null, ephemeral);
        }
    }

    private record PanelPayload(JsonObject embed, JsonArray components) {
    }

    private Reply panelCommand(String lang, String channelOpt) throws Exception {
        String channel = id(channelOpt);
        if (channel == null) channel = panel.defaultChannel();
        if (channel == null) return new Reply(plain(lang, "discordbot.panel.no-channel"), null, true);
        PanelPayload p = buildPanel(lang);
        DiscordRest.Response r;
        if (channel.equals(panel.channelId()) && panel.messageId() != null) {
            r = rest.editMessage(channel, panel.messageId(), null, p.embed(), p.components());
            if (!r.ok()) r = rest.sendChannel(channel, null, p.embed(), p.components());
        } else {
            r = rest.sendChannel(channel, null, p.embed(), p.components());
        }
        if (!r.ok()) return new Reply(plain(lang, "discordbot.panel.failed", "value", String.valueOf(r.code())), null, true);
        String mid = r.object().has("id") ? r.object().get("id").getAsString() : panel.messageId();
        panel.remember(channel, mid);
        return new Reply(plain(lang, "discordbot.panel.posted"), null, true);
    }

    private PanelPayload buildPanel(String lang) throws Exception {
        int[] counts = onMain(() -> new int[]{Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers()});
        ServerPanel.Snapshot snap = panel.snapshot(counts[0], counts[1]);
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("players", plain(lang, "discordbot.panel.field.players"));
        labels.put("registered", plain(lang, "discordbot.panel.field.registered"));
        labels.put("tps", plain(lang, "discordbot.panel.field.tps"));
        labels.put("ip", plain(lang, "discordbot.panel.field.ip"));
        labels.put("version", plain(lang, "discordbot.panel.field.version"));
        labels.put("uptime", plain(lang, "discordbot.panel.field.uptime"));
        labels.put("online-now", plain(lang, "discordbot.panel.field.online-now"));
        labels.put("online-empty", plain(lang, "discordbot.online-empty"));
        labels.put("refresh", plain(lang, "discordbot.panel.btn.refresh"));
        labels.put("online", plain(lang, "discordbot.panel.btn.online"));
        labels.put("tops", plain(lang, "discordbot.panel.btn.tops"));
        labels.put("info", plain(lang, "discordbot.panel.btn.info"));
        return new PanelPayload(panel.statusEmbed(snap, labels), panel.buttons(labels));
    }

    private void refreshPanelQuiet() {
        if (rest == null || panel == null || panel.channelId() == null || panel.messageId() == null) return;
        try {
            String lang = i18n().defaultLanguage();
            PanelPayload p = buildPanel(lang);
            DiscordRest.Response r = rest.editMessage(panel.channelId(), panel.messageId(), null, p.embed(), p.components());
            if (r.ok()) panel.remember(panel.channelId(), panel.messageId());
            else if (r.code() == 404) {
                // mensaje borrado: se publica de nuevo en el próximo /panel
                panel.remember(panel.channelId(), null);
            }
        } catch (Exception e) {
            lastError = e.getClass().getSimpleName();
        }
    }

    private Reply stats(String lang, String player) {
        if (player == null || player.isBlank()) {
            return new Reply(plain(lang, "discordbot.stats-need-player"), null, true);
        }
        UUID uuid = plugin.data().resolve(player);
        if (uuid == null) return new Reply(plain(lang, "discordbot.no-player", "player", player), null, true);
        String[] name = new String[1];
        double[] values = plugin.data().read(uuid, json -> {
            name[0] = PlayerDataService.str(json, "name");
            return StatSnapshot.values(json);
        });
        if (values == null) return new Reply(plain(lang, "discordbot.no-player", "player", player), null, true);
        JsonObject embed = new JsonObject();
        embed.addProperty("title", plain(lang, "discordbot.stats-title", "player", name[0]));
        embed.addProperty("color", EMBED_COLOR);
        String tag = plugin.api().playerTag(name[0], lang);
        if (tag != null) embed.addProperty("description", tag);
        JsonArray fields = new JsonArray();
        for (StatType t : StatType.ALL) {
            if (!stats.isTracked(t) || fields.size() == 25) continue;
            JsonObject f = new JsonObject();
            f.addProperty("name", plain(lang, "stats.names." + t.key));
            f.addProperty("value", "`" + t.format(values[t.ordinal()]) + "`");
            f.addProperty("inline", true);
            fields.add(f);
        }
        embed.add("fields", fields);
        JsonObject thumb = new JsonObject();
        thumb.addProperty("url", "https://mc-heads.net/avatar/" + uuid + "/64");
        embed.add("thumbnail", thumb);
        JsonObject foot = new JsonObject();
        foot.addProperty("text", "LosPerrisStats");
        embed.add("footer", foot);
        return new Reply(null, embed, false);
    }

    private Reply top(String lang, String raw, Integer amount) {
        StatType t = StatType.parse(raw);
        if (t == null) return new Reply(plain(lang, "stats.top.invalid"), null, true);
        int n = amount == null ? 10 : Math.max(3, Math.min(15, amount));
        List<Leaderboards.Entry> rows = stats.leaderboards().top(t);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(n, rows.size()); i++) {
            Leaderboards.Entry e = rows.get(i);
            sb.append(plain(lang, "discordbot.top-line", "position", i + 1, "player", escape(e.name), "value", e.formatted(t))).append('\n');
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", plain(lang, "discordbot.top-title", "stat", plain(lang, "stats.names." + t.key)));
        embed.addProperty("color", EMBED_COLOR);
        embed.addProperty("description", sb.length() == 0 ? plain(lang, "stats.top.empty") : sb.toString());
        return new Reply(null, embed, false);
    }

    private Reply topsEphemeral(String lang) {
        StringBuilder sb = new StringBuilder();
        int shown = 0;
        for (StatType t : StatType.ALL) {
            if (!stats.isTracked(t) || shown >= 4) continue;
            List<Leaderboards.Entry> rows = stats.leaderboards().top(t);
            sb.append("**").append(plain(lang, "stats.names." + t.key)).append("**\n");
            for (int i = 0; i < Math.min(5, rows.size()); i++) {
                Leaderboards.Entry e = rows.get(i);
                sb.append(plain(lang, "discordbot.top-line", "position", i + 1, "player", escape(e.name), "value", e.formatted(t))).append('\n');
            }
            sb.append('\n');
            shown++;
        }
        JsonObject embed = new JsonObject();
        embed.addProperty("title", plain(lang, "discordbot.panel.tops-title"));
        embed.addProperty("color", EMBED_COLOR);
        embed.addProperty("description", sb.length() == 0 ? plain(lang, "stats.top.empty") : sb.toString().trim());
        return new Reply(null, embed, true);
    }

    private Reply online(String lang) throws Exception {
        List<String> names = onlineNames();
        int max = Bukkit.getMaxPlayers();
        JsonObject embed = new JsonObject();
        embed.addProperty("title", plain(lang, "discordbot.online-title", "number", names.size(), "value", max));
        embed.addProperty("color", EMBED_COLOR);
        embed.addProperty("description", names.isEmpty()
                ? plain(lang, "discordbot.online-empty")
                : ServerPanel.bar(names.size(), max) + "\n" + ServerPanel.joinNames(names, 60));
        return new Reply(null, embed, true);
    }

    private Reply infoEphemeral(String lang) throws Exception {
        int[] counts = onMain(() -> new int[]{Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers()});
        ServerPanel.Snapshot snap = panel.snapshot(counts[0], counts[1]);
        JsonObject embed = panel.statusEmbed(snap, Map.of(
                "players", plain(lang, "discordbot.panel.field.players"),
                "registered", plain(lang, "discordbot.panel.field.registered"),
                "tps", plain(lang, "discordbot.panel.field.tps"),
                "ip", plain(lang, "discordbot.panel.field.ip"),
                "version", plain(lang, "discordbot.panel.field.version"),
                "uptime", plain(lang, "discordbot.panel.field.uptime"),
                "online-now", plain(lang, "discordbot.panel.field.online-now"),
                "online-empty", plain(lang, "discordbot.online-empty")));
        return new Reply(null, embed, true);
    }

    private Reply custom(String lang, String name, JsonObject data) throws Exception {
        CustomCommands.Command c = customCommands.get(name);
        if (c == null) return new Reply(plain(lang, "discordbot.unknown-command"), null, true);
        if ("stats".equals(c.action())) {
            String player = null;
            if (!c.options().isEmpty()) player = SlashCommands.option(data, c.options().get(0).name());
            return stats(lang, player);
        }
        if ("online".equals(c.action())) return online(lang);
        int[] counts = onMain(() -> new int[]{Bukkit.getOnlinePlayers().size(), Bukkit.getMaxPlayers()});
        ServerPanel.Snapshot snap = panel.snapshot(counts[0], counts[1]);
        Map<String, String> vars = panel.vars(snap);
        if (c.options() != null) {
            for (CustomCommands.Option opt : c.options()) {
                String v = SlashCommands.option(data, opt.name());
                if (v != null) vars.put(opt.name(), v);
            }
        }
        String text = c.reply() == null ? null : EmbedTemplate.fill(c.reply(), vars);
        JsonObject embed = c.embed() == null ? null : c.embed().build(vars);
        return new Reply(text, embed, c.ephemeral());
    }

    private Reply staff(String lang, JsonObject user, JsonObject member, String raw) throws Exception {
        boolean allowed = false;
        if (member != null && member.has("roles")) {
            for (JsonElement r : member.getAsJsonArray("roles")) if (staffRoles.contains(r.getAsString())) allowed = true;
        }
        if (!allowed) return new Reply(plain(lang, "discordbot.staff.no-permission"), null, true);
        CommandFilter.Verdict v = staffFilter.check(raw);
        if (v != CommandFilter.Verdict.ALLOWED) {
            return embedReply(plain(lang, "discordbot.staff.blocked", "value", v.name().toLowerCase(Locale.ROOT)), 0xED4245, true);
        }
        String cmd = CommandFilter.normalize(raw);
        String who = str(user, "username") + " (" + str(user, "id") + ")";
        log().info("[Discord] " + who + " ejecutó: /" + cmd);
        boolean ok = onMain(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd));
        return embedReply(plain(lang, ok ? "discordbot.staff.done" : "discordbot.staff.failed", "value", "/" + cmd),
                ok ? EMBED_COLOR : 0xED4245, true);
    }

    private static Reply embedReply(String description, int color, boolean ephemeral) {
        JsonObject embed = new JsonObject();
        embed.addProperty("description", description);
        embed.addProperty("color", color);
        return new Reply(null, embed, ephemeral);
    }

    private void syncRoles() {
        if (rest == null || guild == null || rules.isEmpty() || mode != Mode.DISCORDSRV || hook == null) return;
        Map<String, UUID> links = new HashMap<>(hook.linked());
        StatSnapshot snap = StatSnapshot.take(plugin.data());
        Map<UUID, int[]> ranks = rules.usesTop() ? RoleRules.ranks(snap.values(), rules.topStats()) : Map.of();
        int[] none = new int[StatType.ALL.length];
        for (Map.Entry<String, UUID> e : links.entrySet()) {
            double[] v = snap.values().get(e.getValue());
            if (v == null) continue;
            applyRoles(e.getKey(), rules.desired(v, ranks.getOrDefault(e.getValue(), none)));
        }
        for (String d : store.withRoles()) if (!links.containsKey(d)) applyRoles(d, Set.of());
        lastRoleSync = System.currentTimeMillis();
        save();
    }

    private void applyRoles(String discordId, Set<String> desired) {
        Set<String> have = new LinkedHashSet<>(store.roles(discordId));
        Set<String> now = new LinkedHashSet<>(have);
        try {
            for (String r : desired) {
                if (have.contains(r)) continue;
                DiscordRest.Response res = rest.addRole(guild, discordId, r);
                if (res.ok()) now.add(r);
                else if (gone(res)) {
                    now.clear();
                    break;
                } else warnOnce(res.code(), "Discord: no se pudo poner un rol (HTTP " + res.code() + ").");
            }
            for (String r : have) {
                if (desired.contains(r) || !now.contains(r)) continue;
                DiscordRest.Response res = rest.removeRole(guild, discordId, r);
                if (res.ok() || gone(res)) now.remove(r);
                else warnOnce(res.code(), "Discord: no se pudo quitar un rol (HTTP " + res.code() + ").");
            }
        } catch (IOException e) {
            lastError = e.getClass().getSimpleName();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        store.setRoles(discordId, now);
    }

    private static boolean gone(DiscordRest.Response r) {
        return r.code() == 404;
    }

    public Mode mode() {
        return mode;
    }

    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("mode", mode == null ? "off" : mode.name().toLowerCase(Locale.ROOT));
        GatewayClient g = gateway;
        o.addProperty("connected", g != null && g.status() == GatewayClient.Status.READY);
        o.addProperty("state", mode == Mode.DISCORDSRV ? "discordsrv"
                : g == null ? "no-token" : g.status().name().toLowerCase(Locale.ROOT));
        if (g != null && g.ping() >= 0) o.addProperty("ping", g.ping());
        if (g != null && g.failure() != null) o.addProperty("failure", g.failure());
        if (botName != null) o.addProperty("bot", botName);
        o.addProperty("bridge", bridge);
        o.addProperty("panel", panel != null && panel.messageId() != null);
        if (hook != null) o.addProperty("discordsrv", hook.version());
        o.addProperty("roles", mode == Mode.DISCORDSRV && rules != null && !rules.isEmpty() && guild != null);
        if (lastRoleSync > 0) o.addProperty("last_role_sync", lastRoleSync);
        if (lastError != null) o.addProperty("last_error", lastError);
        return o;
    }
}
