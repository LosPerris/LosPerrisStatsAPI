package me.gordi.mineapi.core.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModuleToggleWriterTest {

    private static final String BASE = """
            # Mi comentario
            config-version: 6
            general:
              language: es
            modules:
              # ── Base
              stats: true    # no tocar
              afk: false

              # ── Web
              web: true
            other: 1
            """;

    private static YamlConfiguration yaml(String s) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(s);
        return y;
    }

    @Test
    void changesOnlyThatLineAndKeepsComments() throws Exception {
        String out = ModuleToggleWriter.apply(BASE, "afk", true);
        assertEquals(BASE.replace("  afk: false", "  afk: true"), out);
        out = ModuleToggleWriter.apply(BASE, "stats", false);
        assertTrue(out.contains("  stats: false    # no tocar"), out);
        assertTrue(out.contains("# Mi comentario") && out.contains("# ── Web"));
        assertFalse(yaml(out).getBoolean("modules.stats"));
    }

    @Test
    void addsMissingKeyInsideBlock() throws Exception {
        String out = ModuleToggleWriter.apply(BASE, "heatmap", true);
        YamlConfiguration y = yaml(out);
        assertTrue(y.getBoolean("modules.heatmap"));
        assertEquals(1, y.getInt("other"), "no se mete en otra sección");
        assertTrue(out.contains("# Mi comentario"));
    }

    @Test
    void createsBlockWhenMissing() throws Exception {
        String out = ModuleToggleWriter.apply("config-version: 6\n# fin\n", "web", false);
        assertFalse(yaml(out).getBoolean("modules.web", true));
        assertTrue(out.startsWith("config-version: 6\n# fin\n"));
    }

    @Test
    void renamesOldAliasAndHandlesOldFormat() throws Exception {
        String old = "modules:\n  discord: true\n  stats:\n    enabled: true\n    extra: 1\n";
        String out = ModuleToggleWriter.apply(old, "discord-webhook", false, "discord");
        assertTrue(out.contains("  discord-webhook: false"), out);
        out = ModuleToggleWriter.apply(old, "stats", false);
        assertTrue(out.contains("    enabled: false\n    extra: 1"), out);
    }

    @Test
    void keepsWindowsLineEnds() {
        String out = ModuleToggleWriter.apply(BASE.replace("\n", "\r\n"), "afk", true);
        assertTrue(out.contains("  afk: true\r\n"));
        assertFalse(out.replace("\r\n", "").contains("\n"));
    }

    @Test
    void worksOnTheRealTemplates() throws Exception {
        for (String lang : new String[]{"es", "en"}) {
            String t = Files.readString(new File("src/main/resources/defaults/" + lang + "/config.yml").toPath(), StandardCharsets.UTF_8);
            YamlConfiguration before = yaml(t);
            for (String id : before.getConfigurationSection("modules").getKeys(false)) {
                boolean v = before.getBoolean("modules." + id);
                String out = ModuleToggleWriter.edit(t, id, !v);
                assertNotNull(out, lang + ":" + id);
                assertEquals(t.split("\n").length, out.split("\n").length, "solo cambia una línea");
                assertEquals(!v, yaml(out).getBoolean("modules." + id), lang + ":" + id);
            }
        }
    }

    private static Map<String, ModuleToggleWriter.Switch> extras(boolean quests) {
        Map<String, ModuleToggleWriter.Switch> m = new LinkedHashMap<>();
        m.put("quests", new ModuleToggleWriter.Switch(quests, "Misiones → LosPerrisExtras/settings/quests.yml"));
        m.put("titles", new ModuleToggleWriter.Switch(false, null));
        return m;
    }

    @Test
    void addGroupAppendsMissingSwitchesUnderItsHeader() throws Exception {
        String out = ModuleToggleWriter.addGroup(BASE, "LosPerrisExtras", extras(true));
        assertTrue(out.startsWith(BASE.substring(0, BASE.indexOf("other: 1"))), "lo anterior queda igual:\n" + out);
        assertTrue(out.contains("""
                  web: true

                  # ===== LosPerrisExtras =====
                  quests: true          # Misiones → LosPerrisExtras/settings/quests.yml
                  titles: false
                other: 1
                """), out);
        YamlConfiguration y = yaml(out);
        assertTrue(y.getBoolean("modules.quests"));
        assertFalse(y.getBoolean("modules.titles"));
        assertTrue(y.getBoolean("modules.stats"));
        assertEquals(1, y.getInt("other"));
    }

    @Test
    void addGroupNeverOverwritesAndKeepsSwitchesOfRemovedAddons() {
        String once = ModuleToggleWriter.addGroup(BASE, "LosPerrisExtras", extras(true));
        String edited = once.replace("  quests: true", "  quests: false");
        assertEquals(edited, ModuleToggleWriter.addGroup(edited, "LosPerrisExtras", extras(true)), "ya están: no cambia nada");
        Map<String, ModuleToggleWriter.Switch> more = new LinkedHashMap<>(extras(true));
        more.put("seasons", new ModuleToggleWriter.Switch(false, null));
        String out = ModuleToggleWriter.addGroup(edited, "LosPerrisExtras", more);
        assertTrue(out.contains("  titles: false\n  seasons: false\nother: 1"), "el nuevo va al final de su grupo:\n" + out);
        assertTrue(out.contains("  quests: false"), out);
        assertEquals(1, out.split("# ===== LosPerrisExtras =====", -1).length - 1, "una sola cabecera");
    }

    @Test
    void addGroupCreatesTheModulesBlockWhenMissing() throws Exception {
        String out = ModuleToggleWriter.addGroup("config-version: 10\n", "Extra", extras(false));
        YamlConfiguration y = yaml(out);
        assertFalse(y.getBoolean("modules.quests", true));
        assertTrue(out.contains("modules:\n  # ===== Extra =====\n  quests: false"), out);
    }
}
