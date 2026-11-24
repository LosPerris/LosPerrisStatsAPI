package me.gordi.mineapi.module.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiKeysTest {

    @Test
    void replacesOnlyTheValueKeepingComments() {
        String yaml = "# cabecera\nsecurity:\n  enabled: true\n  api-key: \"PON_TU_CLAVE_AQUI\"  # cámbiela\n  other: 1\n";
        String out = ApiKeys.replaceKey(yaml, "NEW");
        assertEquals("# cabecera\nsecurity:\n  enabled: true\n  api-key: \"NEW\"  # cámbiela\n  other: 1\n", out);
        assertEquals("a:\n  api-key: \"X\"\n", ApiKeys.replaceKey("a:\n  api-key: viejo\n", "X"));
        assertNull(ApiKeys.replaceKey("api-key: raiz\n", "X"), "solo dentro de una sección");
    }

    @Test
    void needsKeyAndGenerate() {
        assertTrue(ApiKeys.needsKey(null));
        assertTrue(ApiKeys.needsKey("  "));
        assertTrue(ApiKeys.needsKey("pon_tu_clave_aqui"));
        assertFalse(ApiKeys.needsKey("abc"));
        String k = ApiKeys.generate();
        assertEquals(32, k.length());
        assertNotEquals(k, ApiKeys.generate());
    }
}
