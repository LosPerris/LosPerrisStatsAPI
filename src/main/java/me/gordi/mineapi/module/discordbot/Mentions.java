package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Limpieza de texto entre Minecraft y Discord (puro, probado con JUnit).
 * <ul>
 *   <li>Hacia Discord: nadie puede mencionar a @everyone, @here, usuarios ni roles desde el
 *   juego (además se envía {@code allowed_mentions: {parse: []}}).</li>
 *   <li>Hacia Minecraft: menciones a texto legible, sin códigos de color ni saltos de línea.</li>
 * </ul>
 */
public final class Mentions {

    private static final char ZWSP = '\u200B';
    private static final Pattern USER = Pattern.compile("<@!?(\\d{5,25})>");
    private static final Pattern ROLE = Pattern.compile("<@&(\\d{5,25})>");
    private static final Pattern CHANNEL = Pattern.compile("<#(\\d{5,25})>");
    private static final Pattern EMOJI = Pattern.compile("<a?:(\\w{1,32}):\\d{5,25}>");
    private static final Pattern COLOR = Pattern.compile("(?i)[&§]([0-9a-fk-orx#])");

    private Mentions() {
    }

    /** Texto del juego seguro para Discord: menciones rotas con un espacio de ancho cero. */
    public static String toDiscord(String text, int max) {
        if (text == null) return "";
        String s = text.replace("@", "@" + ZWSP)        // @everyone, @here, @usuario
                .replace("<#", "<" + ZWSP + "#")         // <#canal>
                .replace("<@", "<" + ZWSP + "@");        // por si alguien ya escribió <@id>
        return cut(s, max);
    }

    /**
     * Texto de Discord seguro para el chat del juego.
     * @param mentions array {@code mentions} del mensaje (usuarios mencionados) o null
     */
    public static String toMinecraft(String content, JsonArray mentions, int max) {
        if (content == null) return "";
        Map<String, String> names = new HashMap<>();
        if (mentions != null) {
            for (JsonElement e : mentions) {
                if (!e.isJsonObject()) continue;
                JsonObject u = e.getAsJsonObject();
                String id = u.has("id") ? u.get("id").getAsString() : null;
                if (id != null) names.put(id, displayName(u, null));
            }
        }
        String s = replace(USER, content, m -> "@" + names.getOrDefault(m.group(1), "user"));
        s = replace(ROLE, s, m -> "@role");
        s = replace(CHANNEL, s, m -> "#channel");
        s = replace(EMOJI, s, m -> ":" + m.group(1) + ":");
        s = s.replace('\n', ' ').replace('\r', ' ');
        StringBuilder clean = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c != 0x7f) clean.append(c);
        }
        s = COLOR.matcher(clean.toString()).replaceAll("");
        s = s.replace("§", "");
        return cut(s.trim(), max);
    }

    /** Nombre visible de un usuario o miembro: apodo, nombre global o nombre de usuario. */
    public static String displayName(JsonObject user, JsonObject member) {
        if (member != null && member.has("nick") && !member.get("nick").isJsonNull()) return member.get("nick").getAsString();
        if (user == null) return "?";
        if (user.has("global_name") && !user.get("global_name").isJsonNull()) return user.get("global_name").getAsString();
        return user.has("username") ? user.get("username").getAsString() : "?";
    }

    private static String cut(String s, int max) {
        return max > 0 && s.length() > max ? s.substring(0, Math.max(0, max - 1)) + "…" : s;
    }

    private static String replace(Pattern p, String s, java.util.function.Function<Matcher, String> f) {
        Matcher m = p.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement(f.apply(m)));
        m.appendTail(sb);
        return sb.toString();
    }
}
