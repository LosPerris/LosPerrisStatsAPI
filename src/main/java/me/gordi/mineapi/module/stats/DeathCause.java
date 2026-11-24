package me.gordi.mineapi.module.stats;

import java.util.Locale;

/**
 * Categoría de una muerte a partir de la causa del último daño (nombre de {@code DamageCause}).
 * Función pura: no depende de Bukkit para poder probarse con JUnit. Vanilla no guarda muertes
 * por causa, así que el contador empieza al instalar esta versión.
 */
public final class DeathCause {

    /** Clave JSON del desglose. */
    public static final String KEY = "death_causes";

    /** Categorías en orden de presentación. */
    public static final String[] CATEGORIES = {"player", "mob", "fall", "lava", "fire", "drowning", "explosion", "void",
            "suffocation", "starvation", "magic", "freeze", "contact", "lightning", "other"};

    private DeathCause() {
    }

    /**
     * @param cause        nombre de la DamageCause (puede ser null)
     * @param killedByPlayer el asesino es un jugador
     * @param byEntity     el último daño lo hizo una entidad (mob o jugador)
     */
    public static String categorize(String cause, boolean killedByPlayer, boolean byEntity) {
        if (killedByPlayer) return "player";
        if (cause == null) return byEntity ? "mob" : "other";
        switch (cause.toUpperCase(Locale.ROOT)) {
            case "ENTITY_ATTACK": case "ENTITY_SWEEP_ATTACK": case "PROJECTILE": case "THORNS":
                return byEntity ? "mob" : "other";
            case "FALL": case "FLY_INTO_WALL":
                return "fall";
            case "LAVA":
                return "lava";
            case "FIRE": case "FIRE_TICK": case "HOT_FLOOR": case "CAMPFIRE": case "MELTING":
                return "fire";
            case "DROWNING": case "DRYOUT":
                return "drowning";
            case "BLOCK_EXPLOSION": case "ENTITY_EXPLOSION":
                return "explosion";
            case "VOID": case "WORLD_BORDER":
                return "void";
            case "SUFFOCATION": case "CRAMMING": case "FALLING_BLOCK":
                return "suffocation";
            case "STARVATION":
                return "starvation";
            case "MAGIC": case "POISON": case "WITHER": case "DRAGON_BREATH": case "SONIC_BOOM":
                return "magic";
            case "FREEZE":
                return "freeze";
            case "CONTACT":
                return "contact";
            case "LIGHTNING":
                return "lightning";
            default:
                return "other";
        }
    }
}
