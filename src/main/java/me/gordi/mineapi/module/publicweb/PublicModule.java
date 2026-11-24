package me.gordi.mineapi.module.publicweb;

import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.api.WebRegistry;
import me.gordi.mineapi.module.web.routes.ReadRoutes;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.module.web.WebModule;
import me.gordi.mineapi.module.web.http.Http;
import me.gordi.mineapi.module.web.http.WebServer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Página y API públicas de solo lectura en {@code /api/public/*} (sin API key), con secciones
 * configurables y límite de peticiones por IP. La administración, el chat y /api/all nunca son
 * públicos. Desactivado por defecto ({@code modules.public.enabled: false}).
 */
public final class PublicModule extends AbstractModule {

    public static final String ID = "public-page";
    public static final String BASE = "/api/public/";

    /** Sección de config → endpoints que abre. */
    private static final Map<String, List<String>> SECTIONS = new LinkedHashMap<>();

    static {
        SECTIONS.put("summary", List.of("summary", "players"));
        SECTIONS.put("leaderboards", List.of("top"));
        SECTIONS.put("players", List.of("players/list"));
        SECTIONS.put("profiles", List.of("profile", "stats"));
        SECTIONS.put("periods", List.of("periods"));
        SECTIONS.put("achievements", List.of("achievements"));
        SECTIONS.put("history", List.of("player/history"));
    }

    private final List<String> routed = new ArrayList<>();
    /** Secciones de los addons (seasons, events, titles...) → abiertas o no. */
    private final Map<String, Boolean> extraSections = new java.util.concurrent.ConcurrentHashMap<>();
    private WebRegistry.Sink sink;
    private RateLimiter limiter;
    private String cors;

    public PublicModule(MineAPI plugin) {
        super(plugin, ID, WebModule.ID);
    }

    /** Nombres de sección válidos (config y documentación). */
    public static List<String> sections() {
        return List.copyOf(SECTIONS.keySet());
    }

    @Override
    protected void onEnable() {
        WebModule web = plugin.modules().get(WebModule.class);
        WebServer server = web.server();
        limiter = new RateLimiter(config().getInt("rate-limit", 60));
        cors = config().getString("cors-origin", "*");
        Map<String, WebServer.Route> handlers = web.readRoutes().endpoints(true);

        JsonObject open = new JsonObject();
        for (Map.Entry<String, List<String>> s : SECTIONS.entrySet()) {
            boolean on = config().getBoolean("sections." + s.getKey(), true);
            open.addProperty(s.getKey(), on);
            if (!on) continue;
            for (String path : s.getValue()) {
                String full = BASE + path;
                server.route(full, guard(handlers.get(path)));
                routed.add(full);
            }
        }
        server.route(BASE + "info", guard(t -> Http.json(t, 200, info(open), cors)));
        routed.add(BASE + "info");

        // endpoints de los addons con sección pública (sections.<sección>, true si no está)
        ReadRoutes read = web.readRoutes();
        sink = new WebRegistry.Sink() {
            public void added(WebRegistry.ReadEntry e) {
                if (e.section() == null) return;
                boolean on = config().getBoolean("sections." + e.section(), true);
                extraSections.put(e.section(), on);
                if (on) server.route(BASE + e.path(), guard(read.extension(e, true, cors)));
            }

            public void removed(WebRegistry.ReadEntry e) {
                if (e.section() == null) return;
                server.unroute(BASE + e.path());
                if (plugin.api().webRegistry().reads().stream().noneMatch(o -> e.section().equals(o.section()))) {
                    extraSections.remove(e.section());
                }
            }
        };
        plugin.api().webRegistry().attach(sink);
        log().info("Página pública activa: /web/dashboard.html?public (" + (routed.size() - 1) + " endpoints)");
    }

    @Override
    protected void onDisable() {
        if (sink != null) plugin.api().webRegistry().detach(sink);
        sink = null;
        WebModule web = plugin.modules().get(WebModule.class);
        if (web != null && web.server() != null) {
            for (String r : routed) web.server().unroute(r);
            for (WebRegistry.ReadEntry e : plugin.api().webRegistry().reads()) {
                if (e.section() != null) web.server().unroute(BASE + e.path());
            }
        }
        routed.clear();
        extraSections.clear();
        limiter = null;
    }

    /** {@code /api/public/info}: título, idioma y secciones abiertas (también las de los addons). */
    private String info(JsonObject core) {
        JsonObject sections = core.deepCopy();
        extraSections.forEach(sections::addProperty);
        JsonObject info = new JsonObject();
        info.addProperty("public", true);
        info.addProperty("language", plugin.i18n().defaultLanguage());
        info.addProperty("title", config().getString("title", ""));
        info.add("sections", sections);
        return Http.GSON.toJson(info);
    }

    /** GET sin key + límite por IP. */
    private WebServer.Route guard(WebServer.Route inner) {
        RateLimiter rl = limiter;
        return t -> {
            String m = t.getRequestMethod();
            if ("OPTIONS".equals(m)) {
                t.getResponseHeaders().set("Access-Control-Allow-Origin", cors);
                t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                t.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equals(m)) {
                Http.json(t, 405, "{\"error\": \"Method not allowed\"}", cors);
                return;
            }
            String ip = Http.remoteIp(t);
            long now = System.currentTimeMillis();
            if (!rl.allow(ip, now)) {
                t.getResponseHeaders().set("Retry-After", String.valueOf(rl.retryAfter(ip, now)));
                Http.json(t, 429, Http.error("Too many requests"), cors);
                return;
            }
            inner.handle(t);
        };
    }

}
