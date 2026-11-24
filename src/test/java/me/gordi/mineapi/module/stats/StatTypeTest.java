package me.gordi.mineapi.module.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StatTypeTest {

    @Test
    void parsesKeysBoardsTokensAndAliases() {
        assertEquals(StatType.HOURS_PLAYED, StatType.parse("playtime"));
        assertEquals(StatType.HOURS_PLAYED, StatType.parse(" Hours "));
        assertEquals(StatType.HOURS_PLAYED, StatType.parse("time"));
        assertEquals(StatType.PLAYER_KILLS, StatType.parse("kills"));
        assertEquals(StatType.MOB_KILLS, StatType.parse("mobs"));
        assertEquals(StatType.DISTANCE_WALKED, StatType.parse("distance"));
        assertNull(StatType.parse("nope"));
        assertNull(StatType.parse(null));
    }

    @Test
    void conversionsRoundTrip() {
        assertEquals(1.0, StatType.HOURS_PLAYED.fromRaw(72_000));
        assertEquals(72_000, StatType.HOURS_PLAYED.toRaw(1.0));
        assertEquals(1.5, StatType.DISTANCE_WALKED.fromRaw(150_000));
        assertEquals(0.01, StatType.HOURS_PLAYED.stored(1));
        assertEquals(0.0, StatType.HOURS_PLAYED.stored(0));
        assertTrue(StatType.HOURS_PLAYED.toRaw(StatType.HOURS_PLAYED.maxValue()) > 0, "sin desbordar int");
        assertTrue(StatType.DISTANCE_WALKED.toRaw(StatType.DISTANCE_WALKED.maxValue()) > 0, "sin desbordar int");
    }

    @Test
    void formats() {
        assertEquals("3.50h", StatType.HOURS_PLAYED.format(3.5));
        assertEquals("1.20km", StatType.DISTANCE_WALKED.format(1.2));
        assertEquals("12", StatType.JUMPS.format(12.9));
    }

    @Test
    void newStatsParse() {
        assertEquals(StatType.VILLAGER_TRADES, StatType.parse("trades"));
        assertEquals(StatType.FISH_CAUGHT, StatType.parse("fish"));
        assertEquals(StatType.BLOCKS_PLACED, StatType.parse("placed"));
        assertEquals(StatType.ORES_MINED, StatType.parse("ores"));
        assertEquals(StatType.DAMAGE_DEALT, StatType.parse("damage"));
        assertEquals(StatType.DAMAGE_TAKEN, StatType.parse("damage_taken"));
    }

    @Test
    void tokensMatchTheVariableRegistry() {
        java.util.List<String> tokens = new java.util.ArrayList<>();
        for (StatType t : StatType.ALL) tokens.add(t.token);
        assertEquals(tokens, me.gordi.mineapi.core.text.Variables.statTokens(),
                "Cada StatType debe estar en Variables.STATS (y en el mismo orden)");
    }
}
