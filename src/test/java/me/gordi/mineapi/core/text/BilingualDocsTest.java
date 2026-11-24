package me.gordi.mineapi.core.text;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cada documento existe en español e inglés y ambos tienen la misma estructura (mismos niveles
 * de títulos en el mismo orden), para que una sección nueva no se quede solo en un idioma.
 */
class BilingualDocsTest {

    private static final String[][] PAIRS = {
            {"docs/es/ARQUITECTURA.md", "docs/en/ARCHITECTURE.md"},
            {"REGISTRO_DE_CAMBIOS.md", "CHANGELOG.md"},
            {"docs/es/API_REFERENCE.md", "docs/en/API_REFERENCE.md"},
            {"docs/es/README.md", "docs/en/README.md"},
            {"docs/es/GUIA.md", "docs/en/GUIDE.md"},
            {"docs/es/DISCORD_SETUP.md", "docs/en/DISCORD_SETUP.md"},
    };

    @Test
    void everyDocumentHasBothLanguagesWithTheSameStructure() throws IOException {
        for (String[] pair : PAIRS) {
            Path es = Path.of(pair[0]), en = Path.of(pair[1]);
            assertTrue(Files.exists(es), es + " no existe");
            assertTrue(Files.exists(en), en + " no existe");
            assertEquals(outline(es), outline(en), "Estructura distinta entre " + es + " y " + en);
        }
    }

    @Test
    void docLanguagesAreSpanishAndEnglish() {
        assertArrayEquals(new String[]{"es", "en"}, Localized.DOC_LANGUAGES);
    }

    /** Niveles de los títulos Markdown (fuera de bloques de código), p. ej. [1, 2, 2, 3]. */
    static List<Integer> outline(Path file) throws IOException {
        boolean code = false;
        List<Integer> out = new java.util.ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.startsWith("```")) code = !code;
            if (code) continue;
            int n = 0;
            while (n < line.length() && line.charAt(n) == '#') n++;
            if (n > 0 && n < line.length() && line.charAt(n) == ' ') out.add(n);
        }
        return out;
    }
}
