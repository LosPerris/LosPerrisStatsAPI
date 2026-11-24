package me.gordi.mineapi.core.time;

import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ZonesTest {

    @Test
    void parsesOrFallsBackToSystem() {
        assertEquals(ZoneId.of("America/Costa_Rica"), Zones.of("America/Costa_Rica"));
        assertEquals(ZoneId.systemDefault(), Zones.of(""));
        assertEquals(ZoneId.systemDefault(), Zones.of(null));
        assertEquals(ZoneId.systemDefault(), Zones.of("Mars/Olympus"));
    }
}
