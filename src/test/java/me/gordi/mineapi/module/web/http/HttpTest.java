package me.gordi.mineapi.module.web.http;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HttpTest {

    @Test
    void parsesQuery() {
        Map<String, String> q = Http.parseQuery("name=St%C3%A9ve&flag&x=1&x=2&bad=%E0%A4%A&&");
        assertEquals("Stéve", q.get("name"));
        assertEquals("", q.get("flag"));
        assertEquals("1", q.get("x"), "se queda el primero");
        assertEquals("%E0%A4%A", q.get("bad"));
        assertTrue(Http.parseQuery(null).isEmpty());
    }

    @Test
    void numericParamsAreClamped() {
        Map<String, String> q = Http.parseQuery("a=500&b=zz&c=-3");
        assertEquals(100, Http.intParam(q, "a", 1, 1, 100));
        assertEquals(7, Http.intParam(q, "b", 7, 1, 100));
        assertEquals(1, Http.intParam(q, "c", 7, 1, 100));
        assertEquals(9L, Http.longParam(q, "missing", 9L));
    }

    @Test
    void constantTimeEquals() {
        assertTrue(Http.constantTimeEquals("abc", "abc"));
        assertFalse(Http.constantTimeEquals("abc", "abd"));
        assertFalse(Http.constantTimeEquals(null, "abc"));
    }

    @Test
    void staticFilesRejectTraversal() {
        assertEquals("script.js", StaticFiles.safeName("/web/script.js"));
        assertNull(StaticFiles.safeName("/web/../plugin.yml"));
        assertNull(StaticFiles.safeName("/web//etc/passwd"));
        assertNull(StaticFiles.safeName("/web/a\\b"));
        assertNull(StaticFiles.safeName("/other"));
        assertTrue(StaticFiles.contentType("i18n.js").startsWith("application/javascript")
                || StaticFiles.contentType("i18n.js").startsWith("text/javascript"));
    }
}
