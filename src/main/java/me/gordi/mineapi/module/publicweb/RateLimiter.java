package me.gordi.mineapi.module.publicweb;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Límite de peticiones por IP en ventanas fijas de 1 minuto (puro, probado con JUnit). */
public final class RateLimiter {

    private static final long WINDOW_MS = 60_000L;
    private static final int MAX_TRACKED = 10_000;

    private final int perMinute;
    private final Map<String, long[]> windows = new ConcurrentHashMap<>();

    /** @param perMinute 0 o menos = sin límite */
    public RateLimiter(int perMinute) {
        this.perMinute = perMinute;
    }

    public boolean allow(String ip, long now) {
        if (perMinute <= 0) return true;
        if (windows.size() > MAX_TRACKED) windows.entrySet().removeIf(e -> now - e.getValue()[0] >= WINDOW_MS);
        long[] w = windows.compute(ip, (k, v) -> {
            if (v == null || now - v[0] >= WINDOW_MS) return new long[]{now, 1};
            v[1]++;
            return v;
        });
        return w[1] <= perMinute;
    }

    /** Segundos hasta que se libera la ventana de esa IP. */
    public long retryAfter(String ip, long now) {
        long[] w = windows.get(ip);
        return w == null ? 0 : Math.max(1, (WINDOW_MS - (now - w[0]) + 999) / 1000);
    }
}
