package me.gordi.mineapi.module.updates;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compara versiones tipo "1.3-V2", "v1.4.0", "2.0-beta1" (JUnit). Separa en tramos de números y
 * letras; los números se comparan como números; alpha/beta/rc/pre/snapshot cuentan como anteriores
 * a la versión sin sufijo.
 */
public final class Versions {

    private static final Set<String> PRE = Set.of("alpha", "a", "beta", "b", "rc", "pre", "snapshot", "dev");

    private Versions() {}

    public static int compare(String a, String b) {
        List<String> x = tokens(a);
        List<String> y = tokens(b);
        int n = Math.max(x.size(), y.size());
        for (int i = 0; i < n; i++) {
            // un lado se acabó: cuenta como 0 (2.0 == 2.0.0; 2.0 > 2.0-beta; 2.0 < 2.0-V2)
            String p = i < x.size() ? x.get(i) : missing(y.get(i));
            String q = i < y.size() ? y.get(i) : missing(x.get(i));
            boolean pn = isNum(p), qn = isNum(q);
            int c;
            if (pn && qn) {
                c = compareNum(p, q);
            } else if (pn != qn) {
                // número frente a etiqueta (1.3.1 vs 1.3-beta): siempre gana el número
                c = pn ? 1 : -1;
            } else {
                boolean pp = PRE.contains(p), qp = PRE.contains(q);
                if (pp != qp) c = pp ? -1 : 1;
                else c = p.compareTo(q);
            }
            if (c != 0) return c < 0 ? -1 : 1;
        }
        return 0;
    }

    public static boolean isNewer(String candidate, String current) {
        return candidate != null && !candidate.isBlank() && compare(candidate, current) > 0;
    }

    static List<String> tokens(String v) {
        List<String> out = new ArrayList<>();
        if (v == null) return out;
        String s = v.trim().toLowerCase(Locale.ROOT);
        if (s.startsWith("v") && s.length() > 1 && Character.isDigit(s.charAt(1))) s = s.substring(1);
        StringBuilder cur = new StringBuilder();
        int kind = 0; // 1 = dígito, 2 = letra
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            int k = Character.isDigit(ch) ? 1 : Character.isLetter(ch) ? 2 : 0;
            if (k != kind && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            kind = k;
            if (k != 0) cur.append(ch);
        }
        if (cur.length() > 0) out.add(cur.toString());
        // ceros finales sin significado: 1.3.0 == 1.3
        while (out.size() > 1 && out.get(out.size() - 1).matches("0+")) out.remove(out.size() - 1);
        return out;
    }

    /** Relleno para el lado más corto: "0" frente a números, "~" (gana a pre-release, pierde con otras etiquetas). */
    private static String missing(String other) {
        return isNum(other) ? "0" : PRE.contains(other) ? "~" : "";
    }

    private static boolean isNum(String s) {
        return !s.isEmpty() && Character.isDigit(s.charAt(0));
    }

    private static int compareNum(String p, String q) {
        String a = p.replaceFirst("^0+(?=.)", ""), b = q.replaceFirst("^0+(?=.)", "");
        if (a.length() != b.length()) return Integer.compare(a.length(), b.length());
        return a.compareTo(b);
    }
}
