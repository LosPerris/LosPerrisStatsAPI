package me.gordi.mineapi.module.discordbot;

import java.security.SecureRandom;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Códigos de vinculación de un solo uso: el jugador pide uno en el juego ({@code /lpa discord link})
 * y lo escribe en Discord ({@code /link <código>}). Caducan a los {@code ttlMs} y un jugador solo
 * tiene uno a la vez. Sin caracteres ambiguos (0/O, 1/I). Seguro entre hilos.
 */
public final class LinkCodes {

    static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(UUID uuid, String name, long expires) {
    }

    /** Resultado de canjear un código. */
    public record Claim(UUID uuid, String name) {
    }

    private final Map<String, Pending> codes = new ConcurrentHashMap<>();
    private final int length;
    private final long ttlMs;
    private final LongSupplier clock;

    public LinkCodes(int length, long ttlMs, LongSupplier clock) {
        this.length = Math.max(4, Math.min(12, length));
        this.ttlMs = Math.max(10_000L, ttlMs);
        this.clock = clock;
    }

    /** Nuevo código para el jugador (anula el anterior). */
    public String create(UUID uuid, String name) {
        codes.values().removeIf(p -> p.uuid().equals(uuid) || p.expires() <= clock.getAsLong());
        String code;
        do {
            StringBuilder sb = new StringBuilder(length);
            for (int i = 0; i < length; i++) sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
            code = sb.toString();
        } while (codes.containsKey(code));
        codes.put(code, new Pending(uuid, name, clock.getAsLong() + ttlMs));
        return code;
    }

    /** Canjea un código (sin distinguir mayúsculas ni espacios); null si no existe o caducó. */
    public Claim claim(String raw) {
        if (raw == null) return null;
        String code = raw.trim().toUpperCase(java.util.Locale.ROOT).replace(" ", "").replace("-", "");
        Pending p = codes.remove(code);
        if (p == null || p.expires() <= clock.getAsLong()) return null;
        return new Claim(p.uuid(), p.name());
    }

    public long ttlMs() {
        return ttlMs;
    }

    int size() {
        return codes.size();
    }
}
