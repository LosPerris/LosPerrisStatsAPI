package me.gordi.mineapi.core.text;

import org.bukkit.ChatColor;

/** Utilidades de texto: colores con '&' y limpieza. Sin estado. */
public final class Text {

    private Text() {
    }

    /** Traduce los códigos '&' a '§'. Nunca devuelve null. */
    public static String color(String text) {
        return text == null ? "" : ChatColor.translateAlternateColorCodes('&', text);
    }

    /** Quita todos los colores (tanto '&' como '§'). */
    public static String strip(String text) {
        String stripped = ChatColor.stripColor(color(text));
        return stripped == null ? "" : stripped;
    }
}
