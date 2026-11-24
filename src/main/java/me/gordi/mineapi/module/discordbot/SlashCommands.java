package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.module.stats.StatType;

import java.util.Map;
import java.util.function.BiFunction;

/** Definiciones JSON de slash commands y lectura de opciones de una interacción. */
public final class SlashCommands {

    static final Map<String, String[]> DISCORD_LOCALES = Map.of(
            "es", new String[]{"es-ES", "es-419"},
            "en", new String[]{"en-US", "en-GB"});

    private static final int STRING = 3;
    private static final int INTEGER = 4;
    private static final int CHANNEL = 7;

    private SlashCommands() {
    }

    /**
     * @param staff incluir /staff
     * @param custom comandos YAML (pueden ser null)
     */
    public static JsonArray build(BiFunction<String, String, String> tr, String def, boolean staff, CustomCommands custom) {
        JsonArray out = new JsonArray();

        JsonObject stats = command("stats", "discordbot.slash.stats", tr, def);
        stats.add("options", array(option("player", "discordbot.slash.player", true, STRING, tr, def)));
        out.add(stats);

        JsonObject top = command("top", "discordbot.slash.top", tr, def);
        JsonObject stat = option("stat", "discordbot.slash.stat", true, STRING, tr, def);
        JsonArray choices = new JsonArray();
        for (StatType t : StatType.ALL) {
            if (choices.size() == 25) break;
            JsonObject c = new JsonObject();
            c.addProperty("name", cut(tr.apply(def, "stats.names." + t.key), 100));
            c.add("name_localizations", localized(tr, "stats.names." + t.key, 100));
            c.addProperty("value", t.token);
            choices.add(c);
        }
        stat.add("choices", choices);
        JsonObject amount = option("amount", "discordbot.slash.amount", false, INTEGER, tr, def);
        amount.addProperty("min_value", 3);
        amount.addProperty("max_value", 15);
        top.add("options", array(stat, amount));
        out.add(top);

        out.add(command("online", "discordbot.slash.online", tr, def));

        JsonObject panel = command("panel", "discordbot.slash.panel", tr, def);
        JsonObject channel = option("channel", "discordbot.slash.channel", false, CHANNEL, tr, def);
        panel.add("options", array(channel));
        out.add(panel);

        if (staff) {
            JsonObject s = command("staff", "discordbot.slash.staff", tr, def);
            s.add("options", array(option("command", "discordbot.slash.command", true, STRING, tr, def)));
            s.add("contexts", array(new com.google.gson.JsonPrimitive(0)));
            out.add(s);
        }
        if (custom != null) custom.appendDefs(out);
        return out;
    }

    private static JsonObject command(String name, String key, BiFunction<String, String, String> tr, String def) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", 1);
        o.addProperty("description", cut(tr.apply(def, key), 100));
        o.add("description_localizations", localized(tr, key, 100));
        return o;
    }

    private static JsonObject option(String name, String key, boolean required, int type,
                                     BiFunction<String, String, String> tr, String def) {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("type", type);
        o.addProperty("description", cut(tr.apply(def, key), 100));
        o.add("description_localizations", localized(tr, key, 100));
        o.addProperty("required", required);
        return o;
    }

    private static JsonObject localized(BiFunction<String, String, String> tr, String key, int max) {
        JsonObject o = new JsonObject();
        for (Map.Entry<String, String[]> e : DISCORD_LOCALES.entrySet()) {
            String text = cut(tr.apply(e.getKey(), key), max);
            for (String loc : e.getValue()) o.addProperty(loc, text);
        }
        return o;
    }

    private static JsonArray array(JsonElement... items) {
        JsonArray a = new JsonArray();
        for (JsonElement i : items) a.add(i);
        return a;
    }

    private static String cut(String s, int max) {
        String t = s == null || s.isBlank() ? "-" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public static String option(JsonObject data, String name) {
        if (data == null || !data.has("options")) return null;
        for (JsonElement e : data.getAsJsonArray("options")) {
            JsonObject o = e.getAsJsonObject();
            if (name.equals(o.get("name").getAsString()) && o.has("value")) {
                JsonElement v = o.get("value");
                if (v.isJsonPrimitive()) return v.getAsString();
            }
        }
        return null;
    }

    public static Integer optionInt(JsonObject data, String name) {
        if (data == null || !data.has("options")) return null;
        for (JsonElement e : data.getAsJsonArray("options")) {
            JsonObject o = e.getAsJsonObject();
            if (name.equals(o.get("name").getAsString()) && o.has("value") && o.get("value").isJsonPrimitive()) {
                try {
                    return o.get("value").getAsInt();
                } catch (RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** Opción tipo canal: Discord manda el snowflake como string en value. */
    public static String channelId(JsonObject data, String name) {
        return option(data, name);
    }

    public static String clientLocale(JsonObject interaction) {
        return interaction.has("locale") ? interaction.get("locale").getAsString().replace('-', '_') : null;
    }
}
