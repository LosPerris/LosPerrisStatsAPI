package me.gordi.mineapi.core.storage;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/** Crea el proveedor según database/database.yml. Si la base de datos falla, usa JSON local. */
public final class StorageFactory {

    private final JavaPlugin plugin;
    /** Firma del database.yml en uso; si cambia en /lpa reload se cambia de proveedor. */
    private String signature = "";

    public StorageFactory(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public StorageProvider create(FileConfiguration db) {
        if (db.getBoolean("enabled", false)) {
            String driver = db.getString("driver", "mysql").toLowerCase(Locale.ROOT);
            if (driver.equals("postgres") || driver.equals("postgresql") || driver.equals("supabase")) {
                plugin.getLogger().info("Almacenamiento: PostgreSQL/Supabase.");
                return new PostgreSQLStorage(plugin, db);
            }
            plugin.getLogger().info("Almacenamiento: MySQL.");
            return new MySQLStorage(plugin, db);
        }
        plugin.getLogger().info("Almacenamiento: JSON (archivos locales).");
        return new JsonStorage(plugin);
    }

    /** Crea e inicializa. Si la base de datos no conecta, cae a JSON hasta el próximo reload. */
    public StorageProvider createAndInit(FileConfiguration db) {
        signature = db.saveToString();
        StorageProvider storage = create(db);
        try {
            storage.init();
            return storage;
        } catch (RuntimeException e) {
            if (storage instanceof JsonStorage) throw e;
            plugin.getLogger().severe("No se pudo conectar a la base de datos: " + e.getMessage());
            plugin.getLogger().severe("Se usa JSON local hasta que corrija database.yml y haga /lpa reload.");
            signature = "";
            JsonStorage fallback = new JsonStorage(plugin);
            fallback.init();
            return fallback;
        }
    }

    /** true si database.yml cambió desde el último arranque/cambio (y actualiza la firma). */
    public boolean changed(FileConfiguration db) {
        String now = db.saveToString();
        if (now.equals(signature)) return false;
        signature = now;
        return true;
    }
}
