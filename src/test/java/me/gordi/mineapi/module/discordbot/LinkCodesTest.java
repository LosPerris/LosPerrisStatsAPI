package me.gordi.mineapi.module.discordbot;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LinkCodesTest {

    private final AtomicLong now = new AtomicLong(1_000_000);
    private final LinkCodes codes = new LinkCodes(6, 60_000, now::get);
    private final UUID ana = UUID.randomUUID();

    @Test
    void codeIsSingleUseAndCaseInsensitive() {
        String c = codes.create(ana, "Ana");
        assertEquals(6, c.length());
        for (char ch : c.toCharArray()) assertTrue(LinkCodes.ALPHABET.indexOf(ch) >= 0);
        LinkCodes.Claim claim = codes.claim(" " + c.substring(0, 3).toLowerCase() + "-" + c.substring(3) + " ");
        assertNotNull(claim);
        assertEquals(ana, claim.uuid());
        assertEquals("Ana", claim.name());
        assertNull(codes.claim(c), "un solo uso");
        assertNull(codes.claim(null));
        assertNull(codes.claim("ZZZZZZ"));
    }

    @Test
    void codesExpire() {
        String c = codes.create(ana, "Ana");
        now.addAndGet(60_000);
        assertNull(codes.claim(c));
    }

    @Test
    void newCodeReplacesOldOne() {
        String first = codes.create(ana, "Ana");
        String second = codes.create(ana, "Ana");
        codes.create(UUID.randomUUID(), "Bob");
        assertEquals(2, codes.size());
        if (!first.equals(second)) assertNull(codes.claim(first));
        assertNotNull(codes.claim(second));
    }

    @Test
    void limitsAreClamped() {
        LinkCodes tiny = new LinkCodes(1, 1, now::get);
        assertEquals(4, tiny.create(ana, "Ana").length());
        assertEquals(10_000, tiny.ttlMs());
    }
}
