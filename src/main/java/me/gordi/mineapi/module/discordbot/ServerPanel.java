package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.gordi.mineapi.core.storage.JsonFiles;
import me.gordi.mineapi.core.time.Durations;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/** Panel de estado Discord: embed, botones y message-id persistido. */
public final class ServerPanel {

    public static final String CUSTOM_PREFIX = "lpa:panel:";
    public static final String BTN_REFRESH = CUSTOM_PREFIX + "refresh";
    public static final String BTN_ONLINE = CUSTOM_PREFIX + "online";
    public static final String BTN_TOPS = CUSTOM_PREFIX + "tops";
    public static final String BTN_INFO = CUSTOM_PREFIX + "info";

    public record Snapshot(int online, int max, int registered, double tps, String uptime, String ip, String version,
                           List<String> onlineNames, long nextUpdateMs) {
    }

    public record Fields(boolean players, boolean registered, boolean tps, boolean ip, boolean version, boolean uptime,
                         boolean onlineList) {
        static Fields from(ConfigurationSection s) {
            if (s == null) return new Fields(true, true, true, true, true, true, true);
            return new Fields(
                    s.getBoolean("players", true),
                    s.getBoolean("registered", true),
                    s.getBoolean("tps", true),
                    s.getBoolean("ip", true),
                    s.getBoolean("version", true),
                    s.getBoolean("uptime", true),
                    s.getBoolean("online-list", true));
        }
    }

    private final EmbedTemplate template;
    private final Fields fields;
    private final long refreshMs;
    private final String defaultChannel;
    private final String serverIp;
    private final String serverVersion;
    private final File stateFile;
    private final IntSupplier registered;
    private final LongSupplier startedAt;
    private final Supplier<List<String>> onlineNames;
    private final Supplier<String> bukkitVersion;
    private Method paperTps;
    private String channelId;
    private String messageId;
    private long lastPublishAt;

    public ServerPanel(ConfigurationSection panel, File stateFile, IntSupplier registered, LongSupplier startedAt,
                       Supplier<List<String>> onlineNames, Supplier<String> bukkitVersion) {
        ConfigurationSection root = panel == null ? new org.bukkit.configuration.MemoryConfiguration() : panel;
        this.template = new EmbedTemplate(root.getConfigurationSection("embed"));
        this.fields = Fields.from(root.getConfigurationSection("fields"));
        long parsed;
        try {
            parsed = Durations.parse(root.getString("refresh-interval", "5m"));
        } catch (IllegalArgumentException e) {
            parsed = 300_000L;
        }
        this.refreshMs = Math.max(30_000L, parsed);
        this.defaultChannel = DiscordBotModule.id(root.getString("default-channel-id", ""));
        this.serverIp = nullToEmpty(root.getString("server-ip", ""));
        this.serverVersion = nullToEmpty(root.getString("server-version", ""));
        this.stateFile = stateFile;
        this.registered = registered;
        this.startedAt = startedAt;
        this.onlineNames = onlineNames;
        this.bukkitVersion = bukkitVersion;
        try {
            paperTps = org.bukkit.Bukkit.getServer().getClass().getMethod("getTPS");
        } catch (Exception ignored) {
            paperTps = null;
        }
        loadState();
    }

    public long refreshMs() { return refreshMs; }
    public String defaultChannel() { return defaultChannel; }
    public String channelId() { return channelId; }
    public String messageId() { return messageId; }

    public void remember(String channel, String message) {
        this.channelId = channel;
        this.messageId = message;
        this.lastPublishAt = System.currentTimeMillis();
        saveState();
    }

    public Snapshot snapshot(int online, int max) {
        List<String> names = onlineNames.get();
        if (names == null) names = List.of();
        long next = Math.max(0L, (lastPublishAt == 0 ? System.currentTimeMillis() : lastPublishAt) + refreshMs
                - System.currentTimeMillis());
        String ver = serverVersion.isBlank() ? shortVersion(bukkitVersion.get()) : serverVersion;
        return new Snapshot(online, max, registered.getAsInt(), readTps(),
                formatUptime(System.currentTimeMillis() - startedAt.getAsLong()),
                serverIp, ver, names, next);
    }

    /** Variables para title/description/footer y custom-commands. */
    public Map<String, String> vars(Snapshot s) {
        int pct = s.max() <= 0 ? 0 : (int) Math.round(100.0 * s.online() / s.max());
        Map<String, String> m = new LinkedHashMap<>();
        m.put("online", String.valueOf(s.online()));
        m.put("max", String.valueOf(s.max()));
        m.put("players", String.valueOf(s.registered()));
        m.put("tps", String.format(Locale.US, "%.1f", s.tps()));
        m.put("uptime", s.uptime());
        m.put("ip", s.ip().isBlank() ? "—" : s.ip());
        m.put("version", s.version().isBlank() ? "—" : s.version());
        m.put("refresh", Durations.format(refreshMs));
        m.put("next_update", Durations.format(Math.max(1_000L, s.nextUpdateMs())));
        m.put("bar", bar(s.online(), s.max()));
        m.put("pct", String.valueOf(pct));
        m.put("status", s.online() > 0 ? "online" : "idle");
        return m;
    }

    public JsonObject statusEmbed(Snapshot s, Map<String, String> label) {
        JsonObject embed = template.build(vars(s));
        JsonArray arr = new JsonArray();
        if (fields.players) {
            arr.add(field(label.getOrDefault("players", "Players"),
                    "`" + s.online() + "/" + s.max() + "`\n" + bar(s.online(), s.max()), true));
        }
        if (fields.registered) {
            arr.add(field(label.getOrDefault("registered", "Registered"), "`" + s.registered() + "`", true));
        }
        if (fields.tps) {
            arr.add(field(label.getOrDefault("tps", "TPS"), "`" + String.format(Locale.US, "%.1f", s.tps()) + "`", true));
        }
        if (fields.ip && !s.ip().isBlank()) {
            arr.add(field(label.getOrDefault("ip", "IP"), "`" + s.ip() + "`", true));
        }
        if (fields.version && !s.version().isBlank()) {
            arr.add(field(label.getOrDefault("version", "Version"), "`" + s.version() + "`", true));
        }
        if (fields.uptime) {
            arr.add(field(label.getOrDefault("uptime", "Uptime"), "`" + s.uptime() + "`", true));
        }
        if (fields.onlineList) {
            String list = s.onlineNames().isEmpty()
                    ? label.getOrDefault("online-empty", "Nobody online.")
                    : joinNames(s.onlineNames(), 40);
            arr.add(field(label.getOrDefault("online-now", "Online now"), list, false));
        }
        embed.add("fields", arr);
        return embed;
    }

    public JsonArray buttons(Map<String, String> labels) {
        JsonArray row = new JsonArray();
        row.add(button(BTN_REFRESH, labels.getOrDefault("refresh", "Refresh"), 2));
        row.add(button(BTN_ONLINE, labels.getOrDefault("online", "Online"), 2));
        row.add(button(BTN_TOPS, labels.getOrDefault("tops", "Tops"), 1));
        row.add(button(BTN_INFO, labels.getOrDefault("info", "Info"), 1));
        JsonObject action = new JsonObject();
        action.addProperty("type", 1);
        action.add("components", row);
        JsonArray components = new JsonArray();
        components.add(action);
        return components;
    }

    public static boolean isPanelButton(String customId) {
        return customId != null && customId.startsWith(CUSTOM_PREFIX);
    }

    /** Barra de ocupación estilo Discord (`▰` / `▱` + porcentaje). */
    public static String bar(int online, int max) {
        int slots = 10;
        int filled = max <= 0 ? 0 : Math.min(slots, (int) Math.round(slots * (online / (double) max)));
        StringBuilder sb = new StringBuilder(slots + 8);
        for (int i = 0; i < slots; i++) sb.append(i < filled ? '▰' : '▱');
        int pct = max <= 0 ? 0 : (int) Math.round(100.0 * online / max);
        return sb.append(" **").append(pct).append("%**").toString();
    }

    public static String joinNames(List<String> names, int max) {
        List<String> use = names.size() > max ? names.subList(0, max) : names;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < use.size(); i++) {
            if (i > 0) sb.append(' ');
            sb.append('`').append(use.get(i).replace('`', '\'')).append('`');
        }
        if (names.size() > max) sb.append(" …");
        return sb.toString();
    }

    private void loadState() {
        JsonObject o = JsonFiles.read(stateFile);
        if (o == null) return;
        channelId = DiscordBotModule.id(o.has("channel_id") ? o.get("channel_id").getAsString() : "");
        messageId = o.has("message_id") ? o.get("message_id").getAsString() : null;
        if (messageId != null && !messageId.matches("\\d{5,25}")) messageId = null;
        lastPublishAt = o.has("last_publish") ? o.get("last_publish").getAsLong() : 0L;
    }

    private void saveState() {
        JsonObject o = new JsonObject();
        if (channelId != null) o.addProperty("channel_id", channelId);
        if (messageId != null) o.addProperty("message_id", messageId);
        o.addProperty("last_publish", lastPublishAt);
        try {
            JsonFiles.write(stateFile, o, true);
        } catch (IOException ignored) {
            // el panel sigue usable en memoria hasta el próximo save
        }
    }

    private double readTps() {
        if (paperTps != null) {
            try {
                double[] tps = (double[]) paperTps.invoke(org.bukkit.Bukkit.getServer());
                if (tps != null && tps.length > 0) return Math.min(20.0, tps[0]);
            } catch (Throwable t) {
                paperTps = null;
            }
        }
        return 20.0;
    }

    private static JsonObject field(String name, String value, boolean inline) {
        JsonObject f = new JsonObject();
        f.addProperty("name", name);
        f.addProperty("value", value == null || value.isBlank() ? "—" : value);
        f.addProperty("inline", inline);
        return f;
    }

    private static JsonObject button(String id, String label, int style) {
        JsonObject b = new JsonObject();
        b.addProperty("type", 2);
        b.addProperty("style", style);
        b.addProperty("label", cut(label, 80));
        b.addProperty("custom_id", id);
        return b;
    }

    private static String formatUptime(long ms) {
        long sec = Math.max(0L, ms / 1000L);
        long d = sec / 86400;
        long h = (sec % 86400) / 3600;
        long m = (sec % 3600) / 60;
        if (d > 0) return d + "d " + h + "h";
        if (h > 0) return h + "h " + m + "m";
        return Math.max(1, m) + "m";
    }

    private static String shortVersion(String raw) {
        if (raw == null || raw.isBlank()) return "";
        int dash = raw.indexOf('-');
        return dash > 0 ? raw.substring(0, dash) : raw;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    private static String cut(String s, int max) {
        String t = s == null || s.isBlank() ? "-" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
