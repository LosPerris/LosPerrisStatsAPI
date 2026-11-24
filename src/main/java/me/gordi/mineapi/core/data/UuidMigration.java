package me.gordi.mineapi.core.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Convierte el all_stats antiguo (claves = nombre del jugador) al formato 2 (claves = UUID).
 * <pre>
 * Antes: { "Steve": {...}, "Alex": {...} }
 * Ahora: { "_format": 2, "players": { "uuid-de-steve": {...}, ... } }
 * </pre>
 * Código puro, sin servidor: se prueba con JUnit.
 */
public final class UuidMigration {

    public static final int FORMAT = 2;
    public static final String FORMAT_KEY = "_format";
    public static final String PLAYERS_KEY = "players";
    /** Marca de entradas cuyo UUID se dedujo (modo offline) y se corregirá cuando el jugador entre. */
    public static final String GUESSED_KEY = "uuid_guessed";

    /**
     * @param root      datos migrados
     * @param migrated  entradas convertidas
     * @param guessed   entradas sin UUID real (se usó el UUID offline)
     * @param merged    duplicados unidos (mismo UUID con varios nombres)
     */
    public record Result(JsonObject root, int migrated, int guessed, int merged) {
    }

    private UuidMigration() {
    }

    public static boolean isMigrated(JsonObject root) {
        return root != null && root.has(FORMAT_KEY) && root.get(FORMAT_KEY).isJsonPrimitive()
                && root.get(FORMAT_KEY).getAsInt() >= FORMAT;
    }

    /** Estructura vacía en formato actual. */
    public static JsonObject empty() {
        JsonObject root = new JsonObject();
        root.addProperty(FORMAT_KEY, FORMAT);
        root.add(PLAYERS_KEY, new JsonObject());
        return root;
    }

    /**
     * @param resolver nombre → UUID conocido por el servidor (o null). Si devuelve null y la
     *                 entrada no trae "uuid", se usa el UUID offline y se marca como deducido.
     */
    public static Result migrate(JsonObject legacy, Function<String, UUID> resolver) {
        if (isMigrated(legacy)) return new Result(legacy, 0, 0, 0);
        JsonObject root = empty();
        JsonObject players = root.getAsJsonObject(PLAYERS_KEY);
        int migrated = 0, guessed = 0, merged = 0;
        if (legacy == null) return new Result(root, 0, 0, 0);

        for (Map.Entry<String, JsonElement> e : legacy.entrySet()) {
            if (!e.getValue().isJsonObject()) continue;
            JsonObject data = e.getValue().getAsJsonObject().deepCopy();
            String name = data.has("name") ? data.get("name").getAsString() : e.getKey();
            UUID uuid = parse(data.has("uuid") ? data.get("uuid").getAsString() : null);
            if (uuid == null && resolver != null) uuid = resolver.apply(name);
            if (uuid == null) {
                uuid = offlineUuid(name);
                data.addProperty(GUESSED_KEY, true);
                guessed++;
            }
            data.addProperty("uuid", uuid.toString());
            data.addProperty("name", name);

            String key = uuid.toString();
            if (players.has(key)) {
                merged++;
                if (lastUpdated(players.get(key)) >= lastUpdated(data)) continue;
            } else {
                migrated++;
            }
            players.add(key, data);
        }
        return new Result(root, migrated, guessed, merged);
    }

    /** UUID que Minecraft asigna en modo offline. */
    public static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    static UUID parse(String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            return UUID.fromString(s.toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    static long lastUpdated(JsonElement el) {
        try {
            if (el != null && el.isJsonObject() && el.getAsJsonObject().has("last_updated")) {
                return el.getAsJsonObject().get("last_updated").getAsLong();
            }
        } catch (Exception ignored) {
            // valor inválido = 0
        }
        return 0L;
    }
}
