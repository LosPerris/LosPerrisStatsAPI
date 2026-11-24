package me.gordi.mineapi.core.config;

import java.util.List;
import java.util.Map;

/**
 * Dónde estaba cada cosa en config v8 (solo para leer una instalación v8, v7 o anterior al migrar a v9).
 * v8: small modules were sections of config.yml (with the old ids {@code public} and {@code console});
 * web, events, scoreboard, milestones, quests and titles owned a whole file in the plugin folder; both
 * Discord modules were sections of discord.yml; database.yml lived in database/.
 */
public final class LegacyLayout {

    /** Archivos enteros de v8 (en la carpeta del plugin). */
    public static final Map<String, String> OWN_FILES = Map.of(
            "web", "web.yml",
            "events", "events.yml",
            "scoreboard", "scoreboard.yml",
            "milestones", "milestones.yml",
            "quests", "quests.yml",
            "titles", "titles.yml");

    public static final String DISCORD = "discord.yml";
    public static final String DATABASE = "database/database.yml";

    /** Todos los archivos de configuración de v8. */
    public static final List<String> FILES = List.of(ConfigLayout.CONFIG, "web.yml", DISCORD, "events.yml", "scoreboard.yml",
            "milestones.yml", "quests.yml", "titles.yml");

    /** Id de v9 -> nombre de su sección en v8/v7 (solo los que cambiaron). */
    private static final Map<String, String> OLD_NAMES = Map.of("public-page", "public", "web-console", "console");

    private LegacyLayout() {
    }

    /** Nombre de la sección del módulo en config.yml v7/v8. */
    public static String section(String id) {
        return OLD_NAMES.getOrDefault(id, id);
    }

    /** Ubicación en v8 de los ajustes de un módulo (id de v9). */
    public static ConfigLayout.Place place(String id) {
        String own = OWN_FILES.get(id);
        if (own != null) return new ConfigLayout.Place(own, null);
        if ("discord-webhook".equals(id) || "discord-bot".equals(id)) return new ConfigLayout.Place(DISCORD, id);
        return new ConfigLayout.Place(ConfigLayout.CONFIG, section(id));
    }
}
