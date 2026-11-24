package me.gordi.mineapi.module.periods;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Cálculos puros de periodos (fechas y rankings), sin Bukkit: probados con JUnit. */
public final class PeriodMath {

    private PeriodMath() {
    }

    /** Primer día del periodo que contiene {@code day}. */
    public static LocalDate startDay(PeriodKind kind, LocalDate day, DayOfWeek weekStart) {
        return kind == PeriodKind.WEEKLY
                ? day.with(TemporalAdjusters.previousOrSame(weekStart))
                : day.withDayOfMonth(1);
    }

    /** Identificador estable: semanal = fecha de inicio (2026-10-26), mensual = 2026-10. */
    public static String id(PeriodKind kind, LocalDate day, DayOfWeek weekStart) {
        LocalDate s = startDay(kind, day, weekStart);
        return kind == PeriodKind.WEEKLY ? s.toString() : s.toString().substring(0, 7);
    }

    public static long startMillis(PeriodKind kind, LocalDate day, DayOfWeek weekStart, ZoneId zone) {
        return startDay(kind, day, weekStart).atStartOfDay(zone).toInstant().toEpochMilli();
    }

    /** Instante en que termina (inicio del siguiente periodo). */
    public static long endMillis(PeriodKind kind, LocalDate day, DayOfWeek weekStart, ZoneId zone) {
        LocalDate s = startDay(kind, day, weekStart);
        LocalDate next = kind == PeriodKind.WEEKLY ? s.plusWeeks(1) : s.plusMonths(1);
        return next.atStartOfDay(zone).toInstant().toEpochMilli();
    }

    public static DayOfWeek weekStart(String raw) {
        if (raw != null) {
            try {
                return DayOfWeek.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return DayOfWeek.MONDAY;
    }

    /** Fila de un top de periodo. */
    public record Row(UUID uuid, String name, double value) {
    }

    /**
     * Ganancia del periodo = max(0, actual − base). Los jugadores sin progreso no aparecen.
     * Orden: mayor ganancia; empate por nombre.
     */
    public static List<Row> ranking(Map<UUID, String> names, Map<UUID, double[]> current,
                                    Map<UUID, double[]> baseline, int index) {
        List<Row> rows = new ArrayList<>();
        for (Map.Entry<UUID, double[]> e : current.entrySet()) {
            double[] base = baseline.get(e.getKey());
            double gain = gain(e.getValue(), base, index);
            if (gain <= 0) continue;
            String name = names.get(e.getKey());
            rows.add(new Row(e.getKey(), name == null ? "?" : name, gain));
        }
        rows.sort(Comparator.comparingDouble(Row::value).reversed().thenComparing(Row::name, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    public static double gain(double[] current, double[] base, int index) {
        if (current == null || index >= current.length) return 0;
        double b = base == null || index >= base.length ? current[index] : base[index];
        double g = current[index] - b;
        return g > 0 ? Math.round(g * 100.0) / 100.0 : 0;
    }
}
