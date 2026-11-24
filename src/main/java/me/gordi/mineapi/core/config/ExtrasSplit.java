package me.gordi.mineapi.core.config;

import me.gordi.mineapi.core.module.ModuleCatalog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Paso de config v9 a v10 (versión 1.4), en memoria y sin tocar archivos (se prueba con JUnit; los archivos los
 * mueve {@link ConfigService}):
 * <ul>
 *   <li>los módulos de {@link ModuleCatalog#MOVED} pasan al addon LosPerrisExtras: sus interruptores van a
 *       {@code plugins/LosPerrisExtras/import.yml} y sus ajustes y datos a esa carpeta;</li>
 *   <li>achievements se une a milestones: settings/achievements.yml pasa a {@code gallery:} de milestones.yml
 *       ({@code gallery.enabled} = el antiguo {@code modules.achievements});</li>
 *   <li>el bloque {@code modules:} se reescribe desde la plantilla con los valores del usuario.</li>
 * </ul>
 * v9 -> v10: extras move to the addon, achievements merge into milestones.
 */
public final class ExtrasSplit {

    /** Carpeta del addon, junto a la del núcleo (plugins/LosPerrisExtras). */
    public static final String ADDON_DIR = "LosPerrisExtras";
    /** Archivo que el addon lee en su primer arranque (interruptores e idioma) y luego borra. */
    public static final String IMPORT = "import.yml";
    /** Datos propios de los módulos que se van (rutas relativas a la carpeta del plugin). */
    public static final List<String> DATA = List.of("data/seasons", "data/events", "data/holograms.json", "data/sanctions");
    /** Archivo antiguo de la galería de logros. */
    public static final String ACHIEVEMENTS = ConfigLayout.SETTINGS_DIR + "/achievements.yml";
    public static final String MILESTONES = ConfigLayout.SETTINGS_DIR + "/milestones.yml";

    private ExtrasSplit() {
    }

    /** Ajustes que se mudan al addon (relativos a la carpeta del plugin). */
    public static List<String> movedSettings() {
        List<String> out = new ArrayList<>();
        for (String id : ModuleCatalog.MOVED) out.add(ConfigLayout.extraFile(id));
        return out;
    }

    /** Resultado: config.yml nuevo, interruptores para el addon y si la galería de logros estaba activa. */
    public record Result(String config, Map<String, Boolean> addonModules, boolean gallery, boolean milestones, List<String> changes) {
    }

    /**
     * @param config   texto de config.yml v9
     * @param template texto de la plantilla config.yml v10 (mismo idioma)
     */
    public static Result config(String config, String template) throws InvalidConfigurationException {
        ConfigLayout.Doc doc = ConfigLayout.parse(config);
        ConfigLayout.Doc tpl = ConfigLayout.parse(template);
        YamlConfiguration user = ConfigLayout.section(doc, "modules");
        YamlConfiguration mods = ConfigLayout.section(tpl, "modules");
        List<String> changes = new ArrayList<>();
        Map<String, Boolean> addon = new LinkedHashMap<>();
        boolean gallery = false;
        for (String k : user.getKeys(false)) {
            Boolean on = switchOf(user, k);
            if (on == null) continue;
            String id = ModuleCatalog.legacyId(k);
            if (id == null) continue;
            if (ModuleCatalog.MOVED.contains(id)) {
                addon.put(id, on);
                changes.add("modules." + id + " -> " + ADDON_DIR + "/config.yml");
            } else if ("achievements".equals(id)) {
                gallery = on;
                changes.add("modules.achievements -> " + MILESTONES + " → gallery.enabled: " + on);
            } else if (mods.contains(id)) {
                mods.set(id, on);
            }
        }
        // no se encienden los hitos por su cuenta (dan recompensas): si estaban apagados, la galería tampoco funcionaba
        if (gallery && !mods.getBoolean("milestones", false)) {
            changes.add("modules.milestones sigue en false: la galería de logros solo se ve con los hitos activos / gallery needs milestones");
        }
        String text = withLead(config, "modules", ConfigLayout.lead(tpl, "modules"));
        text = ConfigLayout.replace(text, "modules", ConfigLayout.dump(mods), ConfigLayout.lead(tpl, "modules"));
        text = ConfigLayout.replaceTop(text, ConfigMigrator.VERSION_KEY, ConfigMigrator.VERSION_KEY + ": " + ConfigMigrator.CURRENT_VERSION, null);
        changes.add(ConfigMigrator.VERSION_KEY + ": 9 -> " + ConfigMigrator.CURRENT_VERSION);
        return new Result(text, addon, gallery, mods.getBoolean("milestones", false), changes);
    }

    /**
     * milestones.yml con la galería: {@code gallery:} de la plantilla, los valores de achievements.yml (si había)
     * y {@code enabled} según estuviera el módulo achievements.
     *
     * @param milestones   texto actual de milestones.yml (o la plantilla si no existía)
     * @param template     plantilla de milestones.yml
     * @param achievements ajustes antiguos de achievements.yml o null
     */
    public static String gallery(String milestones, String template, ConfigurationSection achievements, boolean on) throws InvalidConfigurationException {
        ConfigLayout.Doc tpl = ConfigLayout.parse(template);
        ConfigLayout.Doc cur = ConfigLayout.parse(milestones);
        YamlConfiguration g = ConfigLayout.section(tpl, "gallery");
        if (cur.has("gallery")) ConfigMigrator.overlay(ConfigLayout.section(cur, "gallery"), g, "");
        if (achievements != null) ConfigMigrator.overlay(achievements, g, "");
        g.set("enabled", on);
        return ConfigLayout.replace(milestones, "gallery", ConfigLayout.dump(g), ConfigLayout.lead(tpl, "gallery"));
    }

    /** Texto de import.yml para el addon. */
    public static String importYaml(String language, Map<String, Boolean> modules) {
        YamlConfiguration y = new YamlConfiguration();
        y.set("language", language);
        modules.forEach((id, on) -> y.set("modules." + id, on));
        return "# Lo escribió LosPerrisStatsAPI al pasar a v10; LosPerrisExtras lo lee una vez y lo borra.\n"
                + "# Written by LosPerrisStatsAPI (v10); LosPerrisExtras reads it once and deletes it.\n" + y.saveToString();
    }

    private static Boolean switchOf(ConfigurationSection s, String k) {
        if (s.isBoolean(k)) return s.getBoolean(k);
        if (s.isBoolean(k + ".enabled")) return s.getBoolean(k + ".enabled");
        return null;
    }

    /** Cambia el comentario superior de un bloque (si existe) por {@code lead}. */
    static String withLead(String text, String key, List<String> lead) {
        ConfigLayout.Doc doc = ConfigLayout.parse(text);
        ConfigLayout.Block b = doc.blocks.get(key);
        if (b == null || lead.isEmpty()) return text;
        List<String> out = new ArrayList<>(doc.lines.subList(0, b.lead()));
        out.addAll(lead);
        out.addAll(doc.lines.subList(b.start(), doc.lines.size()));
        return String.join("\n", out) + "\n";
    }
}
