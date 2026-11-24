package me.gordi.mineapi.core.command;

import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.log.DebugLog;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import me.gordi.mineapi.core.i18n.I18n;

/**
 * Ejecutor de /lpa (alias /losperrisapi). Los módulos del núcleo y los addons registran y retiran aquí sus
 * subcomandos ({@code LosPerrisApi.commands()}); un addon puede enlazar su comando antiguo (p. ej. /lpx) a este
 * mismo ejecutor. The /lpa executor; core modules and addons register their subcommands here.
 */
public final class CommandRouter implements TabExecutor {

    private record Entry(Object owner, SubCommand command) {
    }

    private final Supplier<I18n> i18n;
    private final String version;
    private final Predicate<String> on;
    private final Consumer<String> debug;
    private final CommandCatalog catalog = new CommandCatalog();
    private final Map<String, Entry> commands = new LinkedHashMap<>();
    private final Map<String, String> aliases = new LinkedHashMap<>();

    public CommandRouter(MineAPI plugin) {
        this.i18n = plugin::i18n;
        this.version = plugin.getDescription().getVersion();
        this.on = id -> plugin.modules().isEnabled(id);
        this.debug = msg -> plugin.debug().log(DebugLog.Category.GENERAL, msg);
    }

    /** Módulos y ayuda de /lpa help y /lpa modules (núcleo y addons). */
    public CommandCatalog catalog() {
        return catalog;
    }

    public void register(Object owner, SubCommand command) {
        commands.put(command.name, new Entry(owner, command));
        for (String a : command.aliases) aliases.put(a, command.name);
    }

    /** Quita los subcomandos, módulos y líneas de ayuda de {@code owner}. */
    public void unregisterAll(Object owner) {
        catalog.unregisterAll(owner);
        commands.values().removeIf(e -> {
            if (e.owner != owner) return false;
            aliases.values().removeIf(n -> n.equals(e.command.name));
            return true;
        });
    }

    private SubCommand find(String label) {
        String key = label.toLowerCase(Locale.ROOT);
        Entry e = commands.get(aliases.getOrDefault(key, key));
        return e == null ? null : e.command;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        debug.accept("Comando de " + sender.getName() + ": /" + label + " " + String.join(" ", args));
        I18n i18n = this.i18n.get();
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        SubCommand sub = find(args[0]);
        if (sub == null) {
            i18n.send(sender, "commands.unknown");
            sendHelp(sender);
            return true;
        }
        if (!sub.canUse(sender)) {
            i18n.send(sender, "general.no-permission");
            return true;
        }
        if (sub.playerOnly && !(sender instanceof Player)) {
            i18n.send(sender, "general.players-only");
            return true;
        }
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        if (rest.length < sub.minArgs) {
            i18n.send(sender, "general.usage", "usage", i18n.tr(sender, "commands." + sub.name + ".usage"));
            return true;
        }
        sub.executor.run(sender, rest);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Entry e : commands.values()) {
                if (!e.command.canUse(sender)) continue;
                names.add(e.command.name);
                names.addAll(e.command.aliases);
            }
            List<String> out = new ArrayList<>();
            StringUtil.copyPartialMatches(args[0], names, out);
            Collections.sort(out);
            return out;
        }
        SubCommand sub = find(args[0]);
        if (sub == null || !sub.canUse(sender)) return Collections.emptyList();
        String[] rest = Arrays.copyOfRange(args, 1, args.length);
        List<String> options = sub.completer.complete(sender, rest);
        if (options == null) return null; // null = nombres de jugadores (Bukkit)
        List<String> out = new ArrayList<>();
        StringUtil.copyPartialMatches(rest[rest.length - 1], options, out);
        return out;
    }

    public void sendHelp(CommandSender sender) {
        sendHelp(sender, null);
    }

    /** Ayuda agrupada y con clic; {@code topic} = null, "admin" o un id de módulo. */
    public void sendHelp(CommandSender sender, String topic) {
        I18n i18n = this.i18n.get();
        HelpView.Context ctx = new HelpView.Context() {
            public String tr(String key, Object... vars) { return i18n.tr(sender, key, vars); }
            public boolean has(String permission) { return sender.hasPermission(permission); }
            public boolean on(String module) { return HelpCatalog.CORE.equals(module) || on.test(module); }
            public boolean console() { return !(sender instanceof Player); }
            public CatalogView catalog() { return catalog; }
        };
        ChatOut.send(sender, HelpView.build(ctx, version, topic));
    }
}
