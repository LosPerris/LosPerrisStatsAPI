package me.gordi.mineapi.module.periods;

import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class PeriodMathTest {

    private static final LocalDate THU = LocalDate.of(2026, 10, 29);

    @Test
    void weeklyAndMonthlyBoundaries() {
        assertEquals(LocalDate.of(2026, 10, 26), PeriodMath.startDay(PeriodKind.WEEKLY, THU, DayOfWeek.MONDAY));
        assertEquals(LocalDate.of(2026, 10, 25), PeriodMath.startDay(PeriodKind.WEEKLY, THU, DayOfWeek.SUNDAY));
        assertEquals(LocalDate.of(2026, 10, 1), PeriodMath.startDay(PeriodKind.MONTHLY, THU, DayOfWeek.MONDAY));
        assertEquals("2026-10-26", PeriodMath.id(PeriodKind.WEEKLY, THU, DayOfWeek.MONDAY));
        assertEquals("2026-10", PeriodMath.id(PeriodKind.MONTHLY, THU, DayOfWeek.MONDAY));
        // el lunes mismo pertenece a la semana que empieza ese día
        assertEquals("2026-10-26", PeriodMath.id(PeriodKind.WEEKLY, LocalDate.of(2026, 10, 26), DayOfWeek.MONDAY));
    }

    @Test
    void endIsStartOfNextPeriodInZone() {
        ZoneId cr = ZoneId.of("America/Costa_Rica");
        long start = PeriodMath.startMillis(PeriodKind.WEEKLY, THU, DayOfWeek.MONDAY, cr);
        long end = PeriodMath.endMillis(PeriodKind.WEEKLY, THU, DayOfWeek.MONDAY, cr);
        assertEquals(7L * 24 * 3600 * 1000, end - start);
        assertEquals(LocalDate.of(2026, 12, 1).atStartOfDay(cr).toInstant().toEpochMilli(),
                PeriodMath.endMillis(PeriodKind.MONTHLY, LocalDate.of(2026, 11, 30), DayOfWeek.MONDAY, cr));
    }

    @Test
    void weekStartParsing() {
        assertEquals(DayOfWeek.SUNDAY, PeriodMath.weekStart(" sunday "));
        assertEquals(DayOfWeek.MONDAY, PeriodMath.weekStart("lunes"));
        assertEquals(DayOfWeek.MONDAY, PeriodMath.weekStart(null));
    }

    @Test
    void kindParsingAcceptsSpanish() {
        assertEquals(PeriodKind.WEEKLY, PeriodKind.parse("semanal"));
        assertEquals(PeriodKind.MONTHLY, PeriodKind.parse("Month"));
        assertNull(PeriodKind.parse("daily"));
    }

    @Test
    void rankingUsesGainsSkipsZeroAndBreaksTiesByName() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID(), d = UUID.randomUUID();
        Map<UUID, String> names = Map.of(a, "Zed", b, "amy", c, "Bob", d, "New");
        Map<UUID, double[]> cur = new HashMap<>();
        Map<UUID, double[]> base = new HashMap<>();
        cur.put(a, new double[]{15}); base.put(a, new double[]{10}); // +5
        cur.put(b, new double[]{5});  base.put(b, new double[]{0});  // +5
        cur.put(c, new double[]{7});  base.put(c, new double[]{7});  // 0 → fuera
        cur.put(d, new double[]{50});                                // sin base → 0
        List<PeriodMath.Row> rows = PeriodMath.ranking(names, cur, base, 0);
        assertEquals(2, rows.size());
        assertEquals("amy", rows.get(0).name());
        assertEquals("Zed", rows.get(1).name());
        assertEquals(5.0, rows.get(0).value());
    }

    @Test
    void gainNeverNegativeAndRounded() {
        assertEquals(0, PeriodMath.gain(new double[]{3}, new double[]{9}, 0), "un reset no da puntos negativos");
        assertEquals(1.23, PeriodMath.gain(new double[]{2.234}, new double[]{1.0}, 0));
        assertEquals(0, PeriodMath.gain(new double[]{1}, new double[]{0}, 5), "índice fuera de rango");
    }
}
