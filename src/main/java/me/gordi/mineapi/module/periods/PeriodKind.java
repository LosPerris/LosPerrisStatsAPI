package me.gordi.mineapi.module.periods;

import java.util.Locale;

/** Tipos de periodo con top propio. */
public enum PeriodKind {
    WEEKLY("weekly"),
    MONTHLY("monthly");

    public final String id;

    PeriodKind(String id) {
        this.id = id;
    }

    public static PeriodKind parse(String s) {
        if (s == null) return null;
        String v = s.toLowerCase(Locale.ROOT);
        for (PeriodKind k : values()) if (k.id.equals(v)) return k;
        if (v.equals("week") || v.equals("semanal")) return WEEKLY;
        if (v.equals("month") || v.equals("mensual")) return MONTHLY;
        return null;
    }
}
