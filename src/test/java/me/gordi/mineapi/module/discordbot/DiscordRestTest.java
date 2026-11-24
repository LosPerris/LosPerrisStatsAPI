package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** DiscordRest contra un servidor HTTP local que imita la API de Discord. */
class DiscordRestTest {

    private static final String TOKEN = "MTIz.secret-token.xyz";

    private record Req(String method, String path, String auth, String agent, String body) {
    }

    private HttpServer server;
    private final List<Req> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger limited = new AtomicInteger();
    private DiscordRest rest;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        rest = new DiscordRest("http://127.0.0.1:" + server.getAddress().getPort() + "/api/v10/", TOKEN, "1.3-V2");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String path = ex.getRequestURI().getRawPath();
        requests.add(new Req(ex.getRequestMethod(), path, ex.getRequestHeaders().getFirst("Authorization"),
                ex.getRequestHeaders().getFirst("User-Agent"), body));
        int code = 200;
        String out = "{\"id\":\"1\"}";
        if (path.endsWith("/limited") && limited.getAndIncrement() < 1) {
            code = 429;
            out = "{\"retry_after\":0.05,\"global\":false}";
        } else if (path.endsWith("/always429")) {
            code = 429;
            out = "{\"retry_after\":0.01}";
        } else if (path.endsWith("/html")) {
            code = 502;
            out = "<html>bad gateway</html>";
        } else if (ex.getRequestMethod().equals("PUT") && path.contains("/roles/")) {
            code = 204;
            out = "";
        }
        byte[] b = out.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length == 0 ? -1 : b.length);
        if (b.length > 0) ex.getResponseBody().write(b);
        ex.close();
    }

    @Test
    void sendsAuthAndBlocksAllMentions() throws Exception {
        DiscordRest.Response r = rest.sendMessage("555", "hola @everyone");
        assertTrue(r.ok());
        Req q = requests.get(0);
        assertEquals("POST", q.method());
        assertEquals("/api/v10/channels/555/messages", q.path());
        assertEquals("Bot " + TOKEN, q.auth());
        assertTrue(q.agent().startsWith("DiscordBot ("));
        JsonObject body = JsonParser.parseString(q.body()).getAsJsonObject();
        assertEquals(new JsonArray(), body.getAsJsonObject("allowed_mentions").getAsJsonArray("parse"));
    }

    @Test
    void embedsAndInteractionRepliesAlsoBlockMentions() throws Exception {
        JsonObject embed = new JsonObject();
        embed.addProperty("title", "Top");
        rest.sendEmbed("555", embed);
        rest.reply("999", "tok/en", "hi", embed, true);
        JsonObject e = JsonParser.parseString(requests.get(0).body()).getAsJsonObject();
        assertTrue(e.has("allowed_mentions"));
        assertEquals("Top", e.getAsJsonArray("embeds").get(0).getAsJsonObject().get("title").getAsString());
        Req reply = requests.get(1);
        assertEquals("/api/v10/interactions/999/tok%2Fen/callback", reply.path());
        JsonObject data = JsonParser.parseString(reply.body()).getAsJsonObject().getAsJsonObject("data");
        assertEquals(64, data.get("flags").getAsInt());
        assertTrue(data.has("allowed_mentions"));
    }

    @Test
    void retriesAfter429() throws Exception {
        DiscordRest.Response r = rest.request("GET", "/limited", null);
        assertEquals(200, r.code());
        assertEquals(2, requests.size());
        DiscordRest.Response gaveUp = rest.request("GET", "/always429", null);
        assertEquals(429, gaveUp.code(), "tras 3 intentos devuelve el 429");
        assertEquals(5, requests.size());
    }

    @Test
    void nonJsonBodiesAndEmptyResponses() throws Exception {
        DiscordRest.Response r = rest.request("GET", "/html", null);
        assertEquals(502, r.code());
        assertNull(r.body());
        assertEquals(new JsonObject(), r.object());
        DiscordRest.Response role = rest.addRole("1", "2", "3");
        assertEquals(204, role.code());
        assertTrue(role.ok());
        assertEquals("/api/v10/guilds/1/members/2/roles/3", requests.get(1).path());
    }

    @Test
    void commandsGoToGuildOrGlobal() throws Exception {
        rest.registerCommands("app", "guild", new JsonArray());
        rest.registerCommands("app", "", new JsonArray());
        assertEquals("/api/v10/applications/app/guilds/guild/commands", requests.get(0).path());
        assertEquals("/api/v10/applications/app/commands", requests.get(1).path());
        assertEquals("PUT", requests.get(0).method());
    }

    @Test
    void tokenNeverInToString() {
        assertFalse(rest.toString().contains(TOKEN));
        assertFalse(rest.toString().contains("secret"));
    }

    @Test
    void retryAfterParsing() {
        assertEquals(1500, DiscordRest.retryAfter(JsonParser.parseString("{\"retry_after\":1.5}"), null));
        assertEquals(100, DiscordRest.retryAfter(JsonParser.parseString("{\"retry_after\":0}"), null));
        assertEquals(60_000, DiscordRest.retryAfter(JsonParser.parseString("{\"retry_after\":9999}"), null));
        assertEquals(2000, DiscordRest.retryAfter(null, "2"));
    }
}
