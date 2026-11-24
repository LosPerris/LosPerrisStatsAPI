package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/** GatewayClient contra un gateway simulado en local (sin red ni Discord real). */
class GatewayClientTest {

    private static final String TOKEN = "MTIz.gateway-secret.abc";

    private final List<String> events = new CopyOnWriteArrayList<>();
    private final List<String> logs = new CopyOnWriteArrayList<>();

    private final GatewayClient.Handler handler = new GatewayClient.Handler() {
        @Override
        public void onReady(JsonObject data) {
            events.add("READY:" + data.getAsJsonObject("user").get("id").getAsString());
        }

        @Override
        public void onDispatch(String type, JsonObject data) {
            events.add(type + ":" + data.get("content").getAsString());
        }
    };

    private static void await(BooleanSupplier cond, String what) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (!cond.getAsBoolean()) {
            if (System.currentTimeMillis() > end) fail("timeout: " + what);
            Thread.sleep(10);
        }
    }

    /** Siguiente mensaje del cliente que no sea un latido (el primero sale tras un retardo aleatorio). */
    private static JsonObject nextNonBeat(MockGateway gw) throws InterruptedException {
        long end = System.currentTimeMillis() + 5000;
        while (true) {
            JsonObject m = json(gw.next(Math.max(1, end - System.currentTimeMillis())));
            if (m.get("op").getAsInt() != 1) return m;
        }
    }

    private static JsonObject json(String s) {
        assertNotNull(s, "el cliente no envió nada");
        return JsonParser.parseString(s).getAsJsonObject();
    }

    @Test
    void identifiesDispatchesAndHeartbeats() throws Exception {
        try (MockGateway gw = new MockGateway()) {
            GatewayClient c = new GatewayClient(TOKEN, GatewayProtocol.intents(true), gw.url(), handler, logs::add);
            c.start();
            try {
                assertTrue(gw.awaitConnection(5000));
                gw.send("{\"op\":10,\"d\":{\"heartbeat_interval\":100}}");
                JsonObject identify = nextNonBeat(gw); // el primer latido puede llegar antes que IDENTIFY
                assertEquals(2, identify.get("op").getAsInt());
                assertEquals(TOKEN, identify.getAsJsonObject("d").get("token").getAsString());
                assertEquals(GatewayProtocol.intents(true), identify.getAsJsonObject("d").get("intents").getAsInt());

                gw.send("{\"op\":0,\"s\":1,\"t\":\"READY\",\"d\":{\"session_id\":\"S1\",\"user\":{\"id\":\"42\"}}}");
                gw.send("{\"op\":0,\"s\":2,\"t\":\"MESSAGE_CREATE\",\"d\":{\"content\":\"hola\"}}");
                await(() -> events.size() == 2, "dispatch");
                assertEquals(List.of("READY:42", "MESSAGE_CREATE:hola"), events);
                assertEquals(GatewayClient.Status.READY, c.status());

                // Pueden quedar en cola latidos enviados antes de recibir la secuencia 2
                JsonObject beat;
                do {
                    beat = json(gw.next(5000));
                } while (beat.get("op").getAsInt() != 1 || beat.get("d").isJsonNull() || beat.get("d").getAsLong() < 2);
                assertEquals(2, beat.get("d").getAsLong(), "el latido lleva la última secuencia");
                await(() -> c.ping() >= 0, "ping");
            } finally {
                c.stop();
            }
            assertEquals(GatewayClient.Status.STOPPED, c.status());
            assertTrue(logs.stream().noneMatch(l -> l.contains(TOKEN)), "el token nunca va al registro");
        }
    }

    @Test
    void invalidTokenStopsWithoutRetrying() throws Exception {
        try (MockGateway gw = new MockGateway()) {
            GatewayClient c = new GatewayClient(TOKEN, 1, gw.url(), handler, logs::add);
            c.start();
            try {
                assertTrue(gw.awaitConnection(5000));
                gw.send("{\"op\":10,\"d\":{\"heartbeat_interval\":45000}}");
                assertEquals(2, nextNonBeat(gw).get("op").getAsInt());
                gw.close(4004, "Authentication failed.");
                await(() -> c.status() == GatewayClient.Status.FAILED, "failed");
                assertTrue(c.failure().startsWith("4004"));
                assertFalse(gw.awaitConnection(1500), "no reconecta tras 4004");
                assertTrue(logs.stream().anyMatch(l -> l.contains("Token")));
                assertTrue(logs.stream().noneMatch(l -> l.contains(TOKEN)));
            } finally {
                c.stop();
            }
        }
    }

    @Test
    void resumesAfterReconnectRequest() throws Exception {
        try (MockGateway gw = new MockGateway()) {
            GatewayClient c = new GatewayClient(TOKEN, 1, gw.url(), handler, logs::add);
            c.start();
            try {
                assertTrue(gw.awaitConnection(5000));
                gw.send("{\"op\":10,\"d\":{\"heartbeat_interval\":45000}}");
                assertEquals(2, nextNonBeat(gw).get("op").getAsInt());
                gw.send("{\"op\":0,\"s\":5,\"t\":\"READY\",\"d\":{\"session_id\":\"S9\",\"user\":{\"id\":\"1\"}}}");
                await(() -> c.status() == GatewayClient.Status.READY, "ready");
                gw.send("{\"op\":7,\"d\":null}");
                assertTrue(gw.awaitConnection(5000), "reconecta");
                gw.send("{\"op\":10,\"d\":{\"heartbeat_interval\":45000}}");
                JsonObject resume = nextNonBeat(gw);
                assertEquals(6, resume.get("op").getAsInt());
                assertEquals("S9", resume.getAsJsonObject("d").get("session_id").getAsString());
                assertEquals(5, resume.getAsJsonObject("d").get("seq").getAsLong());
                gw.send("{\"op\":0,\"s\":6,\"t\":\"RESUMED\",\"d\":{}}");
                await(() -> c.status() == GatewayClient.Status.READY, "resumed");
            } finally {
                c.stop();
            }
        }
    }
}
