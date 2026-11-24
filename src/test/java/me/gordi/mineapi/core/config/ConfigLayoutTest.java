package me.gordi.mineapi.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** config.yml v7 por bloques: se analiza solo lo necesario y se reescribe solo un bloque. */
class ConfigLayoutTest {

    private static final String TEXT = """
            # Cabecera
            config-version: 7
            general:
              language: es
            modules:
              web: true
              events: false

            # ┌──
            # │ web · Web
            # └──
            web:
              # puerto
              port: 25908
              security:
                api-key: ""   # vacía = se genera

            # ┌──
            # │ events · Eventos
            # └──
            events:
              presets: [esto no es: yaml válido
                 : roto
            """;

    @Test
    void splitsTopLevelBlocksWithTheirBanner() {
        ConfigLayout.Doc d = ConfigLayout.parse(TEXT);
        assertEquals(List.of("config-version", "general", "modules", "web", "events"), List.copyOf(d.keys()));
        assertEquals(List.of("# ┌──", "# │ web · Web", "# └──"), ConfigLayout.lead(d, "web"));
        assertEquals(List.of("# Cabecera"), ConfigLayout.lead(d, "config-version"));
    }

    @Test
    void headAndEnabledSectionsNeverParseADisabledSection() throws Exception {
        ConfigLayout.Doc d = ConfigLayout.parse(TEXT);
        YamlConfiguration head = ConfigLayout.head(d);
        assertEquals(7, head.getInt("config-version"));
        assertFalse(head.getBoolean("modules.events"));
        assertFalse(head.contains("web"), "la cabecera no incluye las secciones");
        YamlConfiguration web = ConfigLayout.section(d, "web");
        assertEquals(25908, web.getInt("port"));
        assertEquals("", web.getString("security.api-key"));
        // la sección de events está rota: solo falla si alguien la analiza
        assertThrows(InvalidConfigurationException.class, () -> ConfigLayout.section(d, "events"));
    }

    @Test
    void replaceTouchesOnlyThatBlock() throws Exception {
        String out = ConfigLayout.replace(TEXT, "web", "port: 8080\nsecurity:\n  api-key: abc\n", null);
        String[] before = TEXT.split("\n"), after = out.split("\n");
        ConfigLayout.Doc d0 = ConfigLayout.parse(TEXT), d1 = ConfigLayout.parse(out);
        assertEquals(String.join("\n", ConfigLayout.lead(d0, "events")), String.join("\n", ConfigLayout.lead(d1, "events")));
        assertTrue(out.startsWith(TEXT.substring(0, TEXT.indexOf("web:\n"))), "todo lo anterior intacto");
        assertTrue(out.endsWith(TEXT.substring(TEXT.indexOf("\n# ┌──\n# │ events") )), "todo lo posterior intacto");
        assertEquals(8080, ConfigLayout.section(d1, "web").getInt("port"));
        assertTrue(after.length < before.length);
    }

    @Test
    void editBlockKeepsComments() throws Exception {
        String out = ConfigLayout.editBlock(TEXT, "web", b -> b.replace("api-key: \"\"", "api-key: \"k-123\""));
        assertTrue(out.contains("    api-key: \"k-123\"   # vacía = se genera"));
        assertTrue(out.contains("  # puerto"));
        assertEquals(TEXT.length() + 5, out.length());
        assertEquals(TEXT, ConfigLayout.editBlock(TEXT, "nope", b -> "x"));
    }

    @Test
    void missingSectionIsAppendedWithItsBanner() throws Exception {
        String out = ConfigLayout.replace(TEXT, "afk", "timeout: 300\n", List.of("# │ afk · AFK"));
        assertTrue(out.startsWith(TEXT.stripTrailing()));
        assertTrue(out.endsWith("\n\n# │ afk · AFK\nafk:\n  timeout: 300\n"), out);
        assertEquals(List.of("# │ afk · AFK"), ConfigLayout.lead(ConfigLayout.parse(out), "afk"));
        String empty = ConfigLayout.replace(TEXT, "console", "", null);
        assertTrue(empty.endsWith("\nconsole: {}\n"));
        assertEquals(0, ConfigLayout.section(ConfigLayout.parse(empty), "console").getKeys(false).size());
    }

    @Test
    void scalarWhereASectionIsExpectedIsAnError() {
        ConfigLayout.Doc d = ConfigLayout.parse("web: 5\nafk: {}\n");
        assertThrows(InvalidConfigurationException.class, () -> ConfigLayout.section(d, "web"));
        assertDoesNotThrow(() -> ConfigLayout.section(d, "afk"));
    }

    @Test
    void realTemplatesSplitIntoTheSameKeysAsAFullParse() throws Exception {
        for (String lang : new String[]{"es", "en"}) {
            String t = Files.readString(new File("src/main/resources/defaults/" + lang + "/config.yml").toPath(), StandardCharsets.UTF_8);
            YamlConfiguration full = new YamlConfiguration();
            full.loadFromString(t);
            ConfigLayout.Doc d = ConfigLayout.parse(t);
            assertEquals(full.getKeys(false), d.keys(), lang);
            for (String k : d.keys()) {
                if (ConfigLayout.HEAD.contains(k)) continue;
                YamlConfiguration s = ConfigLayout.section(d, k);
                for (String p : s.getKeys(true)) {
                    if (!s.isConfigurationSection(p)) assertEquals(full.get(k + "." + p), s.get(p), lang + " " + k + "." + p);
                }
                // reescribir una sección con su propio contenido no cambia lo que se lee
                String again = ConfigLayout.replace(t, k, ConfigLayout.dump(s), null);
                YamlConfiguration re = new YamlConfiguration();
                re.loadFromString(again);
                assertEquals(full.getKeys(true), re.getKeys(true), lang + " " + k);
            }
        }
    }

    @Test
    void placeOfEachModule() {
        assertEquals("settings/web.yml", ConfigLayout.place("web").label());
        assertTrue(ConfigLayout.place("titles").whole());
        assertEquals("settings/titles.yml", ConfigLayout.place("titles").file());
        assertEquals("settings/discord.yml → discord-bot", ConfigLayout.place("discord-bot").label());
        assertEquals("settings/discord.yml → discord-webhook", ConfigLayout.place("discord-webhook").label());
        assertEquals("config.yml → stats", ConfigLayout.place("stats").label());
        assertEquals("settings/public-page.yml", ConfigLayout.place("public-page").label());
        assertTrue(ConfigLayout.isExtraFile("settings/afk.yml"));
        assertFalse(ConfigLayout.isExtraFile(ConfigLayout.WEB));
        assertFalse(ConfigLayout.isExtraFile(ConfigLayout.DATABASE));
        for (me.gordi.mineapi.core.module.ModuleCatalog.Info i : me.gordi.mineapi.core.module.ModuleCatalog.all()) {
            if (!i.hasSettings()) continue;
            String f = ConfigLayout.place(i.id()).file();
            for (String lang : java.util.List.of("es", "en")) {
                assertTrue(new java.io.File("src/main/resources/defaults/" + lang + "/" + f).isFile(), lang + "/" + f + ": plantilla de " + i.id());
            }
        }
        for (String id : ConfigLayout.CONTENT.keySet()) assertTrue(ConfigLayout.place(id).whole(), id + ": la lista va con sus ajustes");
    }
}
