package me.gordi.mineapi.core.i18n;

import me.gordi.mineapi.core.config.ConfigService;
import me.gordi.mineapi.core.text.Template;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.core.text.VariableContext;
import me.gordi.mineapi.core.text.Variables;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * Traducciones ES/EN (y cualquier lang/&lt;código&gt;.yml que añada el usuario).
 * <p>
 * Orden de búsqueda: idioma elegido por el jugador (/lpa lang) → idioma de su cliente (si
 * {@code per-player-language}) → {@code language} del servidor → español empaquetado → la clave.
 * Todo se precompila al cargar: traducir un mensaje es una búsqueda en un HashMap.
 */
public final class I18n {

    public static final String FALLBACK = "es";
    public static final String[] BUNDLED = {"es", "en"};

    /** De dónde salen los archivos de idioma: el del usuario (se crea si falta) y el del JAR. */
    public interface Source {
        /** {@code lang/<code>.yml} de la carpeta del plugin (se copia del JAR si falta). */
        YamlConfiguration load(String path);

        /** El mismo archivo, tal cual viene en el JAR. */
        YamlConfiguration resource(String path);
    }

    private final JavaPlugin plugin;
    private final Source source;
    /** Idiomas del núcleo (solo en addons): idioma del jugador, prefijo y claves comunes. */
    private final I18n parent;
    private final Predicate<String> knownVariable;
    private volatile Map<String, Lang> langs = Collections.emptyMap();
    private volatile String defaultLang = FALLBACK;
    private volatile boolean perPlayer = true;
    private volatile String prefix = "";
    private final Map<UUID, String> overrides = new ConcurrentHashMap<>();
    private final Map<String, String> localeCache = new ConcurrentHashMap<>();
    /** Idiomas de addons que completan los del núcleo (textos de sus comandos y módulos en /lpa). */
    private final List<Extension> extensions = new CopyOnWriteArrayList<>();

    private record Extension(Object owner, I18n texts) {
    }

    public I18n(JavaPlugin plugin, ConfigService configs) {
        this.plugin = plugin;
        this.source = new Source() {
            public YamlConfiguration load(String path) { return configs.load(path, true); }
            public YamlConfiguration resource(String path) { return configs.loadResource(path); }
        };
        this.parent = null;
        this.knownVariable = v -> Variables.isKnown(VariableContext.LANG, v);
    }

    /**
     * Idiomas de un addon: tiene sus propios {@code lang/<código>.yml} y, para el resto, usa los del núcleo
     * ({@code parent}): mismo idioma por jugador, mismo prefijo y las claves comunes. Addon translations.
     * @param knownVariable variables válidas en sus textos
     */
    public I18n(JavaPlugin plugin, Source source, I18n parent, Predicate<String> knownVariable) {
        this.plugin = plugin;
        this.source = source;
        this.parent = parent;
        this.knownVariable = knownVariable;
    }

    /** Carga los idiomas de un addon (el idioma y el prefijo son los del núcleo). */
    public void load() {
        load(null);
    }

    /** Carga (o recarga) todos los idiomas. Avisa de variables desconocidas. */
    public void load(ConfigurationSection config) {
        if (config != null) {
            prefix = Text.color(config.getString("prefix", "&8[&bLosPerris&8] &7"));
            perPlayer = config.getBoolean("per-player-language", true);
        }

        Map<String, Lang> loaded = new LinkedHashMap<>();
        for (String code : BUNDLED) {
            loaded.put(code, Lang.parse(code, source.load("lang/" + code + ".yml")));
        }
        File dir = new File(plugin.getDataFolder(), "lang");
        File[] extra = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (extra != null) {
            for (File f : extra) {
                String code = f.getName().substring(0, f.getName().length() - 4).toLowerCase(Locale.ROOT);
                if (!loaded.containsKey(code)) loaded.put(code, Lang.parse(code, YamlConfiguration.loadConfiguration(f)));
            }
        }
        // Red de seguridad: español empaquetado en el JAR, aunque el usuario borre claves
        YamlConfiguration bundled = source.resource("lang/" + FALLBACK + ".yml");
        loaded.put("__bundled", Lang.parse(FALLBACK, bundled));

        String def = config == null ? FALLBACK : config.getString("language", FALLBACK).toLowerCase(Locale.ROOT);
        if (config != null && !loaded.containsKey(def)) {
            plugin.getLogger().warning("Idioma '" + def + "' no encontrado en lang/. Se usa '" + FALLBACK + "'.");
            def = FALLBACK;
        }
        defaultLang = def;
        langs = loaded;
        localeCache.clear();
        validate(loaded);
    }

    private void validate(Map<String, Lang> loaded) {
        int[] warnings = {0};
        for (Map.Entry<String, Lang> e : loaded.entrySet()) {
            if (e.getKey().startsWith("__")) continue;
            e.getValue().forEach((key, template) -> {
                for (String var : template.variables()) {
                    if (!knownVariable.test(var)) {
                        warnings[0]++;
                        plugin.getLogger().warning("lang/" + e.getKey() + ".yml -> '" + key + "': variable desconocida {" + var
                                + "} (ver/see " + me.gordi.mineapi.core.Links.DOCS + "/es/variables/lang.md)");
                    }
                }
            });
        }
    }

    // ------------------------------------------------------------------ idioma por jugador

    /** Código de idioma que se usa con este destinatario. */
    public String locale(CommandSender sender) {
        if (parent != null) return parent.locale(sender);
        if (!(sender instanceof Player)) return defaultLang;
        Player p = (Player) sender;
        String forced = overrides.get(p.getUniqueId());
        if (forced != null && langs.containsKey(forced)) return forced;
        if (!perPlayer) return defaultLang;
        return normalize(p.getLocale());
    }

    /** "es_ES"/"es_mx" → "es" si existe ese idioma; si no, el del servidor. */
    public String normalize(String clientLocale) {
        if (parent != null) return parent.normalize(clientLocale);
        if (clientLocale == null || clientLocale.isEmpty()) return defaultLang;
        return localeCache.computeIfAbsent(clientLocale, raw -> {
            String lower = raw.toLowerCase(Locale.ROOT);
            if (langs.containsKey(lower)) return lower;
            int i = lower.indexOf('_');
            String base = i > 0 ? lower.substring(0, i) : lower;
            return langs.containsKey(base) ? base : defaultLang;
        });
    }

    /** Idioma forzado por el jugador (null = automático). */
    public void setOverride(UUID uuid, String code) {
        if (parent != null) {
            parent.setOverride(uuid, code);
            return;
        }
        if (code == null) overrides.remove(uuid);
        else overrides.put(uuid, code.toLowerCase(Locale.ROOT));
    }

    public void forget(UUID uuid) {
        if (parent != null) parent.forget(uuid);
        overrides.remove(uuid);
    }

    public boolean isAvailable(String code) {
        if (parent != null) return parent.isAvailable(code);
        return code != null && !code.startsWith("__") && langs.containsKey(code.toLowerCase(Locale.ROOT));
    }

    public Set<String> languages() {
        if (parent != null) return parent.languages();
        Set<String> set = new TreeSet<>();
        for (String k : langs.keySet()) if (!k.startsWith("__")) set.add(k);
        return set;
    }

    public String defaultLanguage() {
        return parent != null ? parent.defaultLanguage() : defaultLang;
    }

    public String prefix() {
        return parent != null ? parent.prefix() : prefix;
    }

    // ------------------------------------------------------------------ textos de addons

    /**
     * Añade los idiomas de un addon como fuente extra: una clave que el núcleo no tiene se busca en ellos
     * (ayuda, uso y nombres de los módulos del addon en /lpa). Addon texts as extra source for core lookups.
     */
    public void extend(Object owner, I18n texts) {
        if (texts != this) extensions.add(new Extension(owner, texts));
    }

    public void unextend(Object owner) {
        extensions.removeIf(e -> e.owner() == owner);
    }

    // ------------------------------------------------------------------ traducción

    private Template find(String lang, String key) {
        Template own = find0(lang, key);
        if (own != null) return own;
        if (parent != null) return parent.find(lang, key);
        for (Extension e : extensions) {
            Template t = e.texts().find0(lang, key);
            if (t != null) return t;
        }
        return null;
    }

    private Template find0(String lang, String key) {
        String defaultLang = defaultLanguage();
        Map<String, Lang> l = langs;
        Lang chosen = l.get(lang);
        Template t = chosen != null ? chosen.get(key) : null;
        if (t == null && !lang.equals(defaultLang)) {
            Lang def = l.get(defaultLang);
            if (def != null) t = def.get(key);
        }
        if (t == null) {
            Lang bundled = l.get("__bundled");
            if (bundled != null) t = bundled.get(key);
        }
        return t;
    }

    private List<Template> ownList(String lang, String key) {
        Map<String, Lang> l = langs;
        for (String code : new String[]{lang, defaultLanguage(), "__bundled"}) {
            Lang x = l.get(code);
            if (x == null) continue;
            List<Template> list = x.getList(key);
            if (list != null) return list;
        }
        return null;
    }

    private List<Template> findList(String lang, String key) {
        List<Template> own = ownList(lang, key);
        if (own != null) return own;
        if (parent != null) return parent.findList(lang, key);
        for (Extension e : extensions) {
            List<Template> list = e.texts().ownList(lang, key);
            if (list != null) return list;
        }
        return null;
    }

    /** Texto traducido para un idioma concreto. */
    public String tr(String lang, String key, Object... vars) {
        Template t = find(lang, key);
        return t == null ? key : t.render(vars);
    }

    /** Texto traducido en el idioma del destinatario. */
    public String tr(CommandSender to, String key, Object... vars) {
        return tr(locale(to), key, vars);
    }

    /** Texto en el idioma por defecto del servidor (consola, broadcasts, web). */
    public String trDefault(String key, Object... vars) {
        return tr(defaultLanguage(), key, vars);
    }

    public List<String> trList(CommandSender to, String key, Object... vars) {
        List<Template> list = findList(locale(to), key);
        if (list == null) return Collections.singletonList(key);
        List<String> out = new ArrayList<>(list.size());
        for (Template t : list) out.add(t.render(vars));
        return out;
    }

    /** Envía prefix + mensaje traducido. Un mensaje vacío en lang no se envía. */
    public void send(CommandSender to, String key, Object... vars) {
        String msg = tr(to, key, vars);
        if (!msg.isEmpty()) to.sendMessage(prefix() + msg);
    }
}
