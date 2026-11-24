package me.gordi.mineapi.core.data;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.storage.StorageProvider;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Datos de todos los jugadores, indexados por UUID (con un índice de nombres en minúsculas para
 * búsquedas O(1)). Toda la E/S va a UN hilo dedicado ("LosPerris-Storage"), en orden.
 * <p>
 * Reglas: lecturas y escrituras de la caché bajo {@code lock}; nunca E/S con el lock tomado;
 * los módulos aportan sus campos con {@link Contributor} (p. ej. el módulo stats copia las
 * estadísticas de Bukkit antes de cada guardado).
 */
public final class PlayerDataService {

    /** Aporta campos al JSON del jugador. Se llama en el hilo principal, con el lock tomado. */
    @FunctionalInterface
    public interface Contributor {
        void contribute(Player player, JsonObject data);
    }

    private static final long PRELOGIN_TIMEOUT_SECONDS = 10;

    private final MineAPI plugin;
    private final Object lock = new Object();
    private final Map<UUID, JsonObject> players = new HashMap<>();
    private final Map<String, UUID> names = new HashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final List<Contributor> contributors = new CopyOnWriteArrayList<>();
    private final ExecutorService io;
    private volatile StorageProvider storage;

    /** @param storage proveedor ya inicializado */
    public PlayerDataService(MineAPI plugin, StorageProvider storage) {
        this.plugin = plugin;
        this.storage = storage;
        this.io = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "LosPerris-Storage");
            t.setDaemon(true);
            return t;
        });
        loadGlobal();
    }

    // ------------------------------------------------------------------ arranque + migración

    private void loadGlobal() {
        JsonObject root = storage.loadGlobalStats();
        if (root == null) root = UuidMigration.empty();
        if (!UuidMigration.isMigrated(root)) {
            boolean hasData = !root.entrySet().isEmpty();
            if (hasData) backupLegacy(root);
            Map<String, UUID> known = knownPlayers();
            UuidMigration.Result r = UuidMigration.migrate(root, n -> known.get(n.toLowerCase(Locale.ROOT)));
            root = r.root();
            if (hasData) {
                plugin.getLogger().info("Migración a UUID completada: " + r.migrated() + " jugadores, "
                        + r.guessed() + " con UUID deducido (se corrige al entrar), " + r.merged() + " duplicados unidos.");
                storage.saveGlobalStats(root); // una sola vez, al arrancar
            }
        }
        JsonObject list = root.getAsJsonObject(UuidMigration.PLAYERS_KEY);
        synchronized (lock) {
            for (Map.Entry<String, JsonElement> e : list.entrySet()) {
                UUID uuid = UuidMigration.parse(e.getKey());
                if (uuid == null || !e.getValue().isJsonObject()) continue;
                JsonObject data = e.getValue().getAsJsonObject();
                players.put(uuid, data);
                String name = str(data, "name");
                if (name != null) indexName(name, uuid);
            }
        }
    }

    private Map<String, UUID> knownPlayers() {
        Map<String, UUID> map = new HashMap<>();
        try {
            for (OfflinePlayer p : Bukkit.getOfflinePlayers()) {
                if (p.getName() != null) map.put(p.getName().toLowerCase(Locale.ROOT), p.getUniqueId());
            }
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudo leer la lista de jugadores del servidor: " + e.getMessage());
        }
        return map;
    }

    private void backupLegacy(JsonObject root) {
        try {
            File dir = new File(plugin.getDataFolder(), "backups");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("no se pudo crear " + dir);
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            File out = new File(dir, "all_stats-pre-uuid-" + stamp + ".json");
            Files.write(out.toPath(), new GsonBuilder().setPrettyPrinting().create().toJson(root).getBytes(StandardCharsets.UTF_8));
            plugin.getLogger().info("Copia de seguridad antes de migrar a UUID: backups/" + out.getName());
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo crear la copia previa a la migración (" + e.getMessage() + "). Se migra igualmente; el original queda en el almacenamiento hasta el primer guardado.");
        }
    }

    // ------------------------------------------------------------------ utilidades internas

    private void indexName(String name, UUID uuid) {
        names.put(name.toLowerCase(Locale.ROOT), uuid);
    }

    private JsonObject byName(String name) {
        if (name == null) return null;
        UUID uuid = names.get(name.toLowerCase(Locale.ROOT));
        return uuid == null ? null : players.get(uuid);
    }

    private void io(Runnable task) {
        try {
            io.execute(() -> {
                try {
                    task.run();
                } catch (Exception e) {
                    plugin.getLogger().warning("Error en tarea de almacenamiento: " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            task.run(); // apagando: se ejecuta en este hilo
        }
    }

    private void debug(String msg) {
        plugin.debug().log(DebugLog.Category.STORAGE, msg);
    }

    public static String str(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
    }

    public static double num(JsonObject o, String key) {
        if (o == null) return 0;
        JsonElement e = o.get(key);
        if (e == null || !e.isJsonPrimitive()) return 0;
        try {
            return e.getAsDouble();
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    // ------------------------------------------------------------------ contribuciones

    public void addContributor(Contributor c) { contributors.add(c); }
    public void removeContributor(Contributor c) { contributors.remove(c); }

    // ------------------------------------------------------------------ carga / descarga

    /** Desde AsyncPlayerPreLoginEvent (hilo asíncrono): espera la carga como máximo 10 s. */
    public void preload(UUID uuid, String name) {
        try {
            Future<JsonObject> f = io.submit(() -> storage.loadPlayerStats(uuid));
            applyLoaded(uuid, name, f.get(PRELOGIN_TIMEOUT_SECONDS, TimeUnit.SECONDS));
            loaded.add(uuid);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            plugin.getLogger().warning("No se pudieron precargar los datos de " + name + " (" + e.getMessage() + "). Se reintentará al entrar.");
        }
    }

    /** Carga de respaldo (jugadores conectados durante un /reload). Callback en el hilo principal. */
    public void loadAsync(Player player, Consumer<Player> callback) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        io(() -> {
            JsonObject json = storage.loadPlayerStats(uuid);
            plugin.scheduler().sync(() -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null) return;
                applyLoaded(uuid, name, json);
                loaded.add(uuid);
                if (callback != null) callback.accept(online);
            });
        });
    }

    private void applyLoaded(UUID uuid, String name, JsonObject json) {
        synchronized (lock) {
            JsonObject current = players.get(uuid);
            if (json == null) json = current;
            // Otra entrada con el mismo nombre: si su UUID era deducido (migración), es este jugador
            UUID other = names.get(name.toLowerCase(Locale.ROOT));
            if (other != null && !other.equals(uuid)) {
                JsonObject o = players.get(other);
                if (o != null && o.has(UuidMigration.GUESSED_KEY)) {
                    players.remove(other);
                    if (json == null) json = o;
                    plugin.getLogger().info("UUID corregido para " + name + ": " + other + " -> " + uuid);
                }
            }
            if (current != null) {
                String old = str(current, "name");
                if (old != null && !old.equals(name)) {
                    if (!old.equalsIgnoreCase(name)) plugin.getLogger().info("Cambio de nombre detectado (" + uuid + "): " + old + " -> " + name);
                    if (uuid.equals(names.get(old.toLowerCase(Locale.ROOT)))) names.remove(old.toLowerCase(Locale.ROOT));
                }
            }
            if (json == null) json = new JsonObject();
            json.addProperty("uuid", uuid.toString());
            json.addProperty("name", name);
            json.remove(UuidMigration.GUESSED_KEY);
            players.put(uuid, json);
            indexName(name, uuid);
        }
    }

    public boolean isLoaded(UUID uuid) { return loaded.contains(uuid); }

    public void unload(UUID uuid) { loaded.remove(uuid); }

    // ------------------------------------------------------------------ sincronizar y guardar

    /** Copia datos del jugador conectado a la caché (hilo principal, sin E/S). */
    public void sync(Player player) {
        String ip = player.getAddress() != null && player.getAddress().getAddress() != null
                ? player.getAddress().getAddress().getHostAddress() : null;
        String name = player.getName();
        synchronized (lock) {
            JsonObject json = liveOrCreate(player);
            for (Contributor c : contributors) c.contribute(player, json);
            if (ip != null) json.addProperty("ip_address", ip);
            json.addProperty("last_updated", System.currentTimeMillis());
            json.addProperty("name", name);
            json.addProperty("skin", "https://minotar.net/helm/" + name + "/100.png");
            json.addProperty("skin_body", "https://minotar.net/armor/body/" + name + "/100.png");
        }
    }

    /** Requiere el lock. */
    private JsonObject liveOrCreate(Player player) {
        JsonObject json = players.get(player.getUniqueId());
        if (json == null) {
            json = new JsonObject();
            json.addProperty("uuid", player.getUniqueId().toString());
            json.addProperty("name", player.getName());
            players.put(player.getUniqueId(), json);
            indexName(player.getName(), player.getUniqueId());
        }
        return json;
    }

    public void savePlayer(Player player, boolean async) {
        sync(player);
        queueSave(player.getUniqueId(), async);
    }

    /** Encola el guardado del jugador (copia hecha ahora, escritura en el hilo de E/S). */
    public void queueSave(UUID uuid, boolean async) {
        JsonObject copy;
        String name;
        synchronized (lock) {
            JsonObject live = players.get(uuid);
            if (live == null) return;
            copy = live.deepCopy();
            name = str(live, "name");
        }
        if (async) io(() -> storage.savePlayerStats(uuid, name, copy));
        else storage.savePlayerStats(uuid, name, copy);
    }

    public void saveGlobal(boolean async) {
        debug("Guardando datos globales (async=" + async + ")");
        JsonObject root = UuidMigration.empty();
        JsonObject list = root.getAsJsonObject(UuidMigration.PLAYERS_KEY);
        synchronized (lock) {
            for (Map.Entry<UUID, JsonObject> e : players.entrySet()) list.add(e.getKey().toString(), e.getValue().deepCopy());
        }
        if (async) io(() -> storage.saveGlobalStats(root));
        else storage.saveGlobalStats(root);
    }

    /** Estado del servidor para webs externas (hilo principal). */
    public void saveServerInfo(boolean async) {
        JsonObject json = new JsonObject();
        json.addProperty("online_count", Bukkit.getOnlinePlayers().size());
        json.addProperty("max_players", Bukkit.getMaxPlayers());
        json.addProperty("status", "ONLINE");
        json.addProperty("last_updated", System.currentTimeMillis());
        JsonArray online = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) online.add(p.getName());
        json.add("online_players", online);
        if (async) io(() -> storage.saveServerInfo(json));
        else storage.saveServerInfo(json);
    }

    /** Guarda jugadores conectados, global e info (hilo principal; E/S en cola). */
    public void saveAll() {
        for (Player p : Bukkit.getOnlinePlayers()) savePlayer(p, true);
        saveGlobal(true);
        saveServerInfo(true);
    }

    // ------------------------------------------------------------------ lectura / escritura

    /** Lee el JSON vivo del jugador con el lock tomado. f recibe null si no existe. */
    public <T> T read(UUID uuid, Function<JsonObject, T> f) {
        synchronized (lock) {
            return f.apply(players.get(uuid));
        }
    }

    public <T> T read(String name, Function<JsonObject, T> f) {
        synchronized (lock) {
            return f.apply(byName(name));
        }
    }

    /** Recorre todos los jugadores con el lock tomado (solo lectura y rápido: copie lo que necesite). */
    public void forEach(Consumer<JsonObject> action) {
        synchronized (lock) {
            for (JsonObject o : players.values()) action.accept(o);
        }
    }

    /** Modifica el JSON del jugador conectado (lo crea si hace falta). Hilo principal. */
    public void mutate(Player player, Consumer<JsonObject> change) {
        synchronized (lock) {
            change.accept(liveOrCreate(player));
        }
    }

    /** Modifica el JSON de un jugador existente. @return false si no existe */
    public boolean mutate(UUID uuid, Consumer<JsonObject> change) {
        synchronized (lock) {
            JsonObject json = players.get(uuid);
            if (json == null) return false;
            change.accept(json);
            return true;
        }
    }

    public double getNumber(UUID uuid, String key) {
        synchronized (lock) {
            return num(players.get(uuid), key);
        }
    }

    public double getNumber(String name, String key) {
        synchronized (lock) {
            return num(byName(name), key);
        }
    }

    /** Copia independiente del jugador (por nombre, sin distinguir mayúsculas) o null. */
    public JsonObject snapshot(String name) {
        synchronized (lock) {
            JsonObject json = byName(name);
            return json == null ? null : json.deepCopy();
        }
    }

    public UUID resolve(String name) {
        if (name == null) return null;
        synchronized (lock) {
            return names.get(name.toLowerCase(Locale.ROOT));
        }
    }

    /** Nombre con sus mayúsculas reales o null. */
    public String realName(String name) {
        synchronized (lock) {
            JsonObject json = byName(name);
            return json == null ? null : str(json, "name");
        }
    }

    /** Quita al jugador de la caché y borra sus datos guardados. */
    public void delete(UUID uuid) {
        synchronized (lock) {
            JsonObject json = players.remove(uuid);
            String name = json == null ? null : str(json, "name");
            if (name != null && uuid.equals(names.get(name.toLowerCase(Locale.ROOT)))) names.remove(name.toLowerCase(Locale.ROOT));
        }
        io(() -> storage.deletePlayerStats(uuid));
    }

    /**
     * Vista compatible con la API antigua: objeto con claves = nombre del jugador.
     * @param sanitize quita ip_address
     */
    public JsonObject nameKeyedSnapshot(boolean sanitize) {
        JsonObject out = new JsonObject();
        synchronized (lock) {
            for (JsonObject src : players.values()) {
                String name = str(src, "name");
                if (name == null) continue;
                JsonObject copy = src.deepCopy();
                if (sanitize) copy.remove("ip_address");
                copy.remove(UuidMigration.GUESSED_KEY);
                out.add(name, copy);
            }
        }
        return out;
    }

    public int size() {
        synchronized (lock) {
            return players.size();
        }
    }

    // ------------------------------------------------------------------ almacenamiento

    /**
     * Cambia de proveedor en caliente (cambió database.yml). Si el nuevo no arranca se mantiene
     * el actual. Hilo principal.
     */
    public void replaceStorage(StorageProvider next) {
        for (Player p : Bukkit.getOnlinePlayers()) sync(p);
        JsonObject root = UuidMigration.empty();
        JsonObject list = root.getAsJsonObject(UuidMigration.PLAYERS_KEY);
        Map<UUID, JsonObject> online = new HashMap<>();
        synchronized (lock) {
            for (Map.Entry<UUID, JsonObject> e : players.entrySet()) list.add(e.getKey().toString(), e.getValue().deepCopy());
            for (Player p : Bukkit.getOnlinePlayers()) {
                JsonObject live = players.get(p.getUniqueId());
                if (live != null) online.put(p.getUniqueId(), live.deepCopy());
            }
        }
        io(() -> {
            try {
                next.init();
            } catch (Exception e) {
                plugin.getLogger().severe("No se pudo cambiar el almacenamiento: " + e.getMessage() + ". Se mantiene el anterior.");
                return;
            }
            StorageProvider old = storage;
            storage = next;
            try {
                old.shutdown();
            } catch (Exception e) {
                plugin.getLogger().warning("Error cerrando el almacenamiento anterior: " + e.getMessage());
            }
            next.saveGlobalStats(root);
            for (Map.Entry<UUID, JsonObject> e : online.entrySet()) next.savePlayerStats(e.getKey(), str(e.getValue(), "name"), e.getValue());
            plugin.getLogger().info("Almacenamiento cambiado a " + next.getClass().getSimpleName() + " y datos copiados.");
        });
    }

    /** ¿Responde el almacenamiento? Hace E/S: llamar desde un hilo asíncrono. */
    public boolean storageHealthy() {
        try {
            return storage.ping();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Nombre del proveedor activo (JsonStorage, MySQLStorage...). */
    public String storageName() {
        return storage.getClass().getSimpleName();
    }

    /** Espera los guardados pendientes (máx. 15 s) y cierra el almacenamiento. */
    public void shutdown() {
        io.shutdown();
        try {
            if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("Los guardados pendientes tardaron más de 15 s; se fuerza el cierre.");
                io.shutdownNow();
            }
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        storage.shutdown();
    }

    /** Lista de UUID en caché (copia). */
    public List<UUID> uuids() {
        synchronized (lock) {
            return new ArrayList<>(players.keySet());
        }
    }
}
