package me.gordi.mineapi.module.stats;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Estadísticas del plugin. Única fuente de verdad para claves JSON, alias, categorías de top,
 * conversión con las estadísticas de Bukkit y formato.
 */
public enum StatType {
    //            clave JSON        top              token          tracked-stats     Bukkit                     decimales  alias
    PLAYER_KILLS("player_kills", "player_kills", "kills", "player_kills", Statistic.PLAYER_KILLS, false, "kills"),
    DEATHS("deaths", "deaths", "deaths", "deaths", Statistic.DEATHS, false),
    MOB_KILLS("mob_kills", "mob_kills", "mob_kills", "mob_kills", Statistic.MOB_KILLS, false, "mobs"),
    HOURS_PLAYED("hours_played", "playtime", "time", "play_time", Statistic.PLAY_ONE_MINUTE, true, "playtime", "play_time", "time", "hours"),
    BLOCKS_BROKEN("blocks_broken", "blocks_broken", "blocks_broken", "blocks_broken", null, false, "blocks"),
    /** Calculada: suma de USE_ITEM de los materiales que son bloques (ver {@link VanillaSums}). */
    BLOCKS_PLACED("blocks_placed", "blocks_placed", "blocks_placed", "blocks_placed", null, false, "placed"),
    /** Calculada: suma de MINE_BLOCK de las menas; el desglose va en el objeto JSON "ores". */
    ORES_MINED("ores_mined", "ores_mined", "ores_mined", "ores_mined", null, false, "ores"),
    JUMPS("jumps", "jumps", "jumps", "jumps", Statistic.JUMP, false),
    DISTANCE_WALKED("distance_walked", "distance_walked", "distance_walked", "distance_walked", Statistic.WALK_ONE_CM, true, "distance"),
    FISH_CAUGHT("fish_caught", "fish_caught", "fish_caught", "fish_caught", Statistic.FISH_CAUGHT, false, "fish"),
    VILLAGER_TRADES("villager_trades", "villager_trades", "trades", "villager_trades", Statistic.TRADED_WITH_VILLAGER, false, "trades"),
    /** Vanilla lo guarda en décimas de punto de vida: se muestra en corazones (raw / 20). */
    DAMAGE_DEALT("damage_dealt", "damage_dealt", "damage_dealt", "damage_dealt", Statistic.DAMAGE_DEALT, true, "damage"),
    DAMAGE_TAKEN("damage_taken", "damage_taken", "damage_taken", "damage_taken", Statistic.DAMAGE_TAKEN, true);

    /** Cache de values() (evita copiar el array en cada llamada). */
    public static final StatType[] ALL = values();
    private static final Map<String, StatType> LOOKUP = new HashMap<>();

    static {
        for (StatType t : ALL) {
            LOOKUP.put(t.key, t);
            LOOKUP.put(t.board, t);
            LOOKUP.put(t.token, t);
            LOOKUP.put(t.trackKey, t);
            for (String a : t.aliases) LOOKUP.put(a, t);
        }
    }

    /** Clave en el JSON del jugador y en la API. */
    public final String key;
    /** Nombre de la categoría de top (/lpa top, API, placeholders top_*). */
    public final String board;
    /** Placeholder corto (%kills%, %time%...). */
    public final String token;
    /** Clave en tracked de config.yml → stats. */
    public final String trackKey;
    /** Estadística vanilla o null si solo existe en el plugin. */
    public final Statistic statistic;
    public final boolean decimal;
    private final String[] aliases;

    StatType(String key, String board, String token, String trackKey, Statistic statistic, boolean decimal, String... aliases) {
        this.key = key;
        this.board = board;
        this.token = token;
        this.trackKey = trackKey;
        this.statistic = statistic;
        this.decimal = decimal;
        this.aliases = aliases;
    }

    /** Acepta clave, categoría, token o alias (sin distinguir mayúsculas); null si no existe. */
    public static StatType parse(String raw) {
        return raw == null ? null : LOOKUP.get(raw.trim().toLowerCase(Locale.ROOT));
    }

    /** true si se calcula sumando estadísticas vanilla (no editable a mano). */
    public boolean computed() {
        return this == BLOCKS_PLACED || this == ORES_MINED;
    }

    /** true si se puede cambiar desde /lpa admin o la API de administración. */
    public boolean editable() {
        return !computed();
    }

    /** Valor del plugin a partir del valor vanilla (ticks → horas, cm → km, décimas de vida → corazones). */
    public double fromRaw(long raw) {
        return switch (this) {
            case HOURS_PLAYED -> raw / 72_000.0;
            case DISTANCE_WALKED -> raw / 100_000.0;
            case DAMAGE_DEALT, DAMAGE_TAKEN -> raw / 20.0;
            default -> raw;
        };
    }

    /** Valor vanilla a partir del valor del plugin. */
    public int toRaw(double value) {
        return switch (this) {
            case HOURS_PLAYED -> (int) Math.round(value * 72_000.0);
            case DISTANCE_WALKED -> (int) Math.round(value * 100_000.0);
            case DAMAGE_DEALT, DAMAGE_TAKEN -> (int) Math.round(value * 20.0);
            default -> (int) Math.round(value);
        };
    }

    /** Máximo editable sin desbordar el int de Bukkit. */
    public double maxValue() {
        return switch (this) {
            case HOURS_PLAYED -> 29_000;
            case DISTANCE_WALKED -> 21_000;
            case DAMAGE_DEALT, DAMAGE_TAKEN -> 100_000_000;
            default -> Integer.MAX_VALUE;
        };
    }

    /** Valor tal como se guarda (2 decimales; el tiempo jugado nunca baja de 0.01 si hay algo). */
    public double stored(long raw) {
        double v = fromRaw(raw);
        if (this == HOURS_PLAYED && raw > 0 && v < 0.01) v = 0.01;
        return decimal ? Math.round(v * 100.0) / 100.0 : v;
    }

    /** Texto para jugadores: 12, 3.50h, 1.20km, 12.5❤. */
    public String format(double value) {
        return switch (this) {
            case HOURS_PLAYED -> String.format(Locale.US, "%.2fh", value);
            case DISTANCE_WALKED -> String.format(Locale.US, "%.2fkm", value);
            case DAMAGE_DEALT, DAMAGE_TAKEN -> String.format(Locale.US, "%.1f❤", value);
            default -> String.valueOf((long) value);
        };
    }

    /** Lectura en vivo desde Bukkit (hilo principal). NaN si es propia del plugin o calculada. */
    public double live(Player p) {
        return statistic == null ? Double.NaN : fromRaw(p.getStatistic(statistic));
    }
}
