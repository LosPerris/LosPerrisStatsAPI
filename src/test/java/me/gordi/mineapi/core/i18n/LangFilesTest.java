package me.gordi.mineapi.core.i18n;

import me.gordi.mineapi.core.text.VariableContext;
import me.gordi.mineapi.core.text.Variables;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

class LangFilesTest {

    private static YamlConfiguration load(String code) {
        File f = new File("src/main/resources/lang/" + code + ".yml");
        assertTrue(f.exists(), f.toString());
        return YamlConfiguration.loadConfiguration(f);
    }

    private static Set<String> leaves(YamlConfiguration y) {
        Set<String> out = new TreeSet<>();
        for (String k : y.getKeys(true)) if (!y.isConfigurationSection(k)) out.add(k);
        return out;
    }

    @Test
    void spanishAndEnglishHaveSameKeys() {
        assertEquals(leaves(load("es")), leaves(load("en")));
    }

    @Test
    void onlyKnownVariables() {
        for (String code : new String[]{"es", "en"}) {
            Lang lang = Lang.parse(code, load(code));
            assertTrue(lang.size() > 50, code);
            lang.forEach((key, t) -> {
                for (String v : t.variables()) {
                    assertTrue(Variables.isKnown(VariableContext.LANG, v), code + ":" + key + " usa {" + v + "}");
                }
            });
        }
    }

    @Test
    void listsAreParsedAsLists() {
        Lang es = Lang.parse("es", load("es"));
        assertNotNull(es.getList("gui.admin.lore"));
        assertNotNull(es.get("general.no-permission"));
    }
}
