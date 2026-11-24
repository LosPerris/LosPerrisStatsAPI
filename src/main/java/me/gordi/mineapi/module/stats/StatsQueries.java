package me.gordi.mineapi.module.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.gordi.mineapi.core.data.PlayerDataService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Consultas del dashboard (totales, posiciones, listado). Bajo el lock solo se copian números;
 * el cálculo se hace fuera. Las funciones estáticas no dependen de Bukkit (se prueban con JUnit).
 */
public final class StatsQueries {

    /** Copia ligera de un jugador. */
    record Row(String name, String uuid, double[] values, long lastUpdated) {
        double value(StatType t) { return values[t.ordinal()]; }
    }

    static final String[] HOUR_LABELS = {"<1 h", "1-5 h", "5-10 h", "10-25 h", "25-50 h", "50-100 h", "100+ h"};
    private static final double[] HOUR_EDGES = {1, 5, 10, 25, 50, 100};
    /** Claves de actividad (el dashboard las traduce); "label" se envía en el idioma por defecto. */
    static final String[] ACTIVITY_KEYS = {"24h", "7d", "30d", "older"};
    private static final long DAY = 86_400_000L;

    private StatsQueries() {
    }

    static List<Row> rows(PlayerDataService data) {
        List<Row> rows = new ArrayList<>(data.size());
        data.forEach(json -> {
            String name = PlayerDataService.str(json, "name");
            if (name == null) return;
            double[] v = new double[StatType.ALL.length];
            for (StatType t : StatType.ALL) v[t.ordinal()] = PlayerDataService.num(json, t.key);
            rows.add(new Row(name, PlayerDataService.str(json, "uuid"), v, (long) PlayerDataService.num(json, "last_updated")));
        });
        return rows;
    }

    /** Totales, medias, máximos y distribuciones. {@code activityLabels} en el orden de ACTIVITY_KEYS. */
    static JsonObject totals(List<Row> rows, long now, String[] activityLabels) {
        int n = StatType.ALL.length;
        double[] sums = new double[n];
        double[] max = new double[n];
        int[] hours = new int[HOUR_EDGES.length + 1];
        int[] activity = new int[ACTIVITY_KEYS.length];
        Set<String> seen = new HashSet<>();
        int players = 0;
        for (Row r : rows) {
            if (!seen.add(r.name().toLowerCase(Locale.ROOT))) continue;
            players++;
            for (int i = 0; i < n; i++) {
                sums[i] += r.values()[i];
                if (r.values()[i] > max[i]) max[i] = r.values()[i];
            }
            double h = r.value(StatType.HOURS_PLAYED);
            int b = 0;
            while (b < HOUR_EDGES.length && h >= HOUR_EDGES[b]) b++;
            hours[b]++;
            long age = r.lastUpdated() <= 0 ? Long.MAX_VALUE : now - r.lastUpdated();
            activity[age <= DAY ? 0 : age <= 7 * DAY ? 1 : age <= 30 * DAY ? 2 : 3]++;
        }
        JsonObject out = new JsonObject();
        out.addProperty("players_tracked", players);
        JsonObject totals = new JsonObject();
        JsonObject averages = new JsonObject();
        JsonObject maxima = new JsonObject();
        for (StatType t : StatType.ALL) {
            int i = t.ordinal();
            totals.addProperty(t.key, round2(sums[i]));
            averages.addProperty(t.key, players == 0 ? 0 : round2(sums[i] / players));
            maxima.addProperty(t.key, round2(max[i]));
        }
        out.add("totals", totals);
        out.add("averages", averages);
        out.add("max", maxima);
        JsonArray hoursDist = new JsonArray();
        for (int i = 0; i < hours.length; i++) hoursDist.add(bucket(null, HOUR_LABELS[i], hours[i]));
        out.add("hours_distribution", hoursDist);
        JsonArray act = new JsonArray();
        for (int i = 0; i < activity.length; i++) act.add(bucket(ACTIVITY_KEYS[i], activityLabels[i], activity[i]));
        out.add("activity", act);
        return out;
    }

    /**
     * Posición (1 = primero) en cada estadística sobre todos los jugadores; null si no existe.
     * Un valor 0 da posición 0 (sin clasificar): empatar a cero con todos no es "ser #1".
     */
    static JsonObject ranks(List<Row> rows, String name) {
        Row me = null;
        for (Row r : rows) {
            if (r.name().equalsIgnoreCase(name)) {
                me = r;
                break;
            }
        }
        if (me == null) return null;
        int[] better = new int[StatType.ALL.length];
        for (Row r : rows) {
            if (r == me) continue;
            for (int i = 0; i < better.length; i++) if (r.values()[i] > me.values()[i]) better[i]++;
        }
        JsonObject ranks = new JsonObject();
        for (StatType t : StatType.ALL) {
            ranks.addProperty(t.key, me.values()[t.ordinal()] > 0 ? better[t.ordinal()] + 1 : 0);
        }
        ranks.addProperty("_total", rows.size());
        return ranks;
    }

    /** Listado resumido (sin IP), filtrado por texto y ordenado por "name" o una clave de stat. */
    static List<JsonObject> list(List<Row> rows, String query, String sortKey, boolean desc) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Row> filtered = new ArrayList<>();
        for (Row r : rows) if (q.isEmpty() || r.name().toLowerCase(Locale.ROOT).contains(q)) filtered.add(r);
        StatType sort = sortKey == null || sortKey.equals("name") ? null : StatType.parse(sortKey);
        Comparator<Row> cmp;
        if (sort != null) cmp = Comparator.comparingDouble(r -> r.value(sort));
        else if ("last_updated".equals(sortKey)) cmp = Comparator.comparingLong(Row::lastUpdated);
        else cmp = Comparator.comparing(r -> r.name().toLowerCase(Locale.ROOT));
        filtered.sort(desc ? cmp.reversed() : cmp);
        List<JsonObject> out = new ArrayList<>(filtered.size());
        for (Row r : filtered) {
            JsonObject o = new JsonObject();
            o.addProperty("name", r.name());
            if (r.uuid() != null) o.addProperty("uuid", r.uuid());
            for (StatType t : StatType.ALL) o.addProperty(t.key, r.value(t));
            o.addProperty("last_updated", r.lastUpdated());
            out.add(o);
        }
        return out;
    }

    /**
     * Suma de los objetos de desglose (ej. "ores", "death_causes") de todos los jugadores.
     * Bajo el lock solo se suman números.
     */
    static JsonObject breakdowns(PlayerDataService data, String... keys) {
        java.util.Map<String, java.util.Map<String, Double>> sums = new java.util.LinkedHashMap<>();
        for (String k : keys) sums.put(k, new java.util.LinkedHashMap<>());
        data.forEach(json -> sumInto(json, sums));
        return toJson(sums);
    }

    static void sumInto(JsonObject json, java.util.Map<String, java.util.Map<String, Double>> sums) {
        for (java.util.Map.Entry<String, java.util.Map<String, Double>> e : sums.entrySet()) {
            com.google.gson.JsonElement el = json.get(e.getKey());
            if (el == null || !el.isJsonObject()) continue;
            for (java.util.Map.Entry<String, com.google.gson.JsonElement> v : el.getAsJsonObject().entrySet()) {
                if (v.getValue().isJsonPrimitive() && v.getValue().getAsJsonPrimitive().isNumber()) {
                    e.getValue().merge(v.getKey(), v.getValue().getAsDouble(), Double::sum);
                }
            }
        }
    }

    static JsonObject toJson(java.util.Map<String, java.util.Map<String, Double>> sums) {
        JsonObject out = new JsonObject();
        for (java.util.Map.Entry<String, java.util.Map<String, Double>> e : sums.entrySet()) {
            JsonObject o = new JsonObject();
            for (java.util.Map.Entry<String, Double> v : e.getValue().entrySet()) o.addProperty(v.getKey(), round2(v.getValue()));
            out.add(e.getKey(), o);
        }
        return out;
    }

    private static JsonObject bucket(String key, String label, int count) {
        JsonObject o = new JsonObject();
        if (key != null) o.addProperty("key", key);
        o.addProperty("label", label);
        o.addProperty("count", count);
        return o;
    }

    static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
