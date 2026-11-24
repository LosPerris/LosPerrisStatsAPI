package me.gordi.mineapi.module.discordbot;

import me.gordi.mineapi.module.stats.StatType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Roles automáticos de Discord para cuentas vinculadas (puro, probado con JUnit):
 * <ul>
 *   <li>{@code linked}: a toda cuenta vinculada.</li>
 *   <li>{@code milestones}: {@code "kills:100": "<rol>"} → al llegar a 100 asesinatos.</li>
 *   <li>{@code top}: {@code "kills:3": "<rol>"} → mientras esté en el top 3 de asesinatos (se quita al salir).</li>
 * </ul>
 */
public final class RoleRules {

    /** Regla de hito: valor mínimo de la estadística. */
    public record Milestone(StatType stat, double value, String role) {
    }

    /** Regla de top: puesto máximo (1 = solo el primero). */
    public record Top(StatType stat, int maxRank, String role) {
    }

    private final String linked;
    private final List<Milestone> milestones;
    private final List<Top> tops;

    public RoleRules(String linked, List<Milestone> milestones, List<Top> tops) {
        this.linked = linked == null || linked.isBlank() ? null : linked.trim();
        this.milestones = List.copyOf(milestones);
        this.tops = List.copyOf(tops);
    }

    /** Lee las reglas de config ({@code "stat:número" → id de rol}); avisa de las inválidas. */
    public static RoleRules parse(String linked, Map<String, Object> milestones, Map<String, Object> tops, Consumer<String> warn) {
        List<Milestone> ms = new ArrayList<>();
        List<Top> ts = new ArrayList<>();
        if (milestones != null) {
            for (Map.Entry<String, Object> e : milestones.entrySet()) {
                Object[] k = key(e.getKey());
                String role = role(e.getValue());
                if (k == null || role == null || ((Double) k[1]) <= 0) {
                    warn.accept("settings/discord.yml → discord-bot.roles.milestones." + e.getKey() + " inválido (use stat:valor: id-de-rol)");
                    continue;
                }
                ms.add(new Milestone((StatType) k[0], (Double) k[1], role));
            }
        }
        if (tops != null) {
            for (Map.Entry<String, Object> e : tops.entrySet()) {
                Object[] k = key(e.getKey());
                String role = role(e.getValue());
                if (k == null || role == null || ((Double) k[1]) < 1 || ((Double) k[1]) > 100) {
                    warn.accept("settings/discord.yml → discord-bot.roles.top." + e.getKey() + " inválido (use stat:puesto(1-100): id-de-rol)");
                    continue;
                }
                ts.add(new Top((StatType) k[0], ((Double) k[1]).intValue(), role));
            }
        }
        return new RoleRules(linked, ms, ts);
    }

    private static Object[] key(String raw) {
        int c = raw.lastIndexOf(':');
        if (c <= 0) return null;
        StatType t = StatType.parse(raw.substring(0, c));
        try {
            return t == null ? null : new Object[]{t, Double.parseDouble(raw.substring(c + 1).trim())};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String role(Object v) {
        String s = v == null ? null : String.valueOf(v).trim();
        return s != null && s.matches("\\d{5,25}") ? s : null;
    }

    /**
     * Roles que debería tener una cuenta vinculada.
     * @param values valores por ordinal de {@link StatType}
     * @param ranks  puestos por ordinal (0 = fuera del top)
     */
    public Set<String> desired(double[] values, int[] ranks) {
        Set<String> out = new LinkedHashSet<>();
        if (linked != null) out.add(linked);
        for (Milestone m : milestones) if (values[m.stat().ordinal()] >= m.value()) out.add(m.role());
        for (Top t : tops) {
            int r = ranks[t.stat().ordinal()];
            if (r > 0 && r <= t.maxRank()) out.add(t.role());
        }
        return out;
    }

    /** Todos los roles que gestiona el plugin. */
    public Set<String> managed() {
        Set<String> out = new LinkedHashSet<>();
        if (linked != null) out.add(linked);
        milestones.forEach(m -> out.add(m.role()));
        tops.forEach(t -> out.add(t.role()));
        return out;
    }

    /** Estadísticas que usan las reglas de top. */
    public java.util.EnumSet<StatType> topStats() {
        java.util.EnumSet<StatType> out = java.util.EnumSet.noneOf(StatType.class);
        tops.forEach(t -> out.add(t.stat()));
        return out;
    }

    /**
     * Puesto de cada jugador en las estadísticas pedidas (1 = primero; empates comparten puesto;
     * valor 0 = sin puesto). Índice por ordinal de {@link StatType}.
     */
    public static Map<java.util.UUID, int[]> ranks(Map<java.util.UUID, double[]> values, Set<StatType> stats) {
        Map<java.util.UUID, int[]> out = new java.util.HashMap<>();
        for (java.util.UUID u : values.keySet()) out.put(u, new int[StatType.ALL.length]);
        for (StatType t : stats) {
            int i = t.ordinal();
            double[] sorted = values.values().stream().mapToDouble(v -> v[i]).filter(v -> v > 0).sorted().toArray();
            for (Map.Entry<java.util.UUID, double[]> e : values.entrySet()) {
                double v = e.getValue()[i];
                if (v <= 0) continue;
                int lo = 0, hi = sorted.length; // primer índice con valor > v
                while (lo < hi) {
                    int mid = (lo + hi) >>> 1;
                    if (sorted[mid] > v) hi = mid;
                    else lo = mid + 1;
                }
                out.get(e.getKey())[i] = sorted.length - lo + 1;
            }
        }
        return out;
    }

    public boolean isEmpty() {
        return linked == null && milestones.isEmpty() && tops.isEmpty();
    }

    /** true si alguna regla depende del top (necesita puestos). */
    public boolean usesTop() {
        return !tops.isEmpty();
    }
}
