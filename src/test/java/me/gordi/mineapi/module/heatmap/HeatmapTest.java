package me.gordi.mineapi.module.heatmap;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;

class HeatmapTest {

    private static final ZoneId Z = ZoneId.of("UTC");

    private static long at(int y, int mo, int d, int h) {
        return ZonedDateTime.of(y, mo, d, h, 0, 0, 0, Z).toInstant().toEpochMilli();
    }

    @Test
    void cellsAndWeeks() {
        ZonedDateTime thu = ZonedDateTime.of(2026, 10, 29, 21, 0, 0, 0, Z);
        assertEquals(3 * 24 + 21, Heatmap.cell(thu));
        assertEquals("2026-10-26", Heatmap.weekId(thu));
        assertEquals(0, Heatmap.cell(ZonedDateTime.of(2026, 10, 26, 0, 0, 0, 0, Z)));
    }

    @Test
    void averagesPeakAndRoundTrip() {
        Heatmap h = new Heatmap(4);
        h.sample(at(2026, 10, 29, 21), Z, 10);
        h.sample(at(2026, 10, 29, 21), Z, 4);
        h.sample(at(2026, 11, 5, 21), Z, 7);   // otra semana, misma casilla
        h.sample(at(2026, 10, 26, 8), Z, 2);
        double[][] avg = h.averages();
        assertEquals(7.0, avg[3][21]);
        assertEquals(2.0, avg[0][8]);
        assertEquals(-1.0, avg[6][0]);
        JsonObject api = h.toApi();
        assertEquals(7.0, api.get("max").getAsDouble());
        assertEquals(3, api.getAsJsonObject("peak").get("day").getAsInt());
        assertEquals(21, api.getAsJsonObject("peak").get("hour").getAsInt());
        assertEquals(2, api.get("weeks").getAsInt());

        Heatmap copy = new Heatmap(4);
        copy.load(h.toJson());
        assertArrayEquals(avg[3], copy.averages()[3]);
        assertEquals(2, copy.weekCount());
    }

    @Test
    void keepsOnlyRecentWeeks() {
        Heatmap h = new Heatmap(2);
        h.sample(at(2026, 10, 5, 1), Z, 1);
        h.sample(at(2026, 10, 12, 1), Z, 2);
        h.sample(at(2026, 10, 19, 1), Z, 3);
        assertEquals(2, h.weekCount());
        assertEquals(2.5, h.averages()[0][1]);
        h.load(null);
        assertEquals(0, h.weekCount());
    }
}
