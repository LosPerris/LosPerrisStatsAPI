package me.gordi.mineapi.module.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatsMenuBarTest {

    @Test
    void barIsClampedTo20Segments() {
        assertEquals("&a" + "|".repeat(10) + "&8" + "|".repeat(10) + " &750%", StatsMenu.bar(0.5));
        assertEquals("&a&8" + "|".repeat(20) + " &70%", StatsMenu.bar(0));
        assertTrue(StatsMenu.bar(1.7).startsWith("&a" + "|".repeat(20) + "&8 "));
        assertTrue(StatsMenu.bar(-1).startsWith("&a&8" + "|".repeat(20)));
    }
}
