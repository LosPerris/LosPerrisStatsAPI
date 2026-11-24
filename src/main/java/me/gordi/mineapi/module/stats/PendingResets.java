package me.gordi.mineapi.module.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.core.storage.JsonFiles;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Jugadores desconectados cuyo reset de estadísticas calculadas (cientos de estadísticas vanilla)
 * se aplica cuando vuelvan a entrar, en vez de reescribir su archivo de estadísticas ahora.
 * Archivo: data/pending_resets.json. Solo hilo principal.
 */
final class PendingResets {

    private final File file;
    private final Logger log;
    private final Set<UUID> uuids = new HashSet<>();

    PendingResets(File file, Logger log) {
        this.file = file;
        this.log = log;
        JsonObject json = JsonFiles.read(file);
        if (json != null && json.has("players")) {
            for (JsonElement e : json.getAsJsonArray("players")) {
                try {
                    uuids.add(UUID.fromString(e.getAsString()));
                } catch (Exception ignored) {
                    // entrada inválida
                }
            }
        }
    }

    void add(UUID uuid) {
        if (uuids.add(uuid)) save();
    }

    /** @return true si había un reset pendiente (y lo quita). Sin E/S si no hay ninguno. */
    boolean take(UUID uuid) {
        if (uuids.isEmpty() || !uuids.remove(uuid)) return false;
        save();
        return true;
    }

    private void save() {
        JsonObject json = new JsonObject();
        JsonArray arr = new JsonArray();
        for (UUID u : uuids) arr.add(u.toString());
        json.add("players", arr);
        try {
            JsonFiles.write(file, json, true);
        } catch (IOException e) {
            log.warning("No se pudo guardar " + file.getName() + ": " + e.getMessage());
        }
    }
}
