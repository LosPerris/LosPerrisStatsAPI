package me.gordi.mineapi.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Implementación de {@link MenuApi}: guarda las páginas; el módulo gui las abre. */
public final class MenuRegistry implements MenuApi {

    public record Entry(Object owner, Page page) {
    }

    /** El módulo gui en marcha. */
    public interface Opener {
        boolean open(Player viewer, Player target, String pageId, int page);
    }

    private final List<Entry> pages = new CopyOnWriteArrayList<>();
    private volatile Opener opener;

    @Override
    public void page(Object owner, Page page) {
        pages.removeIf(e -> e.page().id().equals(page.id()));
        pages.add(new Entry(owner, page));
    }

    @Override
    public boolean open(Player viewer, Player target, String pageId, int page) {
        Opener o = opener;
        return o != null && o.open(viewer, target, pageId, page);
    }

    public void unregisterAll(Object owner) {
        pages.removeIf(e -> e.owner() == owner);
    }

    public void opener(Opener o) {
        opener = o;
    }

    public Page find(String id) {
        for (Entry e : pages) if (e.page().id().equals(id)) return e.page();
        return null;
    }

    public List<Page> pages() {
        return pages.stream().map(Entry::page).toList();
    }
}
