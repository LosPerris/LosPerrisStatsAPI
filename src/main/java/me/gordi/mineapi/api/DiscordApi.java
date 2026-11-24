package me.gordi.mineapi.api;

/**
 * Avisos por el webhook de Discord del núcleo (módulo discord-webhook). Si está apagado, no hace nada.
 * Discord webhook notices; no-op when the module is off.
 */
public interface DiscordApi {

    /** true si el webhook está activo y configurado. */
    boolean available();

    /** Aviso con el estilo del servidor; {@code player} (puede ser null) pone su cabeza en el embed. */
    void notice(String title, String content, String player);

    /** Alerta con color ({@code 0xRRGGBB}). */
    void alert(String title, String content, int color);

    /** Mensaje con el estilo de los hitos. */
    void send(String title, String content, String player);
}
