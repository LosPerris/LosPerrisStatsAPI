package me.gordi.mineapi.core.text;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LangTextTest {

    @Test
    void picksLanguageThenFallbackThenAny() {
        Map<String, String> m = Map.of("es", "Hola", "en", "Hello");
        assertEquals("Hello", LangText.pick(m, "en", "es"));
        assertEquals("Hola", LangText.pick(m, "fr", "es"));
    }

    @Test
    void nullLanguageDoesNotThrow() {
        assertEquals("Hola", LangText.pick(Map.of("es", "Hola"), null, null));
        assertNull(LangText.pick(Map.of(), null, "es"));
    }
}
