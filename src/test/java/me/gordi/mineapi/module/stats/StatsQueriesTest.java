package me.gordi.mineapi.module.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StatsQueriesTest {

    private static final long NOW = 100L * 86_400_000L;
    private static final String[] LABELS = {"a", "b", "c", "d"};

    private static StatsQueries.Row row(String name, double hours, double kills, long updated) {
        double[] v = new double[StatType.ALL.length];
        v[StatType.HOURS_PLAYED.ordinal()] = hours;
        v[StatType.PLAYER_KILLS.ordinal()] = kills;
        return new StatsQueries.Row(name, null, v, updated);
    }

    private final List<StatsQueries.Row> rows = List.of(
            row("Alex", 0.5, 10, NOW - 1000),
            row("Steve", 30, 2, NOW - 3L * 86_400_000L),
            row("steve", 1, 1, 0), // duplicado (no cuenta dos veces)
            row("Bob", 200, 5, NOW - 60L * 86_400_000L));

    @Test
    void totalsAndBuckets() {
        JsonObject t = StatsQueries.totals(rows, NOW, LABELS);
        assertEquals(3, t.get("players_tracked").getAsInt());
        assertEquals(17, t.getAsJsonObject("totals").get("player_kills").getAsDouble());
        assertEquals(200, t.getAsJsonObject("max").get("hours_played").getAsDouble());
        JsonArray act = t.getAsJsonArray("activity");
        assertEquals("24h", act.get(0).getAsJsonObject().get("key").getAsString());
        assertEquals("a", act.get(0).getAsJsonObject().get("label").getAsString());
        int[] counts = new int[4];
        for (int i = 0; i < 4; i++) counts[i] = act.get(i).getAsJsonObject().get("count").getAsInt();
        assertArrayEquals(new int[]{1, 1, 0, 1}, counts);
        JsonArray hours = t.getAsJsonArray("hours_distribution");
        assertEquals(1, hours.get(0).getAsJsonObject().get("count").getAsInt()); // <1 h
        assertEquals(1, hours.get(4).getAsJsonObject().get("count").getAsInt()); // 25-50 h
        assertEquals(1, hours.get(6).getAsJsonObject().get("count").getAsInt()); // 100+ h
    }

    @Test
    void ranks() {
        JsonObject r = StatsQueries.ranks(rows, "alex");
        assertEquals(1, r.get("player_kills").getAsInt());
        assertEquals(4, r.get("hours_played").getAsInt());
        assertEquals(0, r.get("jumps").getAsInt()); // valor 0 = sin clasificar
        assertNull(StatsQueries.ranks(rows, "nadie"));
    }

    @Test
    void listFiltersAndSorts() {
        List<JsonObject> l = StatsQueries.list(rows, "STE", "name", false);
        assertEquals(2, l.size());
        List<JsonObject> byKills = StatsQueries.list(rows, "", "kills", true);
        assertEquals("Alex", byKills.get(0).get("name").getAsString());
        List<JsonObject> byLast = StatsQueries.list(rows, null, "last_updated", true);
        assertEquals("Alex", byLast.get(0).get("name").getAsString());
    }
}
