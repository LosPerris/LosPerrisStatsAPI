package me.gordi.mineapi.core.log;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;

/** Logs de depuración por categoría (general.debug.enabled + general.debug.sections.* en config.yml). */
public final class DebugLog {

    public enum Category { WEB, STORAGE, STATS, SECURITY, GENERAL }

    private final Logger logger;
    private volatile Set<Category> enabled = EnumSet.noneOf(Category.class);

    public DebugLog(Logger logger) {
        this.logger = logger;
    }

    public void reload(ConfigurationSection config) {
        Set<Category> set = EnumSet.noneOf(Category.class);
        // Formato antiguo: "debug: true"
        boolean global = config.isBoolean("debug") ? config.getBoolean("debug") : config.getBoolean("debug.enabled", false);
        if (global) {
            for (Category c : Category.values()) {
                if (config.isBoolean("debug") || config.getBoolean("debug.sections." + c.name().toLowerCase(Locale.ROOT), true)) set.add(c);
            }
        }
        enabled = set;
    }

    public boolean isEnabled(Category category) {
        return enabled.contains(category);
    }

    public void log(Category category, String message) {
        if (!enabled.contains(category)) return;
        logger.info("[DEBUG] [" + category + "] " + ChatColor.stripColor(message));
    }
}
