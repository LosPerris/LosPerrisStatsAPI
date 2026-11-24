package me.gordi.mineapi.core.text;

import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Textos de configuración en varios idiomas: {@code name: "texto"} (todos los idiomas) o
 * {@code name: {es: "...", en: "..."}}. Lo usan misiones, títulos y eventos.
 */
public final class LangText {

    /** Clave que guarda un texto común a todos los idiomas. */
    public static final String ANY = "*";

    private LangText() {
    }

    /** Lee {@code section.key}; mapa vacío si no hay texto. */
    public static Map<String, String> parse(ConfigurationSection section, String key) {
        Map<String, String> out = new HashMap<>();
        if (section.isConfigurationSection(key)) {
            ConfigurationSection l = section.getConfigurationSection(key);
            for (String lang : l.getKeys(false)) {
                String v = l.getString(lang);
                if (v != null && !v.isBlank()) out.put(lang.toLowerCase(Locale.ROOT), v);
            }
        } else {
            String v = section.getString(key);
            if (v != null && !v.isBlank()) out.put(ANY, v);
        }
        return out.containsKey(ANY) ? Map.of(ANY, out.get(ANY)) : Map.copyOf(out);
    }

    /** Texto en {@code lang} (puede ser null), luego {@code fallback}, luego cualquiera; null si el mapa está vacío. */
    public static String pick(Map<String, String> texts, String lang, String fallback) {
        String v = texts.get(ANY);
        if (v == null && lang != null) v = texts.get(lang); // Map.of no admite get(null)
        if (v == null && fallback != null) v = texts.get(fallback);
        if (v == null && !texts.isEmpty()) v = texts.values().iterator().next();
        return v;
    }
}
