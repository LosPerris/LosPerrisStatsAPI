package me.gordi.mineapi.core.text;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Los enlaces relativos de la documentación (README, changelogs y docs/) apuntan a archivos que existen. */
class DocLinksTest {

    private static final Pattern LINK = Pattern.compile("\\]\\(([^)#\\s]+)(#[^)]*)?\\)");

    @Test
    void relativeLinksPointToExistingFiles() throws IOException {
        List<Path> files = new ArrayList<>(List.of(Path.of("README.md"), Path.of("CHANGELOG.md"), Path.of("REGISTRO_DE_CAMBIOS.md")));
        try (Stream<Path> s = Files.walk(Path.of("docs"))) {
            s.filter(p -> p.toString().endsWith(".md")).forEach(files::add);
        }
        List<String> broken = new ArrayList<>();
        for (Path f : files) {
            Matcher m = LINK.matcher(Files.readString(f, StandardCharsets.UTF_8));
            while (m.find()) {
                String target = m.group(1);
                if (target.contains("://") || target.startsWith("mailto:")) continue;
                Path p = (f.getParent() == null ? Path.of(".") : f.getParent()).resolve(target).normalize();
                if (!Files.exists(p)) broken.add(f + " -> " + target);
            }
        }
        assertTrue(broken.isEmpty(), "Enlaces rotos: " + broken);
    }
}
