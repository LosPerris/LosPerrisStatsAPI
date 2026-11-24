package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MentionsTest {

    @Test
    void gameTextCannotPingAnyone() {
        String s = Mentions.toDiscord("hola @everyone y @here <@123456789> <@&987654321> <#11111111>", 0);
        assertFalse(s.contains("@everyone"));
        assertFalse(s.contains("@here"));
        assertFalse(s.contains("<@"));
        assertFalse(s.contains("<#"));
        assertTrue(s.replace("\u200B", "").contains("@everyone"), "el texto sigue siendo legible");
        assertEquals("", Mentions.toDiscord(null, 10));
    }

    @Test
    void cutsLongText() {
        String s = Mentions.toDiscord("x".repeat(50), 10);
        assertEquals(10, s.length());
        assertTrue(s.endsWith("…"));
    }

    @Test
    void discordTextIsReadableAndClean() {
        JsonArray users = JsonParser.parseString("[{\"id\":\"123456789\",\"username\":\"ana\",\"global_name\":\"Ana\"}]").getAsJsonArray();
        String s = Mentions.toMinecraft("hi <@123456789> <@!555555555> <@&987654321> <#11111111> <:pepe:22222222> §cred &l\nnext\u0007", users, 0);
        assertEquals("hi @Ana @user @role #channel :pepe: red  next", s);
    }

    @Test
    void displayNamePrefersNick() {
        JsonObject user = JsonParser.parseString("{\"username\":\"u\",\"global_name\":\"G\"}").getAsJsonObject();
        JsonObject member = JsonParser.parseString("{\"nick\":\"N\"}").getAsJsonObject();
        assertEquals("N", Mentions.displayName(user, member));
        assertEquals("G", Mentions.displayName(user, new JsonObject()));
        assertEquals("u", Mentions.displayName(JsonParser.parseString("{\"username\":\"u\",\"global_name\":null}").getAsJsonObject(), null));
        assertEquals("?", Mentions.displayName(null, null));
    }
}
