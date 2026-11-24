package me.gordi.mineapi.core.config;

import me.gordi.mineapi.core.module.ModuleCatalog;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Carga y migra los archivos YAML del plugin (formato v9). config.yml tiene {@code general}, {@code modules},
 * las secciones del núcleo y {@code advanced}; settings/web.yml, settings/discord.yml y settings/database.yml
 * existen siempre; cada extra con ajustes tiene settings/&lt;id&gt;.yml, que se crea al activarlo
 * ({@link ConfigLayout#place}). Al arrancar solo se analiza la cabecera; los ajustes de un módulo se leen la
 * primera vez que el módulo (activo) los pide, así que los de un módulo apagado no cuestan nada.
 * v9: settings are read lazily per enabled module; a disabled extra has no file at all.
 */
public final class ConfigService {

    /** Carpeta de las plantillas en el JAR: defaults/&lt;idioma&gt;/&lt;archivo&gt;.yml. */
    public static final String DEFAULTS = "defaults/";
    /** Carpeta de los archivos por módulo de v6 (se retira al migrar). */
    public static final String LEGACY_MODULES_DIR = "modules";
    public static final String CONFIG = "config.yml";

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> moduleCache = new ConcurrentHashMap<>();
    private final Map<String, String> templateText = new ConcurrentHashMap<>();
    private volatile String templateLang = "es";
    /** Texto actual de config.yml y su reparto en bloques (se relee con {@link #reloadMain()}). */
    private volatile String text = "";
    private volatile ConfigLayout.Doc doc = ConfigLayout.parse("");
    /** Solo la cabecera: config-version, general y modules. Es lo que devuelve plugin.getConfig(). */
    private volatile YamlConfiguration main = new YamlConfiguration();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ================================================================== config.yml

    /**
     * Crea, migra (v5-v8 -> v9 y v9 -> v10, cada paso con copia zip) o completa config.yml y lee su cabecera.
     *
     * @return false si la migración no pudo hacerse con seguridad (el plugin debe detenerse)
     */
    public boolean prepareMainConfig() {
        File file = configFile();
        if (!file.exists()) {
            String lang = templateLang(Locale.getDefault().getLanguage());
            // instalación nueva: todos los archivos de configuración desde la plantilla de su idioma
            for (String name : ConfigLayout.FILES) {
                File f = new File(plugin.getDataFolder(), name);
                String t = templateText(lang, name);
                if (t == null || f.exists()) continue;
                try {
                    writeAtomic(f, t);
                } catch (IOException e) {
                    plugin.getLogger().log(Level.WARNING, "No se pudo crear " + name, e);
                }
            }
            new File(plugin.getDataFolder(), "lang").mkdirs();
            new File(plugin.getDataFolder(), me.gordi.mineapi.core.data.DataDir.NAME).mkdirs();
            reloadMain();
            templateLang = templateLang(main.getString("general.language", lang));
            return true;
        }
        String current = readText(file);
        if (current == null) return true;
        ConfigLayout.Doc d = ConfigLayout.parse(current);
        YamlConfiguration head;
        try {
            head = ConfigLayout.head(d);
        } catch (InvalidConfigurationException e) {
            formatError(file, e);
            this.text = current;
            this.doc = d;
            this.main = new YamlConfiguration();
            return true; // YAML inválido: no se toca el archivo del usuario
        }
        int version = head.getInt(ConfigMigrator.VERSION_KEY, 1);
        if (version < ConfigMigrator.CURRENT_VERSION) {
            if (version < 9 && !migrate(file, version, head)) return false;
            if (!migrateV10(file)) return false;
        } else {
            String lang = templateLang(head.getString("general.language", "es"));
            updateHead(file, current, head, lang);
        }
        reloadMain();
        templateLang = templateLang(main.getString("general.language", "es"));
        return true;
    }

    /** Relee config.yml y analiza solo su cabecera (no las secciones de los módulos). */
    public synchronized void reloadMain() {
        File file = configFile();
        String current = file.exists() ? readText(file) : "";
        if (current == null) return;
        ConfigLayout.Doc d = ConfigLayout.parse(current);
        try {
            this.main = ConfigLayout.head(d);
        } catch (InvalidConfigurationException e) {
            formatError(file, e);
            this.main = new YamlConfiguration();
        }
        this.text = current;
        this.doc = d;
    }

    /** Cabecera de config.yml (config-version, general y modules). */
    public YamlConfiguration main() {
        return main;
    }

    /** Ajustes generales (sección {@code general} de config.yml). */
    public ConfigurationSection general() {
        ConfigurationSection s = main.getConfigurationSection("general");
        return s != null ? s : new YamlConfiguration();
    }

    public File configFile() {
        return new File(plugin.getDataFolder(), CONFIG);
    }

    /** Añade a general/modules las claves nuevas del JAR, reescribiendo solo esos dos bloques. */
    private void updateHead(File file, String current, YamlConfiguration head, String lang) {
        ConfigLayout.Doc tpl = ConfigLayout.parse(templateText(lang));
        YamlConfiguration tplHead;
        try {
            tplHead = ConfigLayout.head(tpl);
        } catch (InvalidConfigurationException e) {
            return;
        }
        List<String> changes = ConfigMigrator.updateMain(head, tplHead);
        if (changes.isEmpty()) return;
        String out = current;
        for (String key : List.of("general", "modules", "advanced")) {
            YamlConfiguration sec = new YamlConfiguration();
            ConfigurationSection src = head.getConfigurationSection(key);
            if (src == null) continue;
            ConfigMigrator.overlay(src, sec, "");
            copyAllComments(src, sec);
            out = ConfigLayout.replace(out, key, ConfigLayout.dump(sec), ConfigLayout.lead(tpl, key));
        }
        File backup = backup(file);
        try {
            writeAtomic(file, out);
            plugin.getLogger().info("config.yml actualizado (" + changes.size() + " cambios"
                    + (backup != null ? ", copia en backups/" + backup.getName() : "") + "): " + String.join("; ", changes));
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo actualizar config.yml", e);
        }
    }

    private static void copyAllComments(ConfigurationSection from, ConfigurationSection to) {
        try {
            for (String k : from.getKeys(true)) {
                List<String> c = from.getComments(k);
                if (!c.isEmpty()) to.setComments(k, c);
                List<String> i = from.getInlineComments(k);
                if (!i.isEmpty()) to.setInlineComments(k, i);
            }
        } catch (NoSuchMethodError | UnsupportedOperationException ignored) {
        }
    }

    /**
     * Migra a v9 desde v8 (secciones en config.yml + archivos propios en la raíz), v7 (todo en config.yml + listas
     * sueltas), v6 (config.yml corto + modules/&lt;id&gt;.yml) o v5 y anteriores (config.yml largo). Antes de cambiar
     * nada guarda TODOS los archivos implicados en backups/migration-v9-&lt;fecha&gt;.zip; si algo no se puede leer,
     * no toca nada. Se conservan todos los valores (también tokens, webhooks y claves de la API) y los módulos
     * activados. Después: database/ pasa a settings/, server_data/ a data/ y se borra la carpeta docs/ antigua.
     */
    private boolean migrate(File file, int version, YamlConfiguration head) {
        File root = plugin.getDataFolder();
        List<File> sources = new ArrayList<>(List.of(file));
        // --- leer todo antes de tocar nada
        Map<String, YamlConfiguration> loose = new LinkedHashMap<>();
        for (String id : ConfigMigrator.LEGACY_FILES) {
            File f = new File(root, id + ".yml");
            if (!f.exists()) continue;
            YamlConfiguration y = tryLoad(f);
            if (y == null) return refuse(f);
            loose.put(id, y);
            sources.add(f);
        }
        Map<String, YamlConfiguration> legacyModules = new LinkedHashMap<>();
        File dir = new File(root, LEGACY_MODULES_DIR);
        File[] files = dir.isDirectory() ? dir.listFiles((d, n) -> n.endsWith(".yml")) : null;
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                YamlConfiguration y = tryLoad(f);
                if (y == null) return refuse(f);
                String name = f.getName().substring(0, f.getName().length() - 4);
                String id = ModuleCatalog.legacyId(name);
                legacyModules.put(id != null ? id : name, y);
                sources.add(f);
            }
        }
        // archivos de v8 en la raíz y database/database.yml: también al zip (se retiran al terminar)
        for (String name : LegacyLayout.FILES) {
            File f = new File(root, name);
            if (f.exists() && !sources.contains(f)) sources.add(f);
        }
        File oldDb = new File(root, LegacyLayout.DATABASE);
        if (oldDb.isFile()) sources.add(oldDb);
        YamlConfiguration full = tryLoad(file);
        if (full == null) return refuse(file);
        String text0 = readText(file);
        if (text0 == null) return false;

        String lang = templateLang(version < 6 ? full.getString("language", "es") : head.getString("general.language", "es"));
        Map<String, String> templates = new LinkedHashMap<>();
        List<String> needed = new ArrayList<>(ConfigLayout.FILES);
        for (ModuleCatalog.Info info : ModuleCatalog.all()) {
            if (info.hasSettings() && !needed.contains(ConfigLayout.place(info.id()).file())) needed.add(ConfigLayout.place(info.id()).file());
        }
        for (String name : needed) {
            String t = templateText(lang, name);
            if (t == null) {
                plugin.getLogger().severe("Falta la plantilla " + name + " en el JAR / missing template");
                return false;
            }
            templates.put(name, t);
        }
        YamlConfiguration tplHead;
        try {
            tplHead = ConfigLayout.head(ConfigLayout.parse(templates.get(CONFIG)));
        } catch (InvalidConfigurationException e) {
            plugin.getLogger().log(Level.SEVERE, "Plantilla config.yml inválida en el JAR", e);
            return false;
        }

        ConfigurationSection userHead;
        Map<String, ConfigurationSection> modules = new LinkedHashMap<>();
        List<String> changes = new ArrayList<>();
        if (version < 6) {
            ConfigMigrator.Result r = ConfigMigrator.migrateToV6(full, loose, tplHead, id -> moduleTemplate(lang, id));
            userHead = r.main();
            modules.putAll(r.modules());
            changes.addAll(r.changes());
        } else if (version == 6) {
            userHead = full;
            // v6 no debería tener listas sueltas, pero si quedan (y no hay archivo de módulo) se respetan
            loose.forEach((id, y) -> { if (!legacyModules.containsKey(id)) modules.put(id, y); });
            modules.putAll(legacyModules);
        } else {
            userHead = full;
            ConfigLayout.Doc d = ConfigLayout.parse(text0);
            ConfigLayout.Doc discord = null;
            File discordFile = new File(root, LegacyLayout.DISCORD);
            if (version >= 8 && discordFile.isFile()) {
                String t = readText(discordFile);
                if (t == null) return refuse(discordFile);
                discord = ConfigLayout.parse(t);
            }
            for (ModuleCatalog.Info info : ModuleCatalog.migration()) {
                String id = info.id();
                YamlConfiguration y = new YamlConfiguration();
                boolean any = false;
                ConfigLayout.Place old = version >= 8 ? LegacyLayout.place(id) : new ConfigLayout.Place(CONFIG, LegacyLayout.section(id));
                if (old.whole()) {
                    // v8: archivo propio en la raíz (web.yml, events.yml...)
                    File f = new File(root, old.file());
                    if (f.isFile()) {
                        YamlConfiguration own = tryLoad(f);
                        if (own == null) return refuse(f);
                        y = own;
                        any = true;
                    }
                } else {
                    ConfigLayout.Doc src = LegacyLayout.DISCORD.equals(old.file()) ? discord : d;
                    if (src != null && src.has(old.section())) {
                        try {
                            y = ConfigLayout.section(src, old.section());
                            any = true;
                        } catch (InvalidConfigurationException e) {
                            return refuse(LegacyLayout.DISCORD.equals(old.file()) ? discordFile : file);
                        }
                    }
                }
                YamlConfiguration list = version == 7 ? loose.get(id) : null;
                if (list != null) {
                    for (String k : ConfigLayout.CONTENT.getOrDefault(id, List.of())) {
                        if (!list.contains(k)) continue;
                        y.set(k, null);
                        if (list.isConfigurationSection(k)) {
                            y.createSection(k);
                            ConfigMigrator.overlay(list.getConfigurationSection(k), y, k);
                        } else {
                            y.set(k, list.get(k));
                        }
                        any = true;
                    }
                }
                if (any) modules.put(id, y);
            }
            legacyModules.forEach(modules::putIfAbsent);
        }
        ConfigMigrator.V9 v9 = ConfigMigrator.toV9(userHead, modules, templates);
        if (version >= 6) changes.addAll(v9.changes());
        else changes.add(ConfigMigrator.VERSION_KEY + ": " + version + " -> " + ConfigMigrator.CURRENT_VERSION);

        // archivos de settings/ que ya existan (p. ej. copiados a mano) se respetan: no se reescriben
        List<String> kept = new ArrayList<>();
        for (String name : v9.files().keySet()) {
            File f = new File(root, name);
            if (!CONFIG.equals(name) && f.exists()) {
                kept.add(name);
                if (!sources.contains(f)) sources.add(f);
            }
        }

        // 1) Copia zip ANTES de cambiar nada
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File zip = new File(new File(root, "backups"), "migration-v9-" + stamp + ".zip");
        try {
            zip(zip, root, sources);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo crear la copia " + zip.getName() + "; no se migra la configuración. / Backup failed; configuration NOT migrated.", e);
            return false;
        }
        // 2) settings/ primero y config.yml al final (atómicos): si algo falla, config.yml sigue en la versión
        //    antigua y la migración se repite en el próximo arranque; el zip tiene los originales.
        File db = new File(root, ConfigLayout.DATABASE);
        try {
            for (Map.Entry<String, String> e : v9.files().entrySet()) {
                if (!CONFIG.equals(e.getKey()) && !kept.contains(e.getKey())) writeAtomic(new File(root, e.getKey()), e.getValue());
            }
            if (!db.exists()) {
                String dbText = oldDb.isFile() ? readText(oldDb) : templateText(lang, ConfigLayout.DATABASE);
                if (dbText != null) writeAtomic(db, dbText);
            }
            writeAtomic(file, v9.files().get(CONFIG));
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo escribir la configuración v9; los originales están en backups/"
                    + zip.getName() + ". / Could not write the v9 config; originals are in the backup.", e);
            return false;
        }
        // 3) Retirar lo antiguo (todo está en el zip y en los archivos nuevos)
        int removed = 0;
        for (File f : sources) {
            if (f.equals(file) || kept.contains(rel(root, f))) continue;
            if (f.delete()) removed++;
            else plugin.getLogger().warning("No se pudo borrar " + rel(root, f) + " (ya está migrado / already migrated).");
        }
        deleteIfEmpty(dir);
        deleteIfEmpty(oldDb.getParentFile());
        String data = moveDataFolder(root);
        boolean docs = deleteTree(new File(root, "docs"));
        plugin.getLogger().info("Configuración migrada a v9 / Config migrated to v9 (desde / from v" + version + "): "
                + String.join(", ", v9.files().keySet()) + ", " + ConfigLayout.DATABASE
                + (v9.skipped().isEmpty() ? "" : "; extras apagados sin archivo / disabled extras without a file: " + String.join(", ", v9.skipped()))
                + (removed > 0 ? "; " + removed + " archivos antiguos retirados / old files removed" : "")
                + (data != null ? "; " + data : "")
                + (docs ? "; carpeta docs/ retirada (la guía está en internet) / docs/ removed" : "")
                + ". Copia / backup: backups/" + zip.getName() + ". Guía / guide: " + me.gordi.mineapi.core.Links.guide(lang, null));
        if (!kept.isEmpty()) plugin.getLogger().info("Se respetaron / kept existing: " + String.join(", ", kept));
        if (userHead.getBoolean("general.debug.enabled", false) || userHead.getBoolean("advanced.debug.enabled", false)) {
            plugin.getLogger().info("Cambios / changes: " + String.join("; ", changes));
        }
        return true;
    }

    /**
     * v9 -> v10 (1.4): títulos, misiones, rachas, tiempo de juego, temporadas, eventos, hologramas, moderación y
     * alertas pasan al addon LosPerrisExtras, y achievements se une a milestones ({@link ExtrasSplit}). Antes de
     * cambiar nada guarda config.yml, los ajustes y los datos implicados en backups/migration-v10-&lt;fecha&gt;.zip.
     * Ajustes y datos se MUEVEN a plugins/LosPerrisExtras/ (nada se borra sin copia); los interruptores e idioma
     * van a su import.yml. Si el addon ya tiene un archivo con ese nombre, se deja el del núcleo donde está.
     */
    private boolean migrateV10(File file) {
        File root = plugin.getDataFolder();
        File addon = new File(root.getParentFile(), ExtrasSplit.ADDON_DIR);
        String text = readText(file);
        if (text == null) return false;
        String lang;
        boolean debug;
        ExtrasSplit.Result r;
        String milestones = null;
        File achFile = new File(root, ExtrasSplit.ACHIEVEMENTS);
        File msFile = new File(root, ExtrasSplit.MILESTONES);
        try {
            YamlConfiguration head = ConfigLayout.head(ConfigLayout.parse(text));
            lang = templateLang(head.getString("general.language", "es"));
            debug = head.getBoolean("advanced.debug.enabled", false);
            String tpl = templateText(lang);
            if (tpl == null) {
                plugin.getLogger().severe("Falta la plantilla config.yml en el JAR / missing template");
                return false;
            }
            r = ExtrasSplit.config(text, tpl);
            YamlConfiguration ach = null;
            if (achFile.isFile()) {
                ach = tryLoad(achFile);
                if (ach == null) return refuse(achFile);
            }
            // la galería se escribe si milestones.yml ya existe o si había algo de achievements
            if (msFile.isFile() || ach != null || r.gallery()) {
                String msTpl = templateText(lang, ExtrasSplit.MILESTONES);
                String cur = msFile.isFile() ? readText(msFile) : msTpl;
                if (cur == null || msTpl == null) return false;
                milestones = ExtrasSplit.gallery(cur, msTpl, ach, r.gallery());
            }
        } catch (InvalidConfigurationException e) {
            formatError(file, e);
            return false;
        }
        // qué se muda al addon
        List<String> moving = new ArrayList<>();
        for (String rel : ExtrasSplit.movedSettings()) if (new File(root, rel).exists()) moving.add(rel);
        for (String rel : ExtrasSplit.DATA) if (new File(root, rel).exists()) moving.add(rel);

        // 1) copia zip antes de cambiar nada
        List<File> sources = new ArrayList<>(List.of(file));
        if (achFile.isFile()) sources.add(achFile);
        if (msFile.isFile()) sources.add(msFile);
        for (String rel : moving) sources.addAll(filesUnder(new File(root, rel)));
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        File zip = new File(new File(root, "backups"), "migration-v10-" + stamp + ".zip");
        try {
            zip(zip, root, sources);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo crear la copia " + zip.getName() + "; no se migra la configuración. / Backup failed; configuration NOT migrated.", e);
            return false;
        }
        // 2) mudar al addon, escribir milestones.yml e import.yml; config.yml al final (si algo falla se repite)
        List<String> kept = new ArrayList<>();
        try {
            for (String rel : moving) {
                File to = new File(addon, rel);
                if (to.exists()) {
                    kept.add(rel);
                    continue;
                }
                File parent = to.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("no se pudo crear " + parent);
                Files.move(new File(root, rel).toPath(), to.toPath());
            }
            if (milestones != null) writeAtomic(msFile, milestones);
            if (!r.addonModules().isEmpty() || !moving.isEmpty()) {
                writeAtomic(new File(addon, ExtrasSplit.IMPORT), ExtrasSplit.importYaml(lang, r.addonModules()));
            }
            writeAtomic(file, r.config());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo terminar la migración a v10; la copia está en backups/" + zip.getName()
                    + ". / Could not finish the v10 migration; the backup is in backups/.", e);
            return false;
        }
        if (achFile.isFile() && !achFile.delete()) plugin.getLogger().warning("No se pudo borrar " + ExtrasSplit.ACHIEVEMENTS + " (ya está en milestones.yml).");
        boolean addonMissing = plugin.getServer() != null && plugin.getServer().getPluginManager().getPlugin(ExtrasSplit.ADDON_DIR) == null;
        plugin.getLogger().info("Configuración migrada a v10 / Config migrated to v10. Copia / backup: backups/" + zip.getName()
                + (moving.isEmpty() ? "" : "; a / to plugins/" + ExtrasSplit.ADDON_DIR + "/: " + String.join(", ", moving))
                + (milestones != null ? "; logros → " + ExtrasSplit.MILESTONES + " (gallery)" : ""));
        if (!kept.isEmpty()) plugin.getLogger().warning("Ya existían en plugins/" + ExtrasSplit.ADDON_DIR + "/ y se dejaron aquí / already in the addon folder, left here: " + String.join(", ", kept));
        if (r.addonModules().containsValue(Boolean.TRUE) && addonMissing) {
            plugin.getLogger().warning("Tenía activos módulos que ahora están en el addon LosPerrisExtras (" + String.join(", ", on(r.addonModules()))
                    + "). Instale LosPerrisExtras para seguir usándolos; sus ajustes y datos ya están en su carpeta. / "
                    + "Install the LosPerrisExtras addon to keep using them.");
        }
        if (debug) plugin.getLogger().info("Cambios / changes: " + String.join("; ", r.changes()));
        return true;
    }

    private static List<String> on(Map<String, Boolean> m) {
        List<String> out = new ArrayList<>();
        m.forEach((k, v) -> { if (v) out.add(k); });
        return out;
    }

    /** Un archivo, o todos los de una carpeta (para el zip). */
    private static List<File> filesUnder(File f) {
        if (f.isFile()) return List.of(f);
        List<File> out = new ArrayList<>();
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(f.toPath())) {
            walk.filter(Files::isRegularFile).forEach(p -> out.add(p.toFile()));
        } catch (IOException ignored) {
        }
        return out;
    }

    private static String rel(File root, File f) {
        return root.toPath().toAbsolutePath().normalize().relativize(f.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static void deleteIfEmpty(File dir) {
        String[] left = dir != null && dir.isDirectory() ? dir.list() : null;
        if (left != null && left.length == 0) dir.delete();
    }

    /** Borra una carpeta entera. True si existía y se borró. */
    static boolean deleteTree(File dir) {
        if (!dir.isDirectory()) return false;
        try (java.util.stream.Stream<java.nio.file.Path> walk = Files.walk(dir.toPath())) {
            walk.sorted(java.util.Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        } catch (IOException e) {
            return false;
        }
        return !dir.exists();
    }

    /**
     * server_data/ -> data/. Si data/ ya existe se mueve solo lo que no esté allí (nunca se sobrescribe).
     * Devuelve un texto para el registro o null si no había nada que mover.
     */
    String moveDataFolder(File root) {
        File old = new File(root, me.gordi.mineapi.core.data.DataDir.LEGACY);
        File now = new File(root, me.gordi.mineapi.core.data.DataDir.NAME);
        if (!old.isDirectory()) return null;
        try {
            if (!now.exists()) {
                Files.move(old.toPath(), now.toPath());
                return "server_data/ -> data/";
            }
            File[] children = old.listFiles();
            List<String> conflicts = new ArrayList<>();
            if (children != null) {
                for (File c : children) {
                    File target = new File(now, c.getName());
                    if (target.exists()) conflicts.add(c.getName());
                    else Files.move(c.toPath(), target.toPath());
                }
            }
            deleteIfEmpty(old);
            if (!conflicts.isEmpty()) {
                plugin.getLogger().warning("server_data/ y data/ tenían archivos con el mismo nombre; se dejaron en server_data/: "
                        + String.join(", ", conflicts) + " / both folders had these files, left in server_data/");
            }
            return "server_data/ -> data/" + (conflicts.isEmpty() ? "" : " (" + conflicts.size() + " sin mover / not moved)");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo mover server_data/ a data/; muévala a mano con el servidor apagado. / Could not move server_data to data.", e);
            return null;
        }
    }

    private boolean refuse(File f) {
        plugin.getLogger().severe("No se migra la configuración: corrija primero " + f.getName() + " (no se ha tocado nada).");
        plugin.getLogger().severe("Configuration NOT migrated: fix " + f.getName() + " first (nothing was changed).");
        return false;
    }

    /** Plantilla de un módulo con sus claves en la raíz (ajustes + su lista), para migrar desde v5. */
    private YamlConfiguration moduleTemplate(String lang, String id) {
        try {
            return defaults(lang, ConfigLayout.place(id));
        } catch (InvalidConfigurationException e) {
            return null;
        }
    }

    /** Valores por defecto del módulo según la plantilla del JAR (null si no tiene). */
    private YamlConfiguration defaults(String lang, ConfigLayout.Place place) throws InvalidConfigurationException {
        String t = templateText(lang, place.file());
        if (t == null) return null;
        if (place.whole()) {
            YamlConfiguration y = new YamlConfiguration();
            y.loadFromString(t);
            return y;
        }
        ConfigLayout.Doc tpl = ConfigLayout.parse(t);
        return tpl.has(place.section()) ? ConfigLayout.section(tpl, place.section()) : null;
    }

    // ================================================================== ajustes de cada módulo

    /**
     * Configuración del módulo con sus claves en la raíz, leída la primera vez que se pide. Vive en una
     * sección de config.yml, en una sección de discord.yml o en su archivo entero (web.yml, events.yml,
     * scoreboard.yml, milestones.yml, quests.yml, titles.yml), según {@link ConfigLayout#place}. Como solo
     * se pide desde módulos activos, el archivo de un módulo apagado no se abre. Si falta el archivo o la
     * sección se crea desde la plantilla; las claves nuevas del JAR se añaden reescribiendo solo su bloque.
     * Un módulo sin ajustes devuelve una configuración vacía.
     */
    public YamlConfiguration module(String id) {
        return moduleCache.computeIfAbsent(id, this::loadModule);
    }

    /** Olvida la configuración cacheada (se relee al volver a pedirla). */
    public void forget(String id) {
        moduleCache.remove(id);
    }

    public void forgetModules() {
        moduleCache.clear();
    }

    /** ¿Se ha leído ya la configuración del módulo? (para pruebas y /lpa timings). */
    public boolean isLoaded(String id) {
        return moduleCache.containsKey(id);
    }

    /** Guarda la configuración cacheada del módulo en su sitio, reescribiendo solo sus bloques (con comentarios). */
    public synchronized boolean saveModule(String id) {
        YamlConfiguration y = moduleCache.get(id);
        if (y == null) return false;
        ConfigLayout.Place place = ConfigLayout.place(id);
        if (!place.whole()) {
            YamlConfiguration settings = new YamlConfiguration();
            ConfigMigrator.overlay(y, settings, "");
            copyAllComments(y, settings);
            return writeSection(place, ConfigLayout.dump(settings), null);
        }
        File f = fileOf(place);
        String before = readText(f);
        if (before == null) return false;
        String after = before;
        for (String k : y.getKeys(false)) after = ConfigLayout.replaceTop(after, k, ConfigMigrator.topYaml(y, k), null);
        return write(place, before, after);
    }

    /**
     * Edita el texto de los ajustes del módulo en su archivo (su sección, o el archivo entero si es propio),
     * p. ej. para cambiar una línea conservando sus comentarios. El resto no cambia. Devuelve true si se escribió.
     */
    public synchronized boolean editModule(String id, UnaryOperator<String> edit) throws IOException {
        ConfigLayout.Place place = ConfigLayout.place(id);
        File f = fileOf(place);
        if (!f.exists()) return false;
        String before = readText(f);
        if (before == null) return false;
        String after;
        if (place.whole()) {
            after = edit.apply(before);
            if (after == null) return false;
        } else {
            after = ConfigLayout.editBlock(before, place.section(), edit);
        }
        if (after.equals(before)) return false;
        writeAtomic(f, after);
        if (CONFIG.equals(place.file())) {
            this.text = after;
            this.doc = ConfigLayout.parse(after);
        }
        return true;
    }

    /** Edita el texto completo de config.yml de forma atómica (p. ej. el interruptor de un módulo) y relee la cabecera. */
    public synchronized void editText(UnaryOperator<String> edit) throws IOException {
        String before = Files.readString(configFile().toPath(), StandardCharsets.UTF_8);
        String after = edit.apply(before);
        if (after == null || after.equals(before)) return;
        writeAtomic(configFile(), after);
        reloadMain();
    }

    /** Archivo de un lugar de ajustes en la carpeta del plugin. */
    public File fileOf(ConfigLayout.Place place) {
        return new File(plugin.getDataFolder(), place.file());
    }

    private synchronized boolean writeSection(ConfigLayout.Place place, String rootYaml, List<String> lead) {
        File f = fileOf(place);
        String before = readText(f);
        if (before == null) return false;
        return write(place, before, ConfigLayout.replace(before, place.section(), rootYaml, lead));
    }

    private boolean write(ConfigLayout.Place place, String before, String after) {
        if (after.equals(before)) return true;
        try {
            writeAtomic(fileOf(place), after);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + place.label(), e);
            return false;
        }
        if (CONFIG.equals(place.file())) {
            this.text = after;
            this.doc = ConfigLayout.parse(after);
        }
        return true;
    }

    private YamlConfiguration loadModule(String id) {
        ModuleCatalog.Info info = ModuleCatalog.get(id);
        if (info == null || !info.hasSettings()) return new YamlConfiguration();
        ConfigLayout.Place place = ConfigLayout.place(id);
        String tplText = templateText(templateLang, place.file());
        YamlConfiguration defaults;
        try {
            defaults = defaults(templateLang, place);
        } catch (InvalidConfigurationException e) {
            defaults = null;
        }
        if (defaults == null) defaults = new YamlConfiguration();
        ConfigLayout.Doc tpl = ConfigLayout.parse(tplText != null ? tplText : "");

        File f = fileOf(place);
        if (!f.exists()) {
            if (tplText == null) return defaults;
            try {
                writeAtomic(f, tplText);
                plugin.getLogger().info("Creado / created " + place.file());
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo crear " + f.getName(), e);
            }
            if (place.whole()) return defaults;
        }
        String current = CONFIG.equals(place.file()) ? this.text : readText(f);
        if (current == null) return defaults;

        if (!place.whole()) {
            String sec = place.section();
            ConfigLayout.Doc d = ConfigLayout.parse(current);
            if (!d.has(sec)) {
                // sección que falta (p. ej. borrada a mano): se añade la de la plantilla con su comentario
                String body = ConfigLayout.body(tpl, sec);
                if (body != null && writeSection(place, body, ConfigLayout.lead(tpl, sec))) {
                    plugin.getLogger().info(place.file() + ": añadida la sección / section added «" + sec + "»");
                }
                return ConfigMigrator.copy(defaults);
            }
            YamlConfiguration yaml;
            try {
                yaml = ConfigLayout.section(d, sec);
            } catch (InvalidConfigurationException e) {
                plugin.getLogger().severe(place.label() + ": formato inválido; se usan los valores por defecto hasta que se corrija. / invalid section, using defaults.");
                plugin.getLogger().severe("Detalle / detail: " + e.getMessage());
                return ConfigMigrator.copy(defaults);
            }
            List<String> added = ConfigMigrator.mergeModule(info.id(), yaml, defaults);
            if (!added.isEmpty() && writeSection(place, ConfigLayout.dump(yaml), null)) {
                plugin.getLogger().info(place.label() + ": claves nuevas / new keys: " + String.join(", ", added));
            }
            return yaml;
        }

        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.loadFromString(current);
        } catch (InvalidConfigurationException e) {
            formatError(f, e);
            plugin.getLogger().severe(f.getName() + ": se usan los valores por defecto hasta que se corrija. / using defaults until fixed.");
            return ConfigMigrator.copy(defaults);
        }
        List<String> added = ConfigMigrator.mergeModule(info.id(), yaml, defaults);
        if (!added.isEmpty()) {
            java.util.Set<String> tops = new java.util.LinkedHashSet<>();
            for (String k : added) tops.add(k.contains(".") ? k.substring(0, k.indexOf('.')) : k);
            String after = current;
            for (String top : tops) {
                after = ConfigLayout.replaceTop(after, top, ConfigMigrator.topYaml(yaml, top), ConfigLayout.lead(tpl, top));
            }
            if (write(place, current, after)) {
                plugin.getLogger().info(f.getName() + ": claves nuevas / new keys: " + String.join(", ", added));
            }
        }
        return yaml;
    }

    /** Texto de la plantilla config.yml del JAR (con comentarios). */
    public String templateText(String lang) {
        return templateText(lang, CONFIG);
    }

    /** Texto de una plantilla de configuración del JAR (con comentarios), o null si no existe. */
    public String templateText(String lang, String file) {
        String key = lang + "/" + file;
        String cached = templateText.get(key);
        if (cached != null) return cached;
        String t = readResource(DEFAULTS + key);
        if (t == null) t = readResource(DEFAULTS + "es/" + file);
        t = me.gordi.mineapi.core.Links.fill(t);
        if (t != null) templateText.put(key, t);
        return t;
    }

    private String readResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    private String readText(File file) {
        try {
            return Files.readString(file.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo leer " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private void formatError(File file, Exception e) {
        plugin.getLogger().severe("┌──────────────────────────────────────────────────┐");
        plugin.getLogger().severe("│  ERROR DE FORMATO EN " + file.getName());
        plugin.getLogger().severe("└──────────────────────────────────────────────────┘");
        plugin.getLogger().severe("Causas habituales: falta un espacio tras ':' , tabuladores en vez de espacios o comillas sin cerrar.");
        plugin.getLogger().severe("Detalle: " + e.getMessage());
    }

    /** Plantilla del JAR en el idioma pedido (o en español si no existe). */
    public YamlConfiguration template(String lang, String path) {
        YamlConfiguration y = loadResource(DEFAULTS + lang + "/" + path);
        return y != null ? y : loadResource(DEFAULTS + "es/" + path);
    }

    /** Idioma de las plantillas: es o en (cualquier otro usa en). */
    public static String templateLang(String language) {
        String l = language == null ? "" : language.toLowerCase(Locale.ROOT);
        return l.startsWith("es") ? "es" : "en";
    }

    // ================================================================== utilidades

    /**
     * Carga un YAML del data folder. Si no existe lo copia del JAR; si existe añade las claves
     * nuevas del JAR (sin tocar las del usuario) cuando {@code merge} es true.
     */
    public YamlConfiguration load(String path, boolean merge) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) {
            if (plugin.getResource(path) != null) plugin.saveResource(path, false);
            return YamlConfiguration.loadConfiguration(file);
        }
        YamlConfiguration yaml = tryLoad(file);
        if (yaml == null) return new YamlConfiguration();
        if (merge) {
            YamlConfiguration defaults = loadResource(path);
            List<String> added = ConfigMigrator.mergeDefaults(yaml, defaults, List.of());
            Map<String, String> fixed = path.startsWith("lang/")
                    ? ConfigMigrator.fixChangedKeys(yaml, defaults, ConfigMigrator.CHANGED_LANG_KEYS) : Map.of();
            if (!added.isEmpty() || !fixed.isEmpty()) save(yaml, file);
            if (!added.isEmpty()) plugin.getLogger().info(path + ": claves nuevas añadidas: " + String.join(", ", added));
            fixed.forEach((k, before) -> plugin.getLogger().info(path + ": " + k
                    + " tenía el formato de una versión anterior; se repuso el texto nuevo / had an old-version format, restored the new text (antes / before: \"" + before + "\")"));
        }
        return yaml;
    }

    /**
     * Carga un archivo de settings/ (p. ej. settings/database.yml) cuya plantilla está en defaults/&lt;idioma&gt;/.
     * Si no existe lo crea desde la plantilla; si existe añade las claves nuevas sin tocar las del usuario.
     */
    public YamlConfiguration loadSettings(String file) {
        File f = new File(plugin.getDataFolder(), file);
        String t = templateText(templateLang, file);
        if (!f.exists() && t != null) {
            try {
                writeAtomic(f, t);
                plugin.getLogger().info("Creado / created " + file);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "No se pudo crear " + file, e);
            }
        }
        YamlConfiguration yaml = f.exists() ? tryLoad(f) : null;
        if (yaml == null) yaml = new YamlConfiguration();
        if (t != null && f.exists()) {
            YamlConfiguration defaults = new YamlConfiguration();
            try {
                defaults.loadFromString(t);
                List<String> added = ConfigMigrator.mergeDefaults(yaml, defaults, List.of());
                if (!added.isEmpty() && save(yaml, f)) plugin.getLogger().info(file + ": claves nuevas añadidas: " + String.join(", ", added));
            } catch (InvalidConfigurationException ignored) {
            }
        }
        return yaml;
    }

    /**
     * Carga un YAML mostrando un error claro si el formato es inválido.
     * @return null si el archivo tiene errores (así nunca se sobrescribe un archivo roto)
     */
    public YamlConfiguration tryLoad(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (InvalidConfigurationException e) {
            formatError(file, e);
        } catch (IOException e) {
            plugin.getLogger().severe("No se pudo leer " + file.getName() + ": " + e.getMessage());
        }
        return null;
    }

    /** YAML empaquetado en el JAR (o null). */
    public YamlConfiguration loadResource(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (IOException e) {
            return null;
        }
    }

    /** Copia el archivo a backups/ con fecha. Devuelve la copia o null si falló. */
    public File backup(File file) {
        try {
            File dir = new File(plugin.getDataFolder(), "backups");
            if (!dir.exists() && !dir.mkdirs()) return null;
            String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
            String name = file.getName();
            int dot = name.lastIndexOf('.');
            String target = dot > 0 ? name.substring(0, dot) + "-" + stamp + name.substring(dot) : name + "-" + stamp;
            File out = new File(dir, target);
            Files.copy(file.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return out;
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "No se pudo crear la copia de " + file.getName(), e);
            return null;
        }
    }

    /** Zip con los archivos dados (rutas relativas a la carpeta del plugin). */
    static void zip(File zip, File root, List<File> files) throws IOException {
        File dir = zip.getParentFile();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no se pudo crear " + dir);
        File tmp = new File(dir, zip.getName() + ".tmp");
        try (OutputStream os = Files.newOutputStream(tmp.toPath()); ZipOutputStream out = new ZipOutputStream(os)) {
            for (File f : files) {
                String name = root.toPath().toAbsolutePath().normalize().relativize(f.toPath().toAbsolutePath().normalize()).toString().replace('\\', '/');
                out.putNextEntry(new ZipEntry(name));
                Files.copy(f.toPath(), out);
                out.closeEntry();
            }
        }
        move(tmp, zip);
    }

    /** Guarda en un temporal y lo mueve encima (nunca deja un archivo a medias). */
    public boolean save(YamlConfiguration yaml, File file) {
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            yaml.save(tmp);
            move(tmp, file);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "No se pudo guardar " + file.getName(), e);
            tmp.delete();
            return false;
        }
    }

    /** Escribe texto de forma atómica (temporal + mover). */
    public static void writeAtomic(File file, String content) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) throw new IOException("no se pudo crear " + parent);
        File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), content, StandardCharsets.UTF_8);
        move(tmp, file);
    }

    private static void move(File from, File to) throws IOException {
        try {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
