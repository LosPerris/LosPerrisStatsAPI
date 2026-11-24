package me.gordi.mineapi.core.text;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mantiene docs/es/variables y docs/en/variables del repositorio sincronizados con el registro
 * central. Si falla: ejecute {@code mvn test -Ddocs.update=true} para regenerar los archivos.
 */
class VariableDocsTest {

    private static Path dir(String lang) {
        return Path.of("docs", lang, "variables");
    }

    @Test
    void everyVariableIsDocumentedInEveryLanguage() {
        for (String lang : Localized.DOC_LANGUAGES) {
            for (VariableContext ctx : VariableContext.values()) {
                String md = VariableDocs.render(ctx, lang);
                for (Variable v : Variables.of(ctx)) assertTrue(md.contains(ctx.wrap(v.name())), lang + "/" + ctx.id() + ": " + v.name());
                assertTrue(VariableDocs.index(lang).contains(VariableDocs.fileName(ctx)), lang + " index: " + ctx.id());
            }
        }
    }

    @Test
    void languagesProduceDifferentText() {
        for (VariableContext ctx : VariableContext.values()) {
            assertNotEquals(VariableDocs.render(ctx, "es"), VariableDocs.render(ctx, "en"), ctx.id());
        }
    }

    @Test
    void repositoryDocsAreUpToDate() throws IOException {
        boolean update = Boolean.getBoolean("docs.update");
        for (String lang : Localized.DOC_LANGUAGES) {
            Path dir = dir(lang);
            if (update) Files.createDirectories(dir);
            check(dir.resolve("README.md"), VariableDocs.index(lang), update);
            for (VariableContext ctx : VariableContext.values()) {
                check(dir.resolve(VariableDocs.fileName(ctx)), VariableDocs.render(ctx, lang), update);
            }
        }
    }

    private static void check(Path file, String expected, boolean update) throws IOException {
        if (update) {
            Files.writeString(file, expected, StandardCharsets.UTF_8);
            return;
        }
        assertTrue(Files.exists(file), file + " no existe (mvn test -Ddocs.update=true)");
        assertEquals(expected, Files.readString(file, StandardCharsets.UTF_8).replace("\r\n", "\n"),
                file + " desactualizado (mvn test -Ddocs.update=true)");
    }
}
