package me.gordi.mineapi.core.command;

import me.gordi.mineapi.core.i18n.Lang;
import me.gordi.mineapi.core.module.ModuleCatalog;
import me.gordi.mineapi.core.module.ModuleManager;
import me.gordi.mineapi.core.module.Perms;
import me.gordi.mineapi.core.text.Template;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class HelpAndModulesViewTest {

    private static final Lang ES = lang("es"), EN = lang("en");

    private static Lang lang(String code) {
        return Lang.parse(code, YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/" + code + ".yml")));
    }

    private static String tr(Lang l, String key, Object... vars) {
        Template t = l.get(key);
        assertNotNull(t, l.code() + ": falta " + key);
        return t.render(vars);
    }

    private static HelpView.Context help(Lang l, Predicate<String> perm, Set<String> on, boolean console) {
        return new HelpView.Context() {
            public String tr(String key, Object... vars) { return HelpAndModulesViewTest.tr(l, key, vars); }
            public boolean has(String p) { return perm.test(p); }
            public boolean on(String m) { return HelpCatalog.CORE.equals(m) || on.contains(m); }
            public boolean console() { return console; }
        };
    }

    private static String join(List<HelpView.Line> lines) {
        StringBuilder sb = new StringBuilder();
        for (HelpView.Line l : lines) sb.append(l.text()).append('\n');
        return sb.toString();
    }

    @Test
    void everyHelpEntryHasTextInBothLanguages() {
        for (Lang l : new Lang[]{ES, EN}) {
            for (HelpCatalog.Entry e : HelpCatalog.all()) {
                for (String k : new String[]{"usage", "desc", "info"}) tr(l, "help.cmd." + e.key() + "." + k);
                assertTrue(e.module().equals(HelpCatalog.CORE) || ModuleCatalog.get(e.module()) != null, e.key());
            }
            for (ModuleCatalog.Category c : ModuleCatalog.Category.values()) tr(l, "module-info.category." + c.key());
            tr(l, "module-info.category.general");
            for (ModuleCatalog.Info i : ModuleCatalog.all()) {
                tr(l, "module-info." + i.id() + ".name");
                tr(l, "module-info." + i.id() + ".desc");
            }
            for (String r : new String[]{"needs", "error", "unknown", "no-papi", "no-token", "no-webhook"}) {
                tr(l, "commands.modules.reason." + r);
            }
        }
    }

    @Test
    void everyCatalogCommandIsInTheHelp() {
        for (ModuleCatalog.Info i : ModuleCatalog.all()) {
            for (String cmd : i.commands()) {
                boolean found = false;
                for (HelpCatalog.Entry e : HelpCatalog.of(i.id())) found |= e.suggest().startsWith("/lpa " + cmd);
                assertTrue(found, i.id() + ": /lpa " + cmd + " no aparece en la ayuda");
            }
        }
    }

    @Test
    void playerHelpHidesAdminAndOffModules() {
        Set<String> on = new HashSet<>(Set.of("stats", "scoreboard"));
        Predicate<String> player = p -> p.equals(Perms.SCOREBOARD);
        List<HelpView.Line> lines = HelpView.build(help(ES, player, on, false), "1.3", null);
        String text = join(lines);
        assertTrue(text.contains("/lpa top"));
        assertTrue(text.contains("/lpa sb"));
        assertFalse(text.contains("/lpa period"), "periods está apagado");
        assertFalse(text.contains("/lpa reset"), "sin permiso de admin");
        assertFalse(text.contains("help admin"), "sin enlace a admin");
        HelpView.Line top = lines.stream().filter(l -> l.text().contains("/lpa top")).findFirst().orElseThrow();
        assertEquals("/lpa top ", top.command());
        assertFalse(top.run(), "sugiere, no ejecuta");
        assertTrue(top.hover().contains("todos"));
        // la categoría General (núcleo) va al final
        assertTrue(text.indexOf("/lpa lang") > text.indexOf("/lpa sb"));
    }

    @Test
    void adminSeesLinkAndAdminPage() {
        Set<String> on = new HashSet<>(ModuleCatalog.ids());
        List<HelpView.Line> lines = HelpView.build(help(EN, p -> true, on, false), "1.3", null);
        HelpView.Line link = lines.stream().filter(l -> "/lpa help admin".equals(l.command())).findFirst().orElseThrow();
        assertTrue(link.run());
        assertFalse(join(lines).contains("/lpa reset"), "la ayuda principal no lista comandos de admin");
        String admin = join(HelpView.build(help(EN, p -> true, on, false), "1.3", "admin"));
        for (String c : new String[]{"/lpa reset", "/lpa period end", "/lpa modules enable", "/lpa backup", "/lpa reload"}) {
            assertTrue(admin.contains(c), c);
        }
        // un encargado de periodos solo ve lo suyo
        String mod = join(HelpView.build(help(ES, p -> p.equals(Perms.PERIODS_ADMIN), on, false), "1.3", "admin"));
        assertTrue(mod.contains("/lpa period end"));
        assertFalse(mod.contains("/lpa reload"));
        for (String gone : new String[]{"/lpa holo", "/lpa mod ", "/lpa season", "/lpa event", "/lpa title", "/lpa quests"}) {
            assertFalse(admin.contains(gone), "se movió al addon: " + gone);
        }
    }

    @Test
    void consoleGetsPlainTextAndModuleTopicWorks() {
        Set<String> on = Set.of("periods");
        List<HelpView.Line> lines = HelpView.build(help(ES, p -> true, on, true), "1.3", null);
        for (HelpView.Line l : lines) {
            assertNull(l.hover());
            assertNull(l.command());
        }
        String periods = join(HelpView.build(help(ES, p -> true, on, true), "1.3", "periods"));
        assertTrue(periods.contains("/lpa period end"));
        assertTrue(periods.contains("config.yml → periods"));
        String off = join(HelpView.build(help(ES, p -> true, on, true), "1.3", "scoreboard"));
        assertTrue(off.contains("apagado"));
        String unknown = join(HelpView.build(help(ES, p -> true, on, true), "1.3", "nope"));
        assertTrue(unknown.contains("nope"));
        String moved = join(HelpView.build(help(ES, p -> true, on, true), "1.3", "titles"));
        assertTrue(moved.contains("titles"), "un módulo del addon no existe en el núcleo");
    }

    private static ModulesView.Context modules(Lang l, boolean console, boolean toggle) {
        return new ModulesView.Context() {
            public String tr(String key, Object... vars) { return HelpAndModulesViewTest.tr(l, key, vars); }
            public ModuleManager.Status status(String id) {
                return switch (id) {
                    case "stats", "web" -> ModuleManager.Status.ON;
                    case "discord-bot" -> ModuleManager.Status.BROKEN;
                    default -> ModuleManager.Status.OFF;
                };
            }
            public ModuleManager.Problem problem(String id) {
                return id.equals("discord-bot") ? new ModuleManager.Problem("no-token", null) : null;
            }
            public List<String> dependencies(String id) { return id.equals("web") ? List.of("stats") : List.of(); }
            public boolean console() { return console; }
            public boolean canToggle() { return toggle; }
        };
    }

    @Test
    void modulesListAndDetail() {
        List<List<HelpView.Line>> rows = ModulesView.list(modules(ES, false, true));
        assertTrue(rows.get(0).get(0).text().contains("2"));
        assertTrue(rows.get(0).get(0).text().contains(String.valueOf(ModuleCatalog.all().size())));
        int clickable = 0;
        for (List<HelpView.Line> r : rows) for (HelpView.Line l : r) if (l.command() != null) clickable++;
        assertEquals(ModuleCatalog.all().size(), clickable, "cada módulo es clicable");
        StringBuilder all = new StringBuilder();
        for (List<HelpView.Line> r : rows) for (HelpView.Line l : r) all.append(l.text());
        assertTrue(all.toString().contains("discord-bot.token"), "motivo del ⚠");

        List<List<HelpView.Line>> d = ModulesView.detail(modules(EN, false, true), ModuleCatalog.get("web"));
        HelpView.Line button = d.get(d.size() - 1).get(0);
        assertEquals("/lpa modules disable web", button.command());
        assertTrue(button.run());
        StringBuilder sb = new StringBuilder();
        for (List<HelpView.Line> r : d) sb.append(r.get(0).text());
        assertTrue(sb.toString().contains("stats"), "dependencias");
        d = ModulesView.detail(modules(EN, false, false), ModuleCatalog.get("afk"));
        for (List<HelpView.Line> r : d) assertNull(r.get(0).command(), "sin permiso no hay botón");
    }

    @Test
    void pluginYmlDeclaresEveryPermissionUnderAdmin() throws Exception {
        YamlConfiguration y = YamlConfiguration.loadConfiguration(new File("src/main/resources/plugin.yml"));
        assertNotNull(y.getConfigurationSection("permissions"));
        Set<String> declared = new HashSet<>();
        Set<String> adminChildren = new HashSet<>();
        // las claves llevan puntos: se leen con el separador cambiado
        YamlConfiguration raw = new YamlConfiguration();
        raw.options().pathSeparator('/');
        raw.load(new File("src/main/resources/plugin.yml"));
        declared.addAll(raw.getConfigurationSection("permissions").getKeys(false));
        adminChildren.addAll(raw.getConfigurationSection("permissions/losperris.admin/children").getKeys(false));
        for (Field f : Perms.class.getFields()) {
            if (!Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
            String p = (String) f.get(null);
            assertTrue(declared.contains(p), "plugin.yml no declara " + p);
            boolean playerPerm = p.equals(Perms.ADMIN) || p.equals(Perms.SCOREBOARD);
            assertEquals(!playerPerm, adminChildren.contains(p), p);
        }
        assertEquals("op", raw.getString("permissions/losperris.admin/default"));
        assertFalse(declared.contains("losperris.moderation"), "los permisos de los extras los declara el addon");
    }

    /** Catálogo de un addon de prueba: un grupo, un módulo y una línea de ayuda. */
    private static final CatalogView ADDON = new CatalogView() {
        private final Mod quests = new Mod("quests", "players", "LosPerrisExtras/settings/quests.yml", List.of("quests"), List.of(), null);
        public List<String> categories() { return List.of("players"); }
        public List<Mod> all() { return List.of(quests); }
        public Mod get(String id) { return "quests".equals(id) ? quests : null; }
        public List<HelpCatalog.Entry> help() { return List.of(new HelpCatalog.Entry("quests", "quests", null, false, "/lpa quests")); }
        public String guide() { return "https://example.org/extras"; }
    };

    @Test
    void addonModulesAndHelpJoinTheCoreCatalog() {
        CommandCatalog catalog = new CommandCatalog();
        Object owner = new Object();
        catalog.add(owner, ADDON);
        assertTrue(catalog.ids().containsAll(List.of("stats", "quests")));
        assertEquals("players", catalog.categories().get(catalog.categories().size() - 1));
        assertEquals("https://example.org/extras", catalog.guide("quests"));
        assertEquals(CatalogView.CORE.guide(), catalog.guide("stats"));
        HelpView.Context c = new HelpView.Context() {
            public String tr(String key, Object... vars) {
                Template t = ES.get(key);
                return t != null ? t.render(vars) : key; // las claves del addon vienen de sus idiomas
            }
            public boolean has(String p) { return true; }
            public boolean on(String m) { return true; }
            public boolean console() { return false; }
            public CatalogView catalog() { return catalog; }
        };
        List<HelpView.Line> lines = HelpView.build(c, "1.5", null);
        assertTrue(join(lines).contains("module-info.category.players"), join(lines));
        assertTrue(lines.stream().anyMatch(l -> "/lpa quests".equals(l.command())));
        catalog.unregisterAll(owner);
        assertNull(catalog.get("quests"));
        assertFalse(HelpView.build(c, "1.5", null).stream().anyMatch(l -> "/lpa quests".equals(l.command())));
    }
}
