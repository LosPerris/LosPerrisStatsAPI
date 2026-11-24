package me.gordi.mineapi.module.console;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lista de permitidos/prohibidos de la consola web. Código puro (JUnit).
 * <p>Patrones: {@code list} (exacto), {@code say *} (raíz con argumentos o sin ellos),
 * {@code lp*} (comodín). El espacio de nombres se ignora ({@code minecraft:stop} = {@code stop}).
 * Prohibido gana siempre; si la lista de permitidos no está vacía, solo pasa lo que coincida.
 */
public final class CommandFilter {

    /** Resultado de {@link #check}. */
    public enum Verdict { ALLOWED, DENIED, NOT_ALLOWED, INVALID }

    public static final int MAX_LENGTH = 256;

    private final List<Pattern> allow;
    private final List<Pattern> deny;

    public CommandFilter(List<String> allow, List<String> deny) {
        this.allow = compile(allow);
        this.deny = compile(deny);
    }

    /** "/Minecraft:Say  hola" -> "say hola". null si está vacío o es inválido. */
    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.trim();
        while (s.startsWith("/")) s = s.substring(1).trim();
        if (s.isEmpty() || s.length() > MAX_LENGTH) return null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 || c == 0x7f) return null; // saltos de línea y control: nunca
        }
        s = s.replaceAll("\\s+", " ");
        int space = s.indexOf(' ');
        String root = (space < 0 ? s : s.substring(0, space)).toLowerCase(Locale.ROOT);
        int colon = root.indexOf(':');
        if (colon >= 0) root = root.substring(colon + 1);
        if (root.isEmpty()) return null;
        return space < 0 ? root : root + s.substring(space);
    }

    public Verdict check(String raw) {
        String cmd = normalize(raw);
        if (cmd == null) return Verdict.INVALID;
        String lower = cmd.toLowerCase(Locale.ROOT);
        for (Pattern p : deny) if (p.matcher(lower).matches()) return Verdict.DENIED;
        if (allow.isEmpty()) return Verdict.ALLOWED;
        for (Pattern p : allow) if (p.matcher(lower).matches()) return Verdict.ALLOWED;
        return Verdict.NOT_ALLOWED;
    }

    private static List<Pattern> compile(List<String> raw) {
        List<Pattern> out = new ArrayList<>();
        if (raw == null) return out;
        for (String r : raw) {
            if (r == null || r.isBlank()) continue;
            out.add(toPattern(r));
        }
        return out;
    }

    static Pattern toPattern(String glob) {
        // ".*" (estilo regex de configs antiguas) equivale a "*": "op .*" debe bloquear "op Steve"
        String g = glob.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").replace(".*", "*");
        while (g.startsWith("/")) g = g.substring(1);
        int colon = g.indexOf(':');
        int space = g.indexOf(' ');
        if (colon >= 0 && (space < 0 || colon < space)) g = g.substring(colon + 1);
        StringBuilder re = new StringBuilder();
        if (g.endsWith(" *")) {
            // "say *" = "say" o "say <lo que sea>"
            re.append(quoteGlob(g.substring(0, g.length() - 2))).append("(?: .*)?");
        } else {
            re.append(quoteGlob(g));
        }
        return Pattern.compile(re.toString());
    }

    private static String quoteGlob(String s) {
        StringBuilder re = new StringBuilder();
        StringBuilder literal = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '*') {
                literal.append(c);
                continue;
            }
            if (literal.length() > 0) re.append(Pattern.quote(literal.toString()));
            literal.setLength(0);
            re.append(".*");
        }
        if (literal.length() > 0) re.append(Pattern.quote(literal.toString()));
        return re.toString();
    }
}
