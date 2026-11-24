package me.gordi.mineapi.module.stats;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DeathCauseTest {

    @Test
    void categorizesCommonCauses() {
        assertEquals("player", DeathCause.categorize("ENTITY_ATTACK", true, true));
        assertEquals("player", DeathCause.categorize("FALL", true, false), "el asesino jugador manda");
        assertEquals("mob", DeathCause.categorize("ENTITY_ATTACK", false, true));
        assertEquals("mob", DeathCause.categorize("projectile", false, true));
        assertEquals("fall", DeathCause.categorize("FALL", false, false));
        assertEquals("lava", DeathCause.categorize("LAVA", false, false));
        assertEquals("fire", DeathCause.categorize("FIRE_TICK", false, false));
        assertEquals("explosion", DeathCause.categorize("ENTITY_EXPLOSION", false, true));
        assertEquals("void", DeathCause.categorize("VOID", false, false));
        assertEquals("magic", DeathCause.categorize("WITHER", false, false));
        assertEquals("other", DeathCause.categorize("SOMETHING_NEW_1_22", false, false));
        assertEquals("other", DeathCause.categorize(null, false, false));
    }

    @Test
    void everyResultIsAKnownCategory() {
        List<String> cats = Arrays.asList(DeathCause.CATEGORIES);
        for (String c : new String[]{"ENTITY_ATTACK", "FALL", "LAVA", "FIRE", "DROWNING", "BLOCK_EXPLOSION", "VOID",
                "SUFFOCATION", "STARVATION", "MAGIC", "FREEZE", "CONTACT", "LIGHTNING", "CUSTOM"}) {
            assertTrue(cats.contains(DeathCause.categorize(c, false, false)), c);
            assertTrue(cats.contains(DeathCause.categorize(c, false, true)), c);
        }
    }
}
