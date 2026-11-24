package me.gordi.mineapi.module.web;

import me.gordi.mineapi.core.data.DataDir;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.module.afk.AfkModule;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.web.http.Http;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Datos "en vivo" del dashboard. Cada 2 s copia (en el hilo principal) el estado de los jugadores
 * y del servidor a JSON inmutable que los hilos HTTP leen sin tocar la API de Bukkit. Guarda
 * además el historial (memoria + data/history.json), el chat reciente y el registro admin.
 */
public final class DashboardService {

    private static final int CHAT_HISTORY_MAX = 200;
    private volatile AuditLog audit;
    private static final int ADMIN_LOG_MAX = 100;

    private final MineAPI plugin;
    private final long startedAt;
    private final Map<UUID, Long> joinTimes;
    private final Consumer<JsonObject> sink;
    private final int historyMaxPoints;

    private volatile JsonArray onlineSnapshot = new JsonArray();
    private volatile JsonObject serverSnapshot = new JsonObject();
    private final Deque<JsonObject> history = new ArrayDeque<>();
    private final Deque<JsonObject> chatHistory;
    private final Deque<JsonObject> adminLog;

    private long lastTickCheck = System.nanoTime();
    private double measuredTps = 20.0;
    private Method paperTps;
    private int samplesSinceSave;

    /**
     * @param joinTimes   hora de conexión por jugador (la conserva el módulo entre recargas)
     * @param chatHistory y {@code adminLog}: también sobreviven a /lpa reload
     * @param sink        destino de los eventos en vivo (SSE)
     */
    DashboardService(MineAPI plugin, long startedAt, Map<UUID, Long> joinTimes, Deque<JsonObject> chatHistory,
                     Deque<JsonObject> adminLog, int historyMaxPoints, Consumer<JsonObject> sink) {
        this.plugin = plugin;
        this.startedAt = startedAt;
        this.joinTimes = joinTimes;
        this.chatHistory = chatHistory;
        this.adminLog = adminLog;
        this.historyMaxPoints = historyMaxPoints;
        this.sink = sink;
        try {
            paperTps = Bukkit.getServer().getClass().getMethod("getTPS");
        } catch (Exception ignored) {
            // Spigot: se mide a mano
        }
        loadHistory();
    }

    // ------------------------------------------------------------------ instantánea (hilo principal)

    void takeSnapshot() {
        long nowNano = System.nanoTime();
        double elapsed = (nowNano - lastTickCheck) / 1e9;
        lastTickCheck = nowNano;
        if (elapsed > 0) measuredTps = Math.min(20.0, 40.0 / elapsed);

        AfkModule afk = plugin.modules().get(AfkModule.class);
        long now = System.currentTimeMillis();
        JsonArray arr = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName());
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("skin", "https://minotar.net/helm/" + p.getName() + "/64.png");
            o.addProperty("world", p.getWorld().getName());
            o.addProperty("ping", safePing(p));
            o.addProperty("health", Math.round(p.getHealth() * 10.0) / 10.0);
            o.addProperty("food", p.getFoodLevel());
            o.addProperty("level", p.getLevel());
            GameMode gm = p.getGameMode();
            o.addProperty("gamemode", gm == null ? "" : gm.name());
            o.addProperty("afk", afk != null && afk.isAfk(p));
            Long joined = joinTimes.get(p.getUniqueId());
            o.addProperty("session_seconds", joined == null ? 0 : (now - joined) / 1000L);
            arr.add(o);
        }
        onlineSnapshot = arr;

        JsonObject s = new JsonObject();
        s.addProperty("online", arr.size());
        s.addProperty("max_players", Bukkit.getMaxPlayers());
        s.addProperty("version", Bukkit.getVersion());
        s.addProperty("bukkit_version", Bukkit.getBukkitVersion());
        s.addProperty("motd", Text.strip(Bukkit.getMotd()));
        s.addProperty("tps", Math.round(readTps() * 100.0) / 100.0);
        Runtime rt = Runtime.getRuntime();
        s.addProperty("memory_used_mb", (rt.totalMemory() - rt.freeMemory()) / 1048576L);
        s.addProperty("memory_max_mb", rt.maxMemory() / 1048576L);
        s.addProperty("uptime_seconds", (now - startedAt) / 1000L);
        s.addProperty("worlds", Bukkit.getWorlds().size());
        s.addProperty("plugin_version", plugin.getDescription().getVersion());
        s.addProperty("updated", now);
        serverSnapshot = s;
    }

    private static int safePing(Player p) {
        try {
            return p.getPing();
        } catch (Throwable t) {
            return -1;
        }
    }

    private double readTps() {
        if (paperTps != null) {
            try {
                double[] tps = (double[]) paperTps.invoke(Bukkit.getServer());
                if (tps != null && tps.length > 0) return Math.min(20.0, tps[0]);
            } catch (Throwable ignored) {
                paperTps = null;
            }
        }
        return measuredTps;
    }

    public JsonArray online() { return onlineSnapshot; }
    public JsonObject server() { return serverSnapshot; }
    public boolean hasSnapshot() { return serverSnapshot.has("updated"); }

    public JsonObject onlineInfo(String name) {
        for (JsonElement el : onlineSnapshot) {
            JsonObject o = el.getAsJsonObject();
            if (o.get("name").getAsString().equalsIgnoreCase(name)) return o;
        }
        return null;
    }

    public boolean isOnline(String name) {
        return onlineInfo(name) != null;
    }

    // ------------------------------------------------------------------ historial

    void sampleHistory() {
        JsonObject totals = plugin.modules().get(me.gordi.mineapi.module.stats.StatsModule.class).totals();
        JsonObject point = new JsonObject();
        point.addProperty("t", System.currentTimeMillis());
        point.addProperty("online", Bukkit.getOnlinePlayers().size());
        point.addProperty("tps", Math.round(readTps() * 10.0) / 10.0);
        point.addProperty("players_tracked", totals.get("players_tracked").getAsInt());
        JsonObject t = totals.getAsJsonObject("totals");
        for (StatType type : StatType.ALL) point.add(type.key, t.get(type.key));
        synchronized (history) {
            history.addLast(point);
            while (history.size() > historyMaxPoints) history.removeFirst();
        }
        // A disco cada 6 muestras (y siempre al desactivar)
        if (++samplesSinceSave >= 6) {
            samplesSinceSave = 0;
            plugin.scheduler().async(this::saveHistory);
        }
    }

    public JsonArray history(long since) {
        JsonArray arr = new JsonArray();
        synchronized (history) {
            for (JsonObject p : history) if (p.get("t").getAsLong() >= since) arr.add(p);
        }
        return arr;
    }

    private File historyFile() {
        return new File(plugin.getDataFolder(), DataDir.NAME + "/history.json");
    }

    private void loadHistory() {
        File file = historyFile();
        if (!file.exists()) return;
        try {
            JsonElement el = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            if (!el.isJsonArray()) return;
            synchronized (history) {
                for (JsonElement p : el.getAsJsonArray()) {
                    if (p.isJsonObject() && p.getAsJsonObject().has("t")) history.addLast(p.getAsJsonObject());
                }
                while (history.size() > historyMaxPoints) history.removeFirst();
            }
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo leer data/history.json: " + e.getMessage());
        }
    }

    synchronized void saveHistory() {
        JsonArray arr = history(0);
        File file = historyFile();
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) plugin.getLogger().warning("No se pudo crear " + parent);
            File tmp = new File(file.getPath() + ".tmp");
            Files.writeString(tmp.toPath(), Http.GSON.toJson(arr), StandardCharsets.UTF_8);
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo guardar data/history.json: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ chat y eventos

    /**
     * Aviso emergente para el dashboard (no entra en el historial del chat). kind: milestone,
     * alert... El cliente decide si lo muestra según las preferencias del usuario.
     */
    public void notify(String kind, String user, String message, String level) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", "notify");
        obj.addProperty("kind", kind);
        if (user != null) obj.addProperty("user", user);
        obj.addProperty("message", message);
        if (level != null) obj.addProperty("level", level);
        obj.addProperty("time", System.currentTimeMillis());
        sink.accept(obj);
    }

    /** Destino persistente del registro de administración (null = solo memoria). */
    void setAudit(AuditLog audit) {
        this.audit = audit;
    }

    public AuditLog audit() {
        return audit;
    }

    /**
     * Registra un evento y lo envía al chat en vivo. No bloquea.
     * @param type chat | join | quit | system
     */
    public void publish(String type, String user, String uuid, String message) {
        JsonObject obj = new JsonObject();
        obj.addProperty("type", type);
        obj.addProperty("user", user);
        if (uuid != null) obj.addProperty("uuid", uuid);
        obj.addProperty("message", message);
        obj.addProperty("time", System.currentTimeMillis());
        synchronized (chatHistory) {
            chatHistory.addLast(obj);
            while (chatHistory.size() > CHAT_HISTORY_MAX) chatHistory.removeFirst();
        }
        sink.accept(obj);
    }

    public JsonArray chatHistory(int limit) {
        JsonArray arr = new JsonArray();
        synchronized (chatHistory) {
            int skip = Math.max(0, chatHistory.size() - limit);
            int i = 0;
            for (JsonObject o : chatHistory) if (i++ >= skip) arr.add(o);
        }
        return arr;
    }

    // ------------------------------------------------------------------ registro admin

    public void logAdmin(String remote, String action, String details, boolean ok) {
        JsonObject o = new JsonObject();
        o.addProperty("time", System.currentTimeMillis());
        o.addProperty("remote", remote);
        o.addProperty("action", action);
        o.addProperty("details", details);
        o.addProperty("ok", ok);
        synchronized (adminLog) {
            adminLog.addLast(o);
            while (adminLog.size() > ADMIN_LOG_MAX) adminLog.removeFirst();
        }
        AuditLog a = audit;
        if (a != null) plugin.scheduler().async(() -> a.append(o));
        plugin.getLogger().info("[WebAdmin] " + remote + " " + action + (details.isEmpty() ? "" : " " + details) + (ok ? "" : " (FALLÓ)"));
    }

    /** Más reciente primero. */
    public JsonArray adminLog() {
        List<JsonObject> list;
        synchronized (adminLog) {
            list = new ArrayList<>(adminLog);
        }
        JsonArray arr = new JsonArray();
        for (int i = list.size() - 1; i >= 0; i--) arr.add(list.get(i));
        return arr;
    }
}
