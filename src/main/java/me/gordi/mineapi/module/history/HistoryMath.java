package me.gordi.mineapi.module.history;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.TreeMap;

/** Utilidades puras del historial (sin Bukkit, probadas con JUnit). */
final class HistoryMath {

    private HistoryMath() {
    }

    /** Días ordenados (yyyy-MM-dd) desde {@code oldest} incluido. */
    static JsonObject trim(JsonObject days, String oldest) {
        TreeMap<String, JsonElement> sorted = new TreeMap<>();
        for (Map.Entry<String, JsonElement> e : days.entrySet()) {
            if (e.getKey().compareTo(oldest) >= 0) sorted.put(e.getKey(), e.getValue());
        }
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> e : sorted.entrySet()) out.add(e.getKey(), e.getValue());
        return out;
    }
}
