package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

class SlashCommandsTest {

    private static final BiFunction<String, String, String> TR = (lang, key) -> lang + ":" + key;

    private static List<String> names(JsonArray a) {
        List<String> out = new ArrayList<>();
        for (JsonElement e : a) out.add(e.getAsJsonObject().get("name").getAsString());
        return out;
    }

    @Test
    void commandSetDependsOnMode() {
        assertEquals(List.of("stats", "top", "online", "panel"), names(SlashCommands.build(TR, "es", false, null)));
        assertEquals(List.of("stats", "top", "online", "panel", "staff"), names(SlashCommands.build(TR, "es", true, null)));
    }

    @Test
    void includesCustomCommands() {
        CustomCommands custom = new CustomCommands(List.of(Map.of(
                "name", "ip",
                "description", "Server IP",
                "reply", "play.example.com")));
        List<String> n = names(SlashCommands.build(TR, "es", false, custom));
        assertTrue(n.contains("panel"));
        assertTrue(n.contains("ip"));
        assertFalse(n.contains("link"));
    }

    @Test
    void localizedAndWithinDiscordLimits() {
        JsonArray all = SlashCommands.build((l, k) -> "x".repeat(300), "en", true, null);
        for (JsonElement e : all) {
            JsonObject c = e.getAsJsonObject();
            assertTrue(c.get("name").getAsString().matches("[a-z]{1,32}"));
            assertEquals(100, c.get("description").getAsString().length());
            assertTrue(c.getAsJsonObject("description_localizations").has("es-ES"));
            assertTrue(c.getAsJsonObject("description_localizations").has("en-US"));
        }
        JsonObject top = SlashCommands.build(TR, "es", false, null).get(1).getAsJsonObject();
        JsonArray choices = top.getAsJsonArray("options").get(0).getAsJsonObject().getAsJsonArray("choices");
        assertTrue(choices.size() > 0 && choices.size() <= 25);
        assertEquals("es:stats.names.player_kills", choices.get(0).getAsJsonObject().get("name").getAsString());
        assertEquals("kills", choices.get(0).getAsJsonObject().get("value").getAsString());
    }

    @Test
    void readsInteractionOptions() {
        JsonObject data = JsonParser.parseString("{\"options\":[{\"name\":\"player\",\"type\":3,\"value\":\"Ana\"}]}").getAsJsonObject();
        assertEquals("Ana", SlashCommands.option(data, "player"));
        assertNull(SlashCommands.option(data, "stat"));
        assertNull(SlashCommands.option(new JsonObject(), "player"));
        assertEquals("es_ES", SlashCommands.clientLocale(JsonParser.parseString("{\"locale\":\"es-ES\"}").getAsJsonObject()));
        assertNull(SlashCommands.clientLocale(new JsonObject()));
    }

    @Test
    void progressBar() {
        assertTrue(ServerPanel.bar(7, 40).contains("17%") || ServerPanel.bar(7, 40).contains("18%"));
        assertEquals("`A` `B`", ServerPanel.joinNames(List.of("A", "B"), 10));
    }
}
