package me.gordi.mineapi.module.stats;

import com.google.gson.JsonObject;
import me.gordi.mineapi.core.data.PlayerDataService;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Copia de los valores de todos los jugadores en caché: nombre y un {@code double[]} indexado por
 * {@link StatType#ordinal()}. Se toma bajo el lock de la caché (13 números por jugador) y luego se
 * trabaja sin bloquear. La usan periodos, temporadas y hologramas.
 */
public record StatSnapshot(Map<UUID, String> names, Map<UUID, double[]> values) {

    public static final StatSnapshot EMPTY = new StatSnapshot(Map.of(), Map.of());

    public static StatSnapshot take(PlayerDataService data) {
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, double[]> values = new HashMap<>();
        data.forEach(json -> {
            String id = PlayerDataService.str(json, "uuid");
            if (id == null) return;
            UUID uuid;
            try {
                uuid = UUID.fromString(id);
            } catch (IllegalArgumentException e) {
                return;
            }
            values.put(uuid, values(json));
            String name = PlayerDataService.str(json, "name");
            names.put(uuid, name == null ? "?" : name);
        });
        return new StatSnapshot(names, values);
    }

    /** Valores de un jugador (JSON de la caché); null si no hay datos. */
    public static double[] values(JsonObject json) {
        if (json == null) return null;
        double[] v = new double[StatType.ALL.length];
        for (StatType t : StatType.ALL) v[t.ordinal()] = PlayerDataService.num(json, t.key);
        return v;
    }
}
