package me.gordi.mineapi.core.command;

import me.gordi.mineapi.core.module.ModuleCatalog;
import me.gordi.mineapi.core.module.ModuleManager;

import java.util.ArrayList;
import java.util.List;

/** Construye /lpa modules y /lpa modules &lt;id&gt; (sin Bukkit). Builds the modules list and detail. */
public final class ModulesView {

    public interface Context {
        String tr(String key, Object... vars);

        ModuleManager.Status status(String id);

        ModuleManager.Problem problem(String id);

        List<String> dependencies(String id);

        boolean console();

        boolean canToggle();

        /** Comando y módulos del plugin (por defecto, el núcleo). */
        default CatalogView catalog() { return CatalogView.CORE; }
    }

    private ModulesView() {
    }

    /** Cada elemento es una línea de chat formada por varias partes. */
    public static List<List<HelpView.Line>> list(Context c) {
        List<List<HelpView.Line>> out = new ArrayList<>();
        int on = 0;
        List<String> broken = new ArrayList<>();
        List<CatalogView.Mod> all = c.catalog().all();
        for (CatalogView.Mod i : all) {
            ModuleManager.Status st = c.status(i.id());
            if (st == ModuleManager.Status.ON) on++;
            if (st == ModuleManager.Status.BROKEN) broken.add(i.id());
        }
        out.add(List.of(text(c.tr("commands.modules.header", "count", on, "total", all.size()))));
        for (String cat : c.catalog().categories()) {
            List<CatalogView.Mod> mods = c.catalog().in(cat);
            if (mods.isEmpty()) continue;
            List<HelpView.Line> row = new ArrayList<>();
            row.add(text(c.tr("commands.modules.category", "category", c.tr("module-info.category." + cat))));
            boolean first = true;
            for (CatalogView.Mod i : mods) {
                if (!first) row.add(text(c.tr("commands.modules.separator")));
                first = false;
                ModuleManager.Status st = c.status(i.id());
                String label = c.tr("commands.modules." + key(st), "module", i.id());
                String hover = c.tr("commands.modules.hover", "module", c.tr("module-info." + i.id() + ".name"),
                        "description", c.tr("module-info." + i.id() + ".desc"), "value", statusText(c, i.id(), st));
                row.add(c.console() ? text(label) : new HelpView.Line(label, hover, CatalogView.COMMAND + " modules " + i.id(), true));
            }
            out.add(row);
        }
        for (String id : broken) {
            out.add(List.of(text(c.tr("commands.modules.broken-line", "module", id, "reason", reason(c, c.problem(id))))));
        }
        out.add(List.of(text(c.tr(c.console() ? "commands.modules.hint-console" : "commands.modules.hint"))));
        return out;
    }

    public static List<List<HelpView.Line>> detail(Context c, ModuleCatalog.Info i) {
        return detail(c, CatalogView.of(i));
    }

    public static List<List<HelpView.Line>> detail(Context c, CatalogView.Mod i) {
        List<List<HelpView.Line>> out = new ArrayList<>();
        String id = i.id();
        ModuleManager.Status st = c.status(id);
        String d = "commands.modules.detail.";
        add(out, c.tr(d + "title", "module", id, "description", c.tr("module-info." + id + ".name")));
        add(out, c.tr(d + "status", "value", statusText(c, id, st)));
        add(out, c.tr(d + "desc", "description", c.tr("module-info." + id + ".desc")));
        add(out, c.tr(d + "category", "category", c.tr("module-info.category." + i.category())));
        add(out, i.where() == null ? c.tr(d + "no-file") : c.tr(d + "file", "file", i.where()));
        add(out, i.commands().isEmpty() ? c.tr(d + "no-commands")
                : c.tr(d + "commands", "value", CatalogView.COMMAND + " " + String.join(", " + CatalogView.COMMAND + " ", i.commands())));
        if (!i.permissions().isEmpty()) add(out, c.tr(d + "permissions", "permission", String.join(", ", i.permissions())));
        if (i.requires() != null) add(out, c.tr(d + "requires", "value", i.requires()));
        List<String> deps = c.dependencies(id);
        if (!deps.isEmpty()) add(out, c.tr(d + "dependencies", "modules", String.join(", ", deps)));
        add(out, c.tr(d + "guide", "module", id, "url", c.catalog().guide(id)));
        if (c.canToggle()) {
            boolean enabled = st != ModuleManager.Status.OFF;
            String cmd = CatalogView.COMMAND + " modules " + (enabled ? "disable " : "enable ") + id;
            String label = c.tr(d + (enabled ? "disable-button" : "enable-button"));
            out.add(List.of(c.console()
                    ? text(c.tr(d + "toggle-console", "command", cmd))
                    : new HelpView.Line(label, c.tr(d + "toggle-hover", "command", cmd), cmd, true)));
        }
        return out;
    }

    static String key(ModuleManager.Status st) {
        return switch (st) {
            case ON -> "on";
            case OFF -> "off";
            case BROKEN -> "broken";
        };
    }

    static String statusText(Context c, String id, ModuleManager.Status st) {
        if (st == ModuleManager.Status.BROKEN) return c.tr("commands.modules.status.broken", "reason", reason(c, c.problem(id)));
        return c.tr("commands.modules.status." + key(st));
    }

    /** Texto del motivo en el idioma del que pregunta. */
    public static String reason(Context c, ModuleManager.Problem p) {
        if (p == null) p = new ModuleManager.Problem("unknown", null);
        String arg = p.arg() == null ? "" : p.arg();
        return c.tr("commands.modules.reason." + p.key(), "module", arg, "reason", arg);
    }

    private static void add(List<List<HelpView.Line>> out, String s) {
        out.add(List.of(text(s)));
    }

    private static HelpView.Line text(String s) {
        return new HelpView.Line(s, null, null, false);
    }
}
