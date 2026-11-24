package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.gordi.mineapi.module.discordwebhook.DiscordWebhookModule;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Cliente REST Discord (HttpClient JDK). Bloqueante: llamar fuera del hilo principal.
 * Respeta 429 hasta 3 intentos. El token solo va en Authorization (nunca en logs ni {@code toString}).
 */
public final class DiscordRest {

    public record Response(int code, JsonElement body) {
        public boolean ok() {
            return code >= 200 && code < 300;
        }

        public JsonObject object() {
            return body != null && body.isJsonObject() ? body.getAsJsonObject() : new JsonObject();
        }
    }

    private static final int MAX_ATTEMPTS = 3;
    private final HttpClient http;
    private final String base;
    private final String auth;
    private final String agent;

    public DiscordRest(String apiUrl, String token, String version) {
        this.base = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
        this.auth = "Bot " + token;
        this.agent = "DiscordBot (https://github.com/LosPerris/LosPerrisStatsAPI, " + version + ")";
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public Response request(String method, String path, JsonElement body) throws IOException, InterruptedException {
        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", auth)
                    .header("User-Agent", agent);
            if (body != null) {
                b.header("Content-Type", "application/json");
                b.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
            } else {
                b.method(method, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r;
            try {
                r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            } catch (IOException e) {
                last = e;
                continue;
            }
            JsonElement json = null;
            String text = r.body();
            if (text != null && !text.isBlank()) {
                try {
                    json = JsonParser.parseString(text);
                } catch (RuntimeException ignored) {
                    // cuerpo no JSON (proxy, HTML de error...)
                }
            }
            if (r.statusCode() == 429 && attempt < MAX_ATTEMPTS) {
                Thread.sleep(retryAfter(json, r.headers().firstValue("Retry-After").orElse(null)));
                continue;
            }
            return new Response(r.statusCode(), json);
        }
        throw last != null ? last : new IOException("rate limited");
    }

    static long retryAfter(JsonElement body, String header) {
        if (body != null && body.isJsonObject() && body.getAsJsonObject().has("retry_after")) {
            try {
                double s = body.getAsJsonObject().get("retry_after").getAsDouble();
                return Math.min(60_000L, Math.max(100L, (long) (s * 1000)));
            } catch (RuntimeException ignored) {
                // se usa la cabecera
            }
        }
        return DiscordWebhookModule.retryAfterMs(header);
    }

    public static JsonObject noMentions() {
        JsonObject am = new JsonObject();
        am.add("parse", new JsonArray());
        return am;
    }

    public Response me() throws IOException, InterruptedException {
        return request("GET", "/users/@me", null);
    }

    public Response sendMessage(String channel, String content) throws IOException, InterruptedException {
        JsonObject o = new JsonObject();
        o.addProperty("content", content);
        o.add("allowed_mentions", noMentions());
        return request("POST", "/channels/" + channel + "/messages", o);
    }

    public Response sendEmbed(String channel, JsonObject embed) throws IOException, InterruptedException {
        return sendChannel(channel, null, embed, null);
    }

    public Response sendChannel(String channel, String content, JsonObject embed, JsonArray components)
            throws IOException, InterruptedException {
        return request("POST", "/channels/" + channel + "/messages", messageBody(content, embed, components));
    }

    public Response editMessage(String channel, String messageId, String content, JsonObject embed, JsonArray components)
            throws IOException, InterruptedException {
        return request("PATCH", "/channels/" + channel + "/messages/" + messageId, messageBody(content, embed, components));
    }

    public Response reply(String id, String token, String content, JsonObject embed, boolean ephemeral)
            throws IOException, InterruptedException {
        return reply(id, token, content, embed, null, ephemeral);
    }

    /** Callback tipo 4 (respuesta nueva). */
    public Response reply(String id, String token, String content, JsonObject embed, JsonArray components, boolean ephemeral)
            throws IOException, InterruptedException {
        JsonObject data = messageBody(content, embed, components);
        if (ephemeral) data.addProperty("flags", 64);
        JsonObject o = new JsonObject();
        o.addProperty("type", 4);
        o.add("data", data);
        return request("POST", "/interactions/" + id + "/" + enc(token) + "/callback", o);
    }

    /** Callback tipo 7 (edita el mensaje del botón; panel público). */
    public Response updateMessage(String id, String token, String content, JsonObject embed, JsonArray components)
            throws IOException, InterruptedException {
        JsonObject o = new JsonObject();
        o.addProperty("type", 7);
        o.add("data", messageBody(content, embed, components));
        return request("POST", "/interactions/" + id + "/" + enc(token) + "/callback", o);
    }

    private static JsonObject messageBody(String content, JsonObject embed, JsonArray components) {
        JsonObject data = new JsonObject();
        if (content != null) data.addProperty("content", content);
        if (embed != null) {
            JsonArray arr = new JsonArray();
            arr.add(embed);
            data.add("embeds", arr);
        }
        if (components != null) data.add("components", components);
        data.add("allowed_mentions", noMentions());
        return data;
    }

    public Response registerCommands(String appId, String guild, JsonArray commands) throws IOException, InterruptedException {
        String path = guild == null || guild.isBlank() ? "/applications/" + appId + "/commands"
                : "/applications/" + appId + "/guilds/" + guild + "/commands";
        return request("PUT", path, commands);
    }

    public Response addRole(String guild, String user, String role) throws IOException, InterruptedException {
        return request("PUT", "/guilds/" + guild + "/members/" + user + "/roles/" + role, null);
    }

    public Response removeRole(String guild, String user, String role) throws IOException, InterruptedException {
        return request("DELETE", "/guilds/" + guild + "/members/" + user + "/roles/" + role, null);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "DiscordRest[" + base + "]";
    }
}
