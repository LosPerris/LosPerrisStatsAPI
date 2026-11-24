package me.gordi.mineapi.api;

/**
 * Texto corto junto al nombre del jugador (p. ej. su título activo): sale en el perfil web ({@code title})
 * y en el embed de estadísticas del bot de Discord. Short tag shown next to a player's name.
 */
@FunctionalInterface
public interface PlayerTag {

    /** Etiqueta para el jugador en ese idioma (sin colores) o null. Puede llamarse desde cualquier hilo. */
    String tag(String playerName, String lang);
}
