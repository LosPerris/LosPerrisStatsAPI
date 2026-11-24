package me.gordi.mineapi.module.web.routes;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.api.WebApi;
import me.gordi.mineapi.api.WebRegistry;
import me.gordi.mineapi.core.module.Module;
import me.gordi.mineapi.core.text.Csv;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.module.backups.BackupsModule;
import me.gordi.mineapi.module.updates.UpdatesModule;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.periods.PeriodKind;
import me.gordi.mineapi.module.periods.PeriodsModule;
import me.gordi.mineapi.module.stats.StatsModule;
import me.gordi.mineapi.module.web.DashboardService;
import me.gordi.mineapi.module.web.http.ApiAuth;
import me.gordi.mineapi.module.web.http.Http;
import me.gordi.mineapi.module.web.http.WebServer;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** /api/admin/... (POST con header Authorization). Cada acción queda en el registro admin. */
public final class AdminRoutes {

    private static final String BASE = "/api/admin";
    private static final Set<String> POST_ONLY = Set.of("/stat", "/reset", "/leaderboard/refresh", "/save", "/reload", "/broadcast", "/period/end",
            "/backups/run", "/updates/check");

    private final MineAPI plugin;
    private final StatsModule stats;
    private final DashboardService dash;
    private final ApiAuth auth;
    private final ReadRoutes read;

    public AdminRoutes(MineAPI plugin, StatsModule stats, DashboardService dash, ApiAuth auth, ReadRoutes read) {
        this.plugin = plugin;
        this.stats = stats;
        this.dash = dash;
        this.auth = auth;
        this.read = read;
    }

    public void register(WebServer web) {
        web.route(BASE, this::handle);
    }

    private void send(HttpExchange t, int code, String json) throws IOException {
        Http.json(t, code, json, auth.adminCors());
    }

    private <T> T onMainThread(Callable<T> task) throws Exception {
        return Bukkit.getScheduler().callSyncMethod(plugin, task).get(10, TimeUnit.SECONDS);
    }

    static String action(String path) {
        String a = path.length() > BASE.length() ? path.substring(BASE.length()) : "/";
        return a.length() > 1 && a.endsWith("/") ? a.substring(0, a.length() - 1) : a;
    }

    private void handle(HttpExchange t) throws IOException {
        if (!auth.admin(t)) return;
        String action = action(t.getRequestURI().getPath());
        String ip = Http.remoteIp(t);
        try {
            if ("GET".equals(t.getRequestMethod())) {
                if ("/".equals(action) || "/check".equals(action)) send(t, 200, "{\"ok\": true}");
                else if ("/log".equals(action)) send(t, 200, Http.GSON.toJson(dash.adminLog()));
                else if ("/backups".equals(action)) moduleStatus(t, BackupsModule.class, BackupsModule.ID);
                else if ("/updates".equals(action)) moduleStatus(t, UpdatesModule.class, UpdatesModule.ID);
                else if ("/export.csv".equals(action)) exportCsv(t, ip);
                else if (extension("GET", action, t, null)) return;
                else if (POST_ONLY.contains(action) || plugin.api().webRegistry().hasAdminPath(action)) {
                    t.getResponseHeaders().set("Allow", "POST, OPTIONS");
                    send(t, 405, Http.error("Method not allowed: use POST"));
                } else send(t, 404, Http.error("Unknown admin endpoint"));
                return;
            }
            if (!"POST".equals(t.getRequestMethod())) {
                send(t, 405, Http.error("Method not allowed"));
                return;
            }
            Http.Body body = Http.readJson(t);
            if (!body.ok()) {
                send(t, body.status(), Http.error(body.error()));
                return;
            }
            JsonObject b = body.body();
            switch (action) {
                case "/stat" -> setStat(t, b, ip);
                case "/reset" -> reset(t, b, ip);
                case "/leaderboard/refresh" -> {
                    plugin.scheduler().async(() -> stats.leaderboards().update(plugin.data()));
                    dash.logAdmin(ip, "leaderboard-refresh", "", true);
                    send(t, 202, "{\"ok\": true, \"message\": \"Leaderboard refresh scheduled\"}");
                }
                case "/save" -> {
                    onMainThread(() -> {
                        plugin.data().saveAll();
                        return null;
                    });
                    dash.logAdmin(ip, "save", "", true);
                    send(t, 200, "{\"ok\": true}");
                }
                case "/reload" -> {
                    dash.logAdmin(ip, "reload", "", true);
                    // Se responde antes: el reload reinicia este mismo servidor web
                    send(t, 202, "{\"ok\": true, \"message\": \"Reload scheduled; the web server will restart\"}");
                    plugin.scheduler().sync(plugin::reloadSystem);
                }
                case "/broadcast" -> broadcast(t, b, ip);
                case "/period/end" -> endPeriod(t, b, ip);
                case "/backups/run" -> runBackup(t, ip);
                case "/updates/check" -> {
                    UpdatesModule um = plugin.modules().get(UpdatesModule.class);
                    if (um == null || !um.configured()) {
                        send(t, 404, Http.error("Update checker not configured (updates.source / updates.id)"));
                        return;
                    }
                    um.check(); // ya estamos en un hilo del servidor web
                    dash.logAdmin(ip, "updates-check", "", true);
                    send(t, 200, Http.GSON.toJson(um.status()));
                }
                default -> {
                    if (!extension("POST", action, t, b)) send(t, 404, Http.error("Unknown admin endpoint"));
                }
            }
        } catch (TimeoutException e) {
            send(t, 504, Http.error("Server main thread did not respond in time"));
        } catch (Exception e) {
            plugin.getLogger().warning("[WebAdmin] Error en " + action + ": " + e);
            dash.logAdmin(ip, action.substring(1), "error: " + e.getClass().getSimpleName(), false);
            send(t, 500, Http.error("Internal error"));
        }
    }

    // ------------------------------------------------------------------ fase 3

    /** Acción de un addon ({@link me.gordi.mineapi.api.WebApi#admin}); false si no hay ninguna. */
    private boolean extension(String method, String action, HttpExchange t, JsonObject body) throws Exception {
        WebRegistry.AdminEntry e = plugin.api().webRegistry().admin(method, action);
        if (e == null) return false;
        WebApi.Reply r = e.handler().handle(new ApiRequest(plugin, t, false, dash).body(body));
        send(t, r.status(), r.json());
        return true;
    }

    /** GET de estado de un módulo con método status() (backups, updates). */
    private void moduleStatus(HttpExchange t, Class<? extends Module> type, String id) throws IOException {
        Module m = plugin.modules().get(type);
        if (m == null || !m.isEnabled()) {
            send(t, 404, Http.error("Module '" + id + "' disabled"));
            return;
        }
        JsonObject o;
        if (m instanceof BackupsModule bm) o = bm.status();
        else o = ((UpdatesModule) m).status();
        send(t, 200, Http.GSON.toJson(o));
    }

    private void runBackup(HttpExchange t, String ip) throws IOException {
        BackupsModule bm = plugin.modules().get(BackupsModule.class);
        if (bm == null) {
            send(t, 404, Http.error("Module 'backups' disabled"));
            return;
        }
        boolean started = bm.run(null);
        dash.logAdmin(ip, "backup", started ? "started" : "already running", started);
        if (!started) send(t, 409, Http.error("A backup is already running"));
        else send(t, 202, "{\"ok\": true, \"message\": \"Backup started\"}");
    }

    /** Todos los jugadores en CSV (sin IP). Se genera en este hilo, no en el principal. */
    private void exportCsv(HttpExchange t, String ip) throws IOException {
        JsonObject all = plugin.data().nameKeyedSnapshot(true);
        List<JsonObject> rows = new ArrayList<>(all.size());
        for (Map.Entry<String, JsonElement> e : all.entrySet()) {
            if (e.getValue().isJsonObject()) rows.add(e.getValue().getAsJsonObject());
        }
        rows.sort(Comparator.comparing(o -> o.has("name") ? o.get("name").getAsString().toLowerCase(Locale.ROOT) : ""));
        String csv = Csv.table(rows, List.of("name", "uuid"), Set.of("ip_address"));
        dash.logAdmin(ip, "export-csv", rows.size() + " players", true);
        String date = java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
        Http.download(t, "text/csv; charset=UTF-8", "losperris-players-" + date + ".csv", csv, auth.adminCors());
    }

    private void reset(HttpExchange t, JsonObject b, String ip) throws Exception {
        String player = Http.str(b, "player");
        if (player == null || !Http.PLAYER_NAME.matcher(player).matches()) {
            send(t, 400, Http.error("Invalid 'player'"));
            return;
        }
        boolean ok = onMainThread(() -> stats.reset(player));
        dash.logAdmin(ip, "reset", player, ok);
        if (!ok) {
            send(t, 404, Http.error("Player not found"));
            return;
        }
        read.invalidate(player);
        send(t, 200, "{\"ok\": true}");
    }

    /** Cierra ya un periodo ({"type":"weekly|monthly"}): anuncia, archiva y empieza otro. */
    private void endPeriod(HttpExchange t, JsonObject b, String ip) throws IOException {
        PeriodsModule pm = plugin.modules().get(PeriodsModule.class);
        if (pm == null) {
            send(t, 404, Http.error("Module 'periods' disabled"));
            return;
        }
        PeriodKind kind = PeriodKind.parse(Http.str(b, "type"));
        if (kind == null || !pm.isActive(kind)) {
            send(t, 400, Http.error("Invalid 'type' (weekly or monthly, and enabled in config)"));
            return;
        }
        boolean ok = pm.endPeriod(kind, true, null);
        dash.logAdmin(ip, "period-end", kind.id, ok);
        if (!ok) {
            send(t, 409, Http.error("Period already ending"));
            return;
        }
        read.invalidate(null);
        send(t, 202, "{\"ok\": true, \"message\": \"Period end scheduled\"}");
    }

    private void broadcast(HttpExchange t, JsonObject b, String ip) throws Exception {
        String message = Http.str(b, "message");
        if (message == null || message.trim().isEmpty() || message.length() > 256) {
            send(t, 400, Http.error("'message' must be 1-256 characters"));
            return;
        }
        String colored = Text.color(message.replace('\n', ' ').replace('\r', ' ').trim());
        String prefix = plugin.getPrefix();
        onMainThread(() -> {
            Bukkit.broadcastMessage(prefix + colored);
            return null;
        });
        String plain = Text.strip(colored);
        dash.publish("system", "Web", null, plain);
        dash.logAdmin(ip, "broadcast", plain, true);
        send(t, 200, "{\"ok\": true}");
    }

    private void setStat(HttpExchange t, JsonObject b, String ip) throws Exception {
        String player = Http.str(b, "player");
        String mode = Http.str(b, "mode");
        if (mode == null) mode = "set";
        if (player == null || !Http.PLAYER_NAME.matcher(player).matches()) {
            send(t, 400, Http.error("Invalid 'player'"));
            return;
        }
        StatType type = StatType.parse(Http.str(b, "stat"));
        if (type == null) {
            StringBuilder allowed = new StringBuilder();
            for (StatType st : StatType.ALL) if (st.editable()) allowed.append(allowed.length() == 0 ? "" : ", ").append(st.key);
            send(t, 400, Http.error("Invalid 'stat'. Allowed: " + allowed));
            return;
        }
        if (!"set".equals(mode) && !"add".equals(mode)) {
            send(t, 400, Http.error("'mode' must be 'set' or 'add'"));
            return;
        }
        double value;
        try {
            value = b.get("value").getAsDouble();
        } catch (Exception e) {
            send(t, 400, Http.error("'value' must be a number"));
            return;
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            send(t, 400, Http.error("'value' must be a finite number"));
            return;
        }
        boolean add = "add".equals(mode);
        StatsModule.EditResult r = onMainThread(() -> stats.setStat(player, type, add, value));
        String desc = type.key + " " + mode + " " + value;
        if (r.code() != 200) {
            dash.logAdmin(ip, "stat", player + " " + desc + " -> " + r.error(), false);
            send(t, r.code(), Http.error(r.error()));
            return;
        }
        read.invalidate(player);
        dash.logAdmin(ip, "stat", r.player() + " " + desc + " = " + r.value(), true);
        JsonObject out = new JsonObject();
        out.addProperty("ok", true);
        out.addProperty("player", r.player());
        out.addProperty("stat", type.key);
        out.addProperty("value", r.value());
        send(t, 200, Http.GSON.toJson(out));
    }
}
