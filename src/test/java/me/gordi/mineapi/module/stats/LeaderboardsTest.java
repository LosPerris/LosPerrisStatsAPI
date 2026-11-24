package me.gordi.mineapi.module.stats;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LeaderboardsTest {

    private static Leaderboards.Entry e(String name, double deaths, long updated) {
        return Leaderboards.row(JsonParser.parseString(
                "{\"name\":\"" + name + "\",\"deaths\":" + deaths + ",\"last_updated\":" + updated + "}").getAsJsonObject());
    }

    @Test
    void sortsDedupesAndCaps() {
        List<Leaderboards.Entry> rows = new ArrayList<>();
        rows.add(e("Alex", 5, 1));
        rows.add(e("Steve", 1, 1));
        rows.add(e("steve", 9, 2)); // más reciente: gana
        for (int i = 0; i < 150; i++) rows.add(e("p" + i, 0, 0));
        Map<StatType, List<Leaderboards.Entry>> top = Leaderboards.build(rows);
        List<Leaderboards.Entry> deaths = top.get(StatType.DEATHS);
        assertEquals(Leaderboards.MAX_ENTRIES, deaths.size());
        assertEquals("steve", deaths.get(0).name);
        assertEquals(9, deaths.get(0).value(StatType.DEATHS));
        assertEquals("Alex", deaths.get(1).name);
        assertEquals(StatType.ALL.length, top.size());
    }

    @Test
    void missingNameIsSafe() {
        assertEquals("?", Leaderboards.row(JsonParser.parseString("{}").getAsJsonObject()).name);
    }
}
