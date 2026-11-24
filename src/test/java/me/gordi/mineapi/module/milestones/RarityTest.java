package me.gordi.mineapi.module.milestones;

import org.junit.jupiter.api.Test;

import static me.gordi.mineapi.module.milestones.Rarity.Tier.*;
import static org.junit.jupiter.api.Assertions.*;

class RarityTest {

    @Test
    void tiersFollowThresholds() {
        Rarity.Thresholds t = Rarity.Thresholds.DEFAULT;
        assertEquals(COMMON, Rarity.tier(80, t));
        assertEquals(COMMON, Rarity.tier(50, t));
        assertEquals(UNCOMMON, Rarity.tier(49.9, t));
        assertEquals(RARE, Rarity.tier(10, t));
        assertEquals(EPIC, Rarity.tier(4, t));
        assertEquals(LEGENDARY, Rarity.tier(0.5, t));
        assertEquals(LEGENDARY, Rarity.tier(0, t));
    }

    @Test
    void percentIsRoundedAndSafe() {
        assertEquals(33.3, Rarity.percent(1, 3));
        assertEquals(100.0, Rarity.percent(4, 4));
        assertEquals(0.0, Rarity.percent(3, 0));
    }
}
