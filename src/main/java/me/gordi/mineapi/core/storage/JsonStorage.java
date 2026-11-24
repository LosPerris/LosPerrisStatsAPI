package me.gordi.mineapi.core.storage;

import me.gordi.mineapi.core.data.DataDir;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

public class JsonStorage implements StorageProvider {

    private static final String DEFAULT_FOLDER = DataDir.NAME + "/players";
    private static final String LEGACY_FOLDER = "player_data";

    private final JavaPlugin plugin;
    private final File statsFolder;
    private final File globalFile;
    private final Gson gson;

    public JsonStorage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        
        String folderName = plugin.getConfig().getString("advanced.stats-folder", plugin.getConfig().getString("general.stats-folder", ""));
        if (folderName == null || folderName.trim().isEmpty()) {
            // Sin clave en config: usa la carpeta antigua si ya existe (compatibilidad), si no la documentada
            File legacy = new File(plugin.getDataFolder(), LEGACY_FOLDER);
            folderName = legacy.isDirectory() ? LEGACY_FOLDER : DEFAULT_FOLDER;
        }
        this.statsFolder = new File(plugin.getDataFolder(), folderName);
        
        File serverDataFolder = new File(plugin.getDataFolder(), DataDir.NAME);
        this.globalFile = new File(serverDataFolder, "all_stats.json");
    }

    @Override
    public boolean ping() {
        return statsFolder.isDirectory() && statsFolder.canWrite();
    }

    @Override
    public void init() {
        if (!statsFolder.exists() && !statsFolder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta " + statsFolder.getPath());
        }
        File serverDataFolder = new File(plugin.getDataFolder(), DataDir.NAME);
        if (!serverDataFolder.exists() && !serverDataFolder.mkdirs()) {
            plugin.getLogger().warning("No se pudo crear la carpeta " + serverDataFolder.getPath());
        }
        
        File oldGlobalFile = new File(plugin.getDataFolder(), "all_stats.json");
        if (!this.globalFile.exists() && oldGlobalFile.exists()) {
            try {
                Files.move(oldGlobalFile.toPath(), this.globalFile.toPath());
                plugin.getLogger().info("Migración: 'all_stats.json' movido a carpeta 'data'.");
            } catch (IOException e) {
                plugin.getLogger().warning("Fallo al migrar all_stats.json: " + e.getMessage());
            }
        }
    }

    @Override
    public void shutdown() {
    }

    @Override
    public JsonObject loadPlayerStats(UUID uuid) {
        return readJson(new File(statsFolder, uuid.toString() + ".json"));
    }

    @Override
    public void savePlayerStats(UUID uuid, String name, JsonObject data) {
        File file = new File(statsFolder, uuid.toString() + ".json");
        writeStringToFile(file, gson.toJson(data));
    }

    @Override
    public void deletePlayerStats(UUID uuid) {
        File file = new File(statsFolder, uuid.toString() + ".json");
        if (file.exists()) {
            try {
                Files.delete(file.toPath());
            } catch (IOException e) {
                plugin.getLogger().warning("No se pudo borrar " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    @Override
    public JsonObject loadGlobalStats() {
        JsonObject json = readJson(globalFile);
        return json != null ? json : new JsonObject();
    }

    @Override
    public void saveGlobalStats(JsonObject data) {
        writeStringToFile(globalFile, gson.toJson(data));
    }

    @Override
    public void saveServerInfo(JsonObject data) {
        File infoFile = new File(plugin.getDataFolder(), DataDir.NAME + "/server_info.json");
        writeStringToFile(infoFile, gson.toJson(data));
    }

    /**
     * Lee un archivo JSON en UTF-8. Si está corrupto, lo renombra a *.corrupt-<fecha>
     * para no perderlo y devuelve null en lugar de lanzar una excepción.
     */
    private JsonObject readJson(File file) {
        if (!file.exists()) return null;
        try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            JsonElement element = JsonParser.parseReader(reader);
            if (element != null && element.isJsonObject()) {
                return element.getAsJsonObject();
            }
            throw new JsonParseException("El contenido no es un objeto JSON");
        } catch (JsonParseException | IllegalStateException e) {
            File backup = new File(file.getParentFile(), file.getName() + ".corrupt-" + System.currentTimeMillis());
            plugin.getLogger().severe("Archivo JSON corrupto: " + file.getPath() + " (" + e.getMessage() + "). Copia guardada en " + backup.getName());
            try {
                Files.move(file.toPath(), backup.toPath());
            } catch (IOException ex) {
                plugin.getLogger().warning("No se pudo respaldar " + file.getName() + ": " + ex.getMessage());
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Error leyendo " + file.getName() + ": " + e.getMessage());
        }
        return null;
    }

    /** Escritura atómica: escribe a un .tmp y lo mueve encima del archivo final. */
    private void writeStringToFile(File file, String content) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        File tmp = new File(parent, file.getName() + ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(tmp.toPath(), StandardCharsets.UTF_8)) {
                writer.write(content);
            }
            try {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save file " + file.getName() + ": " + e.getMessage());
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (IOException ignored) {
            }
        }
    }
}
