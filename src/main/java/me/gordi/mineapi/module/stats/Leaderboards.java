package me.gordi.mineapi.module.stats;

import com.google.gson.JsonObject;
import me.gordi.mineapi.core.data.PlayerDataService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Tops por categoría (máx. {@link #MAX_ENTRIES}). Se recalculan en un hilo de trabajo: bajo el lock
 * solo se copian 7 números por jugador; ordenar se hace fuera.
 */
public final class Leaderboards {

    public static final int MAX_ENTRIES = 100;

    /** Fila inmutable de un top. */
    public static final class Entry {
        public final String name;
        private final double[] values;
        final long lastUpdated;

        Entry(String name, double[] values, long lastUpdated) {
            this.name = name;
            this.values = values;
            this.lastUpdated = lastUpdated;
        }

        public double value(StatType type) { return values[type.ordinal()]; }

        public String formatted(StatType type) { return type.format(value(type)); }
    }

    private volatile Map<StatType, List<Entry>> cache = new EnumMap<>(StatType.class);

    /** Recalcula (cualquier hilo). */
    public int update(PlayerDataService data) {
        List<Entry> rows = new ArrayList<>(data.size());
        data.forEach(json -> rows.add(row(json)));
        Map<StatType, List<Entry>> next = build(rows);
        cache = next;
        return rows.size();
    }

    /** Separado para poder probarlo sin Bukkit. */
    static Map<StatType, List<Entry>> build(Collection<Entry> rows) {
        // Un registro por nombre sin distinguir mayúsculas (se queda el más reciente)
        Map<String, Entry> byName = new HashMap<>();
        for (Entry e : rows) byName.merge(e.name.toLowerCase(Locale.ROOT), e, (a, b) -> b.lastUpdated > a.lastUpdated ? b : a);
        Entry[] all = byName.values().toArray(new Entry[0]);
        Map<StatType, List<Entry>> out = new EnumMap<>(StatType.class);
        for (StatType t : StatType.ALL) {
            Entry[] copy = all.clone();
            Arrays.sort(copy, Comparator.comparingDouble((Entry e) -> e.value(t)).reversed());
            out.put(t, List.of(Arrays.copyOf(copy, Math.min(copy.length, MAX_ENTRIES))));
        }
        return out;
    }

    static Entry row(JsonObject json) {
        double[] values = new double[StatType.ALL.length];
        for (StatType t : StatType.ALL) values[t.ordinal()] = PlayerDataService.num(json, t.key);
        String name = PlayerDataService.str(json, "name");
        return new Entry(name == null ? "?" : name, values, (long) PlayerDataService.num(json, "last_updated"));
    }

    public List<Entry> top(StatType type) {
        return type == null ? List.of() : cache.getOrDefault(type, List.of());
    }

    /** Acepta alias (kills, time, hours...). Lista vacía si la categoría no existe. */
    public List<Entry> top(String type) {
        return top(StatType.parse(type));
    }
}
