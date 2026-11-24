package me.gordi.mineapi.core.time;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DurationsTest {

    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(30_000L, Durations.parse("30s"));
        assertEquals(10 * 60_000L, Durations.parse("10m"));
        assertEquals(10 * 60_000L, Durations.parse("10"), "número suelto = minutos");
        assertEquals(2 * 3_600_000L + 30 * 60_000L, Durations.parse("2h30m"));
        assertEquals(86_400_000L + 3_600_000L, Durations.parse(" 1d 1h "));
        assertEquals(2 * 604_800_000L, Durations.parse("2W"));
    }

    @Test
    void permanentAndInvalid() {
        for (String p : new String[]{null, "", "0", "perm", "permanente", "Permanent"}) {
            assertEquals(Durations.PERMANENT, Durations.parse(p), String.valueOf(p));
        }
        for (String bad : new String[]{"h", "5x", "-5m", "1y", "0m", "99999999m"}) {
            assertThrows(IllegalArgumentException.class, () -> Durations.parse(bad), bad);
        }
    }

    @Test
    void formats() {
        assertEquals("∞", Durations.format(0));
        assertEquals("45s", Durations.format(45_000));
        assertEquals("1h 5m", Durations.format(3_900_000));
        assertEquals("1d 2h", Durations.format(93_600_000));
        assertEquals("0s", Durations.format(500));
    }

    @Test
    void compactAndOldNumbers() throws Exception {
        assertEquals("5m", Durations.compact(300));
        assertEquals("2h", Durations.compact(7200));
        assertEquals("90s", Durations.compact(90));
        assertEquals("1d", Durations.compact(86400));
        org.bukkit.configuration.file.YamlConfiguration y = new org.bukkit.configuration.file.YamlConfiguration();
        y.loadFromString("a: 10\nb: \"10m\"\nc: 0.5\nd: \"basura\"\n");
        assertEquals(600, Durations.seconds(y, -1, 60, "a"), "número solo: unidad antigua de la clave");
        assertEquals(600, Durations.seconds(y, -1, 1, "b"));
        assertEquals(1800, Durations.seconds(y, -1, 3600, "c"), "decimales: 0.5 h");
        assertEquals(-1, Durations.seconds(y, -1, 1, "d"));
        assertEquals(600, Durations.seconds(y, -1, 60, "nueva", "a"), "nombre antiguo como alias");
    }
}
