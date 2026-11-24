package me.gordi.mineapi.core.data;

import org.bukkit.plugin.Plugin;

import java.io.File;

/**
 * Carpeta de datos del plugin ({@code data/}, antes {@code server_data/}). Único sitio con su nombre: todo el
 * código construye sus rutas desde aquí. Data folder name (single place); v9 renamed it from server_data.
 */
public final class DataDir {

    /** Nombre actual de la carpeta de datos (dentro de la del plugin). */
    public static final String NAME = "data";
    /** Nombre hasta config v8 (la migración a v9 la renombra). */
    public static final String LEGACY = "server_data";

    private DataDir() {
    }

    /** Carpeta de datos. */
    public static File root(Plugin plugin) {
        return new File(plugin.getDataFolder(), NAME);
    }

    /** Archivo o carpeta dentro de la de datos, p. ej. {@code file(plugin, "history.json")}. */
    public static File file(Plugin plugin, String relative) {
        return new File(root(plugin), relative);
    }

    /** Ruta relativa a la carpeta del plugin, p. ej. {@code path("players")} = {@code data/players}. */
    public static String path(String relative) {
        return NAME + "/" + relative;
    }

    /**
     * Ruta de {@code general.stats-folder} de una versión anterior pasada a la carpeta nueva: lo que empezaba
     * por {@code server_data/} pasa a {@code data/}; una ruta propia del usuario se respeta tal cual.
     */
    public static String migrate(String old) {
        if (old == null) return null;
        String p = old.replace('\\', '/');
        if (p.equals(LEGACY)) return NAME;
        if (p.startsWith(LEGACY + "/")) return NAME + p.substring(LEGACY.length());
        return old;
    }
}
