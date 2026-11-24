package me.gordi.mineapi.core.config;

import me.gordi.mineapi.core.module.ModuleCatalog;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExtrasSplitTest {

    private static String tpl(String lang, String f) throws Exception {
        return Files.readString(new File("src/main/resources/defaults/" + lang + "/" + f).toPath());
    }

    private static YamlConfiguration yaml(String s) throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(s);
        return y;
    }

    private static final String V9 = """
            # banner del usuario
            config-version: 9
            general:
              language: "en"
              prefix: "&cMIO "
            # comentario viejo de módulos
            modules:
              stats: true
              web: false
              milestones: true
              achievements: true
              titles: true
              quests: false
              seasons: true
              holograms: true
              heatmap: false
            periods:
              update-interval: "7s"
            """;

    @Test
    void movesExtrasToAddonAndKeepsCoreValues() throws Exception {
        ExtrasSplit.Result r = ExtrasSplit.config(V9, tpl("en", "config.yml"));
        YamlConfiguration c = yaml(r.config());
        assertEquals(10, c.getInt("config-version"));
        assertEquals("&cMIO ", c.getString("general.prefix"));
        assertEquals("7s", c.getString("periods.update-interval"), "el resto del archivo no cambia");
        assertFalse(c.getBoolean("modules.web"));
        assertFalse(c.getBoolean("modules.heatmap"));
        assertTrue(c.getBoolean("modules.milestones"));
        assertTrue(c.getBoolean("modules.scoreboard"), "módulo que faltaba: valor de la plantilla");
        for (String id : ModuleCatalog.MOVED) assertFalse(c.contains("modules." + id), id);
        assertFalse(c.contains("modules.achievements"));
        assertEquals(Map.of("titles", true, "quests", false, "seasons", true, "holograms", true), r.addonModules());
        assertTrue(r.gallery());
        assertTrue(r.config().startsWith("# banner del usuario"));
        assertFalse(r.config().contains("comentario viejo de módulos"), "comentario de la plantilla (nombra el addon)");
        assertTrue(r.config().contains("LosPerrisExtras"));
        // repetir no cambia nada
        assertEquals(r.config(), ExtrasSplit.config(r.config(), tpl("en", "config.yml")).config());
    }

    @Test
    void achievementsWithoutMilestonesDoesNotTurnRewardsOn() throws Exception {
        String v9 = V9.replace("  milestones: true\n", "  milestones: false\n");
        ExtrasSplit.Result r = ExtrasSplit.config(v9, tpl("es", "config.yml"));
        assertFalse(yaml(r.config()).getBoolean("modules.milestones"));
        assertTrue(r.gallery());
        assertTrue(r.changes().stream().anyMatch(s -> s.contains("milestones")));
    }

    @Test
    void galleryTakesOldAchievementsSettings() throws Exception {
        String ms = "cooldown: 42\nstats:\n  player_kills:\n    5: \"x\"\n";
        YamlConfiguration ach = yaml("update-interval: \"10m\"\ntiers:\n  rare: 15\n");
        String out = ExtrasSplit.gallery(ms, tpl("es", ExtrasSplit.MILESTONES), ach, false);
        YamlConfiguration y = yaml(out);
        assertEquals(42, y.getInt("cooldown"), "los hitos del usuario no cambian");
        assertEquals(List.of("5"), List.copyOf(y.getConfigurationSection("stats.player_kills").getKeys(false)));
        assertFalse(y.getBoolean("gallery.enabled"));
        assertEquals("10m", y.getString("gallery.update-interval"));
        assertEquals(15, y.getInt("gallery.tiers.rare"));
        assertEquals(50, y.getInt("gallery.tiers.uncommon"), "lo que falta, de la plantilla");
        // sobre la plantilla: el bloque se sustituye en su sitio
        String fresh = ExtrasSplit.gallery(tpl("en", ExtrasSplit.MILESTONES), tpl("en", ExtrasSplit.MILESTONES), null, true);
        assertEquals(1, fresh.split("\ngallery:", -1).length - 1);
        assertTrue(yaml(fresh).getBoolean("gallery.enabled"));
    }

    @Test
    void importFileForTheAddon() throws Exception {
        YamlConfiguration y = yaml(ExtrasSplit.importYaml("en", Map.of("titles", true)));
        assertEquals("en", y.getString("language"));
        assertTrue(y.getBoolean("modules.titles"));
        assertEquals(9, ExtrasSplit.movedSettings().size());
        assertTrue(ExtrasSplit.movedSettings().contains("settings/seasons.yml"));
    }
}
