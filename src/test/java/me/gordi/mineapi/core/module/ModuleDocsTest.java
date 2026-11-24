package me.gordi.mineapi.core.module;

import me.gordi.mineapi.core.command.HelpCatalog;
import me.gordi.mineapi.core.config.ConfigLayout;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La guía (docs/es/GUIA.md, docs/en/GUIDE.md) y la cabecera de cada módulo en su archivo de
 * configuración no se quedan atrás: todo módulo de {@link ModuleCatalog} aparece con sus ajustes,
 * comandos y permisos. The guides and every module header cover every catalog module.
 */
class ModuleDocsTest {

    private static final String[][] GUIDES = {{"es", "docs/es/GUIA.md"}, {"en", "docs/en/GUIDE.md"}};

    @Test
    void everyCatalogModuleHasItsSectionInBothGuides() throws IOException {
        for (String[] g : GUIDES) {
            String lang = g[0];
            String text = Files.readString(Path.of(g[1]), StandardCharsets.UTF_8);
            YamlConfiguration l = YamlConfiguration.loadConfiguration(new File("src/main/resources/lang/" + lang + ".yml"));
            for (ModuleCatalog.Info m : ModuleCatalog.all()) {
                String section = section(text, m.id());
                assertNotNull(section, g[1] + ": falta la sección ### … (`" + m.id() + "`)");
                assertTrue(section.contains("`modules." + m.id() + ": true`"), g[1] + " " + m.id() + ": falta cómo activarlo");
                if (m.hasSettings()) assertTrue(section.contains("`" + m.settings() + "`"), g[1] + " " + m.id() + ": falta " + m.settings());
                assertTrue(text.contains("<a id=\"" + m.id() + "\"></a>"), g[1] + ": falta el ancla #" + m.id());
                for (String p : m.permissions()) assertTrue(section.contains("`" + p + "`"), g[1] + " " + m.id() + ": falta el permiso " + p);
                for (HelpCatalog.Entry e : HelpCatalog.of(m.id())) {
                    String usage = l.getString("help.cmd." + e.key() + ".usage");
                    assertNotNull(usage, lang + ": help.cmd." + e.key() + ".usage");
                    assertTrue(section.contains("`" + usage + "`"), g[1] + " " + m.id() + ": falta el comando " + usage);
                }
            }
        }
    }

    @Test
    void everyAdminPermissionIsInTheGuidePermissionTable() throws IOException {
        for (String[] g : GUIDES) {
            String text = Files.readString(Path.of(g[1]), StandardCharsets.UTF_8);
            for (HelpCatalog.Entry e : HelpCatalog.all()) {
                if (e.permission() != null) assertTrue(text.contains("| `" + e.permission() + "` |"), g[1] + ": falta " + e.permission() + " en la tabla de permisos");
            }
        }
    }

    @Test
    void everyModuleHasItsHeaderWhereItsSettingsLive() throws Exception {
        for (String lang : new String[]{"es", "en"}) {
            String guide = "es".equals(lang) ? "docs/es/GUIA.md" : "docs/en/GUIDE.md";
            String guideUrl = "{{DOCS_URL}}/" + guide.substring("docs/".length());
            String dir = "src/main/resources/defaults/" + lang + "/";
            Path cfg = Path.of(dir + "config.yml");
            ConfigLayout.Doc doc = ConfigLayout.parse(Files.readString(cfg, StandardCharsets.UTF_8));
            for (ModuleCatalog.Info m : ModuleCatalog.all()) {
                ConfigLayout.Place place = ConfigLayout.place(m.id());
                boolean inConfig = ConfigLayout.CONFIG.equals(place.file());
                assertEquals(m.hasSettings() && inConfig, doc.has(m.id()), cfg + ": sección " + m.id() + " no coincide con ModuleCatalog");
                if (!m.hasSettings()) continue;
                if (inConfig) {
                    // config.yml: una línea por sección, sin ruido
                    List<String> lead = ConfigLayout.lead(doc, m.id());
                    assertEquals(1, lead.size(), cfg + " · " + m.id() + ": cabecera de una línea");
                    String h = lead.get(0);
                    assertTrue(h.startsWith("# ── " + m.id() + " · ") && h.endsWith(" ──"), cfg + " · " + m.id() + ": " + h);
                    continue;
                }
                Path file = Path.of(dir + place.file());
                String text = Files.readString(file, StandardCharsets.UTF_8);
                String where = file + " · " + m.id();
                if (place.whole()) {
                    // settings/<id>.yml: cabecera corta de 4 líneas
                    String header = header(text);
                    assertTrue(header.startsWith("# LosPerrisStatsAPI · ") && header.contains("(" + place.file() + ")"), where + ": cabecera");
                    assertTrue(header.contains("modules." + m.id() + ": true"), where + ": falta cómo activarlo");
                    assertTrue(header.contains(guideUrl + "#" + m.id()), where + ": falta el enlace a la guía");
                    assertTrue(text.split("\\R\\R", 2)[0].split("\\R").length <= 5, where + ": cabecera corta (hasta la primera línea vacía)");
                    continue;
                }
                // sección de discord.yml: recuadro completo (comparten archivo)
                String header = String.join("\n", ConfigLayout.lead(ConfigLayout.parse(text), place.section()));
                assertTrue(header.contains("│ " + m.id() + " · "), where + ": el recuadro no nombra el módulo");
                assertTrue(header.contains("modules." + m.id() + ": true"), where + ": falta cómo activarlo");
                assertTrue(header.contains("/lpa modules enable " + m.id()), where + ": falta /lpa modules enable");
                assertTrue(header.contains(guideUrl + "#" + m.id()), where + ": falta el enlace a la guía");
                for (String c : m.commands()) assertTrue(header.contains("/lpa " + c), where + ": falta el comando /lpa " + c);
                for (String p : m.permissions()) assertTrue(header.contains(p), where + ": falta el permiso " + p);
                checkVariables(lang, header, where);
            }
            assertTrue(Files.readString(cfg, StandardCharsets.UTF_8).contains(guideUrl), cfg + ": falta el enlace a la guía");
        }
    }

    private static void checkVariables(String lang, String header, String where) {
        Matcher v = Pattern.compile("\\{\\{DOCS_URL}}/" + lang + "/variables/ \\(([^)]*)\\)").matcher(header);
        if (v.find()) {
            for (String doc : v.group(1).split(",")) {
                Path p = Path.of("docs/" + lang + "/variables/" + doc.trim());
                assertTrue(Files.exists(p), where + ": no existe " + p);
            }
        }
    }

    /** Texto de la sección {@code ### … (`id`)} hasta el siguiente título. */
    private static String section(String text, String id) {
        int i = text.indexOf("(`" + id + "`)\n");
        if (i < 0) return null;
        int start = text.lastIndexOf("\n### ", i);
        if (start < 0 || text.indexOf('\n', start + 1) < i) return null;
        int end = text.indexOf("\n#", i);
        return text.substring(start, end < 0 ? text.length() : end);
    }

    /** Los comentarios del principio del archivo (hasta la primera clave). */
    private static String header(String yml) {
        StringBuilder b = new StringBuilder();
        for (String line : yml.split("\r?\n")) {
            if (!line.isBlank() && !line.startsWith("#")) break;
            b.append(line).append('\n');
        }
        return b.toString();
    }
}
