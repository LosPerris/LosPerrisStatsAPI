package me.gordi.mineapi.module.discordbot;

import me.gordi.mineapi.module.stats.StatType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class RoleRulesTest {

    private static Map<String, Object> map(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }

    @Test
    void parsesAndWarnsOnInvalidRules() {
        List<String> warn = new ArrayList<>();
        RoleRules r = RoleRules.parse("100000000000000001",
                map("kills:100", "200000000000000002", "time:10", 300000000000000003L, "nope:5", "1234567", "kills:x", "1234567", "kills:5", "not-a-role"),
                map("kills:1", "400000000000000004", "kills:0", "1234567", "kills:101", "1234567"), warn::add);
        assertEquals(5, warn.size(), warn.toString());
        assertEquals(Set.of("100000000000000001", "200000000000000002", "300000000000000003", "400000000000000004"), r.managed());
        assertTrue(r.usesTop());
        assertEquals(EnumSet.of(StatType.PLAYER_KILLS), r.topStats());
        assertFalse(r.isEmpty());
        assertTrue(RoleRules.parse("", null, null, warn::add).isEmpty());
    }

    @Test
    void desiredRoles() {
        RoleRules r = RoleRules.parse("1000001", map("kills:100", "2000002"), map("kills:3", "3000003"), w -> fail(w));
        double[] v = new double[StatType.ALL.length];
        int[] ranks = new int[StatType.ALL.length];
        assertEquals(Set.of("1000001"), r.desired(v, ranks));
        v[StatType.PLAYER_KILLS.ordinal()] = 100;
        ranks[StatType.PLAYER_KILLS.ordinal()] = 3;
        assertEquals(Set.of("1000001", "2000002", "3000003"), r.desired(v, ranks));
        ranks[StatType.PLAYER_KILLS.ordinal()] = 4;
        assertEquals(Set.of("1000001", "2000002"), r.desired(v, ranks));
    }

    @Test
    void ranksShareTiesAndSkipZero() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        int k = StatType.PLAYER_KILLS.ordinal();
        Map<UUID, double[]> values = Map.of(a, vals(k, 50), b, vals(k, 80), c, vals(k, 50), d, vals(k, 0));
        Map<UUID, int[]> r = RoleRules.ranks(values, EnumSet.of(StatType.PLAYER_KILLS));
        assertEquals(1, r.get(b)[k]);
        assertEquals(2, r.get(a)[k]);
        assertEquals(2, r.get(c)[k]);
        assertEquals(0, r.get(d)[k]);
        assertEquals(0, r.get(b)[StatType.DEATHS.ordinal()], "estadística no pedida");
    }

    private static double[] vals(int i, double v) {
        double[] out = new double[StatType.ALL.length];
        out[i] = v;
        return out;
    }
}
