package me.gordi.mineapi.core.command;

import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.config.ModuleToggleWriter;
import me.gordi.mineapi.core.module.Module;
import me.gordi.mineapi.core.module.ModuleCatalog;
import me.gordi.mineapi.core.module.ModuleManager;
import me.gordi.mineapi.core.module.Perms;
import me.gordi.mineapi.core.scheduler.TaskScheduler;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Subcomandos del núcleo: help, version, reload, lang, modules (y enable/disable) y timings. */
public final class CoreCommands {

    /** Permiso padre: incluye todos los de administración (ver plugin.yml). */
    public static final String ADMIN = Perms.ADMIN;

    private CoreCommands() {
    }

    public static void register(MineAPI plugin, CommandRouter router) {
        var i18n = plugin.i18n();

        router.register(plugin, SubCommand.of("help").aliases("ayuda")
                .executes((s, a) -> router.sendHelp(s, a.length > 0 ? a[0] : null))
                .completes((s, a) -> {
                    List<String> out = new ArrayList<>();
                    out.add("admin");
                    out.addAll(router.catalog().ids());
                    return out;
                }).build());

        router.register(plugin, SubCommand.of("version")
                .executes((s, a) -> {
                    i18n.send(s, "commands.version.version", "version", plugin.getDescription().getVersion());
                    i18n.send(s, "commands.version.authors", "authors", String.join(", ", plugin.getDescription().getAuthors()));
                    for (Plugin addon : plugin.api().addonRegistry().plugins()) {
                        i18n.send(s, "commands.version.addon", "module", addon.getName(), "version", addon.getDescription().getVersion());
                    }
                }).build());

        router.register(plugin, SubCommand.of("reload").permission(Perms.RELOAD)
                .executes((s, a) -> {
                    long start = System.nanoTime();
                    if (!plugin.reloadSystem()) {
                        i18n.send(s, "commands.reload.failed");
                        return;
                    }
                    i18n.send(s, "commands.reload.done", "value", String.format(Locale.ROOT, "%.1f", (System.nanoTime() - start) / 1e6));
                }).build());

        router.register(plugin, SubCommand.of("lang").aliases("idioma", "language").playerOnly()
                .executes((s, a) -> {
                    Player p = (Player) s;
                    if (a.length == 0) {
                        i18n.send(s, "commands.lang.current", "language", i18n.locale(s), "languages", String.join(", ", i18n.languages()));
                        return;
                    }
                    String code = a[0].toLowerCase(Locale.ROOT);
                    if (code.equals("auto")) {
                        i18n.setOverride(p.getUniqueId(), null);
                        plugin.data().mutate(p, json -> json.remove("locale"));
                    } else if (i18n.isAvailable(code)) {
                        i18n.setOverride(p.getUniqueId(), code);
                        plugin.data().mutate(p, json -> json.addProperty("locale", code));
                    } else {
                        i18n.send(s, "commands.lang.unknown", "languages", String.join(", ", i18n.languages()));
                        return;
                    }
                    plugin.data().queueSave(p.getUniqueId(), true);
                    i18n.send(s, "commands.lang.changed", "language", i18n.locale(s));
                })
                .completes((s, a) -> {
                    List<String> out = new ArrayList<>(i18n.languages());
                    out.add("auto");
                    return out;
                }).build());

        router.register(plugin, SubCommand.of("modules").aliases("modulos").permission(Perms.MODULES)
                .executes((s, a) -> modules(plugin, s, a))
                .completes((s, a) -> {
                    if (a.length == 1) {
                        List<String> out = new ArrayList<>(router.catalog().ids());
                        if (s.hasPermission(Perms.MODULES_TOGGLE)) {
                            out.add("enable");
                            out.add("disable");
                        }
                        return out;
                    }
                    if (a.length == 2 && (a[0].equalsIgnoreCase("enable") || a[0].equalsIgnoreCase("disable"))) {
                        boolean on = a[0].equalsIgnoreCase("enable");
                        List<String> out = new ArrayList<>();
                        for (String id : router.catalog().ids()) if (plugin.isModuleFlagOn(id) != on) out.add(id);
                        return out;
                    }
                    return List.of();
                }).build());

        router.register(plugin, SubCommand.of("timings").permission(Perms.TIMINGS)
                .executes((s, a) -> {
                    if (a.length > 0 && a[0].equalsIgnoreCase("reset")) {
                        plugin.scheduler().resetTimings();
                        i18n.send(s, "commands.timings.reset");
                        return;
                    }
                    i18n.send(s, "commands.timings.header");
                    for (Map.Entry<String, TaskScheduler.Timing> e : plugin.scheduler().timings().entrySet()) {
                        TaskScheduler.Timing t = e.getValue();
                        s.sendMessage(i18n.tr(s, t.async ? "commands.timings.line-async" : "commands.timings.line",
                                "task", e.getKey(),
                                "avg", String.format(Locale.ROOT, "%.3f", t.avgMs()),
                                "max", String.format(Locale.ROOT, "%.3f", t.maxMs()),
                                "runs", t.runs()));
                    }
                })
                .completes((s, a) -> List.of("reset")).build());
    }

    private static void modules(MineAPI plugin, CommandSender s, String[] a) {
        var i18n = plugin.i18n();
        ModulesView.Context ctx = context(plugin, s);
        if (a.length == 0) {
            for (List<HelpView.Line> row : ModulesView.list(ctx)) ChatOut.row(s, row);
            return;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        boolean enable = sub.equals("enable") || sub.equals("activar");
        boolean disable = sub.equals("disable") || sub.equals("desactivar");
        if (enable || disable) {
            if (!s.hasPermission(Perms.MODULES_TOGGLE)) {
                i18n.send(s, "general.no-permission");
                return;
            }
            if (a.length < 2) {
                i18n.send(s, "general.usage", "usage", i18n.tr(s, "commands.modules.usage-toggle"));
                return;
            }
            toggle(plugin, s, a[1], enable);
            return;
        }
        CatalogView.Mod mod = plugin.commands().catalog().get(sub);
        if (mod == null || plugin.modules().byId(mod.id()) == null) {
            i18n.send(s, "commands.modules.unknown", "module", a[0]);
            return;
        }
        for (List<HelpView.Line> row : ModulesView.detail(ctx, mod)) ChatOut.row(s, row);
    }

    private static ModulesView.Context context(MineAPI plugin, CommandSender s) {
        var i18n = plugin.i18n();
        ModuleManager mm = plugin.modules();
        return new ModulesView.Context() {
            public String tr(String key, Object... vars) { return i18n.tr(s, key, vars); }
            public ModuleManager.Status status(String id) { return mm.status(id); }
            public ModuleManager.Problem problem(String id) { return mm.problem(id); }
            public List<String> dependencies(String id) {
                Module m = mm.byId(id);
                return m == null ? List.of() : m.dependencies();
            }
            public boolean console() { return !(s instanceof Player); }
            public boolean canToggle() { return s.hasPermission(Perms.MODULES_TOGGLE); }
            public CatalogView catalog() { return plugin.commands().catalog(); }
        };
    }

    /**
     * /lpa modules enable|disable &lt;id&gt;: guarda el interruptor en config.yml (solo esa línea,
     * escritura atómica) y lo aplica al momento sin reiniciar ni recargar el resto.
     */
    static void toggle(MineAPI plugin, CommandSender s, String arg, boolean on) {
        var i18n = plugin.i18n();
        ModuleManager mm = plugin.modules();
        CatalogView.Mod mod = plugin.commands().catalog().get(arg);
        String id = mod == null ? null : mod.id();
        if (id == null || mm.byId(id) == null) {
            i18n.send(s, "commands.modules.unknown", "module", arg);
            return;
        }
        if (on) {
            List<String> missing = mm.missingDependencies(id);
            if (!missing.isEmpty()) {
                i18n.send(s, "commands.modules.toggle.needs", "module", id, "modules", String.join(", ", missing));
                return;
            }
        }
        boolean retry = on && plugin.isModuleFlagOn(id) && mm.status(id) == ModuleManager.Status.BROKEN;
        if (plugin.isModuleFlagOn(id) == on && !retry) {
            i18n.send(s, on ? "commands.modules.toggle.already-on" : "commands.modules.toggle.already-off", "module", id);
            return;
        }
        if (!retry) {
            try {
                List<String> alias = new ArrayList<>();
                for (Map.Entry<String, String> e : ModuleCatalog.ALIASES.entrySet()) if (e.getValue().equals(id)) alias.add(e.getKey());
                // solo cambia la línea del módulo en config.yml (comentarios y secciones intactos) y relee la cabecera
                plugin.configs().editText(text -> ModuleToggleWriter.apply(text, id, on, alias.toArray(new String[0])));
            } catch (Exception e) {
                plugin.getLogger().warning("No se pudo guardar config.yml al cambiar el módulo " + id + ": " + e.getMessage());
                i18n.send(s, "commands.modules.toggle.write-failed", "reason", String.valueOf(e.getMessage()));
                return;
            }
        }
        ModuleManager.Changes c = mm.sync();
        for (String off : c.disabled()) {
            // un módulo apagado no ocupa memoria
            plugin.configs().forget(off);
            plugin.api().addonRegistry().forget(off);
        }
        plugin.getLogger().info(s.getName() + (on ? " activó" : " desactivó") + " el módulo " + id
                + " (activados: " + c.enabled() + ", desactivados: " + c.disabled() + ").");
        if (on) {
            if (mm.isEnabled(id)) {
                ModuleManager.Problem p = mm.problem(id);
                i18n.send(s, "commands.modules.toggle.enabled", "module", id);
                if (p != null) i18n.send(s, "commands.modules.toggle.warning", "module", id, "reason", ModulesView.reason(context(plugin, s), p));
            } else {
                i18n.send(s, "commands.modules.toggle.failed", "module", id, "reason", ModulesView.reason(context(plugin, s), mm.problem(id)));
            }
        } else {
            i18n.send(s, "commands.modules.toggle.disabled", "module", id);
        }
        List<String> alsoOff = new ArrayList<>(c.disabled());
        alsoOff.remove(id);
        List<String> alsoOn = new ArrayList<>(c.enabled());
        alsoOn.remove(id);
        if (!alsoOff.isEmpty()) i18n.send(s, "commands.modules.toggle.also-disabled", "modules", String.join(", ", alsoOff));
        if (!alsoOn.isEmpty()) i18n.send(s, "commands.modules.toggle.also-enabled", "modules", String.join(", ", alsoOn));
    }
}
