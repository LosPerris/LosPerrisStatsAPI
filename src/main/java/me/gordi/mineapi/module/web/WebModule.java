package me.gordi.mineapi.module.web;

import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.api.WebRegistry;
import me.gordi.mineapi.core.event.MilestoneReachedEvent;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.module.stats.StatsModule;
import me.gordi.mineapi.module.web.http.ApiAuth;
import me.gordi.mineapi.module.web.http.Http;
import me.gordi.mineapi.module.web.http.SseHub;
import me.gordi.mineapi.module.web.http.StaticFiles;
import me.gordi.mineapi.module.web.http.WebServer;
import me.gordi.mineapi.module.web.routes.AdminRoutes;
import me.gordi.mineapi.module.web.routes.ReadRoutes;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * API HTTP + dashboard web. Si el puerto no se puede abrir solo se desactiva este módulo; el
 * resto del plugin sigue funcionando. /lpa reload reinicia el servidor web (aplica puerto, key...).
 */
public final class WebModule extends AbstractModule implements Listener {

    public static final String ID = "web";

    // Sobreviven a /lpa reload (la instancia del módulo es la misma)
    private final long startedAt = System.currentTimeMillis();
    private final Map<UUID, Long> joinTimes = new ConcurrentHashMap<>();
    private final Deque<JsonObject> chatHistory = new ArrayDeque<>();
    private final Deque<JsonObject> adminLog = new ArrayDeque<>();

    private WebServer server;
    private SseHub sse;
    private DashboardService dash;
    private ReadRoutes read;
    private ApiAuth auth;

    public WebModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() throws IOException {
        ensureApiKey();
        FileConfiguration c = config();
        auth = new ApiAuth(new ApiAuth.Settings(
                c.getBoolean("security.enabled", true),
                c.getString("security.api-key", "").trim(),
                c.getBoolean("security.allow-query-key", true),
                c.getString("security.cors-origin", "*"),
                c.getBoolean("admin-enabled", true),
                c.getBoolean("admin-allow-cors", false)), log(), plugin.debug());

        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) joinTimes.putIfAbsent(p.getUniqueId(), now);
        sse = new SseHub(Math.max(1, c.getInt("max-chat-clients", 20)));
        int historyPoints = Math.max(10, Math.min(10_000, c.getInt("history-max-points", 288)));
        dash = new DashboardService(plugin, startedAt, joinTimes, chatHistory, adminLog, historyPoints, sse::publish);
        if (c.getBoolean("audit-log", true)) {
            dash.setAudit(new AuditLog(new File(plugin.getDataFolder(), "logs/web-audit.log"), 1_048_576L, log()));
        }

        StatsModule stats = plugin.modules().get(StatsModule.class);
        int port = c.getInt("port", 25908);
        try {
            server = new WebServer(c.getString("bind-address", "0.0.0.0"), port,
                    Math.max(2, c.getInt("threads", 8)), log(), plugin.debug());
        } catch (IOException e) {
            throw new IOException("no se pudo abrir el puerto " + port + " (revise 'port' y 'web.bind-address'): " + e.getMessage(), e);
        }
        read = new ReadRoutes(plugin, stats, dash, auth, sse);
        read.register(server);
        new AdminRoutes(plugin, stats, dash, auth, read).register(server);
        server.route("/dashboard", t -> {
                    // conserva ?public (página pública) al redirigir
                    t.getResponseHeaders().set("Location", "/web/dashboard.html" + keepQuery(t));
                    t.sendResponseHeaders(302, -1);
                })
                .route("/web", new StaticFiles(plugin))
                .route("/", t -> {
                    // "/" -> dashboard; cualquier otra ruta desconocida -> 404 JSON
                    String m = t.getRequestMethod();
                    if ("/".equals(t.getRequestURI().getPath()) && ("GET".equals(m) || "HEAD".equals(m))) {
                        t.getResponseHeaders().set("Location", "/dashboard" + keepQuery(t));
                        t.sendResponseHeaders(302, -1);
                    } else {
                        Http.json(t, 404, "{\"error\": \"Not found\"}", null);
                    }
                });
        server.start();
        DashboardService d = dash;
        ReadRoutes r = read;
        plugin.api().webRegistry().live(new WebRegistry.Live() {
            public void notify(String kind, String user, String message, String level) { d.notify(kind, user, message, level); }
            public void invalidate(String player) { r.invalidate(player); }
        });

        listen(this);
        subscribe(MilestoneReachedEvent.class, e -> dash.notify("milestone", e.player().getName(), e.message(), "info"));
        syncTimer("snapshot", 1L, 40L, dash::takeSnapshot);
        long historyTicks = Math.max(1, c.getInt("history-interval-minutes", 5)) * 60L * 20L;
        syncTimer("history", 200L, historyTicks, dash::sampleHistory);
        log().info("Servidor web iniciado en el puerto " + port);
    }

    @Override
    protected void onDisable() {
        plugin.api().webRegistry().live(null);
        if (read != null) read.detach();
        if (server != null) server.stop();
        if (sse != null) sse.shutdown();
        if (dash != null) dash.saveHistory();
        server = null;
        sse = null;
        dash = null;
        read = null;
        auth = null;
    }

    /** Autenticación de la API (null si el módulo está desactivado). La usan consola y moderación. */
    public ApiAuth auth() {
        return auth;
    }

    /** Servidor HTTP en marcha (null si el módulo está desactivado). Lo usa el módulo public. */
    public WebServer server() {
        return server;
    }

    /** Endpoints de lectura reutilizables (null si el módulo está desactivado). */
    public ReadRoutes readRoutes() {
        return read;
    }

    /** Servicio del dashboard (null si el módulo está desactivado). */
    public DashboardService dashboard() {
        return dash;
    }

    // ------------------------------------------------------------------ eventos del chat en vivo

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        joinTimes.put(p.getUniqueId(), System.currentTimeMillis());
        dash.publish("join", p.getName(), p.getUniqueId().toString(), i18n().trDefault("web.events.join"));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        joinTimes.remove(p.getUniqueId());
        dash.publish("quit", p.getName(), p.getUniqueId().toString(), i18n().trDefault("web.events.quit"));
    }

    @SuppressWarnings("deprecation") // AsyncPlayerChatEvent: compatible con Spigot y Paper
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        DashboardService d = dash;
        if (d != null) d.publish("chat", e.getPlayer().getName(), e.getPlayer().getUniqueId().toString(), e.getMessage());
    }

    // ------------------------------------------------------------------ API key

    /**
     * Seguridad activada y key vacía o de ejemplo: genera una, la guarda en settings/web.yml (sin perder
     * comentarios) y la muestra en consola.
     */
    private void ensureApiKey() {
        FileConfiguration c = config();
        if (!c.getBoolean("security.enabled", true)) {
            log().warning("════════════════════════════════════════════════════");
            log().warning(" PELIGRO: security.enabled = false en settings/web.yml.");
            log().warning(" Cualquiera que conozca la IP y el puerto puede leer");
            log().warning(" las estadísticas y el chat. Active la seguridad.");
            log().warning("════════════════════════════════════════════════════");
            return;
        }
        String key = c.getString("security.api-key", "");
        if (!ApiKeys.needsKey(key)) {
            if (key.trim().length() < 16) {
                log().warning("La API key de settings/web.yml → security.api-key es muy corta (" + key.trim().length() + " caracteres). Use al menos 16, o déjela vacía para generar una.");
            }
            return;
        }
        String generated = ApiKeys.generate();
        boolean written;
        try {
            // solo cambia la línea api-key de la settings/web.yml (conserva comentarios)
            written = plugin.configs().editModule(ID, block -> ApiKeys.replaceKey(block, generated));
        } catch (IOException e) {
            log().warning("No se pudo editar settings/web.yml directamente: " + e.getMessage());
            written = false;
        }
        c.set("security.api-key", generated);
        if (!written) plugin.configs().saveModule(ID);
        log().warning("════════════════════════════════════════════════════");
        log().warning(" Se generó una API key nueva y se guardó en settings/web.yml → security.api-key:");
        log().warning("   " + generated);
        log().warning(" Úsela en el dashboard o con el header:");
        log().warning("   Authorization: Bearer <key>");
        log().warning("════════════════════════════════════════════════════");
    }

    /** "?public" y similares: solo letras, números, = y & (nada que inyecte una cabecera). */
    private static String keepQuery(com.sun.net.httpserver.HttpExchange t) {
        String q = t.getRequestURI().getRawQuery();
        return q == null || q.isEmpty() || !q.matches("[A-Za-z0-9_=&%.-]{1,64}") ? "" : "?" + q;
    }
}
