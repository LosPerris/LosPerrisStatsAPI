package me.gordi.mineapi.module.web.http;

import com.sun.net.httpserver.HttpExchange;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

/** Sirve los archivos de {@code web/} dentro del JAR (solo lectura, sin recorrer rutas). */
public final class StaticFiles implements WebServer.Route {

    /** CSP del dashboard (también va en un meta, pero frame-ancestors solo funciona como cabecera). */
    private static final String DASHBOARD_CSP = "default-src 'self'; script-src 'self'; style-src 'self'; "
            + "img-src 'self' data: https://minotar.net; connect-src 'self'; object-src 'none'; "
            + "base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    private static final Map<String, String> TYPES = Map.ofEntries(
            Map.entry("html", "text/html; charset=UTF-8"),
            Map.entry("css", "text/css; charset=UTF-8"),
            Map.entry("js", "application/javascript; charset=UTF-8"),
            Map.entry("json", "application/json; charset=UTF-8"),
            Map.entry("png", "image/png"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("webp", "image/webp"),
            Map.entry("svg", "image/svg+xml"),
            Map.entry("ico", "image/x-icon"),
            Map.entry("woff2", "font/woff2"));

    private final JavaPlugin plugin;

    public StaticFiles(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    static String contentType(String fileName) {
        int dot = fileName.lastIndexOf('.');
        String ext = dot < 0 ? "" : fileName.substring(dot + 1);
        return TYPES.getOrDefault(ext, "application/octet-stream");
    }

    /** Nombre seguro dentro de web/ o null. */
    static String safeName(String path) {
        String name = path.startsWith("/web/") ? path.substring(5) : "";
        if (name.isEmpty() || name.contains("..") || name.startsWith("/") || name.contains("\\")) return null;
        return name;
    }

    @Override
    public void handle(HttpExchange t) throws IOException {
        if (!"GET".equals(t.getRequestMethod())) {
            Http.json(t, 405, "{\"error\": \"Method not allowed\"}", null);
            return;
        }
        String name = safeName(t.getRequestURI().getPath());
        try (InputStream is = name == null ? null : plugin.getResource("web/" + name)) {
            if (is == null) {
                Http.json(t, 404, "{\"error\": \"File not found\"}", null);
                return;
            }
            String type = contentType(name);
            var h = t.getResponseHeaders();
            h.set("Content-Type", type);
            h.set("X-Content-Type-Options", "nosniff");
            h.set("Referrer-Policy", "no-referrer");
            if (name.endsWith(".html")) {
                h.set("X-Frame-Options", "DENY");
                h.set("Content-Security-Policy", DASHBOARD_CSP);
            }
            if (type.startsWith("image/")) {
                h.set("Cache-Control", "public, max-age=86400");
            } else if (type.startsWith("text/") || type.startsWith("application/javascript")) {
                // Revalidar siempre: tras actualizar el plugin no se usan JS/CSS antiguos
                h.set("Cache-Control", "no-cache");
            }
            t.sendResponseHeaders(200, 0);
            try (OutputStream os = t.getResponseBody()) {
                is.transferTo(os);
            }
        }
    }
}
