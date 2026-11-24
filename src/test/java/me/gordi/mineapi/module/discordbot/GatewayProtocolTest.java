package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewayProtocolTest {

    @Test
    void parsesFrames() {
        GatewayProtocol.Frame f = GatewayProtocol.parse("{\"op\":0,\"s\":42,\"t\":\"MESSAGE_CREATE\",\"d\":{\"content\":\"secret\"}}");
        assertEquals(0, f.op());
        assertEquals(42L, f.seq());
        assertEquals("MESSAGE_CREATE", f.type());
        assertEquals("secret", f.object().get("content").getAsString());
        assertEquals("op=0 t=MESSAGE_CREATE s=42", GatewayProtocol.describe(f));
        GatewayProtocol.Frame hello = GatewayProtocol.parse("{\"op\":10,\"s\":null,\"t\":null,\"d\":{\"heartbeat_interval\":41250}}");
        assertNull(hello.seq());
        assertNull(hello.type());
        assertEquals("op=10", GatewayProtocol.describe(hello));
        assertEquals(new JsonObject(), GatewayProtocol.parse("{\"op\":11}").object());
    }

    @Test
    void buildsPayloads() {
        assertEquals("{\"op\":1,\"d\":null}", GatewayProtocol.heartbeat(null));
        assertEquals("{\"op\":1,\"d\":7}", GatewayProtocol.heartbeat(7L));
        JsonObject id = JsonParser.parseString(GatewayProtocol.identify("T", 513)).getAsJsonObject();
        assertEquals(2, id.get("op").getAsInt());
        assertEquals(513, id.getAsJsonObject("d").get("intents").getAsInt());
        JsonObject r = JsonParser.parseString(GatewayProtocol.resume("T", "sess", 9)).getAsJsonObject();
        assertEquals(6, r.get("op").getAsInt());
        assertEquals("sess", r.getAsJsonObject("d").get("session_id").getAsString());
    }

    @Test
    void intentsAndCloseCodes() {
        assertEquals(1, GatewayProtocol.intents(false));
        assertEquals(1 | 512 | 32768, GatewayProtocol.intents(true));
        assertTrue(GatewayProtocol.fatalClose(4004));
        assertTrue(GatewayProtocol.fatalClose(4014));
        assertFalse(GatewayProtocol.fatalClose(4000));
        assertFalse(GatewayProtocol.fatalClose(1000));
    }

    @Test
    void urls() {
        assertEquals("wss://gateway.discord.gg/?v=10&encoding=json", GatewayProtocol.url(null));
        assertEquals("wss://x.gg/?v=10&encoding=json", GatewayProtocol.url("wss://x.gg/"));
        assertEquals("wss://x.gg/?a=1&v=10&encoding=json", GatewayProtocol.url("wss://x.gg/?a=1"));
        assertEquals("wss://x.gg/?v=10&encoding=json", GatewayProtocol.url("wss://x.gg/?v=10&encoding=json"));
    }
}
