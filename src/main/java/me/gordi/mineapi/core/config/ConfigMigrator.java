package me.gordi.mineapi.core.config;

import me.gordi.mineapi.core.module.ModuleCatalog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Migración de la configuración entre versiones (clave {@code config-version}) y relleno de claves
 * nuevas copiando sus comentarios. Es código puro (sin servidor): se prueba con JUnit.
 *
 * <p>v9 (actual): config.yml con la cabecera, las secciones del núcleo y {@code advanced}; settings/ con
 * web.yml, discord.yml, database.yml y un archivo por extra activo (ver {@link ConfigLayout#place}). v8 tenía
 * los módulos pequeños en config.yml y seis archivos propios en la raíz ({@link LegacyLayout}); v7 todo en
 * config.yml y solo las listas aparte; v6 un archivo por módulo en {@code modules/<id>.yml}; v5 y anteriores un
 * config.yml largo. Todas se reducen a «cabecera + YAML de cada módulo» en memoria y se compone v9 con
 * {@link #toV9}.
 */
public final class ConfigMigrator {

    public static final int CURRENT_VERSION = 10;
    /** Versión de la disposición en archivos (settings/); v10 solo separa los extras ({@link ExtrasSplit}). */
    public static final int V9_VERSION = 9;
    public static final String VERSION_KEY = "config-version";

    /**
     * Secciones que el usuario edita como listas libres, por módulo (rutas dentro de su archivo).
     * Si ya existen, no se rellenan sus hijos: lo borrado a propósito no reaparece en cada arranque.
     */
    static final Map<String, List<String>> USER_MAPS = Map.of(
            "milestones", List.of("stats"),
            "quests", List.of("daily", "weekly"),
            "titles", List.of("titles"),
            "streaks", List.of("rewards"),
            "periods", List.of("rewards"),
            "seasons", List.of("rewards"),
            "events", List.of("rewards", "presets"),
            "discord-bot", List.of("roles.milestones", "roles.top"));

    /** Claves de config.yml (v5) que pasan a {@code general.*} con el mismo nombre. */
    static final List<String> GENERAL_KEYS = List.of("language", "per-player-language", "timezone", "prefix",
            "stats-folder", "disabled-worlds", "auto-save-interval");

    /** Sección de config.yml (v5) -> {módulo, ruta dentro de modules/<módulo>.yml ("" = raíz)}. */
    static final Map<String, String[]> LEGACY = new LinkedHashMap<>();

    static {
        legacy("tracked-stats", "stats", "tracked");
        legacy("computed-stats-interval", "stats", "computed-stats-interval");
        legacy("leaderboard-cache", "stats", "leaderboard-cache");
        legacy("afk-timeout", "afk", "timeout");
        legacy("port", "web", "port");
        legacy("web", "web", "");
        legacy("security", "web", "security");
        legacy("discord", "discord-webhook", "");
        legacy("discordbot", "discord-bot", "");
        legacy("playtime-rewards", "playtime", "");
        legacy("public", "public-page", "");
        legacy("console", "web-console", "");
        for (String same : List.of("history", "periods", "achievements", "scoreboard", "moderation",
                "alerts", "heatmap", "backups", "updates", "streaks", "seasons", "titles", "events", "holograms")) {
            legacy(same, same, "");
        }
    }

    /** Archivos sueltos de v5 que pasan a ser el archivo de su módulo. */
    public static final List<String> LEGACY_FILES = List.of("milestones", "quests", "titles");

    /**
     * Claves renombradas en v9, por módulo: nombre nuevo -> nombre antiguo. El antiguo sigue valiendo (alias) y,
     * si el usuario lo tiene, no se añade el nuevo al completar claves. Renamed keys (new -> old alias).
     */
    public static final Map<String, Map<String, String>> RENAMED = Map.of(
            "history", Map.of("update-every", "interval-minutes"),
            "updates", Map.of("check-every", "check-hours"),
            "heatmap", Map.of("sample-every", "sample-minutes"));
    /** Unidad (segundos) del número antiguo de cada clave renombrada. */
    /** Claves de tiempo que conservan su nombre en v9: módulo -> clave -> unidad antigua en segundos (número suelto). */
    static final Map<String, Map<String, Long>> DURATION_KEYS = Map.of(
            "stats", Map.of("leaderboard-cache", 1L, "computed-stats-interval", 1L),
            "periods", Map.of("update-interval", 1L),
            "seasons", Map.of("update-interval", 1L),
            "achievements", Map.of("update-interval", 1L),
            "holograms", Map.of("update-interval", 1L),
            "afk", Map.of("timeout", 1L));

    static final Map<String, Long> RENAMED_UNIT = Map.of("interval-minutes", 60L, "check-hours", 3600L, "sample-minutes", 60L);

    /** Claves de la cabecera de config.yml renombradas o movidas en v9: nueva -> antigua. */
    public static final Map<String, String> HEAD_RENAMED = Map.of(
            "general.auto-save", "general.auto-save-interval",
            "advanced.stats-folder", "general.stats-folder",
            "advanced.debug", "general.debug");

    private ConfigMigrator() {
    }

    private static void legacy(String oldKey, String module, String newPath) {
        LEGACY.put(oldKey, new String[]{module, newPath});
    }

    /** Resultado de la migración a v6: config.yml nuevo, archivos de módulo y cambios legibles. */
    public record Result(YamlConfiguration main, Map<String, YamlConfiguration> modules, List<String> changes) {
    }

    /**
     * Migra una configuración v1-v5 al formato v6 (en memoria; no toca archivos).
     *
     * @param old            config.yml antiguo
     * @param oldFiles       milestones/quests/titles antiguos (id del módulo -> YAML); pueden faltar
     * @param mainTemplate   config.yml v6 del JAR (en el idioma elegido)
     * @param moduleTemplate plantilla {@code modules/<id>.yml} del JAR, o null si el módulo no tiene
     */
    public static Result migrateToV6(YamlConfiguration old, Map<String, YamlConfiguration> oldFiles,
                                     YamlConfiguration mainTemplate, Function<String, YamlConfiguration> moduleTemplate) {
        List<String> changes = new ArrayList<>();
        int version = old.getInt(VERSION_KEY, 1);
        legacySteps(old, version, changes);

        // ---------- config.yml: general + modules
        YamlConfiguration main = copy(mainTemplate);
        for (String k : GENERAL_KEYS) {
            if (old.contains(k) && !old.isConfigurationSection(k)) main.set("general." + k, old.get(k));
        }
        if (old.contains("periods.week-start")) main.set("general.week-start", old.getString("periods.week-start"));
        if (old.isBoolean("debug")) {
            main.set("general.debug.enabled", old.getBoolean("debug"));
        } else if (old.isConfigurationSection("debug")) {
            overlay(old.getConfigurationSection("debug"), main, "general.debug");
        }
        ConfigurationSection mods = old.getConfigurationSection("modules");
        if (mods != null) {
            for (String key : mods.getKeys(false)) {
                String id = ModuleCatalog.legacyId(key);
                if (id == null) continue;
                Boolean on = mods.isBoolean(key) ? Boolean.valueOf(mods.getBoolean(key))
                        : mods.isBoolean(key + ".enabled") ? Boolean.valueOf(mods.getBoolean(key + ".enabled")) : null;
                if (on != null) main.set("modules." + id, on);
            }
        }
        // El antiguo 'enabled' de milestones.yml era un segundo interruptor: se combinan
        YamlConfiguration oldMilestones = oldFiles.get("milestones");
        if (oldMilestones != null && oldMilestones.isBoolean("enabled") && !oldMilestones.getBoolean("enabled")) {
            main.set("modules.milestones", false);
            changes.add("milestones.yml enabled: false -> modules.milestones: false");
        }
        main.set(VERSION_KEY, CURRENT_VERSION);

        // ---------- modules/<id>.yml
        Map<String, YamlConfiguration> files = new LinkedHashMap<>();
        for (ModuleCatalog.Info info : ModuleCatalog.migration()) {
            if (!info.hasSettings()) continue;
            YamlConfiguration tpl = moduleTemplate.apply(info.id());
            YamlConfiguration out = tpl != null ? copy(tpl) : new YamlConfiguration();
            boolean touched = false;
            for (Map.Entry<String, String[]> e : LEGACY.entrySet()) {
                if (!e.getValue()[0].equals(info.id()) || !old.contains(e.getKey())) continue;
                String path = e.getValue()[1];
                if (old.isConfigurationSection(e.getKey())) {
                    ConfigurationSection src = old.getConfigurationSection(e.getKey());
                    if ("periods".equals(e.getKey())) src = without(src, "week-start");
                    clearUserMaps(out, info.id(), src, path);
                    overlay(src, out, path);
                } else if (!path.isEmpty()) {
                    out.set(path, old.get(e.getKey()));
                }
                changes.add(e.getKey() + " -> " + info.settings() + (path.isEmpty() ? "" : ": " + path));
                touched = true;
            }
            YamlConfiguration loose = oldFiles.get(info.id());
            if (loose != null) {
                ConfigurationSection src = "milestones".equals(info.id()) ? without(loose, "enabled") : loose;
                clearUserMaps(out, info.id(), src, "");
                overlay(src, out, "");
                changes.add(info.id() + ".yml -> " + info.where());
                touched = true;
            }
            if (tpl != null || touched) files.put(info.id(), out);
        }
        changes.add(VERSION_KEY + ": " + version + " -> " + CURRENT_VERSION);
        return new Result(main, files, changes);
    }

    /**
     * Resultado de la migración a v9: texto de cada archivo (ruta relativa -> texto), extras cuyo archivo no se
     * crea (apagados y sin cambios) y cambios legibles.
     */
    public record V9(Map<String, String> files, List<String> skipped, List<String> changes) {
    }

    /**
     * Compone los archivos v9 a partir de las plantillas (ruta -> texto, con comentarios; deben estar config.yml,
     * settings/web.yml, settings/discord.yml y settings/&lt;extra&gt;.yml de cada extra con ajustes) y de los valores
     * del usuario: {@code head} (general, modules y, si existe, advanced; en el formato de v5-v8) y la
     * configuración de cada módulo (id actual -> YAML con sus claves en la raíz). Renombra lo que cambió en v9
     * (auto-save, stats-folder y debug a advanced, server_data a data, claves de tiempo con unidades). El archivo
     * de un extra solo se crea si el extra está activo o el usuario cambió algo. No toca archivos.
     */
    public static V9 toV9(ConfigurationSection head, Map<String, ? extends ConfigurationSection> modules, Map<String, String> templates) {
        List<String> changes = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        Map<String, String> out = new LinkedHashMap<>();
        try {
            String tplText = templates.get(ConfigLayout.CONFIG);
            ConfigLayout.Doc tpl = ConfigLayout.parse(tplText);
            YamlConfiguration general = ConfigLayout.section(tpl, "general");
            YamlConfiguration mods = ConfigLayout.section(tpl, "modules");
            YamlConfiguration advanced = ConfigLayout.section(tpl, "advanced");
            ConfigurationSection ug = head.getConfigurationSection("general");
            if (ug != null) {
                overlay(without(ug, List.of("stats-folder", "debug", "auto-save-interval")), general, "");
                if (ug.contains("auto-save-interval") && !ug.contains("auto-save")) {
                    Object v = ug.get("auto-save-interval");
                    String d = v instanceof Number n ? compact(n, 1L) : String.valueOf(v);
                    general.set("auto-save", d);
                    changes.add("general.auto-save-interval: " + v + " -> general.auto-save: \"" + d + "\"");
                }
                if (ug.isString("stats-folder")) {
                    String f = me.gordi.mineapi.core.data.DataDir.migrate(ug.getString("stats-folder"));
                    advanced.set("stats-folder", f);
                    changes.add("general.stats-folder -> advanced.stats-folder: " + f);
                }
                if (ug.isBoolean("debug")) {
                    advanced.set("debug.enabled", ug.getBoolean("debug"));
                    changes.add("general.debug -> advanced.debug");
                } else if (ug.isConfigurationSection("debug")) {
                    overlay(ug.getConfigurationSection("debug"), advanced, "debug");
                    changes.add("general.debug -> advanced.debug");
                }
            }
            ConfigurationSection ua = head.getConfigurationSection("advanced");
            if (ua != null) overlay(ua, advanced, "");
            if (advanced.isString("stats-folder")) advanced.set("stats-folder", me.gordi.mineapi.core.data.DataDir.migrate(advanced.getString("stats-folder")));
            ConfigurationSection um = head.getConfigurationSection("modules");
            if (um != null) {
                for (String k : um.getKeys(false)) {
                    String id = ModuleCatalog.legacyId(k);
                    Boolean on = um.isBoolean(k) ? Boolean.valueOf(um.getBoolean(k))
                            : um.isBoolean(k + ".enabled") ? Boolean.valueOf(um.getBoolean(k + ".enabled")) : null;
                    if (id == null || on == null) continue;
                    mods.set(id, on);
                    if (!id.equals(k)) changes.add("modules." + k + " -> modules." + id);
                }
            }
            String text = tplText;
            text = ConfigLayout.replace(text, "general", ConfigLayout.dump(general), null);
            text = ConfigLayout.replace(text, "modules", ConfigLayout.dump(mods), null);
            text = ConfigLayout.replace(text, "advanced", ConfigLayout.dump(advanced), null);
            // la plantilla ya es v10; este paso deja la disposición v9 y el siguiente (ExtrasSplit) la completa
            text = ConfigLayout.replaceTop(text, VERSION_KEY, VERSION_KEY + ": " + V9_VERSION, null);
            out.put(ConfigLayout.CONFIG, text);
            for (String f : ConfigLayout.FILES) {
                if (!f.equals(ConfigLayout.CONFIG) && !f.equals(ConfigLayout.DATABASE) && templates.containsKey(f)) out.put(f, templates.get(f));
            }

            for (ModuleCatalog.Info info : ModuleCatalog.migration()) {
                if (!info.hasSettings()) continue;
                String id = info.id();
                ConfigLayout.Place place = ConfigLayout.place(id);
                String fileTpl = templates.get(place.file());
                ConfigurationSection mod = modules.get(id);
                if (mod != null) mod = v9Keys(id, mod, changes);
                if (fileTpl == null) {
                    // módulo antiguo (ya no está en el JAR): sus valores tal cual; el paso a v10 los lleva a su sitio
                    if (mod != null) {
                        YamlConfiguration y = new YamlConfiguration();
                        overlay(mod, y, "");
                        out.put(place.file(), ConfigLayout.dump(y));
                        changes.add(id + " -> " + place.label());
                    }
                    continue;
                }
                boolean extra = ConfigLayout.isExtraFile(place.file());
                if (extra) {
                    boolean on = mods.getBoolean(id, false);
                    boolean custom = mod != null && customized(id, mod, fileTpl);
                    if (!on && !custom) {
                        skipped.add(id);
                        continue;
                    }
                    out.put(place.file(), mod == null ? fileTpl : composeWhole(fileTpl, id, mod, fileTpl));
                    changes.add(id + " -> " + place.label());
                    continue;
                }
                if (mod == null) continue;
                String current = out.get(place.file());
                out.put(place.file(), place.whole()
                        ? composeWhole(current, id, mod, fileTpl)
                        : composeSection(current, id, place.section(), mod, ConfigLayout.parse(fileTpl)));
                changes.add(id + " -> " + place.label());
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            throw new IllegalStateException("plantilla inválida / invalid template", e);
        }
        for (String k : modules.keySet()) {
            if (ModuleCatalog.legacyId(k) == null) changes.add(k + ": módulo desconocido, se conserva solo en la copia / unknown module, kept only in the backup");
        }
        changes.add(VERSION_KEY + ": " + head.getInt(VERSION_KEY, 1) + " -> " + V9_VERSION);
        return new V9(out, skipped, changes);
    }

    /** Copia de los ajustes de un módulo con los nombres de v9 (claves de tiempo, carpetas data/). */
    static ConfigurationSection v9Keys(String id, ConfigurationSection mod, List<String> changes) {
        YamlConfiguration y = new YamlConfiguration();
        overlay(mod, y, "");
        for (Map.Entry<String, String> e : RENAMED.getOrDefault(id, Map.of()).entrySet()) {
            String old = e.getValue();
            if (!y.contains(old) || y.contains(e.getKey())) continue;
            Object v = y.get(old);
            String d = v instanceof Number n ? compact(n, RENAMED_UNIT.getOrDefault(old, 1L)) : String.valueOf(v);
            y.set(old, null);
            y.set(e.getKey(), d);
            changes.add(id + "." + old + ": " + v + " -> " + id + "." + e.getKey() + ": \"" + d + "\"");
        }
        for (Map.Entry<String, Long> e : DURATION_KEYS.getOrDefault(id, Map.of()).entrySet()) {
            if (y.get(e.getKey()) instanceof Number n) y.set(e.getKey(), compact(n, e.getValue()));
        }
        // nombres de estadística coherentes (kills -> player_kills, time -> play_time); los alias siguen valiendo
        if (("periods".equals(id) || "seasons".equals(id)) && y.isList("stats")) {
            List<String> stats = new ArrayList<>();
            for (Object o : y.getList("stats")) stats.add(statName(String.valueOf(o)));
            y.set("stats", stats);
        }
        if ("titles".equals(id) && y.isConfigurationSection("titles")) {
            ConfigurationSection ts = y.getConfigurationSection("titles");
            for (String k : ts.getKeys(false)) if (ts.isString(k + ".stat")) ts.set(k + ".stat", statName(ts.getString(k + ".stat")));
        }
        if ("quests".equals(id)) {
            for (String period : List.of("daily", "weekly")) {
                ConfigurationSection qs = y.getConfigurationSection(period);
                if (qs == null) continue;
                for (String k : qs.getKeys(false)) {
                    String obj = qs.getString(k + ".objective");
                    if (obj != null && obj.startsWith("stat:")) qs.set(k + ".objective", "stat:" + statName(obj.substring(5)));
                }
            }
        }
        if ("backups".equals(id) && y.isList("folders")) {
            List<String> folders = new ArrayList<>();
            for (Object o : y.getList("folders")) folders.add(me.gordi.mineapi.core.data.DataDir.migrate(String.valueOf(o)));
            if (!folders.equals(y.getStringList("folders"))) changes.add("backups.folders: server_data -> data");
            y.set("folders", folders);
        }
        return y;
    }

    /** Número antiguo (en {@code unit} segundos, admite decimales) a texto corto: 300 s -> "5m". */
    static String compact(Number n, long unit) {
        return me.gordi.mineapi.core.time.Durations.compact(Math.round(n.doubleValue() * unit));
    }

    /** Nombre de seguimiento de una estadística (alias -> nombre de stats.tracked); desconocida: tal cual. */
    static String statName(String raw) {
        me.gordi.mineapi.module.stats.StatType t = me.gordi.mineapi.module.stats.StatType.parse(raw);
        return t == null || t.trackKey == null ? raw : t.trackKey;
    }

    /** ¿Algún valor del usuario es distinto del de la plantilla del archivo? */
    static boolean customized(String id, ConfigurationSection user, String template) throws org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration tpl = new YamlConfiguration();
        tpl.loadFromString(template);
        YamlConfiguration merged = copy(tpl);
        clearUserMaps(merged, id, user, "");
        overlay(user, merged, "");
        // "60s" y "1m" son lo mismo: las duraciones se comparan en segundos
        java.util.Set<String> durations = new java.util.HashSet<>(DURATION_KEYS.getOrDefault(id, Map.of()).keySet());
        durations.addAll(RENAMED.getOrDefault(id, Map.of()).keySet());
        for (String k : durations) {
            for (YamlConfiguration y : List.of(merged, tpl)) {
                if (y.contains(k)) y.set(k, me.gordi.mineapi.core.time.Durations.seconds(y, -1, 1, k));
            }
        }
        return !leaves(merged).equals(leaves(tpl));
    }

    private static Map<String, Object> leaves(ConfigurationSection s) {
        Map<String, Object> m = new java.util.TreeMap<>();
        for (String k : s.getKeys(true)) {
            if (s.isConfigurationSection(k)) {
                if (s.getConfigurationSection(k).getKeys(false).isEmpty()) m.put(k, "{}");
            } else {
                m.put(k, String.valueOf(s.get(k)));
            }
        }
        return m;
    }

    /** Sección {@code key} de {@code text} con los valores del usuario sobre la de la plantilla. */
    static String composeSection(String text, String id, String key, ConfigurationSection user, ConfigLayout.Doc tpl)
            throws org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration out = ConfigLayout.section(tpl, key);
        clearUserMaps(out, id, user, "");
        overlay(user, out, "");
        return ConfigLayout.replace(text, key, ConfigLayout.dump(out), ConfigLayout.lead(tpl, key));
    }

    /**
     * Archivo entero de un módulo: cada clave de primer nivel que tiene el usuario se reescribe con sus valores
     * y los comentarios de la plantilla; las demás quedan como en la plantilla.
     */
    static String composeWhole(String text, String id, ConfigurationSection user, String template)
            throws org.bukkit.configuration.InvalidConfigurationException {
        YamlConfiguration all = new YamlConfiguration();
        all.loadFromString(template);
        clearUserMaps(all, id, user, "");
        overlay(user, all, "");
        String out = text;
        for (String k : user.getKeys(false)) out = ConfigLayout.replaceTop(out, k, topYaml(all, k), null);
        return out;
    }

    /**
     * Texto YAML de la clave de primer nivel {@code k} ({@code k:} y su cuerpo) con los comentarios de sus
     * claves interiores y el comentario en línea de {@code k}. No incluye el comentario superior de {@code k}
     * (ese se queda en el archivo, encima del bloque).
     */
    public static String topYaml(ConfigurationSection src, String k) {
        YamlConfiguration tmp = new YamlConfiguration();
        if (src.isConfigurationSection(k)) {
            ConfigurationSection sec = src.getConfigurationSection(k);
            tmp.createSection(k);
            overlay(sec, tmp, k);
            try {
                for (String sub : sec.getKeys(true)) {
                    List<String> c = sec.getComments(sub);
                    if (!c.isEmpty()) tmp.setComments(k + "." + sub, c);
                    List<String> i = sec.getInlineComments(sub);
                    if (!i.isEmpty()) tmp.setInlineComments(k + "." + sub, i);
                }
            } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            }
        } else {
            tmp.set(k, src.get(k));
        }
        try {
            List<String> i = src.getInlineComments(k);
            if (!i.isEmpty()) tmp.setInlineComments(k, i);
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
        }
        String t = tmp.saveToString();
        if (src.isConfigurationSection(k) && src.getConfigurationSection(k).getKeys(false).isEmpty()) {
            t = t.replaceFirst("(?m)^" + java.util.regex.Pattern.quote(k) + ":\\s*$", java.util.regex.Matcher.quoteReplacement(k) + ": {}");
        }
        return t;
    }

    /** Pasos antiguos que aún tienen sentido antes del reparto (v1 -> v2). */
    private static void legacySteps(YamlConfiguration config, int version, List<String> changes) {
        if (version >= 2) return;
        // v1 -> v2: arquitectura modular. Los interruptores antiguos pasan a modules.<id>.enabled
        if (config.isBoolean("scoreboard.enabled")) {
            config.set("modules.scoreboard.enabled", config.getBoolean("scoreboard.enabled"));
            config.set("scoreboard.enabled", null);
            changes.add("scoreboard.enabled -> modules.scoreboard");
        }
        if (config.isBoolean("discord.enabled")) {
            config.set("modules.discord.enabled", config.getBoolean("discord.enabled"));
            config.set("discord.enabled", null);
            changes.add("discord.enabled -> modules.discord-webhook");
        }
        if (config.isConfigurationSection("milestones")) {
            config.set("milestones", null);
            changes.add("sección 'milestones' antigua eliminada (los hitos viven en milestones.yml)");
        }
    }

    /**
     * Rellena un config.yml v6 con las claves nuevas del JAR. Interruptores de módulos en formato
     * {@code modules.<id>.enabled} o con id antiguo se normalizan a {@code modules.<id>: true}.
     */
    public static List<String> updateMain(YamlConfiguration config, YamlConfiguration template) {
        List<String> changes = new ArrayList<>();
        ConfigurationSection mods = config.getConfigurationSection("modules");
        if (mods != null) {
            for (String key : new ArrayList<>(mods.getKeys(false))) {
                String id = ModuleCatalog.canonical(key);
                if (id == null || (id.equals(key) && mods.isBoolean(key))) continue;
                Boolean on = mods.isBoolean(key) ? Boolean.valueOf(mods.getBoolean(key))
                        : mods.isBoolean(key + ".enabled") ? Boolean.valueOf(mods.getBoolean(key + ".enabled")) : null;
                if (on == null) continue;
                mods.set(key, null);
                mods.set(id, on);
                changes.add("modules." + key + " -> modules." + id + ": " + on);
            }
        }
        changes.addAll(mergeDefaults(config, template, List.of(), HEAD_RENAMED));
        return changes;
    }

    /** Añade las claves que falten (con sus comentarios) sin tocar las listas libres del módulo. */
    public static List<String> mergeModule(String module, ConfigurationSection target, ConfigurationSection template) {
        return mergeDefaults(target, template, USER_MAPS.getOrDefault(module, List.of()), RENAMED.getOrDefault(module, Map.of()));
    }

    /** Añade las claves que falten (con sus comentarios). Nunca cambia valores existentes. */
    public static List<String> mergeDefaults(ConfigurationSection target, ConfigurationSection defaults, List<String> maps) {
        return mergeDefaults(target, defaults, maps, Map.of());
    }

    /**
     * Como {@link #mergeDefaults(ConfigurationSection, ConfigurationSection, List)}, pero una clave nueva cuyo
     * nombre antiguo ({@code renamed}: nueva -> antigua) ya tiene el usuario no se añade: el valor antiguo manda.
     */
    public static List<String> mergeDefaults(ConfigurationSection target, ConfigurationSection defaults, List<String> maps,
                                             Map<String, String> renamed) {
        List<String> added = new ArrayList<>();
        if (defaults == null) return added;
        List<String> userMaps = new ArrayList<>();
        for (String m : maps) if (target.isConfigurationSection(m)) userMaps.add(m);
        for (String key : defaults.getKeys(true)) {
            if (target.contains(key) || isInside(userMaps, key)) continue;
            if (hasOldName(target, key, renamed)) continue;
            String parent = parentOf(key);
            if (parent != null && !target.isConfigurationSection(parent)) continue; // el padre existe y no es sección: respetar al usuario
            if (defaults.isConfigurationSection(key)) {
                target.createSection(key);
            } else {
                target.set(key, defaults.get(key));
            }
            copyComments(defaults, target, key);
            if (!isUnderAdded(added, key)) added.add(key);
        }
        return added;
    }

    /**
     * Textos de idioma que cambiaron de formato entre versiones. Si el archivo del servidor usa en una de estas
     * claves una variable que el JAR ya no envía (p. ej. {@code {value}} en {@code help.line}), se saldría
     * «{value}» en el chat; en ese caso se repone el texto del JAR. También si el texto es el de serie de la versión
     * anterior (no lo personalizó nadie). Clave → textos de serie anteriores. Lang keys whose format changed.
     */
    public static final Map<String, List<String>> CHANGED_LANG_KEYS = Map.ofEntries(
            Map.entry("help.line", List.of("&f {usage} &7- {value}")),
            Map.entry("commands.modules.header", List.of("&7Módulos:", "&7Modules:")),
            Map.entry("commands.modules.on", List.of("&a ● {module}")),
            Map.entry("commands.modules.off", List.of("&8 ○ {module}")),
            Map.entry("commands.modules.detail.guide", List.of("&7 Guía: &fdocs/es/GUIA.md &8(sección «{module}»)", "&7 Guide: &fdocs/en/GUIDE.md &8(section «{module}»)")),
            Map.entry("commands.modules.detail.file", List.of("&7 Ajustes: &fplugins/LosPerrisStatsAPI/{file}", "&7 Settings: &fplugins/LosPerrisStatsAPI/{file}")),
            Map.entry("commands.modules.detail.no-file", List.of("&7 Ajustes: &8no tiene archivo propio", "&7 Settings: &8no file of its own")),
            Map.entry("commands.modules.reason.no-token", List.of("sin token del bot (modules/discord-bot.yml)", "no bot token (modules/discord-bot.yml)",
                    "sin token del bot (config.yml → discord-bot.token)", "no bot token (config.yml → discord-bot.token)",
                    "sin token del bot (discord.yml → discord-bot.token)", "no bot token (discord.yml → discord-bot.token)")),
            Map.entry("commands.modules.reason.no-webhook", List.of("sin webhook-url válida (modules/discord-webhook.yml)", "no valid webhook-url (modules/discord-webhook.yml)",
                    "sin webhook-url válida (config.yml → discord-webhook.webhook-url)", "no valid webhook-url (config.yml → discord-webhook.webhook-url)",
                    "sin webhook-url válida (discord.yml → discord-webhook.webhook-url)", "no valid webhook-url (discord.yml → discord-webhook.webhook-url)")),
            Map.entry("module-info.category.extra-web", List.of("Extras · web")),
            Map.entry("module-info.category.extra-server", List.of("Extras · servidor", "Extras · server")));

    private static final java.util.regex.Pattern PLACEHOLDER = java.util.regex.Pattern.compile("\\{([a-z_-]+)\\}");

    /**
     * Repone las claves de {@code keys} cuyo texto usa variables que la versión del JAR no tiene o es el texto de serie
     * de una versión anterior. Un texto personalizado que solo usa variables válidas se respeta.
     * @return las claves repuestas, con el texto anterior (para el registro)
     */
    public static Map<String, String> fixChangedKeys(ConfigurationSection target, ConfigurationSection defaults, Map<String, List<String>> keys) {
        Map<String, String> fixed = new LinkedHashMap<>();
        if (defaults == null) return fixed;
        for (String key : new java.util.TreeSet<>(keys.keySet())) {
            if (!target.isString(key) || !defaults.isString(key)) continue;
            String mine = target.getString(key), jar = defaults.getString(key);
            java.util.Set<String> valid = placeholders(jar);
            boolean stale = keys.get(key).contains(mine) && !mine.equals(jar);
            for (String p : placeholders(mine)) if (!valid.contains(p)) stale = true;
            if (stale) {
                fixed.put(key, mine);
                target.set(key, jar);
            }
        }
        return fixed;
    }

    private static java.util.Set<String> placeholders(String text) {
        java.util.Set<String> out = new java.util.HashSet<>();
        java.util.regex.Matcher m = PLACEHOLDER.matcher(text == null ? "" : text);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    // ------------------------------------------------------------------ ayudas

    /** Copia profunda conservando comentarios y cabecera. */
    static YamlConfiguration copy(YamlConfiguration src) {
        YamlConfiguration out = new YamlConfiguration();
        try {
            out.loadFromString(src.saveToString());
        } catch (Exception e) {
            throw new IllegalStateException("plantilla inválida", e);
        }
        return out;
    }

    /** Escribe los valores de {@code src} dentro de {@code out} bajo {@code prefix} (conserva comentarios de la plantilla). */
    static void overlay(ConfigurationSection src, ConfigurationSection out, String prefix) {
        for (String key : src.getKeys(true)) {
            String dest = prefix.isEmpty() ? key : prefix + "." + key;
            if (src.isConfigurationSection(key)) {
                if (!out.isConfigurationSection(dest)) {
                    if (out.contains(dest)) out.set(dest, null);
                    out.createSection(dest);
                }
            } else {
                out.set(dest, src.get(key));
            }
        }
    }

    /** Las listas libres del usuario sustituyen por completo a las de la plantilla. */
    static void clearUserMaps(YamlConfiguration out, String module, ConfigurationSection src, String prefix) {
        for (String map : USER_MAPS.getOrDefault(module, List.of())) {
            String rel = relative(map, prefix);
            if (rel == null || !src.isConfigurationSection(rel)) continue;
            ConfigurationSection sec = out.getConfigurationSection(map);
            if (sec == null) continue;
            for (String k : new ArrayList<>(sec.getKeys(false))) sec.set(k, null);
        }
    }

    /** Ruta de {@code map} relativa a {@code prefix}, o null si no está debajo. */
    private static String relative(String map, String prefix) {
        if (prefix.isEmpty()) return map;
        if (map.startsWith(prefix + ".")) return map.substring(prefix.length() + 1);
        return null;
    }

    private static ConfigurationSection without(ConfigurationSection src, String key) {
        return without(src, List.of(key));
    }

    static ConfigurationSection without(ConfigurationSection src, List<String> keys) {
        boolean any = false;
        for (String key : keys) any |= src.contains(key);
        if (!any) return src;
        YamlConfiguration c = new YamlConfiguration();
        for (String k : src.getKeys(true)) {
            if (insideAny(keys, k)) continue;
            if (src.isConfigurationSection(k)) c.createSection(k);
            else c.set(k, src.get(k));
        }
        return c;
    }

    private static boolean insideAny(List<String> keys, String k) {
        for (String key : keys) if (k.equals(key) || k.startsWith(key + ".")) return true;
        return false;
    }

    /** ¿{@code key} (o una sección que la contiene) es un nombre nuevo cuyo nombre antiguo tiene el usuario? */
    private static boolean hasOldName(ConfigurationSection target, String key, Map<String, String> renamed) {
        for (Map.Entry<String, String> e : renamed.entrySet()) {
            if ((key.equals(e.getKey()) || key.startsWith(e.getKey() + ".")) && target.contains(e.getValue())) return true;
        }
        return false;
    }

    private static boolean isInside(List<String> userMaps, String key) {
        for (String m : userMaps) if (key.startsWith(m + ".")) return true;
        return false;
    }

    private static boolean isUnderAdded(List<String> added, String key) {
        for (String a : added) if (key.startsWith(a + ".")) return true;
        return false;
    }

    private static String parentOf(String key) {
        int i = key.lastIndexOf('.');
        return i < 0 ? null : key.substring(0, i);
    }

    private static void copyComments(ConfigurationSection from, ConfigurationSection to, String key) {
        try {
            List<String> c = from.getComments(key);
            if (!c.isEmpty()) to.setComments(key, c);
            List<String> inline = from.getInlineComments(key);
            if (!inline.isEmpty()) to.setInlineComments(key, inline);
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
            // Servidores antiguos sin soporte de comentarios: se añade la clave sin comentario
        }
    }
}
