package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Slash definidos en {@code discord-bot.custom-commands} (máx. 15). */
public final class CustomCommands {

    public static final Set<String> RESERVED = Set.of("stats", "top", "online", "staff", "panel", "link", "unlink");

    public record Option(String name, String description, boolean required) {
    }

    public record Command(String name, String description, boolean ephemeral, String reply, String action,
                          EmbedTemplate embed, List<Option> options) {
    }

    private final List<Command> commands;

    public CustomCommands(List<Map<?, ?>> raw) {
        List<Command> out = new ArrayList<>();
        if (raw != null) {
            for (Map<?, ?> m : raw) {
                if (out.size() >= 15) break;
                Command c = parse(m);
                if (c != null) out.add(c);
            }
        }
        this.commands = List.copyOf(out);
    }

    public List<Command> all() {
        return commands;
    }

    public Command get(String name) {
        if (name == null) return null;
        String n = name.toLowerCase(Locale.ROOT);
        for (Command c : commands) if (c.name.equals(n)) return c;
        return null;
    }

    public void appendDefs(JsonArray defs) {
        for (Command c : commands) {
            JsonObject o = new JsonObject();
            o.addProperty("name", c.name);
            o.addProperty("type", 1);
            o.addProperty("description", cut(c.description, 100));
            if (!c.options.isEmpty()) {
                JsonArray opts = new JsonArray();
                for (Option opt : c.options) {
                    JsonObject op = new JsonObject();
                    op.addProperty("name", opt.name);
                    op.addProperty("type", 3);
                    op.addProperty("description", cut(opt.description, 100));
                    op.addProperty("required", opt.required);
                    opts.add(op);
                }
                o.add("options", opts);
            }
            defs.add(o);
        }
    }

    private static Command parse(Map<?, ?> m) {
        Object nameObj = m.get("name");
        if (nameObj == null) return null;
        String name = String.valueOf(nameObj).trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9-]{1,32}") || RESERVED.contains(name)) return null;
        String description = str(m, "description", name);
        boolean ephemeral = bool(m.get("ephemeral"), true);
        String reply = m.get("reply") == null ? null : String.valueOf(m.get("reply"));
        String action = m.get("action") == null ? "reply" : String.valueOf(m.get("action")).trim().toLowerCase(Locale.ROOT);
        EmbedTemplate embed = null;
        Object emb = m.get("embed");
        if (emb instanceof Map<?, ?> em) {
            org.bukkit.configuration.MemoryConfiguration mc = new org.bukkit.configuration.MemoryConfiguration();
            for (Map.Entry<?, ?> e : em.entrySet()) {
                if (e.getKey() != null) mc.set(String.valueOf(e.getKey()), e.getValue());
            }
            embed = new EmbedTemplate(mc);
        }
        List<Option> options = new ArrayList<>();
        Object opts = m.get("options");
        if (opts instanceof List<?> list) {
            for (Object o : list) {
                if (!(o instanceof Map<?, ?> om)) continue;
                String on = str(om, "name", "").trim().toLowerCase(Locale.ROOT);
                if (!on.matches("[a-z0-9-]{1,32}")) continue;
                options.add(new Option(on, str(om, "description", on), bool(om.get("required"), false)));
            }
        }
        if ((reply == null || reply.isBlank()) && embed == null && !"stats".equals(action) && !"online".equals(action)) {
            return null;
        }
        return new Command(name, description, ephemeral, reply, action, embed, List.copyOf(options));
    }

    private static String str(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : String.valueOf(v);
    }

    private static boolean bool(Object o, boolean def) {
        if (o instanceof Boolean b) return b;
        if (o == null) return def;
        return Boolean.parseBoolean(String.valueOf(o));
    }

    private static String cut(String s, int max) {
        String t = s == null || s.isBlank() ? "-" : s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }
}
