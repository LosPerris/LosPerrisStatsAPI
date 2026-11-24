package me.gordi.mineapi.core.command;


import java.util.ArrayList;
import java.util.List;

/**
 * Construye el texto de /lpa help (sin Bukkit, se prueba con JUnit). Solo se construye cuando
 * alguien lo pide. Builds /lpa help lines; only commands the sender can use from active modules.
 */
public final class HelpView {

    /** Lo que la vista necesita saber del que pregunta. */
    public interface Context {
        String tr(String key, Object... vars);

        boolean has(String permission);

        /** true si el módulo está activo ({@link HelpCatalog#CORE} siempre lo está). */
        boolean on(String module);

        /** true en la consola: sin hover ni clic. */
        boolean console();

        /** Comando y módulos del plugin (por defecto, el núcleo). */
        default CatalogView catalog() { return CatalogView.CORE; }
    }

    /**
     * Una línea de chat. {@code hover} y {@code command} pueden ser null.
     * @param run true = el clic ejecuta el comando; false = lo escribe en el chat
     */
    public record Line(String text, String hover, String command, boolean run) {
        static Line of(String text) { return new Line(text, null, null, false); }
    }

    private HelpView() {
    }

    /** Ayuda general (comandos de jugador) o la de un tema: {@code admin} o un id de módulo. */
    public static List<Line> build(Context c, String version, String topic) {
        if (topic == null || topic.isBlank()) return main(c, version);
        if (topic.equalsIgnoreCase("admin")) return admin(c, version);
        CatalogView.Mod info = c.catalog().get(topic);
        if (info == null) return List.of(Line.of(c.tr("help.unknown-topic", "module", topic)));
        return module(c, version, info);
    }

    private static List<Line> main(Context c, String version) {
        List<Line> out = header(c, version, "help.title");
        boolean any = false;
        for (String cat : c.catalog().categories()) {
            any |= group(c, out, c.tr("module-info.category." + cat), entries(c, cat, false));
        }
        any |= group(c, out, c.tr("module-info.category.general"), visible(c, c.catalog().help(HelpCatalog.CORE), false));
        if (!any) out.add(Line.of(c.tr("help.none")));
        if (hasAny(c, true)) {
            out.add(c.console() ? Line.of(c.tr("help.admin-link"))
                    : new Line(c.tr("help.admin-link"), c.tr("help.admin-link-hover"), CatalogView.COMMAND + " help admin", true));
        }
        out.add(Line.of(c.tr("help.detail")));
        out.add(Line.of(c.tr("help.footer")));
        return out;
    }

    private static List<Line> admin(Context c, String version) {
        List<Line> out = header(c, version, "help.admin-title");
        boolean any = false;
        for (String cat : c.catalog().categories()) {
            any |= group(c, out, c.tr("module-info.category." + cat), entries(c, cat, true));
        }
        any |= group(c, out, c.tr("module-info.category.general"), visible(c, c.catalog().help(HelpCatalog.CORE), true));
        if (!any) out.add(Line.of(c.tr("help.none-admin")));
        out.add(Line.of(c.tr("help.footer")));
        return out;
    }

    private static List<Line> module(Context c, String version, CatalogView.Mod info) {
        List<Line> out = new ArrayList<>();
        out.add(Line.of(c.tr("help.module-title", "module", info.id(), "description", c.tr("module-info." + info.id() + ".name"))));
        out.add(Line.of(c.tr("help.module-desc", "description", c.tr("module-info." + info.id() + ".desc"))));
        if (!c.on(info.id())) out.add(Line.of(c.tr("help.module-off")));
        List<HelpCatalog.Entry> list = new ArrayList<>();
        for (HelpCatalog.Entry e : c.catalog().help(info.id())) if (e.permission() == null || c.has(e.permission())) list.add(e);
        if (list.isEmpty()) out.add(Line.of(c.tr("help.no-commands")));
        for (HelpCatalog.Entry e : list) out.add(line(c, e));
        if (info.where() != null) out.add(Line.of(c.tr("help.module-file", "file", info.where())));
        out.add(Line.of(c.tr("help.footer")));
        return out;
    }

    private static List<Line> header(Context c, String version, String key) {
        List<Line> out = new ArrayList<>();
        out.add(Line.of(c.tr(key, "version", version)));
        if (!c.console()) out.add(Line.of(c.tr("help.hint")));
        return out;
    }

    private static List<HelpCatalog.Entry> entries(Context c, String cat, boolean admin) {
        List<HelpCatalog.Entry> out = new ArrayList<>();
        for (CatalogView.Mod i : c.catalog().in(cat)) {
            if (!c.on(i.id())) continue;
            out.addAll(visible(c, c.catalog().help(i.id()), admin));
        }
        return out;
    }

    private static List<HelpCatalog.Entry> visible(Context c, List<HelpCatalog.Entry> list, boolean admin) {
        List<HelpCatalog.Entry> out = new ArrayList<>();
        for (HelpCatalog.Entry e : list) {
            if (e.admin() == admin && (e.permission() == null || c.has(e.permission()))) out.add(e);
        }
        return out;
    }

    private static boolean hasAny(Context c, boolean admin) {
        for (HelpCatalog.Entry e : c.catalog().help()) {
            if (e.admin() == admin && c.on(e.module()) && (e.permission() == null || c.has(e.permission()))) return true;
        }
        return false;
    }

    private static boolean group(Context c, List<Line> out, String title, List<HelpCatalog.Entry> list) {
        if (list.isEmpty()) return false;
        out.add(Line.of(c.tr("help.category", "category", title)));
        for (HelpCatalog.Entry e : list) out.add(line(c, e));
        return true;
    }

    private static Line line(Context c, HelpCatalog.Entry e) {
        String base = "help.cmd." + e.key() + ".";
        String usage = c.tr(base + "usage");
        String text = c.tr("help.line", "usage", usage, "description", c.tr(base + "desc"));
        if (c.console()) return Line.of(text);
        String hover = c.tr("help.hover", "usage", usage, "description", c.tr(base + "info"),
                "permission", e.permission() == null ? c.tr("help.everyone") : e.permission());
        return new Line(text, hover, e.suggest(), false);
    }
}
