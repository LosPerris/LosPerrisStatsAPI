package me.gordi.mineapi.module.periods;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.module.stats.StatSnapshot;
import me.gordi.mineapi.module.stats.StatType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Motor de "ganancia desde una base" que comparten los tops semanales/mensuales y las temporadas.
 * Guarda los valores de cada jugador al empezar ({@code baseline}) y calcula los tops como
 * {@code actual − base}. No hay contadores por evento: el coste es un recorrido de la caché cada
 * pocos minutos en un hilo asíncrono. Sin Bukkit: se prueba con JUnit.
 */
public final class GainTracker {

    /** Resultado inmutable del último cálculo. */
    public record Board(Map<StatType, List<PeriodMath.Row>> tops, Map<UUID, double[]> gains, Map<UUID, int[]> ranks) {
        public static final Board EMPTY = new Board(Map.of(), Map.of(), Map.of());

        public List<PeriodMath.Row> top(StatType t) {
            return tops.getOrDefault(t, List.of());
        }
    }

    private final Map<UUID, double[]> baseline = new ConcurrentHashMap<>();
    private volatile Board board = Board.EMPTY;
    private volatile boolean dirty;

    /** Empieza de cero: la base pasan a ser los valores actuales. */
    public void restart(StatSnapshot snap) {
        baseline.clear();
        for (Map.Entry<UUID, double[]> e : snap.values().entrySet()) baseline.put(e.getKey(), e.getValue().clone());
        board = Board.EMPTY;
        dirty = true;
    }

    /** Primer avistamiento de un jugador (entrada): su base son sus valores actuales. */
    public void seen(UUID uuid, double[] values) {
        if (values != null && baseline.putIfAbsent(uuid, values.clone()) == null) dirty = true;
    }

    /** El jugador fue reseteado: sus valores vuelven a 0 y la base también. */
    public void zero(UUID uuid) {
        baseline.put(uuid, new double[StatType.ALL.length]);
        dirty = true;
    }

    public boolean isEmpty() {
        return baseline.isEmpty();
    }

    /** Recalcula el tablero. Jugadores nuevos o estadísticas sin base toman la base actual. */
    public Board compute(StatSnapshot snap, List<StatType> tracked, int size) {
        for (Map.Entry<UUID, double[]> e : snap.values().entrySet()) {
            double[] base = baseline.get(e.getKey());
            if (base == null) {
                baseline.put(e.getKey(), e.getValue().clone());
                dirty = true;
                continue;
            }
            for (int i = 0; i < base.length; i++) {
                if (Double.isNaN(base[i])) {
                    base[i] = e.getValue()[i];
                    dirty = true;
                }
            }
        }
        Map<StatType, List<PeriodMath.Row>> tops = new EnumMap<>(StatType.class);
        Map<UUID, double[]> gains = new HashMap<>();
        Map<UUID, int[]> ranks = new HashMap<>();
        for (StatType t : tracked) {
            List<PeriodMath.Row> rows = PeriodMath.ranking(snap.names(), snap.values(), baseline, t.ordinal());
            for (int i = 0; i < rows.size(); i++) {
                PeriodMath.Row r = rows.get(i);
                gains.computeIfAbsent(r.uuid(), k -> new double[StatType.ALL.length])[t.ordinal()] = r.value();
                ranks.computeIfAbsent(r.uuid(), k -> new int[StatType.ALL.length])[t.ordinal()] = i + 1;
            }
            tops.put(t, List.copyOf(rows.subList(0, Math.min(size, rows.size()))));
        }
        Board b = new Board(tops, gains, ranks);
        board = b;
        return b;
    }

    public Board board() {
        return board;
    }

    /** Ganancias del jugador (copia; ceros si no ha progresado). */
    public double[] gains(UUID uuid) {
        double[] g = board.gains().get(uuid);
        return g == null ? new double[StatType.ALL.length] : g.clone();
    }

    /** Puestos del jugador (copia; 0 = sin puesto). */
    public int[] ranks(UUID uuid) {
        int[] r = board.ranks().get(uuid);
        return r == null ? new int[StatType.ALL.length] : r.clone();
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markSaved() {
        dirty = false;
    }

    // ------------------------------------------------------------------ JSON

    /** Escribe {@code keys} (orden de las columnas) y {@code baseline} {uuid: [..]} en {@code root}. */
    public void write(JsonObject root) {
        JsonArray keys = new JsonArray();
        for (StatType t : StatType.ALL) keys.add(t.key);
        root.add("keys", keys);
        JsonObject base = new JsonObject();
        for (Map.Entry<UUID, double[]> e : baseline.entrySet()) {
            JsonArray arr = new JsonArray();
            for (double d : e.getValue()) arr.add(Double.isNaN(d) ? 0 : d);
            base.add(e.getKey().toString(), arr);
        }
        root.add("baseline", base);
    }

    /**
     * Lee lo que escribió {@link #write}. Las columnas se emparejan por nombre: una estadística
     * añadida después queda sin base (NaN) y toma el valor actual en el siguiente cálculo.
     */
    public void read(JsonObject root) {
        baseline.clear();
        if (root == null) return;
        List<String> keys = new ArrayList<>();
        if (root.has("keys") && root.get("keys").isJsonArray()) for (JsonElement k : root.getAsJsonArray("keys")) keys.add(k.getAsString());
        if (!root.has("baseline") || !root.get("baseline").isJsonObject()) return;
        for (Map.Entry<String, JsonElement> e : root.getAsJsonObject("baseline").entrySet()) {
            try {
                UUID uuid = UUID.fromString(e.getKey());
                JsonArray arr = e.getValue().getAsJsonArray();
                double[] v = new double[StatType.ALL.length];
                boolean[] known = new boolean[v.length];
                for (int i = 0; i < keys.size() && i < arr.size(); i++) {
                    StatType t = StatType.parse(keys.get(i));
                    if (t != null && t.key.equals(keys.get(i))) {
                        v[t.ordinal()] = arr.get(i).getAsDouble();
                        known[t.ordinal()] = true;
                    }
                }
                for (int i = 0; i < v.length; i++) if (!known[i]) v[i] = Double.NaN;
                baseline.put(uuid, v);
            } catch (Exception ignored) {
                // entrada corrupta: se ignora (ese jugador empieza de cero)
            }
        }
        dirty = false;
    }

    /** Ganadores (hasta {@code n} por estadística) como JSON {stat:[{name,uuid,value}]}. */
    public static JsonObject winners(Board b, List<StatType> tracked, int n) {
        JsonObject winners = new JsonObject();
        for (StatType t : tracked) {
            List<PeriodMath.Row> rows = b.top(t);
            JsonArray arr = new JsonArray();
            for (PeriodMath.Row r : rows.subList(0, Math.min(n, rows.size()))) {
                JsonObject o = new JsonObject();
                o.addProperty("name", r.name());
                o.addProperty("uuid", r.uuid().toString());
                o.addProperty("value", r.value());
                arr.add(o);
            }
            winners.add(t.key, arr);
        }
        return winners;
    }
}
