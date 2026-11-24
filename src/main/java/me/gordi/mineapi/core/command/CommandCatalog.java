package me.gordi.mineapi.core.command;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lo que muestran /lpa help y /lpa modules: los módulos y la ayuda del núcleo y, detrás, los de cada addon
 * registrado (con sus grupos), más las líneas de ayuda sueltas que añada un addon. Core catalog plus addons.
 */
public final class CommandCatalog implements CatalogView {

    private record Part(Object owner, CatalogView view) {
    }

    private record Help(Object owner, HelpCatalog.Entry entry) {
    }

    private final List<Part> parts = new CopyOnWriteArrayList<>();
    private final List<Help> extra = new CopyOnWriteArrayList<>();

    /** Módulos, grupos y ayuda de un addon. */
    public void add(Object owner, CatalogView view) {
        parts.add(new Part(owner, view));
    }

    /** Una línea de ayuda suelta (p. ej. de un comando sin módulo). */
    public void help(Object owner, HelpCatalog.Entry entry) {
        extra.add(new Help(owner, entry));
    }

    public void unregisterAll(Object owner) {
        parts.removeIf(p -> p.owner() == owner);
        extra.removeIf(h -> h.owner() == owner);
    }

    @Override
    public List<String> categories() {
        List<String> out = new ArrayList<>(CORE.categories());
        for (Part p : parts) for (String c : p.view().categories()) if (!out.contains(c)) out.add(c);
        return out;
    }

    @Override
    public List<Mod> all() {
        List<Mod> out = new ArrayList<>(CORE.all());
        for (Part p : parts) out.addAll(p.view().all());
        return out;
    }

    @Override
    public Mod get(String id) {
        Mod m = CORE.get(id);
        for (Part p : parts) {
            if (m != null) break;
            m = p.view().get(id);
        }
        return m;
    }

    @Override
    public List<HelpCatalog.Entry> help() {
        List<HelpCatalog.Entry> out = new ArrayList<>(CORE.help());
        for (Part p : parts) out.addAll(p.view().help());
        for (Help h : extra) out.add(h.entry());
        return out;
    }

    @Override
    public String guide() {
        return CORE.guide();
    }

    @Override
    public String guide(String module) {
        for (Part p : parts) if (p.view().get(module) != null) return p.view().guide();
        return guide();
    }

    /** Ids de todos los módulos (núcleo y addons), en orden. */
    public List<String> ids() {
        List<String> out = new ArrayList<>();
        for (Mod m : all()) out.add(m.id());
        return out;
    }
}
