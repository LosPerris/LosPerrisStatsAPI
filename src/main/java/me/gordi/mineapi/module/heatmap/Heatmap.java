package me.gordi.mineapi.module.heatmap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.TreeMap;

/**
 * Mapa de calor de actividad: media de jugadores conectados por día de la semana × hora.
 * Se guarda por semanas (suma y nº de muestras en 168 casillas) y se conservan las últimas
 * {@code keepWeeks}. Código puro (JUnit); el módulo lo protege con un lock.
 */
public final class Heatmap {

    public static final int CELLS = 7 * 24;

    private final int keepWeeks;
    /** semana (lunes, yyyy-MM-dd) -> [suma de conectados, muestras] por casilla. */
    private final TreeMap<String, double[][]> weeks = new TreeMap<>();

    public Heatmap(int keepWeeks) {
        this.keepWeeks = Math.max(1, keepWeeks);
    }

    /** Casilla 0..167: día (lunes = 0) * 24 + hora. */
    public static int cell(ZonedDateTime t) {
        return (t.getDayOfWeek().getValue() - 1) * 24 + t.getHour();
    }

    public static String weekId(ZonedDateTime t) {
        LocalDate monday = t.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.toString();
    }

    public void sample(long epochMillis, ZoneId zone, int online) {
        ZonedDateTime t = Instant.ofEpochMilli(epochMillis).atZone(zone);
        double[][] w = weeks.computeIfAbsent(weekId(t), k -> new double[2][CELLS]);
        int c = cell(t);
        w[0][c] += Math.max(0, online);
        w[1][c] += 1;
        while (weeks.size() > keepWeeks) weeks.pollFirstEntry();
    }

    public int weekCount() {
        return weeks.size();
    }

    /** Media por casilla (7 filas × 24 columnas); -1 = sin datos. */
    public double[][] averages() {
        double[] sum = new double[CELLS];
        double[] n = new double[CELLS];
        for (double[][] w : weeks.values()) {
            for (int i = 0; i < CELLS; i++) {
                sum[i] += w[0][i];
                n[i] += w[1][i];
            }
        }
        double[][] out = new double[7][24];
        for (int i = 0; i < CELLS; i++) {
            out[i / 24][i % 24] = n[i] == 0 ? -1 : Math.round(sum[i] / n[i] * 100.0) / 100.0;
        }
        return out;
    }

    /** {"days":[[24 medias] x7], "max": pico, "weeks": n, "peak": {"day":0..6,"hour":h}} */
    public JsonObject toApi() {
        double[][] avg = averages();
        JsonArray days = new JsonArray();
        double max = 0;
        int peakDay = -1, peakHour = -1;
        for (int d = 0; d < 7; d++) {
            JsonArray hours = new JsonArray();
            for (int h = 0; h < 24; h++) {
                double v = avg[d][h];
                hours.add(v);
                if (v > max) {
                    max = v;
                    peakDay = d;
                    peakHour = h;
                }
            }
            days.add(hours);
        }
        JsonObject o = new JsonObject();
        o.add("days", days);
        o.addProperty("max", max);
        o.addProperty("weeks", weeks.size());
        if (peakDay >= 0) {
            JsonObject peak = new JsonObject();
            peak.addProperty("day", peakDay);
            peak.addProperty("hour", peakHour);
            o.add("peak", peak);
        }
        return o;
    }

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        JsonObject ws = new JsonObject();
        for (Map.Entry<String, double[][]> e : weeks.entrySet()) {
            JsonObject w = new JsonObject();
            w.add("s", array(e.getValue()[0]));
            w.add("n", array(e.getValue()[1]));
            ws.add(e.getKey(), w);
        }
        root.addProperty("version", 1);
        root.add("weeks", ws);
        return root;
    }

    public void load(JsonObject root) {
        weeks.clear();
        if (root == null || !root.has("weeks") || !root.get("weeks").isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("weeks").entrySet()) {
            try {
                JsonObject w = e.getValue().getAsJsonObject();
                double[][] data = new double[2][CELLS];
                read(w.getAsJsonArray("s"), data[0]);
                read(w.getAsJsonArray("n"), data[1]);
                weeks.put(e.getKey(), data);
            } catch (RuntimeException ignored) {
                // semana corrupta: se descarta
            }
        }
        while (weeks.size() > keepWeeks) weeks.pollFirstEntry();
    }

    private static JsonArray array(double[] v) {
        JsonArray a = new JsonArray();
        for (double d : v) {
            if (d == Math.rint(d)) a.add((long) d);
            else a.add(d);
        }
        return a;
    }

    private static void read(JsonArray a, double[] into) {
        for (int i = 0; i < Math.min(a.size(), into.length); i++) into[i] = a.get(i).getAsDouble();
    }
}
