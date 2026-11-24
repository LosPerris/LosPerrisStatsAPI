package me.gordi.mineapi.module.web.http;

import com.sun.net.httpserver.HttpExchange;
import me.gordi.mineapi.core.log.DebugLog;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Autenticación de la API: lectura (header Bearer o ?key=) y administración (solo header,
 * con límite de intentos fallidos por IP).
 */
public final class ApiAuth {

    private static final int ADMIN_MAX_FAILURES = 10;
    private static final long ADMIN_FAILURE_WINDOW_MS = 5 * 60_000L;

    /** Ajustes de seguridad leídos de settings/web.yml al activar el módulo. */
    public record Settings(boolean securityEnabled, String apiKey, boolean allowQueryKey, String corsOrigin,
                           boolean adminEnabled, boolean adminAllowCors) {
        public boolean adminUsable() {
            return adminEnabled && securityEnabled && !apiKey.isEmpty();
        }
    }

    private final Settings s;
    private final Logger log;
    private final DebugLog debug;
    private final Map<String, long[]> adminFailures = new ConcurrentHashMap<>();

    public ApiAuth(Settings settings, Logger log, DebugLog debug) {
        this.s = settings;
        this.log = log;
        this.debug = debug;
    }

    public Settings settings() {
        return s;
    }

    public String cors() {
        return s.corsOrigin();
    }

    /** Origen CORS para respuestas admin (null = sin cabecera). */
    public String adminCors() {
        return s.adminAllowCors() ? s.corsOrigin() : null;
    }

    private static void preflight(HttpExchange t, String origin) throws IOException {
        if (origin != null) {
            t.getResponseHeaders().set("Access-Control-Allow-Origin", origin);
            t.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            t.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
            t.getResponseHeaders().set("Access-Control-Max-Age", "600");
        }
        t.sendResponseHeaders(204, -1);
        t.close();
    }

    private boolean bearerOk(HttpExchange t) {
        String h = t.getRequestHeaders().getFirst("Authorization");
        return h != null && h.startsWith("Bearer ") && Http.constantTimeEquals(h.substring(7).trim(), s.apiKey());
    }

    /** Lectura. @return true si puede seguir (si no, ya se respondió). */
    public boolean read(HttpExchange t) throws IOException {
        if ("OPTIONS".equals(t.getRequestMethod())) {
            preflight(t, s.corsOrigin());
            return false;
        }
        if (!s.securityEnabled()) return true;
        if (s.apiKey().isEmpty()) {
            Http.json(t, 401, "{\"error\": \"Unauthorized: API key not configured\"}", s.corsOrigin());
            return false;
        }
        if (bearerOk(t)) return true;
        if (s.allowQueryKey()) {
            // EventSource (chat) no puede enviar cabeceras: se admite ?key=
            String q = Http.query(t).get("key");
            if (q != null && Http.constantTimeEquals(q, s.apiKey())) return true;
        }
        debug.log(DebugLog.Category.SECURITY, "Acceso denegado desde " + t.getRemoteAddress());
        Http.json(t, 401, "{\"error\": \"Unauthorized: Invalid or missing API Key\"}", s.corsOrigin());
        return false;
    }

    /** Administración: solo header (la key nunca va en la URL para escrituras). */
    public boolean admin(HttpExchange t) throws IOException {
        if ("OPTIONS".equals(t.getRequestMethod())) {
            preflight(t, adminCors());
            return false;
        }
        if (!s.adminEnabled()) {
            Http.json(t, 403, Http.error("Admin API disabled (web.admin-enabled: false)"), adminCors());
            return false;
        }
        if (!s.securityEnabled() || s.apiKey().isEmpty()) {
            Http.json(t, 403, Http.error("Admin API requires security.enabled: true and an api-key"), adminCors());
            return false;
        }
        String ip = Http.remoteIp(t);
        long now = System.currentTimeMillis();
        long[] fail = adminFailures.get(ip);
        if (fail != null && now - fail[1] > ADMIN_FAILURE_WINDOW_MS) {
            adminFailures.remove(ip);
            fail = null;
        }
        if (fail != null && fail[0] >= ADMIN_MAX_FAILURES) {
            Http.json(t, 429, Http.error("Too many failed attempts, try again later"), adminCors());
            return false;
        }
        if (bearerOk(t)) {
            adminFailures.remove(ip);
            return true;
        }
        if (adminFailures.size() > 10_000) adminFailures.clear();
        adminFailures.compute(ip, (k, v) -> {
            if (v == null) return new long[]{1, now};
            v[0]++;
            return v;
        });
        log.warning("[WebAdmin] Acceso denegado desde " + ip + " a " + t.getRequestURI().getPath());
        Http.json(t, 401, Http.error("Unauthorized: admin actions require the header 'Authorization: Bearer <key>'"), adminCors());
        return false;
    }
}
