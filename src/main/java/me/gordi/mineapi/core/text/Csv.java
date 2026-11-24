package me.gordi.mineapi.core.text;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * CSV (RFC 4180, separador coma, CRLF) para exportar a hojas de cálculo. Los textos que empiezan
 * por = + - @ o tabulador se prefijan con ' para evitar inyección de fórmulas (los números no).
 */
public final class Csv {

    private Csv() {}

    public static String cell(String v) {
        if (v == null) return "";
        String s = v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0 && !isNumber(s)) s = "'" + s;
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = '"' + s.replace("\"", "\"\"") + '"';
        }
        return s;
    }

    public static void row(StringBuilder out, Collection<String> cells) {
        boolean first = true;
        for (String c : cells) {
            if (!first) out.append(',');
            out.append(cell(c));
            first = false;
        }
        out.append("\r\n");
    }

    /**
     * Tabla con una fila por objeto: columnas = {@code leading} y luego el resto de claves
     * primitivas encontradas (orden alfabético). Objetos/arrays anidados se omiten; {@code exclude}
     * quita columnas (p. ej. ip_address).
     */
    public static String table(List<JsonObject> rows, List<String> leading, Set<String> exclude) {
        Set<String> cols = new LinkedHashSet<>(leading);
        TreeSet<String> rest = new TreeSet<>();
        for (JsonObject o : rows) {
            for (Map.Entry<String, JsonElement> e : o.entrySet()) {
                if (e.getValue().isJsonPrimitive() && !cols.contains(e.getKey())) rest.add(e.getKey());
            }
        }
        cols.addAll(rest);
        cols.removeAll(exclude);
        StringBuilder out = new StringBuilder(64 + rows.size() * cols.size() * 6);
        out.append('\uFEFF'); // BOM: Excel abre UTF-8 bien
        row(out, cols);
        List<String> cells = new ArrayList<>(cols.size());
        for (JsonObject o : rows) {
            cells.clear();
            for (String c : cols) cells.add(value(o.get(c)));
            row(out, cells);
        }
        return out.toString();
    }

    private static String value(JsonElement e) {
        if (e == null || !e.isJsonPrimitive()) return "";
        JsonPrimitive p = e.getAsJsonPrimitive();
        if (p.isNumber()) {
            double d = p.getAsDouble();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? Long.toString((long) d) : p.getAsString();
        }
        return p.getAsString();
    }

    private static boolean isNumber(String s) {
        try {
            Double.parseDouble(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
