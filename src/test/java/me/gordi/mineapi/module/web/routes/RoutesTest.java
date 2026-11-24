package me.gordi.mineapi.module.web.routes;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RoutesTest {

    @Test
    void adminActionNormalizesPath() {
        assertEquals("/", AdminRoutes.action("/api/admin"));
        assertEquals("/", AdminRoutes.action("/api/admin/"));
        assertEquals("/stat", AdminRoutes.action("/api/admin/stat/"));
        assertEquals("/leaderboard/refresh", AdminRoutes.action("/api/admin/leaderboard/refresh"));
    }

    @Test
    void milestoneProgress() {
        JsonObject info = JsonParser.parseString("{\"deaths\":[{\"value\":10},{\"value\":20}],\"jumps\":[{\"value\":5}]}").getAsJsonObject();
        JsonObject stats = JsonParser.parseString("{\"deaths\":15,\"jumps\":7}").getAsJsonObject();
        JsonArray out = ReadRoutes.progress(info, stats, new JsonObject());
        JsonObject deaths = out.get(0).getAsJsonObject();
        assertEquals(20, deaths.get("next").getAsInt());
        assertEquals(0.5, deaths.get("progress").getAsDouble(), 1e-9);
        JsonObject jumps = out.get(1).getAsJsonObject();
        assertFalse(jumps.has("next"));
        assertEquals(1, jumps.get("progress").getAsDouble());
    }
}
