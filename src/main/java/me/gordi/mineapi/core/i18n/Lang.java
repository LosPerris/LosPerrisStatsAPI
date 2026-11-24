package me.gordi.mineapi.core.i18n;

import me.gordi.mineapi.core.text.Template;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/** Un idioma ya compilado: cada mensaje es un {@link Template} listo para renderizar. */
public final class Lang {

    private final String code;
    private final Map<String, Template> messages = new HashMap<>();
    private final Map<String, List<Template>> lists = new HashMap<>();

    private Lang(String code) {
        this.code = code;
    }

    /** Compila todas las hojas de la sección (mensajes y listas de mensajes). */
    public static Lang parse(String code, ConfigurationSection yaml) {
        Lang lang = new Lang(code);
        if (yaml == null) return lang;
        for (String key : yaml.getKeys(true)) {
            if (yaml.isConfigurationSection(key)) continue;
            if (yaml.isList(key)) {
                List<Template> list = new ArrayList<>();
                for (String line : yaml.getStringList(key)) list.add(Template.compile(line));
                lang.lists.put(key, Collections.unmodifiableList(list));
            } else {
                lang.messages.put(key, Template.compile(yaml.getString(key)));
            }
        }
        return lang;
    }

    public String code() { return code; }
    public Template get(String key) { return messages.get(key); }
    public List<Template> getList(String key) { return lists.get(key); }
    public int size() { return messages.size() + lists.size(); }

    /** Recorre todos los textos originales para validarlos. */
    public void forEach(BiConsumer<String, Template> action) {
        messages.forEach(action);
        lists.forEach((k, v) -> {
            for (Template t : v) action.accept(k, t);
        });
    }
}
