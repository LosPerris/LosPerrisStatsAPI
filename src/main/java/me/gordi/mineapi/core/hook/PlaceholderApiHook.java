package me.gordi.mineapi.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Acceso seguro a PlaceholderAPI (dependencia opcional). La clase interna solo se carga si el
 * plugin está activo, así nunca hay NoClassDefFoundError.
 */
public final class PlaceholderApiHook {

    private PlaceholderApiHook() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    /** Aplica los placeholders de PAPI si está instalado; si no, devuelve el texto igual. */
    public static String apply(Player player, String text) {
        if (text == null || text.indexOf('%') < 0 || !isAvailable()) return text;
        try {
            return Bridge.apply(player, text);
        } catch (NoClassDefFoundError | RuntimeException e) {
            return text;
        }
    }

    private static final class Bridge {
        static String apply(Player player, String text) {
            return me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(player, text);
        }
    }
}
