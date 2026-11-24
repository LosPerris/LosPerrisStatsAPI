package me.gordi.mineapi.module.web.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Utilidades HTTP sin estado (respuestas JSON, query, cuerpo). */
public final class Http {

    /** Gson sin escapado HTML: emojis y Unicode salen como UTF-8 real. */
    public static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    /** Nombres de jugador válidos de Minecraft. */
    public static final Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    public static final int MAX_BODY = 8192;

    private Http() {
    }

    /** Respuesta JSON. {@code corsOrigin} null = sin cabecera CORS. */
    public static void json(HttpExchange t, int status, String body, String corsOrigin) throws IOException {
        t.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        if (corsOrigin != null) t.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        t.getResponseHeaders().set("Cache-Control", "no-store");
        t.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = t.getResponseBody()) {
            os.write(bytes);
        }
    }

    /** Descarga de texto (CSV...). {@code fileName} va en Content-Disposition. */
    public static void download(HttpExchange t, String contentType, String fileName, String body, String corsOrigin) throws IOException {
        t.getResponseHeaders().set("Content-Type", contentType);
        t.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + fileName.replaceAll("[^A-Za-z0-9._-]", "_") + "\"");
        if (corsOrigin != null) t.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        t.getResponseHeaders().set("Cache-Control", "no-store");
        t.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        t.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = t.getResponseBody()) {
            os.write(bytes);
        }
    }

    public static String error(String message) {
        JsonObject o = new JsonObject();
        o.addProperty("error", message);
        return GSON.toJson(o);
    }

    /** Parsea y decodifica la query (el primer valor de cada clave gana). */
    public static Map<String, String> query(HttpExchange t) {
        return parseQuery(t.getRequestURI().getRawQuery());
    }

    public static Map<String, String> parseQuery(String raw) {
        Map<String, String> params = new HashMap<>();
        if (raw == null || raw.isEmpty()) return params;
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int eq = pair.indexOf('=');
            String k = eq >= 0 ? pair.substring(0, eq) : pair;
            String v = eq >= 0 ? pair.substring(eq + 1) : "";
            try {
                k = URLDecoder.decode(k, StandardCharsets.UTF_8);
                v = URLDecoder.decode(v, StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // mal codificado: se usa tal cual
            }
            params.putIfAbsent(k, v);
        }
        return params;
    }

    public static int intParam(Map<String, String> params, String key, int def, int min, int max) {
        int v = def;
        String raw = params.get(key);
        if (raw != null) {
            try {
                v = Integer.parseInt(raw.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return Math.max(min, Math.min(max, v));
    }

    public static long longParam(Map<String, String> params, String key, long def) {
        String raw = params.get(key);
        if (raw == null) return def;
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException e) {
            return def;
        }
    }

    /** Comparación en tiempo constante (no filtra la key por tiempos de respuesta). */
    public static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }

    public static String remoteIp(HttpExchange t) {
        InetSocketAddress a = t.getRemoteAddress();
        return a == null || a.getAddress() == null ? "?" : a.getAddress().getHostAddress();
    }

    /** Resultado de leer un cuerpo JSON: {@code body} o el error a devolver. */
    public record Body(JsonObject body, int status, String error) {
        public boolean ok() { return body != null; }
    }

    /** Lee un cuerpo JSON de como máximo {@link #MAX_BODY} bytes. */
    public static Body readJson(HttpExchange t) throws IOException {
        String ct = t.getRequestHeaders().getFirst("Content-Type");
        if (ct == null || !ct.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            return new Body(null, 415, "Content-Type must be application/json");
        }
        byte[] buf = new byte[MAX_BODY + 1];
        int total = 0;
        try (InputStream is = t.getRequestBody()) {
            int r;
            while (total < buf.length && (r = is.read(buf, total, buf.length - total)) != -1) total += r;
        }
        if (total > MAX_BODY) return new Body(null, 413, "Body too large");
        if (total == 0) return new Body(new JsonObject(), 200, null);
        try {
            JsonElement el = JsonParser.parseString(new String(buf, 0, total, StandardCharsets.UTF_8));
            if (!el.isJsonObject()) throw new IllegalArgumentException();
            return new Body(el.getAsJsonObject(), 200, null);
        } catch (Exception e) {
            return new Body(null, 400, "Invalid JSON body");
        }
    }

    /** Campo de texto o null. */
    public static String str(JsonObject body, String key) {
        if (!body.has(key) || body.get(key).isJsonNull()) return null;
        try {
            return body.get(key).getAsString();
        } catch (Exception e) {
            return null;
        }
    }
}
