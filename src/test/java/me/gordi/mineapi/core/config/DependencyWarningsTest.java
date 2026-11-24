package me.gordi.mineapi.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class DependencyWarningsTest {

    private static YamlConfiguration yaml(String s) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(s);
        return y;
    }

    @Test
    void warnsOnceForExplicitSettingsOnly() throws Exception {
        // reglas propias (como las del addon LosPerrisExtras) con la misma lógica
        List<DependencyWarnings.Rule> rules = List.of(
                new DependencyWarnings.Rule("moderation", "discord", "discord-webhook", false),
                new DependencyWarnings.Rule("alerts", "notify.discord", "discord-webhook", true),
                new DependencyWarnings.Rule("playtime", "exclude-afk", "afk", false));
        Map<String, ConfigurationSection> s = new HashMap<>();
        s.put("moderation", yaml("discord: true\n"));
        s.put("alerts", yaml("notify:\n  discord: true\n"));
        s.put("playtime", yaml("exclude-afk: true\n"));
        Set<String> on = Set.of("moderation", "alerts", "playtime");
        List<String> w = DependencyWarnings.check(rules, on::contains, s::get);
        assertEquals(1, w.size(), "una sola línea");
        assertTrue(w.get(0).contains("moderation.discord → discord-webhook"));
        assertTrue(w.get(0).contains("playtime.exclude-afk → afk"), "afk apagado: se avisa");
        assertFalse(w.get(0).contains("alerts.notify.discord"), "valor de plantilla esperando a Discord: sin aviso");
    }

    @Test
    void silentWhenDependencyOnOrModuleOff() throws Exception {
        Map<String, ConfigurationSection> s = new HashMap<>();
        s.put("periods", yaml("announce:\n  discord: true\n"));
        assertTrue(DependencyWarnings.check(Set.of("periods", "discord-webhook")::contains, s::get).isEmpty());
        assertTrue(DependencyWarnings.check(Set.<String>of()::contains, id -> {
            throw new AssertionError("no se leen ajustes de módulos apagados: " + id);
        }).isEmpty());
    }

    @Test
    void ruleDefaultsMatchTemplates() {
        for (String lang : List.of("es", "en")) {
            for (DependencyWarnings.Rule r : DependencyWarnings.RULES) {
                ConfigLayout.Place p = ConfigLayout.place(r.module());
                YamlConfiguration f = YamlConfiguration.loadConfiguration(new File("src/main/resources/defaults/" + lang + "/" + p.file()));
                ConfigurationSection sec = p.whole() ? f : f.getConfigurationSection(p.section());
                assertNotNull(sec, r.module());
                assertTrue(sec.isBoolean(r.path()), lang + " " + r.module() + "." + r.path());
                assertEquals(r.templateValue(), sec.getBoolean(r.path()), lang + " " + r.module() + "." + r.path());
            }
        }
    }

    @Test
    void freshInstallHasNoWarnings() {
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(new File("src/main/resources/defaults/es/config.yml"));
        List<String> w = DependencyWarnings.check(id -> cfg.getBoolean("modules." + id), id -> {
            ConfigLayout.Place p = ConfigLayout.place(id);
            YamlConfiguration f = YamlConfiguration.loadConfiguration(new File("src/main/resources/defaults/es/" + p.file()));
            return p.whole() ? f : f.getConfigurationSection(p.section());
        });
        assertTrue(w.isEmpty(), w.toString());
    }
}
