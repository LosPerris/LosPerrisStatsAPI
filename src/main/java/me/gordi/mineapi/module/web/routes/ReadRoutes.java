package me.gordi.mineapi.module.web.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.core.module.Module;
import me.gordi.mineapi.core.module.ModuleCatalog;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.api.WebRegistry;
import me.gordi.mineapi.core.data.UuidMigration;
import me.gordi.mineapi.module.milestones.MilestoneGallery;
import me.gordi.mineapi.module.discordbot.DiscordBotModule;
import me.gordi.mineapi.module.heatmap.HeatmapModule;
import me.gordi.mineapi.module.history.HistoryModule;
import me.gordi.mineapi.module.milestones.MilestonesModule;
import me.gordi.mineapi.module.periods.PeriodsModule;
import me.gordi.mineapi.module.stats.Leaderboards;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;
import me.gordi.mineapi.module.web.DashboardService;
import me.gordi.mineapi.module.web.http.ApiAuth;
import me.gordi.mineapi.module.web.http.Http;
import me.gordi.mineapi.module.web.http.SseHub;
import me.gordi.mineapi.module.web.http.WebServer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Endpoints de solo lectura (/api/...). Rutas y JSON idénticos a la versión anterior. */
public final class ReadRoutes {

    private static final long ALL_CACHE_MS = 5_000L;
    private static final long SUMMARY_CACHE_MS = 3_000L;
    private static final long OFFLINE_CACHE_MS = 60_000L;
    private static final int OFFLINE_CACHE_MAX = 500;
    /** Cada cuánto se rehace el índice nombre -> UUID de los jugadores que solo tiene el servidor. */
    private static final long VANILLA_INDEX_MS = 5 * 60_000L;

    private final MineAPI plugin;
    private final StatsModule stats;
    private final DashboardService dash;
    private final ApiAuth auth;
    private final SseHub sse;

    private volatile String allCache;
    private volatile long allCacheTime;
    private volatile String summaryCache;
    private volatile long summaryCacheTime;
    private volatile String publicSummaryCache;
    private volatile long publicSummaryCacheTime;
    private volatile String periodsCache;
    private volatile long periodsCacheTime;
    private final Map<String, Cached> offlineCache = new ConcurrentHashMap<>();
    /** Solo se usa en el hilo principal. Main thread only. */
    private Map<String, UUID> vanillaIndex = Map.of();
    private long vanillaIndexTime;

    private record Cached(int status, String body, long time) {
    }

    private final WebRegistry extensions;
    private WebRegistry.Sink sink;

    public ReadRoutes(MineAPI plugin, StatsModule stats, DashboardService dash, ApiAuth auth, SseHub sse) {
        this.plugin = plugin;
        this.extensions = plugin.api().webRegistry();
        this.stats = stats;
        this.dash = dash;
        this.auth = auth;
        this.sse = sse;
    }

    /**
     * Endpoints de lectura sin autenticación (ruta relativa a /api/ → handler). Los usa este
     * registro (con API key) y el módulo public (sin key, solo las secciones permitidas).
     */
    public Map<String, WebServer.Route> endpoints(boolean pub) {
        Map<String, WebServer.Route> m = new LinkedHashMap<>();
        m.put("players", this::players);
        m.put("stats", t -> stats(t, pub));
        m.put("top", t -> top(t, pub));
        m.put("summary", t -> summary(t, pub));
        m.put("profile", t -> profile(t, pub));
        m.put("players/list", this::list);
        m.put("periods", this::periods);
        m.put("achievements", this::achievements);
        m.put("player/history", this::playerHistory);
        m.put("heatmap", this::heatmap);
        return m;
    }

    public void register(WebServer web) {
        for (Map.Entry<String, WebServer.Route> e : endpoints(false).entrySet()) web.route("/api/" + e.getKey(), get(e.getValue()));
        web.route("/api/all", get(this::all))
                .route("/api/history", get(t -> ok(t, Http.GSON.toJson(dash.history(Http.longParam(Http.query(t), "since", 0))))))
                .route("/api/chat/history", get(t -> ok(t, Http.GSON.toJson(dash.chatHistory(Http.intParam(Http.query(t), "limit", 50, 1, 200))))))
                .route("/api/discord", get(this::discord))
                .route("/api/chat/stream", t -> {
                    if (auth.read(t)) sse.accept(t, auth.cors());
                });
        // endpoints de los addons (también los que se registren después)
        sink = new WebRegistry.Sink() {
            public void added(WebRegistry.ReadEntry e) { web.route("/api/" + e.path(), get(extension(e, false, auth.cors()))); }
            public void removed(WebRegistry.ReadEntry e) { web.unroute("/api/" + e.path()); }
        };
        extensions.attach(sink);
    }

    /** Suelta los endpoints de los addons (al apagar el módulo web). */
    public void detach() {
        if (sink != null) extensions.detach(sink);
        sink = null;
    }

    /** Ruta HTTP para un endpoint de addon ({@code pub}: página pública, sin key). */
    public WebServer.Route extension(WebRegistry.ReadEntry e, boolean pub, String cors) {
        return t -> {
            ApiRequest r = new ApiRequest(plugin, t, pub, null);
            var reply = e.handler().handle(r);
            Http.json(t, reply.status(), reply.json(), cors);
        };
    }

    /** Tras editar/resetear desde la administración. */
    public void invalidate(String player) {
        allCache = null;
        summaryCache = null;
        publicSummaryCache = null;
        periodsCache = null;
        if (player != null) offlineCache.remove(player.toLowerCase(Locale.ROOT));
    }

    // ------------------------------------------------------------------ utilidades

    private WebServer.Route get(WebServer.Route inner) {
        return t -> {
            if (!auth.read(t)) return;
            if (!"GET".equals(t.getRequestMethod())) {
                send(t, 405, "{\"error\": \"Method not allowed\"}");
                return;
            }
            inner.handle(t);
        };
    }

    private void send(HttpExchange t, int status, String body) throws IOException {
        Http.json(t, status, body, auth.cors());
    }

    private void ok(HttpExchange t, String body) throws IOException {
        send(t, 200, body);
    }

    /** Copia pública del jugador (sin IP ni marcas internas) o null. */
    private JsonObject publicSnapshot(String name) {
        JsonObject o = plugin.data().snapshot(name);
        if (o != null) {
            o.remove("ip_address");
            o.remove(UuidMigration.GUESSED_KEY);
        }
        return o;
    }

    private static boolean validName(String name) {
        return name != null && Http.PLAYER_NAME.matcher(name).matches();
    }

    // ------------------------------------------------------------------ endpoints

    private void players(HttpExchange t) throws IOException {
        if (dash.hasSnapshot()) {
            ok(t, Http.GSON.toJson(dash.online()));
            return;
        }
        JsonArray arr = new JsonArray(); // aún sin instantánea (primeros 2 s)
        for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
            JsonObject o = new JsonObject();
            o.addProperty("name", p.getName());
            o.addProperty("uuid", p.getUniqueId().toString());
            o.addProperty("skin", "https://minotar.net/helm/" + p.getName() + "/64.png");
            arr.add(o);
        }
        ok(t, Http.GSON.toJson(arr));
    }

    private void stats(HttpExchange t, boolean pub) throws Exception {
        String name = Http.query(t).get("name");
        if (name == null || name.isEmpty()) {
            send(t, 400, "{\"error\": \"Missing 'name' parameter\"}");
            return;
        }
        if (!validName(name)) {
            send(t, 400, "{\"error\": \"Invalid 'name' parameter\"}");
            return;
        }
        JsonObject cached = publicSnapshot(name);
        if (cached != null) {
            ok(t, Http.GSON.toJson(cached));
            return;
        }
        Cached c = offline(name);
        send(t, c.status, c.body);
    }

    private void all(HttpExchange t) throws IOException {
        long now = System.currentTimeMillis();
        String json = allCache;
        if (json == null || now - allCacheTime > ALL_CACHE_MS) {
            json = Http.GSON.toJson(plugin.data().nameKeyedSnapshot(true));
            allCache = json;
            allCacheTime = now;
        }
        ok(t, json);
    }

    private void top(HttpExchange t, boolean pub) throws IOException {
        Map<String, String> q = Http.query(t);
        int limit = Http.intParam(q, "limit", 10, 1, Leaderboards.MAX_ENTRIES);
        List<Leaderboards.Entry> sorted = stats.leaderboards().top(q.getOrDefault("stat", "mob_kills"));
        JsonArray out = new JsonArray();
        for (Leaderboards.Entry e : sorted.subList(0, Math.min(limit, sorted.size()))) {
            JsonObject o = publicSnapshot(e.name);
            if (o != null) out.add(o);
        }
        ok(t, Http.GSON.toJson(out));
    }

    private void summary(HttpExchange t, boolean pub) throws IOException {
        long now = System.currentTimeMillis();
        String json = pub ? publicSummaryCache : summaryCache;
        long time = pub ? publicSummaryCacheTime : summaryCacheTime;
        if (json == null || now - time > SUMMARY_CACHE_MS) {
            JsonObject out = stats.totals();
            JsonObject server = dash.server();
            if (pub) {
                // Página pública: sin datos internos del servidor
                for (String k : new String[]{"memory_used_mb", "memory_max_mb", "bukkit_version"}) server.remove(k);
            }
            out.add("server", server);
            JsonObject tops = new JsonObject();
            for (StatType type : StatType.ALL) {
                JsonArray arr = new JsonArray();
                List<Leaderboards.Entry> list = stats.leaderboards().top(type);
                for (Leaderboards.Entry e : list.subList(0, Math.min(5, list.size()))) {
                    JsonObject o = new JsonObject();
                    o.addProperty("name", e.name);
                    o.addProperty("value", e.value(type));
                    arr.add(o);
                }
                tops.add(type.board, arr);
            }
            out.add("top5", tops);
            out.addProperty("admin_enabled", !pub && auth.settings().adminUsable());
            if (!pub) out.addProperty("security_enabled", auth.settings().securityEnabled());
            out.addProperty("language", plugin.i18n().defaultLanguage());
            out.add("modules", modulesInfo());
            json = Http.GSON.toJson(out);
            if (pub) {
                publicSummaryCache = json;
                publicSummaryCacheTime = now;
            } else {
                summaryCache = json;
                summaryCacheTime = now;
            }
        }
        ok(t, json);
    }

    /** Módulos activos, del núcleo y de los addons (el dashboard oculta lo que no esté). */
    private JsonObject modulesInfo() {
        JsonObject m = new JsonObject();
        for (Module mod : plugin.modules().all()) m.addProperty(mod.id(), mod.isEnabled());
        // nombres antiguos (public, console...) para clientes de la API anteriores a v9 / old names for older clients
        ModuleCatalog.ALIASES.forEach((old, id) -> m.addProperty(old, plugin.modules().isEnabled(id)));
        for (Map.Entry<String, Boolean> f : extensions.flags()) m.addProperty(f.getKey(), f.getValue());
        return m;
    }

    private void disabled(HttpExchange t, String module) throws IOException {
        send(t, 404, Http.error("Module '" + module + "' disabled"));
    }

    private void periods(HttpExchange t) throws IOException {
        PeriodsModule pm = plugin.modules().get(PeriodsModule.class);
        if (pm == null || !pm.isEnabled()) {
            disabled(t, PeriodsModule.ID);
            return;
        }
        Map<String, String> q = Http.query(t);
        String name = q.get("name");
        int limit = Http.intParam(q, "limit", 10, 1, PeriodsModule.BOARD_SIZE);
        if (name != null && !name.isEmpty()) {
            if (!validName(name)) {
                send(t, 400, "{\"error\": \"Invalid 'name' parameter\"}");
                return;
            }
            ok(t, Http.GSON.toJson(pm.json(plugin.data().resolve(name), limit)));
            return;
        }
        long now = System.currentTimeMillis();
        if (limit == 10) {
            String json = periodsCache;
            if (json == null || now - periodsCacheTime > ALL_CACHE_MS) {
                json = Http.GSON.toJson(pm.json(null, 10));
                periodsCache = json;
                periodsCacheTime = now;
            }
            ok(t, json);
            return;
        }
        ok(t, Http.GSON.toJson(pm.json(null, limit)));
    }

    private void achievements(HttpExchange t) throws IOException {
        MilestonesModule mm = plugin.modules().get(MilestonesModule.class);
        MilestoneGallery am = mm == null ? null : mm.gallery();
        if (am == null) {
            disabled(t, MilestonesModule.ID);
            return;
        }
        String name = Http.query(t).get("name");
        if (name != null && !name.isEmpty() && !validName(name)) {
            send(t, 400, "{\"error\": \"Invalid 'name' parameter\"}");
            return;
        }
        ok(t, Http.GSON.toJson(am.json(name == null || name.isEmpty() ? null : name)));
    }

    private void playerHistory(HttpExchange t) throws IOException {
        HistoryModule hm = plugin.modules().get(HistoryModule.class);
        if (hm == null || !hm.isEnabled()) {
            disabled(t, HistoryModule.ID);
            return;
        }
        Map<String, String> q = Http.query(t);
        String name = q.get("name");
        if (!validName(name)) {
            send(t, 400, "{\"error\": \"Invalid 'name' parameter\"}");
            return;
        }
        UUID uuid = plugin.data().resolve(name);
        JsonObject h = uuid == null ? null : hm.history(uuid, Http.intParam(q, "days", 30, 1, 3650));
        if (h == null) {
            send(t, 404, "{\"error\": \"Player not found\"}");
            return;
        }
        ok(t, Http.GSON.toJson(h));
    }

    private void profile(HttpExchange t, boolean pub) throws Exception {
        String name = Http.query(t).get("name");
        if (!validName(name)) {
            send(t, 400, "{\"error\": \"Invalid 'name' parameter\"}");
            return;
        }
        JsonObject data = publicSnapshot(name);
        boolean inCache = data != null;
        if (data == null) {
            Cached c = offline(name);
            if (c.status != 200) {
                send(t, c.status, c.body);
                return;
            }
            data = JsonParser.parseString(c.body).getAsJsonObject();
        }
        JsonObject achieved = data.has("milestones") && data.get("milestones").isJsonObject()
                ? data.getAsJsonObject("milestones") : new JsonObject();
        data.remove("milestones");
        for (String hidden : extensions.hiddenKeys()) data.remove(hidden);

        String realName = data.has("name") ? data.get("name").getAsString() : name;
        JsonObject out = new JsonObject();
        out.addProperty("name", realName);
        out.addProperty("in_cache", inCache);
        out.add("stats", data);
        if (inCache) {
            JsonObject ranks = stats.ranks(realName);
            if (ranks != null) out.add("ranks", ranks);
        }
        MilestonesModule mm = plugin.modules().get(MilestonesModule.class);
        out.add("milestones", mm == null ? new JsonArray() : progress(mm.info(), data, achieved));
        // addons: campos extra (misiones...) y etiqueta del jugador (título activo)
        String lang = Http.query(t).get("lang");
        UUID uuid = plugin.data().resolve(realName);
        if (uuid != null) extensions.contribute(realName, uuid, lang, out, plugin.getLogger()::warning);
        String tag = plugin.api().playerTag(realName, lang);
        if (tag != null) out.addProperty("title", tag);
        JsonObject online = dash.onlineInfo(realName);
        out.addProperty("online", online != null);
        if (online != null) {
            if (pub) online.remove("ping");
            out.add("session", online);
        }
        ok(t, Http.GSON.toJson(out));
    }

    private void heatmap(HttpExchange t) throws IOException {
        HeatmapModule hm = plugin.modules().get(HeatmapModule.class);
        if (hm == null) {
            disabled(t, HeatmapModule.ID);
            return;
        }
        ok(t, Http.GSON.toJson(hm.toApi()));
    }

    /** Estado del bot de Discord (nunca incluye el token). Solo con API key. */
    private void discord(HttpExchange t) throws IOException {
        DiscordBotModule dm = plugin.modules().get(DiscordBotModule.class);
        if (dm == null) {
            disabled(t, DiscordBotModule.ID);
            return;
        }
        ok(t, Http.GSON.toJson(dm.status()));
    }


    /** Progreso hacia el siguiente hito de cada estadística. */
    static JsonArray progress(JsonObject info, JsonObject stats, JsonObject achieved) {
        JsonArray result = new JsonArray();
        for (Map.Entry<String, JsonElement> e : info.entrySet()) {
            String stat = e.getKey();
            double current = stats.has(stat) ? stats.get(stat).getAsDouble() : 0;
            int reached = achieved.has(stat) ? achieved.get(stat).getAsInt() : 0;
            JsonObject m = new JsonObject();
            m.addProperty("stat", stat);
            m.addProperty("current", current);
            m.addProperty("highest_reached", reached);
            JsonArray levels = new JsonArray();
            Integer next = null;
            int prev = 0;
            for (JsonElement le : e.getValue().getAsJsonArray()) {
                JsonObject lvl = le.getAsJsonObject();
                int value = lvl.get("value").getAsInt();
                boolean done = value <= reached || current >= value;
                lvl.addProperty("reached", done);
                if (!done && next == null) next = value;
                if (done) prev = value;
                levels.add(lvl);
            }
            m.add("levels", levels);
            if (next != null) {
                m.addProperty("next", next);
                double span = next - prev;
                m.addProperty("progress", span <= 0 ? 0 : Math.max(0, Math.min(1, (current - prev) / span)));
            } else {
                m.addProperty("progress", 1);
            }
            result.add(m);
        }
        return result;
    }

    private void list(HttpExchange t) throws IOException {
        Map<String, String> q = Http.query(t);
        String query = q.getOrDefault("q", "");
        if (query.length() > 16) query = query.substring(0, 16);
        String sort = q.getOrDefault("sort", "name");
        if (!"name".equals(sort) && !"last_updated".equals(sort) && StatType.parse(sort) == null) sort = "name";
        boolean desc = q.containsKey("order") ? "desc".equalsIgnoreCase(q.get("order")) : !"name".equals(sort);
        int size = Http.intParam(q, "size", 25, 1, 100);
        int page = Http.intParam(q, "page", 1, 1, 100_000);

        List<JsonObject> all = stats.list(query, sort, desc);
        JsonArray items = new JsonArray();
        int from = (page - 1) * size;
        for (int i = from; i < Math.min(all.size(), from + size); i++) {
            JsonObject o = all.get(i);
            o.addProperty("online", dash.isOnline(o.get("name").getAsString()));
            items.add(o);
        }
        JsonObject out = new JsonObject();
        out.addProperty("total", all.size());
        out.addProperty("page", page);
        out.addProperty("size", size);
        out.addProperty("pages", Math.max(1, (all.size() + size - 1) / size));
        out.add("items", items);
        ok(t, Http.GSON.toJson(out));
    }

    // ------------------------------------------------------------------ jugadores sin datos del plugin

    /** Stats vanilla de quien nunca pasó por la caché (hilo principal, caché de 60 s). */
    private Cached offline(String name) throws Exception {
        String key = name.toLowerCase(Locale.ROOT);
        long now = System.currentTimeMillis();
        Cached c = offlineCache.get(key);
        if (c != null && now - c.time <= OFFLINE_CACHE_MS) return c;
        if (offlineCache.size() > OFFLINE_CACHE_MAX) offlineCache.clear();
        JsonObject json = Bukkit.getScheduler().callSyncMethod(plugin, () -> readVanilla(name)).get(5, TimeUnit.SECONDS);
        c = json == null ? new Cached(404, "{\"error\": \"Player not found\"}", now) : new Cached(200, Http.GSON.toJson(json), now);
        offlineCache.put(key, c);
        return c;
    }

    /**
     * Hilo principal. El jugador se busca en el índice de nombres de los datos y, si no está, en un índice de los
     * jugadores del servidor que se rehace cada pocos minutos: antes se recorría getOfflinePlayers() en cada
     * consulta (getOfflinePlayer(String) puede consultar a Mojang). Main thread; indexed lookups, no per-request scan.
     */
    private JsonObject readVanilla(String name) {
        UUID uuid = plugin.data().resolve(name);
        if (uuid == null) uuid = vanillaUuid(name);
        if (uuid == null) return null;
        OfflinePlayer target = Bukkit.getOfflinePlayer(uuid);
        if (target.getName() == null) return null;
        String real = target.getName();
        JsonObject json = new JsonObject();
        json.addProperty("name", real);
        json.addProperty("deaths", target.getStatistic(Statistic.DEATHS));
        json.addProperty("player_kills", target.getStatistic(Statistic.PLAYER_KILLS));
        json.addProperty("mob_kills", target.getStatistic(Statistic.MOB_KILLS));
        json.addProperty("hours_played", Math.round(target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20.0 / 3600.0 * 100.0) / 100.0);
        try {
            json.addProperty("jumps", target.getStatistic(Statistic.JUMP));
            json.addProperty("fish_caught", target.getStatistic(Statistic.FISH_CAUGHT));
            json.addProperty("villager_trades", target.getStatistic(Statistic.TRADED_WITH_VILLAGER));
            json.addProperty("damage_dealt", Math.round(target.getStatistic(Statistic.DAMAGE_DEALT) / 2.0) / 10.0);
            json.addProperty("damage_taken", Math.round(target.getStatistic(Statistic.DAMAGE_TAKEN) / 2.0) / 10.0);
        } catch (IllegalArgumentException ignored) {
            // estadística no disponible en esta versión
        }
        json.addProperty("skin", "https://minotar.net/helm/" + real + "/100.png");
        json.addProperty("skin_body", "https://minotar.net/armor/body/" + real + "/100.png");
        return json;
    }

    private UUID vanillaUuid(String name) {
        long now = System.currentTimeMillis();
        if (now - vanillaIndexTime > VANILLA_INDEX_MS) {
            Map<String, UUID> index = new HashMap<>();
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null) index.put(op.getName().toLowerCase(Locale.ROOT), op.getUniqueId());
            }
            vanillaIndex = index;
            vanillaIndexTime = now;
        }
        return vanillaIndex.get(name.toLowerCase(Locale.ROOT));
    }
}
