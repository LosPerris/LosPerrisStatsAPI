package me.gordi.mineapi.module.console;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.module.web.DashboardService;
import me.gordi.mineapi.module.web.WebModule;
import me.gordi.mineapi.module.web.http.ApiAuth;
import me.gordi.mineapi.module.web.http.Http;
import me.gordi.mineapi.module.web.http.SseHub;
import me.gordi.mineapi.module.web.http.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Consola web en vivo (solo administradores, header Authorization):
 * <ul>
 *   <li>GET  /api/admin/console          últimas líneas</li>
 *   <li>GET  /api/admin/console/stream   líneas nuevas (SSE)</li>
 *   <li>POST /api/admin/console/command  {"command": "..."} filtrado por allow/deny y auditado</li>
 * </ul>
 * Las líneas se limpian (colores, API key, IP, patrones) y se limitan por segundo para que una
 * avalancha de log no afecte al servidor. Desactivado: no hay appender, ni rutas, ni hilos.
 */
public final class ConsoleModule extends AbstractModule {

    public static final String ID = "web-console";
    private static final String BASE = "/api/admin/console";

    private final Deque<JsonObject> buffer = new ArrayDeque<>();
    private int bufferMax;
    private int maxPerSecond;
    private long windowStart;
    private int windowCount;
    private int dropped;

    private WebServer server;
    private ApiAuth auth;
    private DashboardService dash;
    private SseHub hub;
    private LogTap tap;
    private LogLines lines;
    private CommandFilter filter;

    public ConsoleModule(MineAPI plugin) {
        super(plugin, ID, WebModule.ID);
    }

    @Override
    protected void onEnable() {
        WebModule web = plugin.modules().get(WebModule.class);
        server = web.server();
        auth = web.auth();
        dash = web.dashboard();
        FileConfiguration c = config();
        bufferMax = Math.max(20, Math.min(2000, c.getInt("buffer-lines", 300)));
        maxPerSecond = Math.max(5, c.getInt("max-lines-per-second", 60));
        lines = new LogLines(plugin.configs().module(WebModule.ID).getString("security.api-key", ""), c.getBoolean("hide-ips", true),
                c.getStringList("redact"));
        filter = new CommandFilter(c.getStringList("allow"), c.getStringList("deny"));
        hub = new SseHub(Math.max(1, c.getInt("max-clients", 3)));
        tap = attach(this::onLine);
        server.route(BASE, this::handle);
        if (!auth.settings().adminUsable()) {
            log().warning("Consola web activa pero la administración no es usable (web.admin-enabled y security con api-key).");
        }
    }

    @Override
    protected void onDisable() {
        if (tap != null) tap.detach();
        if (hub != null) hub.shutdown();
        if (server != null) server.unroute(BASE);
        synchronized (buffer) {
            buffer.clear();
        }
        tap = null;
        hub = null;
        server = null;
        auth = null;
        dash = null;
    }

    private LogTap attach(LogTap.Sink sink) {
        try {
            return Log4jTap.attach(sink);
        } catch (NoClassDefFoundError | ClassCastException e) {
            log().info("Log4j no disponible; la consola web usará java.util.logging (solo mensajes de plugins).");
            return JulTap.attach(sink);
        }
    }

    // ------------------------------------------------------------------ líneas (cualquier hilo)

    private void onLine(long time, String level, String raw) {
        JsonObject o;
        synchronized (buffer) {
            long second = time / 1000L;
            if (second != windowStart) {
                if (dropped > 0) push(time, "WARN", "… " + dropped + " lines skipped (console.max-lines-per-second)");
                windowStart = second;
                windowCount = 0;
                dropped = 0;
            }
            if (++windowCount > maxPerSecond) {
                dropped++;
                return;
            }
            o = push(time, level, lines.clean(raw));
        }
        SseHub h = hub;
        if (h != null && !h.isEmpty()) h.publish(o);
    }

    private JsonObject push(long time, String level, String message) {
        JsonObject o = new JsonObject();
        o.addProperty("t", time);
        o.addProperty("l", level);
        o.addProperty("m", message);
        buffer.addLast(o);
        while (buffer.size() > bufferMax) buffer.removeFirst();
        return o;
    }

    // ------------------------------------------------------------------ HTTP

    private void handle(HttpExchange t) throws IOException {
        if (!auth.admin(t)) return;
        String path = t.getRequestURI().getPath();
        String action = path.length() > BASE.length() ? path.substring(BASE.length()) : "/";
        if (action.length() > 1 && action.endsWith("/")) action = action.substring(0, action.length() - 1);
        String method = t.getRequestMethod();
        if ("GET".equals(method) && "/".equals(action)) {
            JsonArray arr = new JsonArray();
            synchronized (buffer) {
                for (JsonObject o : buffer) arr.add(o);
            }
            JsonObject out = new JsonObject();
            out.add("lines", arr);
            out.addProperty("max_clients", config().getInt("max-clients", 3));
            send(t, 200, Http.GSON.toJson(out));
        } else if ("GET".equals(method) && "/stream".equals(action)) {
            hub.accept(t, auth.adminCors());
        } else if ("POST".equals(method) && "/command".equals(action)) {
            command(t);
        } else {
            send(t, 404, Http.error("Unknown console endpoint"));
        }
    }

    private void command(HttpExchange t) throws IOException {
        String ip = Http.remoteIp(t);
        Http.Body body = Http.readJson(t);
        if (!body.ok()) {
            send(t, body.status(), Http.error(body.error()));
            return;
        }
        String raw = Http.str(body.body(), "command");
        CommandFilter.Verdict v = filter.check(raw);
        String cmd = CommandFilter.normalize(raw);
        if (v != CommandFilter.Verdict.ALLOWED) {
            String shown = cmd == null ? "(invalid)" : cmd;
            dash.logAdmin(ip, "console", shown + " -> " + v.name().toLowerCase(java.util.Locale.ROOT), false);
            int code = v == CommandFilter.Verdict.INVALID ? 400 : 403;
            String msg = switch (v) {
                case INVALID -> "Invalid command (1-" + CommandFilter.MAX_LENGTH + " characters, one line)";
                case DENIED -> "Command blocked by console.deny";
                default -> "Command not in console.allow";
            };
            send(t, code, Http.error(msg));
            return;
        }
        boolean ok;
        try {
            ok = Bukkit.getScheduler().callSyncMethod(plugin,
                    () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd)).get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            dash.logAdmin(ip, "console", cmd + " -> timeout", false);
            send(t, 504, Http.error("Server main thread did not respond in time"));
            return;
        } catch (Exception e) {
            dash.logAdmin(ip, "console", cmd + " -> error", false);
            send(t, 500, Http.error("Command failed: " + e.getClass().getSimpleName()));
            return;
        }
        dash.logAdmin(ip, "console", cmd, ok);
        JsonObject out = new JsonObject();
        out.addProperty("ok", ok);
        out.addProperty("command", cmd);
        if (!ok) out.addProperty("error", "Unknown command");
        send(t, ok ? 200 : 404, Http.GSON.toJson(out));
    }

    private void send(HttpExchange t, int code, String json) throws IOException {
        Http.json(t, code, json, auth.adminCors());
    }
}
