package me.gordi.mineapi.core.config;

import me.gordi.mineapi.core.module.ModuleCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ConfigMigratorTest {

    private static final String DEFAULTS = "src/main/resources/defaults/";

    static String text(String lang, String file) {
        try {
            return Files.readString(new File(DEFAULTS + lang + "/" + file).toPath());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static String templateText(String lang) {
        return text(lang, ConfigLayout.CONFIG);
    }

    static Map<String, String> templates(String lang) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String f : allFiles()) m.put(f, text(lang, f));
        return m;
    }

    /** Todas las plantillas: config.yml, settings/web, discord, database y el archivo de cada extra con ajustes. */
    static List<String> allFiles() {
        List<String> files = new ArrayList<>(ConfigLayout.FILES);
        for (ModuleCatalog.Info i : ModuleCatalog.all()) {
            String f = ConfigLayout.place(i.id()).file();
            if (i.hasSettings() && !files.contains(f)) files.add(f);
        }
        return files;
    }

    private static YamlConfiguration tplHead(String lang) {
        try {
            return ConfigLayout.head(ConfigLayout.parse(templateText(lang)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Ajustes del módulo en la plantilla (claves en la raíz, con su lista si la tiene). */
    private static YamlConfiguration moduleTpl(String lang, String id) {
        try {
            ConfigLayout.Place p = ConfigLayout.place(id);
            // los módulos que pasaron al addon (y achievements) ya no traen plantilla: se vuelcan tal cual
            if (!new File(DEFAULTS + lang + "/" + p.file()).isFile()) return null;
            if (p.whole()) return yaml(text(lang, p.file()));
            ConfigLayout.Doc d = ConfigLayout.parse(text(lang, p.file()));
            return d.has(p.section()) ? ConfigLayout.section(d, p.section()) : null;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static YamlConfiguration legacy(String name) {
        return YamlConfiguration.loadConfiguration(new File("src/test/resources/legacy-v5/" + name + ".yml"));
    }

    private static Map<String, YamlConfiguration> legacyFiles() {
        Map<String, YamlConfiguration> m = new LinkedHashMap<>();
        for (String id : ConfigMigrator.LEGACY_FILES) m.put(id, legacy(id));
        return m;
    }

    private static ConfigMigrator.Result migrate(YamlConfiguration old, Map<String, YamlConfiguration> files, String lang) {
        return ConfigMigrator.migrateToV6(old, files, tplHead(lang), id -> moduleTpl(lang, id));
    }

    private static YamlConfiguration yaml(String text) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(text);
        return y;
    }

    /** Ajustes de un módulo dentro de los archivos v9 compuestos. */
    private static YamlConfiguration settingsOf(ConfigMigrator.V9 v9, String id) throws Exception {
        ConfigLayout.Place p = ConfigLayout.place(id);
        String t = v9.files().get(p.file());
        assertNotNull(t, p.file());
        return p.whole() ? yaml(t) : ConfigLayout.section(ConfigLayout.parse(t), p.section());
    }

    @Test
    void bundledTemplatesAreV9AndCoverEveryModule() throws Exception {
        assertEquals(10, ConfigMigrator.CURRENT_VERSION);
        for (String lang : List.of("es", "en")) {
            YamlConfiguration main = yaml(templateText(lang));
            assertEquals(ConfigMigrator.CURRENT_VERSION, main.getInt(ConfigMigrator.VERSION_KEY));
            Map<String, String> tpls = templates(lang);
            for (String f : tpls.keySet()) {
                assertFalse(tpls.get(f).contains("modules/"), lang + "/" + f + ": aún nombra modules/");
                assertFalse(tpls.get(f).contains("server_data"), lang + "/" + f + ": aún nombra server_data");
                assertFalse(tpls.get(f).contains("docs/"), lang + "/" + f + ": la guía va por enlace, no por docs/");
            }
            ConfigLayout.Doc doc = ConfigLayout.parse(templateText(lang));
            ConfigLayout.Doc discord = ConfigLayout.parse(tpls.get(ConfigLayout.DISCORD));
            List<String> order = new ArrayList<>(List.of(ConfigMigrator.VERSION_KEY, "general", "modules"));
            for (ModuleCatalog.Info info : ModuleCatalog.all()) {
                String id = info.id();
                assertTrue(main.isBoolean("modules." + id), lang + " config.yml: modules." + id);
                ConfigLayout.Place p = ConfigLayout.place(id);
                boolean inConfig = info.hasSettings() && ConfigLayout.CONFIG.equals(p.file());
                assertEquals(inConfig, doc.has(id), lang + " config.yml: sección " + id);
                if (inConfig) order.add(id);
                if (!info.hasSettings()) continue;
                if (ConfigLayout.DISCORD.equals(p.file())) assertTrue(discord.has(id), lang + " discord.yml: " + id);
                if (p.whole()) {
                    YamlConfiguration own = yaml(tpls.get(p.file()));
                    assertFalse(own.getKeys(false).isEmpty(), lang + "/" + p.file());
                    for (String k : ConfigLayout.CONTENT.getOrDefault(id, List.of())) {
                        assertTrue(own.isConfigurationSection(k), lang + "/" + p.file() + ": lista " + k);
                    }
                }
            }
            order.add("advanced");
            assertEquals(order, new ArrayList<>(doc.keys()), lang + ": orden de config.yml (lo importante primero, advanced al final)");
            assertEquals(List.of("discord-webhook", "discord-bot"), new ArrayList<>(discord.keys()));
            assertTrue(yaml(tpls.get(ConfigLayout.DATABASE)).contains("pool.max_connections"));
        }
    }

    @Test
    void defaultsMatchTheOriginalOnesExceptGamification() throws Exception {
        YamlConfiguration m = yaml(templateText("es"));
        for (String on : List.of("stats", "web", "web-console", "heatmap", "history", "scoreboard", "afk", "periods", "gui", "placeholders", "backups", "updates")) {
            assertTrue(m.getBoolean("modules." + on), on);
        }
        for (String off : List.of("public-page", "discord-webhook", "discord-bot", "milestones")) {
            assertFalse(m.getBoolean("modules." + off), off);
        }
        for (String moved : ModuleCatalog.MOVED) assertFalse(m.contains("modules." + moved), "en el addon: " + moved);
        assertFalse(m.contains("modules.achievements"), "achievements va dentro de milestones");
    }

    @Test
    void onlyConfigHasTheBigBannerTheRestAShortHeader() {
        for (String lang : List.of("es", "en")) {
            List<String> lines = List.of(templateText(lang).split("\n"));
            assertTrue(lines.get(5).contains("| |     ___  ___ | |_/ /___ _ __ _ __ _ ___"), lang + ": arte ASCII de LosPerrisStats");
            assertTrue(templateText(lang).contains(lang.equals("es") ? "EMPIECE AQUÍ" : "START HERE"));
            for (String f : allFiles()) {
                if (f.equals(ConfigLayout.CONFIG)) continue;
                String t = text(lang, f);
                assertTrue(t.startsWith("# LosPerrisStatsAPI · "), lang + "/" + f + ": cabecera corta");
                assertFalse(t.contains("___"), lang + "/" + f + ": sin banner");
                assertTrue(t.contains("{{DOCS_URL}}/" + lang + "/"), lang + "/" + f + ": enlace a la guía");
            }
        }
    }

    @Test
    void esAndEnTemplatesHaveSameKeys() throws Exception {
        for (String f : allFiles()) {
            assertEquals(yaml(text("es", f)).getKeys(true), yaml(text("en", f)).getKeys(true), f);
        }
    }

    @Test
    void enTemplatesHaveNoSpanishComments() throws Exception {
        for (String name : allFiles()) {
            File f = new File(DEFAULTS + "en/" + name);
            for (String line : Files.readAllLines(f.toPath())) {
                String t = line.trim();
                if (t.startsWith("#")) assertFalse(t.matches(".*[áéíóúñ¡¿].*"), f.getName() + ": " + t);
            }
        }
    }

    @Test
    void templatesUseTrackedStatNames() throws Exception {
        for (String lang : List.of("es", "en")) {
            for (String s : moduleTpl(lang, "periods").getStringList("stats")) assertEquals(ConfigMigrator.statName(s), s, lang + " periods");
        }
    }

    @Test
    void v5ComposesV9Files() throws Exception {
        YamlConfiguration old = legacy("config");
        old.set("port", 9999);
        old.set("security.api-key", "clave-del-usuario-123456");
        ConfigMigrator.Result r = migrate(old, legacyFiles(), "es");
        ConfigMigrator.V9 v9 = ConfigMigrator.toV9(r.main(), r.modules(), templates("es"));
        YamlConfiguration cfg = yaml(v9.files().get("config.yml"));
        assertEquals(9, cfg.getInt(ConfigMigrator.VERSION_KEY));
        assertTrue(cfg.isConfigurationSection("stats.tracked"));
        for (String id : List.of("web", "events", "scoreboard", "milestones", "quests", "titles", "discord-bot", "discord-webhook", "afk", "alerts")) {
            assertFalse(cfg.contains(id), "config.yml no lleva " + id);
        }
        YamlConfiguration web = yaml(v9.files().get(ConfigLayout.WEB));
        assertEquals(9999, web.getInt("port"));
        assertEquals("clave-del-usuario-123456", web.getString("security.api-key"));
        if (cfg.getBoolean("modules.milestones")) {
            YamlConfiguration ms = settingsOf(v9, "milestones");
            assertTrue(ms.isConfigurationSection("stats"));
            assertEquals(r.modules().get("milestones").getConfigurationSection("stats").getKeys(true), ms.getConfigurationSection("stats").getKeys(true));
        }
        assertTrue(v9.files().get("config.yml").startsWith(text("es", "config.yml").substring(0, 200)), "banner conservado");
        assertFalse(v9.files().containsKey(ConfigLayout.DATABASE), "la base de datos se mueve aparte, no desde la plantilla");
    }

    @Test
    void v6ModuleFilesGoToTheirPlace() throws Exception {
        YamlConfiguration head = yaml("""
                config-version: 6
                general:
                  language: "en"
                  timezone: "America/Costa_Rica"
                  auto-save-interval: 600
                  stats-folder: "server_data/players"
                modules:
                  events: false
                  milestones: true
                  discord-bot: true
                """);
        Map<String, YamlConfiguration> mods = new LinkedHashMap<>();
        mods.put("web", yaml("port: 8080\nsecurity:\n  api-key: \"mi-clave-secreta-0001\"\n"));
        mods.put("discord-bot", yaml("token: \"abc.def.ghi\"\nguild-id: \"123\"\n"));
        mods.put("discord-webhook", yaml("webhook-url: \"https://discord.com/api/webhooks/1/x\"\n"));
        mods.put("events", yaml("presets:\n  solo:\n    stat: kills\n    minutes: 5\n"));
        mods.put("milestones", yaml("cooldown: 7\nstats:\n  kills:\n    5: \"x\"\n"));
        mods.put("afk", yaml("timeout: 77\n"));
        mods.put("history", yaml("interval-minutes: 15\n"));
        ConfigMigrator.V9 r = ConfigMigrator.toV9(head, mods, templates("en"));
        YamlConfiguration cfg = yaml(r.files().get("config.yml"));
        assertEquals("en", cfg.getString("general.language"));
        assertEquals("America/Costa_Rica", cfg.getString("general.timezone"));
        assertEquals("MONDAY", cfg.getString("general.week-start").toUpperCase(), "clave nueva desde la plantilla");
        assertEquals("10m", cfg.getString("general.auto-save"));
        assertFalse(cfg.contains("general.auto-save-interval"));
        assertEquals("data/players", cfg.getString("advanced.stats-folder"));
        assertFalse(cfg.contains("general.stats-folder"));
        assertEquals("15m", cfg.getString("history.update-every"));
        assertFalse(cfg.contains("history.interval-minutes"));
        assertFalse(cfg.getBoolean("modules.events"));
        assertTrue(cfg.getBoolean("modules.discord-bot"));
        assertEquals("77s", settingsOf(r, "afk").getString("timeout"), "número antiguo en segundos -> texto con unidad");
        assertEquals(8080, settingsOf(r, "web").getInt("port"));
        assertEquals("mi-clave-secreta-0001", settingsOf(r, "web").getString("security.api-key"));
        assertEquals("abc.def.ghi", settingsOf(r, "discord-bot").getString("token"));
        assertEquals("https://discord.com/api/webhooks/1/x", settingsOf(r, "discord-webhook").getString("webhook-url"));
        assertEquals(List.of("solo"), new ArrayList<>(settingsOf(r, "events").getConfigurationSection("presets").getKeys(false)),
                "evento apagado pero personalizado: su archivo se crea con los presets del usuario");
        YamlConfiguration ms = settingsOf(r, "milestones");
        assertEquals(7, ms.getInt("cooldown"));
        assertEquals(List.of("kills"), new ArrayList<>(ms.getConfigurationSection("stats").getKeys(false)));
        assertTrue(r.files().get(ConfigLayout.DISCORD).contains("│ discord-bot · "), "recuadro conservado");
        assertFalse(r.files().containsKey("settings/quests.yml"), "sin ajustes del usuario: sin archivo");
    }

    @Test
    void v7SectionsAndListsMoveToOwnFiles() throws Exception {
        YamlConfiguration head = yaml("config-version: 7\ngeneral:\n  language: es\nmodules:\n  web: true\n  titles: true\n  quests: true\n  public: true\n");
        Map<String, YamlConfiguration> mods = new LinkedHashMap<>();
        mods.put("web", yaml("port: 25999\nsecurity:\n  enabled: true\n  api-key: \"UN_clave_v7_000000000\"\n"));
        mods.put("events", yaml("update-interval: 7\n"));
        mods.put("titles", yaml("format: \"<{title}> \"\ntitles:\n  solo:\n    name: \"Solo\"\n    stat: kills\n    value: 1\n"));
        mods.put("quests", yaml("daily:\n  q1:\n    objective: \"stat:kills\"\n    amount: 3\n"));
        ConfigMigrator.V9 r = ConfigMigrator.toV9(head, mods, templates("es"));
        String web = r.files().get(ConfigLayout.WEB);
        assertEquals("UN_clave_v7_000000000", yaml(web).getString("security.api-key"));
        assertEquals(25999, yaml(web).getInt("port"));
        assertTrue(web.contains("# Puerto donde se escuchará la API Web"), "comentarios de la plantilla");
        assertEquals(7, settingsOf(r, "events").getInt("update-interval"));
        YamlConfiguration titles = settingsOf(r, "titles");
        assertEquals("<{title}> ", titles.getString("format"));
        assertEquals(List.of("solo"), new ArrayList<>(titles.getConfigurationSection("titles").getKeys(false)));
        assertEquals("player_kills", titles.getString("titles.solo.stat"), "nombre de estadística coherente");
        YamlConfiguration quests = settingsOf(r, "quests");
        assertEquals(List.of("q1"), new ArrayList<>(quests.getConfigurationSection("daily").getKeys(false)));
        assertEquals("stat:player_kills", quests.getString("daily.q1.objective"));
        // las claves que faltan las completa el addon con su plantilla al cargar
        YamlConfiguration cfg = yaml(r.files().get("config.yml"));
        assertFalse(cfg.contains("web"));
        assertTrue(cfg.getBoolean("modules.public-page"), "public -> public-page");
        assertFalse(cfg.contains("modules.public"));
        assertTrue(r.files().containsKey("settings/public-page.yml"), "extra activo: archivo creado");
    }

    @Test
    void v8ToV9KeepsSecretsAndValuesAndSkipsUntouchedExtras() throws Exception {
        YamlConfiguration head = yaml("""
                config-version: 8
                general:
                  language: "es"
                  prefix: "&cMIO "
                  stats-folder: "server_data/players"
                  auto-save-interval: 90
                  debug:
                    enabled: true
                    sections:
                      web: false
                modules:
                  stats: true
                  console: false
                  milestones: true
                  seasons: false
                  quests: false
                  discord-webhook: true
                """);
        Map<String, YamlConfiguration> mods = new LinkedHashMap<>();
        mods.put("web", yaml("port: 25908\nsecurity:\n  api-key: \"UN_CAtVw6OCWQ1uwE172Cnefm6RtsSYu\"\n"));
        mods.put("discord-webhook", yaml("webhook-url: \"https://discord.com/api/webhooks/123/v6hook\"\n"));
        mods.put("discord-bot", yaml("token: \"FAKE.v6token.preserve\"\n"));
        mods.put("milestones", yaml("cooldown: 42\n"));
        mods.put("seasons", yaml("update-interval: 60\nstats:\n  - kills\n  - mob_kills\n  - time\n  - blocks_broken\n  - ores_mined\n"));
        mods.put("periods", yaml("update-interval: 7\nstats:\n  - kills\n  - time\n"));
        mods.put("heatmap", yaml("sample-minutes: 10\n"));
        mods.put("updates", yaml("check-hours: 6\n"));
        mods.put("backups", yaml("folders:\n  - server_data\n  - extra\n"));
        ConfigMigrator.V9 r = ConfigMigrator.toV9(head, mods, templates("es"));
        YamlConfiguration cfg = yaml(r.files().get("config.yml"));
        assertEquals("&cMIO ", cfg.getString("general.prefix"));
        assertEquals("90s", cfg.getString("general.auto-save"));
        assertEquals("data/players", cfg.getString("advanced.stats-folder"));
        assertTrue(cfg.getBoolean("advanced.debug.enabled"));
        assertFalse(cfg.getBoolean("advanced.debug.sections.web"));
        assertFalse(cfg.contains("general.debug"));
        assertFalse(cfg.getBoolean("modules.web-console"), "console: false -> web-console: false");
        assertTrue(cfg.getBoolean("modules.milestones"), "true del usuario respetado");
        assertTrue(cfg.getBoolean("modules.discord-webhook"));
        assertEquals("7s", cfg.getString("periods.update-interval"));
        assertEquals(List.of("player_kills", "play_time"), cfg.getStringList("periods.stats"));
        assertEquals("6h", cfg.getString("updates.check-every"));
        assertEquals(List.of("data", "extra"), cfg.getStringList("backups.folders"));
        assertEquals("UN_CAtVw6OCWQ1uwE172Cnefm6RtsSYu", settingsOf(r, "web").getString("security.api-key"));
        assertEquals("https://discord.com/api/webhooks/123/v6hook", settingsOf(r, "discord-webhook").getString("webhook-url"));
        assertEquals("FAKE.v6token.preserve", settingsOf(r, "discord-bot").getString("token"));
        assertEquals(42, settingsOf(r, "milestones").getInt("cooldown"));
        assertEquals("10m", settingsOf(r, "heatmap").getString("sample-every"));
        // seasons ya no está en el núcleo: sus valores se guardan tal cual (con nombres v9) para el addon
        assertEquals(List.of("player_kills", "mob_kills", "play_time", "blocks_broken", "ores_mined"), settingsOf(r, "seasons").getStringList("stats"));
        assertFalse(r.files().containsKey("settings/quests.yml"));
        assertFalse(r.files().containsKey("settings/web-console.yml"), "consola apagada y sin cambios");
    }

    @Test
    void migrationIsIdempotent() throws Exception {
        ConfigMigrator.Result r = migrate(legacy("config"), legacyFiles(), "es");
        ConfigMigrator.V9 v9 = ConfigMigrator.toV9(r.main(), r.modules(), templates("es"));
        String v10 = ExtrasSplit.config(v9.files().get("config.yml"), templateText("es")).config();
        ConfigLayout.Doc doc = ConfigLayout.parse(v10);
        assertEquals(List.of(), ConfigMigrator.updateMain(ConfigLayout.head(doc), tplHead("es")));
        for (ModuleCatalog.Info i : ModuleCatalog.all()) {
            if (!i.hasSettings() || v9.skipped().contains(i.id())) continue;
            assertTrue(ConfigMigrator.mergeModule(i.id(), settingsOf(v9, i.id()), moduleTpl("es", i.id())).isEmpty(), i.id());
        }
    }

    @Test
    void topYamlKeepsInnerCommentsAndReplacesOnlyThatBlock() throws Exception {
        String tpl = "# banner\n\n# puerto\nport: 1\n\n# seguridad\nsecurity:\n  # activar\n  enabled: true\n  api-key: \"\"\n\n# fin\nlast: x\n";
        YamlConfiguration y = yaml(tpl);
        y.set("security.api-key", "nueva");
        String out = ConfigLayout.replaceTop(tpl, "security", ConfigMigrator.topYaml(y, "security"), null);
        assertEquals(tpl.replace("api-key: \"\"", "api-key: nueva"), out);
        String added = ConfigLayout.replaceTop(out, "extra", "extra: 5\n", List.of("# nuevo"));
        assertTrue(added.endsWith("last: x\n\n# nuevo\nextra: 5\n"));
    }

    @Test
    void userListsReplaceTemplateLists() throws Exception {
        YamlConfiguration ms = yaml("""
                enabled: false
                stats:
                  kills:
                    10: "x"
                """);
        Map<String, YamlConfiguration> files = new HashMap<>(Map.of("milestones", ms));
        ConfigMigrator.Result r = migrate(legacy("config"), files, "en");
        YamlConfiguration out = r.modules().get("milestones");
        assertEquals(List.of("kills"), new ArrayList<>(out.getConfigurationSection("stats").getKeys(false)));
        assertFalse(r.main().getBoolean("modules.milestones"), "enabled: false de milestones.yml se respeta");
        // y al arrancar después no reaparecen los hitos borrados
        assertTrue(ConfigMigrator.mergeModule("milestones", out, moduleTpl("en", "milestones")).isEmpty());
    }

    @Test
    void migratesV1Switches() throws Exception {
        YamlConfiguration old = yaml("""
                web:
                  port: 9999
                scoreboard:
                  enabled: false
                  title: "&bMio"
                discord:
                  enabled: true
                  webhook-url: "https://example"
                milestones:
                  kills: [10]
                """);
        ConfigMigrator.Result r = migrate(old, Map.of(), "es");
        assertFalse(r.main().getBoolean("modules.scoreboard"));
        assertTrue(r.main().getBoolean("modules.discord-webhook"));
        assertEquals("&bMio", r.modules().get("scoreboard").getString("title"));
        assertFalse(r.modules().get("scoreboard").contains("enabled"));
        assertEquals("https://example", r.modules().get("discord-webhook").getString("webhook-url"));
        assertEquals(9999, r.modules().get("web").getInt("port"));
        assertTrue(r.main().getBoolean("modules.web"));
        assertFalse(r.main().getBoolean("modules.public") || r.main().getBoolean("modules.public-page"));
    }

    @Test
    void updateMainNormalizesOldFlagsAndAddsMissing() throws Exception {
        YamlConfiguration cfg = yaml("""
                config-version: 6
                general:
                  language: "es"
                modules:
                  discord:
                    enabled: true
                  discordbot: false
                  web: false
                """);
        List<String> changes = ConfigMigrator.updateMain(cfg, tplHead("es"));
        assertFalse(changes.isEmpty());
        assertTrue(cfg.getBoolean("modules.discord-webhook"));
        assertFalse(cfg.getBoolean("modules.discord-bot"));
        assertFalse(cfg.getBoolean("modules.web"), "valor del usuario intacto");
        assertFalse(cfg.contains("modules.discord"));
        assertEquals("5m", cfg.getString("general.auto-save"));
    }

    @Test
    void mergeNeverOverridesScalarParent() throws Exception {
        YamlConfiguration target = yaml("web: false\n");
        YamlConfiguration defs = yaml("web:\n  port: 1\n");
        assertTrue(ConfigMigrator.mergeDefaults(target, defs, List.of()).isEmpty());
        assertFalse(target.getBoolean("web"));
    }

    @Test
    void backupZipContainsSources(@TempDir File dir) throws Exception {
        File a = new File(dir, "config.yml");
        Files.writeString(a.toPath(), "x: 1\n");
        File m = new File(new File(dir, "modules"), "web.yml");
        m.getParentFile().mkdirs();
        Files.writeString(m.toPath(), "port: 1\n");
        File zip = new File(new File(dir, "backups"), "m.zip");
        ConfigService.zip(zip, dir, List.of(a, m));
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zip)) {
            assertNotNull(z.getEntry("config.yml"));
            assertNotNull(z.getEntry("modules/web.yml"), "ruta relativa: se puede restaurar tal cual");
        }
    }

    @Test
    void changedLangKeysWithStalePlaceholdersAreRestored() throws Exception {
        YamlConfiguration mine = new YamlConfiguration();
        mine.loadFromString("help:\n  line: '&f {usage} &7- {value}'\ncommands:\n  modules:\n    header: '&aMis módulos {count}'\n");
        YamlConfiguration jar = new YamlConfiguration();
        jar.loadFromString("help:\n  line: '&f  {usage} &8· &7{description}'\ncommands:\n  modules:\n    header: '&7Módulos {count}/{total}'\n");
        java.util.Map<String, String> fixed = ConfigMigrator.fixChangedKeys(mine, jar, ConfigMigrator.CHANGED_LANG_KEYS);
        assertEquals(java.util.List.of("help.line"), java.util.List.copyOf(fixed.keySet()));
        assertEquals("&f {usage} &7- {value}", fixed.get("help.line"));
        assertEquals("&f  {usage} &8· &7{description}", mine.getString("help.line"));
        assertEquals("&aMis módulos {count}", mine.getString("commands.modules.header"), "un texto propio con variables válidas se respeta");

        mine.set("commands.modules.header", "&7Módulos:");
        fixed = ConfigMigrator.fixChangedKeys(mine, jar, ConfigMigrator.CHANGED_LANG_KEYS);
        assertEquals(java.util.List.of("commands.modules.header"), java.util.List.copyOf(fixed.keySet()), "el texto de serie de v5 se actualiza");
        assertEquals("&7Módulos {count}/{total}", mine.getString("commands.modules.header"));
    }

    @Test
    void phaseFourLangFileGetsUsableHelpLine() throws Exception {
        for (String l : new String[]{"es", "en"}) {
            YamlConfiguration jar = YamlConfiguration.loadConfiguration(new java.io.File("src/main/resources/lang/" + l + ".yml"));
            YamlConfiguration v5 = new YamlConfiguration();
            v5.loadFromString("help:\n  line: '&f {usage} &7- {value}'\n");
            ConfigMigrator.mergeDefaults(v5, jar, java.util.List.of());
            ConfigMigrator.fixChangedKeys(v5, jar, ConfigMigrator.CHANGED_LANG_KEYS);
            assertFalse(v5.getString("help.line").contains("{value}"), l);
        }
    }
}
