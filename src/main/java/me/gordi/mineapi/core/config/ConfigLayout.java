package me.gordi.mineapi.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Dónde vive la configuración de cada módulo (v9) y lectura/escritura por bloques. Un bloque es una clave
 * de primer nivel ({@code general:}, {@code stats:}...) con sus líneas sangradas. Solo se analiza como YAML el
 * bloque que se pide: la sección de un módulo apagado nunca se convierte en objetos, y el archivo propio de
 * un módulo apagado ni se abre. Al reescribir un bloque el resto del archivo queda idéntico, byte a byte.
 * Código puro (sin servidor): se prueba con JUnit.
 *
 * <p>v9 layout: config.yml holds the head and the core sections (stats, periods, history, backups, updates)
 * plus {@code advanced} at the end; settings/web.yml, settings/discord.yml (one section per Discord module) and
 * settings/database.yml always exist; every extra with settings owns settings/&lt;id&gt;.yml, created only when
 * the extra is enabled.
 */
public final class ConfigLayout {

    /** Bloques que se leen siempre al arrancar: la cabecera del archivo y {@code advanced}. */
    public static final List<String> HEAD = List.of(ConfigMigrator.VERSION_KEY, "general", "modules", "advanced");

    /** Módulos con una lista larga (id -> claves de la lista). La lista va al final de su archivo. */
    public static final Map<String, List<String>> CONTENT = Map.of(
            "milestones", List.of("stats"),
            "quests", List.of("daily", "weekly"),
            "titles", List.of("titles"));

    public static final String CONFIG = "config.yml";
    /** Carpeta de los archivos de ajustes (todo lo que no es config.yml). */
    public static final String SETTINGS_DIR = "settings";
    public static final String WEB = SETTINGS_DIR + "/web.yml";
    public static final String DISCORD = SETTINGS_DIR + "/discord.yml";
    public static final String DATABASE = SETTINGS_DIR + "/database.yml";

    /** Módulos del núcleo con su sección en config.yml (en este orden). */
    public static final List<String> CORE_SECTIONS = List.of("stats", "periods", "history", "backups", "updates");

    /** Archivos que existen siempre (instalación nueva y migración), en el orden en que se documentan. */
    public static final List<String> FILES = List.of(CONFIG, WEB, DISCORD, DATABASE);

    /**
     * Ubicación de los ajustes de un módulo: archivo y sección dentro de él ({@code null} = el archivo entero,
     * con las claves en la raíz). Where a module's settings live.
     */
    public record Place(String file, String section) {
        public boolean whole() {
            return section == null;
        }

        /** Texto para el usuario: {@code config.yml → stats}, {@code settings/discord.yml → discord-bot} o {@code settings/web.yml}. */
        public String label() {
            return whole() ? file : file + " → " + section;
        }
    }

    public static Place place(String id) {
        if ("web".equals(id)) return new Place(WEB, null);
        if ("discord-webhook".equals(id) || "discord-bot".equals(id)) return new Place(DISCORD, id);
        if (CORE_SECTIONS.contains(id)) return new Place(CONFIG, id);
        return new Place(extraFile(id), null);
    }

    /** Archivo de un extra: {@code settings/<id>.yml}. */
    public static String extraFile(String id) {
        return SETTINGS_DIR + "/" + id + ".yml";
    }

    /** ¿Es un archivo que solo existe mientras su extra está activo (o personalizado)? */
    public static boolean isExtraFile(String file) {
        return file.startsWith(SETTINGS_DIR + "/") && !FILES.contains(file);
    }

    private static final Pattern TOP_KEY = Pattern.compile("^[A-Za-z0-9_\"'][^:#]*:(\\s.*)?$");
    private static final String INDENT = "  ";

    private ConfigLayout() {
    }

    /**
     * Un bloque: {@code lead} = primera línea de su comentario superior, {@code start} = línea de la clave,
     * {@code end} = fin exclusivo del cuerpo (sin los comentarios y líneas vacías que preceden al siguiente).
     */
    public record Block(String key, int lead, int start, int end) {
    }

    /** Archivo partido en líneas y bloques. */
    public static final class Doc {
        final List<String> lines;
        final Map<String, Block> blocks;

        Doc(List<String> lines, Map<String, Block> blocks) {
            this.lines = lines;
            this.blocks = blocks;
        }

        public boolean has(String key) {
            return blocks.containsKey(key);
        }

        public java.util.Set<String> keys() {
            return blocks.keySet();
        }
    }

    public static Doc parse(String text) {
        List<String> lines = new ArrayList<>(List.of(text.replace("\r\n", "\n").split("\n", -1)));
        if (!lines.isEmpty() && lines.get(lines.size() - 1).isEmpty()) lines.remove(lines.size() - 1);
        List<Integer> starts = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) if (TOP_KEY.matcher(lines.get(i)).matches()) starts.add(i);
        Map<String, Block> blocks = new LinkedHashMap<>();
        for (int n = 0; n < starts.size(); n++) {
            int start = starts.get(n);
            int next = n + 1 < starts.size() ? starts.get(n + 1) : lines.size();
            int end = next;
            // las líneas finales vacías o comentarios en columna 0 son del bloque siguiente (su cabecera)
            while (end > start + 1 && isLeadLine(lines.get(end - 1))) end--;
            int lead = start;
            while (lead > 0 && lines.get(lead - 1).startsWith("#")) lead--;
            String key = keyOf(lines.get(start));
            blocks.putIfAbsent(key, new Block(key, lead, start, end));
        }
        return new Doc(lines, blocks);
    }

    private static boolean isLeadLine(String l) {
        return l.isBlank() || l.startsWith("#");
    }

    static String keyOf(String line) {
        String k = line.substring(0, line.indexOf(':')).trim();
        if (k.length() >= 2 && (k.charAt(0) == '"' || k.charAt(0) == '\'') && k.charAt(k.length() - 1) == k.charAt(0)) {
            k = k.substring(1, k.length() - 1);
        }
        return k;
    }

    /** Texto YAML (con sus claves de primer nivel) de los bloques dados, en el orden del archivo. */
    public static String blocksText(Doc doc, List<String> keys) {
        StringBuilder sb = new StringBuilder();
        for (Block b : doc.blocks.values()) {
            if (!keys.contains(b.key())) continue;
            for (int i = b.lead(); i < b.end(); i++) sb.append(doc.lines.get(i)).append('\n');
        }
        return sb.toString();
    }

    /** Cabecera (config-version, general, modules y advanced) ya analizada. */
    public static YamlConfiguration head(Doc doc) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        y.loadFromString(blocksText(doc, HEAD));
        return y;
    }

    /** Cuerpo de una sección sin la sangría (sus claves quedan en la raíz), o null si no existe. */
    public static String body(Doc doc, String key) {
        Block b = doc.blocks.get(key);
        if (b == null) return null;
        String first = doc.lines.get(b.start());
        String inline = first.substring(first.indexOf(':') + 1).trim();
        if (inline.startsWith("#")) inline = "";
        if (!inline.isEmpty()) return "{}".equals(inline) || "~".equals(inline) || "null".equals(inline) ? "" : null;
        int indent = -1;
        for (int i = b.start() + 1; i < b.end(); i++) {
            String l = doc.lines.get(i);
            if (l.isBlank() || l.trim().startsWith("#")) continue;
            indent = l.length() - l.stripLeading().length();
            break;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = b.start() + 1; i < b.end(); i++) {
            String l = doc.lines.get(i);
            int lead = l.length() - l.stripLeading().length();
            sb.append(indent > 0 ? l.substring(Math.min(lead, indent)) : l.stripLeading()).append('\n');
        }
        return sb.toString();
    }

    /** Sección de un módulo como YAML propio (claves en la raíz). Vacía si no existe. */
    public static YamlConfiguration section(Doc doc, String key) throws InvalidConfigurationException {
        YamlConfiguration y = new YamlConfiguration();
        String body = body(doc, key);
        if (body == null && doc.has(key)) throw new InvalidConfigurationException("«" + key + ":» debe ser una sección / must be a section");
        if (body != null && !body.isBlank()) y.loadFromString(body);
        return y;
    }

    /** Sangra un YAML de raíz para colocarlo debajo de una clave de primer nivel. */
    public static List<String> indent(String rootYaml) {
        List<String> out = new ArrayList<>();
        for (String l : rootYaml.replace("\r\n", "\n").split("\n", -1)) out.add(l.isBlank() ? "" : INDENT + l);
        while (!out.isEmpty() && out.get(out.size() - 1).isEmpty()) out.remove(out.size() - 1);
        return out;
    }

    /**
     * Sustituye el cuerpo de {@code key} (la línea de la clave y sus líneas sangradas) por {@code rootYaml}.
     * El comentario superior y el resto del archivo no cambian. Si la clave no existe, se añade al final
     * con {@code leadIfMissing} como comentario superior.
     */
    public static String replace(String text, String key, String rootYaml, List<String> leadIfMissing) {
        Doc doc = parse(text);
        Block b = doc.blocks.get(key);
        List<String> body = indent(rootYaml);
        List<String> out = new ArrayList<>(doc.lines);
        List<String> section = new ArrayList<>();
        section.add(quoteKey(key) + (body.isEmpty() ? ": {}" : ":"));
        section.addAll(body);
        if (b != null) {
            for (int i = b.end() - 1; i >= b.start(); i--) out.remove(i);
            out.addAll(b.start(), section);
        } else {
            while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
            out.add("");
            if (leadIfMissing != null) out.addAll(leadIfMissing);
            out.addAll(section);
        }
        return String.join("\n", out) + "\n";
    }

    /**
     * Sustituye el bloque de primer nivel {@code key} (su línea y las sangradas) por {@code keyYaml}, que ya
     * empieza por {@code key:} en la columna 0 (sirve para secciones, listas y valores sueltos). El comentario
     * superior y el resto del archivo no cambian. Si la clave no existe, se añade al final con {@code leadIfMissing}.
     */
    public static String replaceTop(String text, String key, String keyYaml, List<String> leadIfMissing) {
        Doc doc = parse(text);
        Block b = doc.blocks.get(key);
        List<String> block = new ArrayList<>(List.of(keyYaml.replace("\r\n", "\n").split("\n", -1)));
        while (!block.isEmpty() && block.get(block.size() - 1).isBlank()) block.remove(block.size() - 1);
        List<String> out = new ArrayList<>(doc.lines);
        if (b != null) {
            for (int i = b.end() - 1; i >= b.start(); i--) out.remove(i);
            out.addAll(b.start(), block);
        } else {
            while (!out.isEmpty() && out.get(out.size() - 1).isBlank()) out.remove(out.size() - 1);
            out.add("");
            if (leadIfMissing != null) out.addAll(leadIfMissing);
            out.addAll(block);
        }
        return String.join("\n", out) + "\n";
    }

    /** Edita solo el texto del bloque {@code key} (clave + cuerpo), p. ej. para cambiar una línea conservando comentarios. */
    public static String editBlock(String text, String key, java.util.function.UnaryOperator<String> edit) {
        Doc doc = parse(text);
        Block b = doc.blocks.get(key);
        if (b == null) return text;
        String block = String.join("\n", doc.lines.subList(b.start(), b.end())) + "\n";
        String edited = edit.apply(block);
        if (edited == null || edited.equals(block)) return text;
        List<String> out = new ArrayList<>(doc.lines.subList(0, b.start()));
        out.addAll(List.of(edited.replaceAll("\n+$", "").split("\n", -1)));
        out.addAll(doc.lines.subList(b.end(), doc.lines.size()));
        return String.join("\n", out) + "\n";
    }

    /** Comentario superior del bloque (banner del módulo), para copiarlo de la plantilla. */
    public static List<String> lead(Doc doc, String key) {
        Block b = doc.blocks.get(key);
        return b == null ? List.of() : new ArrayList<>(doc.lines.subList(b.lead(), b.start()));
    }

    private static String quoteKey(String key) {
        return key.matches("[A-Za-z0-9_-]+") ? key : "\"" + key + "\"";
    }

    /** Vuelca una sección a texto YAML de raíz (con comentarios). */
    public static String dump(YamlConfiguration y) {
        return y.getKeys(false).isEmpty() ? "" : y.saveToString();
    }
}
