package me.gordi.mineapi.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cambia {@code modules.<id>: true|false} en el texto de config.yml tocando solo esa línea, para
 * conservar los comentarios del usuario. Si el texto no tiene la forma esperada, usa YAML (pierde
 * comentarios pero nunca deja un archivo roto). Edits one switch line, keeping comments.
 */
public final class ModuleToggleWriter {

    private static final Pattern MODULES_LINE = Pattern.compile("^modules:\\s*(#.*)?$");
    private static final Pattern TOP_KEY = Pattern.compile("^[^\\s#][^:]*:.*$");
    private static final Pattern KEY_LINE = Pattern.compile("^(\\s+)[\\w-]+\\s*:.*$");
    /** Ancho de "id: valor" antes del comentario, como en la plantilla. Width before the inline comment. */
    private static final int COMMENT_COLUMN = 22;
    private static final String GROUP_MARK = "# =====";
    private static final Pattern SWITCH = Pattern.compile("^(\\s+)([\\w-]+)(\\s*:\\s*)(.*?)(\\s+#.*)?$");
    private static final Pattern OLD_ENABLED = Pattern.compile("^(\\s+)enabled(\\s*:\\s*)(true|false)(\\s+#.*)?$");

    /**
     * Interruptor de un módulo de addon: valor si falta en config.yml y comentario de la línea (o null).
     * An addon module switch: value used when missing, and its inline comment.
     */
    public record Switch(boolean on, String comment) {
    }

    private ModuleToggleWriter() {
    }

    /**
     * @param text  contenido actual de config.yml
     * @param id    id del módulo (clave bajo {@code modules:})
     * @param alias ids antiguos que también cuentan (pueden estar vacíos)
     * @return el texto nuevo, ya comprobado: al cargarlo, {@code modules.<id>} vale {@code on}
     */
    public static String apply(String text, String id, boolean on, String... alias) {
        String edited = edit(text, id, on, alias);
        if (edited != null && check(edited, id, on)) return edited;
        return viaYaml(text, id, on, alias);
    }

    /** Edición línea a línea; null si no se pudo. */
    static String edit(String text, String id, boolean on, String... alias) {
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        int start = blockStart(lines);
        String value = on ? "true" : "false";
        if (start < 0) {
            // sin bloque modules: se añade al final
            newBlock(lines);
            lines.add("  " + id + ": " + value);
            lines.add("");
            return String.join(nl, lines);
        }
        int end = blockEnd(lines, start);
        List<String> names = new ArrayList<>();
        names.add(id);
        names.addAll(Arrays.asList(alias));
        String indent = null;
        int last = start;
        for (int i = start + 1; i < end; i++) {
            String line = lines.get(i);
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            last = i;
            Matcher m = SWITCH.matcher(line);
            if (!m.matches()) continue;
            if (indent == null) indent = m.group(1);
            if (!m.group(1).equals(indent) || !names.contains(m.group(2))) continue;
            String rest = m.group(4);
            String comment = m.group(5) == null ? "" : m.group(5);
            if (rest.matches("(?i)true|false|yes|no|on|off")) {
                lines.set(i, m.group(1) + id + m.group(3) + value + comment);
                return String.join(nl, lines);
            }
            if (rest.isEmpty()) {
                // formato antiguo: <id>:\n    enabled: x
                for (int j = i + 1; j < end; j++) {
                    String sub = lines.get(j);
                    if (sub.isBlank() || sub.trim().startsWith("#")) continue;
                    Matcher e = OLD_ENABLED.matcher(sub);
                    if (e.matches() && e.group(1).length() > indent.length()) {
                        lines.set(j, e.group(1) + "enabled" + e.group(2) + value + (e.group(4) == null ? "" : e.group(4)));
                        return String.join(nl, lines);
                    }
                    if (sub.length() - sub.stripLeading().length() <= indent.length()) break;
                }
            }
            return null;
        }
        lines.add(last + 1, (indent == null ? "  " : indent) + id + ": " + value);
        return String.join(nl, lines);
    }

    /** Comentario que agrupa los interruptores de un addon. Header comment of an addon group. */
    public static String header(String title) {
        return GROUP_MARK + " " + title + " =====";
    }

    /**
     * Añade a {@code modules:} los interruptores que falten, juntos bajo {@link #header(String)} (el grupo se crea al
     * final del bloque si no existe). Nunca cambia ni quita los que ya están, aunque sean de un addon que ya no está.
     * Adds the missing switches of an addon under its header; existing values are never touched.
     *
     * @return el texto nuevo (igual si no faltaba nada)
     */
    public static String addGroup(String text, String title, Map<String, Switch> switches) {
        YamlConfiguration before = load(text);
        Map<String, Switch> missing = new LinkedHashMap<>();
        switches.forEach((id, sw) -> {
            if (!before.contains("modules." + id)) missing.put(id, sw);
        });
        if (missing.isEmpty()) return text;
        String edited = insertGroup(text, header(title), missing);
        if (edited != null && checkGroup(edited, before, missing)) return edited;
        missing.forEach((id, sw) -> before.set("modules." + id, sw.on()));
        before.setComments("modules." + missing.keySet().iterator().next(), List.of(header(title).substring(1)));
        return before.saveToString();
    }

    private static String insertGroup(String text, String header, Map<String, Switch> missing) {
        String nl = text.contains("\r\n") ? "\r\n" : "\n";
        List<String> lines = new ArrayList<>(Arrays.asList(text.split("\r?\n", -1)));
        int start = blockStart(lines);
        if (start < 0) {
            if (text.lines().anyMatch(l -> l.startsWith("modules:"))) return null; // p. ej. modules: {} en una línea
            newBlock(lines);
            lines.add("  " + header);
            for (Map.Entry<String, Switch> e : missing.entrySet()) lines.add(switchLine("  ", e.getKey(), e.getValue()));
            lines.add("");
            return String.join(nl, lines);
        }
        int end = blockEnd(lines, start);
        String indent = null;
        int last = start;
        int head = -1;
        for (int i = start + 1; i < end; i++) {
            String line = lines.get(i);
            if (line.trim().equals(header)) head = i;
            if (line.isBlank() || line.trim().startsWith("#")) continue;
            last = i;
            Matcher m = KEY_LINE.matcher(line);
            if (indent == null && m.matches()) indent = m.group(1);
        }
        if (indent == null) indent = "  ";
        List<String> add = new ArrayList<>();
        int at;
        if (head >= 0) {
            // al final del grupo: hasta la cabecera del siguiente grupo o el final del bloque
            at = head + 1;
            for (int i = head + 1; i < end; i++) {
                String line = lines.get(i);
                if (line.trim().startsWith(GROUP_MARK)) break;
                if (!line.isBlank() && !line.trim().startsWith("#")) at = i + 1;
            }
        } else {
            at = last + 1;
            if (last > start) add.add("");
            add.add(indent + header);
        }
        for (Map.Entry<String, Switch> e : missing.entrySet()) add.add(switchLine(indent, e.getKey(), e.getValue()));
        lines.addAll(at, add);
        return String.join(nl, lines);
    }

    /** Línea {@code modules:} o -1. */
    private static int blockStart(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) if (MODULES_LINE.matcher(lines.get(i)).matches()) return i;
        return -1;
    }

    /** Primera línea después del bloque que empieza en {@code start}. */
    private static int blockEnd(List<String> lines, int start) {
        for (int i = start + 1; i < lines.size(); i++) if (TOP_KEY.matcher(lines.get(i)).matches()) return i;
        return lines.size();
    }

    /** Añade un bloque {@code modules:} vacío al final. */
    private static void newBlock(List<String> lines) {
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        lines.add("");
        lines.add("modules:");
    }

    private static String switchLine(String indent, String id, Switch sw) {
        StringBuilder b = new StringBuilder(indent).append(id).append(": ").append(sw.on());
        if (sw.comment() == null || sw.comment().isBlank()) return b.toString();
        int width = indent.length() + COMMENT_COLUMN;
        do b.append(' '); while (b.length() < width);
        return b.append("# ").append(sw.comment()).toString();
    }

    /** Cada interruptor nuevo vale lo pedido y los que ya había siguen igual. */
    private static boolean checkGroup(String text, YamlConfiguration before, Map<String, Switch> missing) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            return false;
        }
        for (Map.Entry<String, Switch> e : missing.entrySet()) {
            String path = "modules." + e.getKey();
            if (!y.isBoolean(path) || y.getBoolean(path) != e.getValue().on()) return false;
        }
        for (String k : before.getKeys(true)) {
            if (before.isConfigurationSection(k)) continue;
            if (!y.contains(k) || !String.valueOf(before.get(k)).equals(String.valueOf(y.get(k)))) return false;
        }
        return true;
    }

    private static YamlConfiguration load(String text) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            throw new IllegalStateException("config.yml no es YAML válido: " + e.getMessage(), e);
        }
        return y;
    }

    private static boolean check(String text, String id, boolean on) {
        YamlConfiguration y = new YamlConfiguration();
        try {
            y.loadFromString(text);
        } catch (InvalidConfigurationException e) {
            return false;
        }
        String path = "modules." + id;
        if (y.isBoolean(path)) return y.getBoolean(path) == on;
        return y.isBoolean(path + ".enabled") && y.getBoolean(path + ".enabled") == on;
    }

    private static String viaYaml(String text, String id, boolean on, String... alias) {
        YamlConfiguration y = load(text);
        for (String a : alias) y.set("modules." + a, null);
        y.set("modules." + id, on);
        return y.saveToString();
    }
}
