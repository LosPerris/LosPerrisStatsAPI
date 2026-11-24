package me.gordi.mineapi.core.time;

import java.util.Locale;

/**
 * Duraciones legibles: {@code 30s}, {@code 10m}, {@code 1h30m}, {@code 2d}, {@code 1w}.
 * {@code perm}, {@code permanent}, {@code 0} o vacío = permanente (0 ms). Código puro (JUnit).
 */
public final class Durations {

    public static final long PERMANENT = 0L;

    private Durations() {
    }

    /**
     * @return milisegundos; {@link #PERMANENT} para permanente
     * @throws IllegalArgumentException si el texto no es válido
     */
    public static long parse(String raw) {
        if (raw == null) return PERMANENT;
        String s = raw.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty() || s.equals("0") || s.equals("perm") || s.equals("permanent") || s.equals("permanente")) {
            return PERMANENT;
        }
        long total = 0;
        long number = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '0' && c <= '9') {
                number = (number < 0 ? 0 : number) * 10 + (c - '0');
                if (number > 1_000_000) throw new IllegalArgumentException("duration too long: " + raw);
                continue;
            }
            if (c == ' ') continue;
            if (number < 0) throw new IllegalArgumentException("invalid duration: " + raw);
            long unit = switch (c) {
                case 's' -> 1000L;
                case 'm' -> 60_000L;
                case 'h' -> 3_600_000L;
                case 'd' -> 86_400_000L;
                case 'w' -> 604_800_000L;
                default -> throw new IllegalArgumentException("invalid duration unit '" + c + "': " + raw);
            };
            total += number * unit;
            number = -1;
        }
        if (number >= 0) total += number * 60_000L; // número suelto = minutos
        if (total <= 0) throw new IllegalArgumentException("invalid duration: " + raw);
        return total;
    }

    /**
     * Segundos de un ajuste de duración del YAML. Acepta texto con unidad ({@code "30s"}, {@code "5m"},
     * {@code "1h"}, {@code "1h30m"}, {@code "2d"}) o un número suelto en la unidad antigua de la clave
     * ({@code unitSeconds}: 1 = segundos, 60 = minutos, 3600 = horas), así las configuraciones de antes siguen
     * valiendo. Se prueba con cada clave en orden (la primera es la nueva; las demás, sus nombres antiguos).
     * Valor ausente o inválido: {@code def}. Config duration in seconds; plain numbers keep the old unit.
     */
    public static long seconds(org.bukkit.configuration.ConfigurationSection c, long def, long unitSeconds, String... keys) {
        if (c == null) return def;
        for (String key : keys) {
            if (!c.contains(key)) continue;
            Object raw = c.get(key);
            if (raw instanceof Number n) return Math.round(n.doubleValue() * unitSeconds);
            String s = String.valueOf(raw).trim();
            if (s.isEmpty()) return def;
            try {
                if (s.matches("\\d+(\\.\\d+)?")) return Math.round(Double.parseDouble(s) * unitSeconds);
                long ms = parse(s);
                return ms <= 0 ? def : ms / 1000L;
            } catch (IllegalArgumentException e) {
                return def;
            }
        }
        return def;
    }

    /** {@code 1d 2h 5m} (sin idioma: las unidades son universales). 0 = "∞". */
    public static String format(long millis) {
        if (millis <= 0) return "∞";
        long seconds = millis / 1000L;
        long d = seconds / 86_400, h = seconds % 86_400 / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        StringBuilder sb = new StringBuilder();
        if (d > 0) sb.append(d).append("d ");
        if (h > 0) sb.append(h).append("h ");
        if (m > 0) sb.append(m).append("m ");
        if (s > 0 && d == 0 && h == 0) sb.append(s).append("s ");
        return sb.length() == 0 ? "0s" : sb.toString().trim();
    }

    /** Texto corto para config.yml: 300 -> "5m", 7200 -> "2h", 90 -> "90s", 86400 -> "1d". Seconds to "5m"/"1h". */
    public static String compact(long seconds) {
        if (seconds <= 0) return "0s";
        if (seconds % 86_400 == 0) return seconds / 86_400 + "d";
        if (seconds % 3600 == 0) return seconds / 3600 + "h";
        if (seconds % 60 == 0) return seconds / 60 + "m";
        return seconds + "s";
    }
}
