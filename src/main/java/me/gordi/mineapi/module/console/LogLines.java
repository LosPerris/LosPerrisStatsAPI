package me.gordi.mineapi.module.console;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Limpieza de líneas de consola antes de enviarlas a la web: quita colores (ANSI y §),
 * oculta la API key, las IP (opcional) y lo que coincida con patrones propios, y recorta.
 * Código puro (JUnit).
 */
public final class LogLines {

    public static final int MAX_LINE = 2000;
    private static final Pattern ANSI = Pattern.compile("\u001B\\[[0-9;?]*[ -/]*[@-~]");
    private static final Pattern SECTION = Pattern.compile("§[0-9a-fk-orx]", Pattern.CASE_INSENSITIVE);
    private static final Pattern IPV4 = Pattern.compile("\\b(\\d{1,3})\\.(\\d{1,3})\\.\\d{1,3}\\.\\d{1,3}\\b");
    static final String MASK = "****";

    private final String secret;
    private final boolean hideIps;
    private final List<Pattern> redact;

    public LogLines(String secret, boolean hideIps, List<String> redactPatterns) {
        this.secret = secret == null || secret.length() < 6 ? null : secret;
        this.hideIps = hideIps;
        this.redact = new ArrayList<>();
        if (redactPatterns != null) {
            for (String p : redactPatterns) {
                try {
                    if (p != null && !p.isBlank()) redact.add(Pattern.compile(p));
                } catch (Exception ignored) {
                    // patrón inválido: se ignora (se avisa al cargar la config)
                }
            }
        }
    }

    public String clean(String raw) {
        if (raw == null) return "";
        String s = raw;
        if (s.indexOf('\u001B') >= 0) s = ANSI.matcher(s).replaceAll("");
        if (s.indexOf('§') >= 0) s = SECTION.matcher(s).replaceAll("");
        if (secret != null && s.contains(secret)) s = s.replace(secret, MASK);
        if (hideIps) s = IPV4.matcher(s).replaceAll("$1.$2.x.x");
        for (Pattern p : redact) s = p.matcher(s).replaceAll(MASK);
        if (s.length() > MAX_LINE) s = s.substring(0, MAX_LINE) + "…";
        return s;
    }
}
