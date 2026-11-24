package me.gordi.mineapi.core.text;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Mensaje precompilado con variables {@code {nombre}}. Se analiza y colorea UNA vez al cargar;
 * renderizar solo concatena trozos (sin regex ni replace por llamada).
 */
public final class Template {

    private static final Template EMPTY = new Template(new String[]{""}, false);

    /** Trozos alternos: índice par = literal ya coloreado, índice impar = nombre de variable. */
    private final String[] parts;
    private final boolean hasVariables;

    private Template(String[] parts, boolean hasVariables) {
        this.parts = parts;
        this.hasVariables = hasVariables;
    }

    /** Compila el texto (aplica colores '&'). */
    public static Template compile(String raw) {
        return compile(raw, true);
    }

    public static Template compile(String raw, boolean colorize) {
        if (raw == null || raw.isEmpty()) return EMPTY;
        List<String> out = new ArrayList<>();
        StringBuilder literal = new StringBuilder();
        int i = 0;
        boolean vars = false;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '{') {
                int end = raw.indexOf('}', i + 1);
                if (end > i + 1 && isVariableName(raw, i + 1, end)) {
                    out.add(colorize ? Text.color(literal.toString()) : literal.toString());
                    literal.setLength(0);
                    out.add(raw.substring(i + 1, end));
                    vars = true;
                    i = end + 1;
                    continue;
                }
            }
            literal.append(c);
            i++;
        }
        out.add(colorize ? Text.color(literal.toString()) : literal.toString());
        return new Template(out.toArray(new String[0]), vars);
    }

    private static boolean isVariableName(String s, int from, int to) {
        for (int i = from; i < to; i++) {
            char c = s.charAt(i);
            if (!(c >= 'a' && c <= 'z') && !(c >= '0' && c <= '9') && c != '_' && c != '-') return false;
        }
        return true;
    }

    /**
     * Renderiza con pares clave/valor: {@code render("player", "Steve", "value", 10)}.
     * Las variables sin valor se dejan tal cual ({@code {x}}) para que se note el error.
     */
    public String render(Object... keyValues) {
        if (!hasVariables) return parts[0];
        StringBuilder sb = new StringBuilder(64);
        for (int i = 0; i < parts.length; i++) {
            if ((i & 1) == 0) {
                sb.append(parts[i]);
                continue;
            }
            String name = parts[i];
            Object value = null;
            boolean found = false;
            for (int k = 0; k + 1 < keyValues.length; k += 2) {
                if (name.equals(keyValues[k])) {
                    value = keyValues[k + 1];
                    found = true;
                    break;
                }
            }
            if (found) sb.append(value);
            else sb.append('{').append(name).append('}');
        }
        return sb.toString();
    }

    /** Variables que usa el mensaje (para validación). */
    public Set<String> variables() {
        if (!hasVariables) return Collections.emptySet();
        Set<String> set = new LinkedHashSet<>();
        for (int i = 1; i < parts.length; i += 2) set.add(parts[i]);
        return set;
    }
}
