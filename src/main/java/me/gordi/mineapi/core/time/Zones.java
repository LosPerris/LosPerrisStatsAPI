package me.gordi.mineapi.core.time;

import java.time.ZoneId;

/** Zona horaria de config.yml ({@code general.timezone}); vacía o inválida = la del servidor. */
public final class Zones {

    private Zones() {
    }

    public static ZoneId of(String raw) {
        if (raw == null || raw.isBlank()) return ZoneId.systemDefault();
        try {
            return ZoneId.of(raw.trim());
        } catch (Exception e) {
            return ZoneId.systemDefault();
        }
    }
}
