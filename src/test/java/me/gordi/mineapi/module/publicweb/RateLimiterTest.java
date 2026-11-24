package me.gordi.mineapi.module.publicweb;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    @Test
    void limitsPerIpPerMinute() {
        RateLimiter rl = new RateLimiter(3);
        long t = 1_000_000;
        for (int i = 0; i < 3; i++) assertTrue(rl.allow("1.1.1.1", t + i));
        assertFalse(rl.allow("1.1.1.1", t + 10));
        assertTrue(rl.allow("2.2.2.2", t + 10), "otra IP tiene su propio cupo");
        assertEquals(60, rl.retryAfter("1.1.1.1", t + 10));
        assertTrue(rl.allow("1.1.1.1", t + 60_000), "ventana nueva");
    }

    @Test
    void zeroMeansUnlimited() {
        RateLimiter rl = new RateLimiter(0);
        for (int i = 0; i < 1000; i++) assertTrue(rl.allow("x", i));
        assertEquals(0, rl.retryAfter("x", 0));
    }
}
