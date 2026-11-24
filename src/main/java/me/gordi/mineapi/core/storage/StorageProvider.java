package me.gordi.mineapi.core.storage;

import com.google.gson.JsonObject;
import java.util.UUID;

public interface StorageProvider {
    /**
     * Inicializa el almacenamiento.
     * @throws IllegalStateException si no se puede inicializar (ej. base de datos inaccesible).
     */
    void init();
    void shutdown();
    
    JsonObject loadPlayerStats(UUID uuid);
    void savePlayerStats(UUID uuid, String name, JsonObject data);
    void deletePlayerStats(UUID uuid);
    
    JsonObject loadGlobalStats();
    void saveGlobalStats(JsonObject data);
    
    void saveServerInfo(JsonObject data);

    /**
     * Comprobación ligera de salud (la usa el módulo de alertas desde un hilo asíncrono).
     * @return false si el almacenamiento no responde
     */
    default boolean ping() {
        return true;
    }
}
