package me.gordi.mineapi.api;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Implementación de {@link WebApi}. Lo registrado queda aquí y los módulos web/public-page se
 * "enganchan" ({@link #attach}) para servirlo; así sobrevive a recargas del servidor web.
 */
public final class WebRegistry implements WebApi {

    public record ReadEntry(Object owner, String path, String section, Handler handler) {
    }

    public record AdminEntry(Object owner, String method, String path, Handler handler) {
    }

    /** Recibe altas y bajas de endpoints de lectura (WebModule, PublicModule). */
    public interface Sink {
        void added(ReadEntry e);

        void removed(ReadEntry e);
    }

    /** Lo que hace falta del módulo web en marcha. */
    public interface Live {
        void notify(String kind, String user, String message, String level);

        void invalidate(String player);
    }

    private record Owned<T>(Object owner, T value) {
    }

    private final List<ReadEntry> reads = new CopyOnWriteArrayList<>();
    private final Map<String, AdminEntry> admins = new ConcurrentHashMap<>();
    private final List<Owned<ProfileContributor>> profiles = new CopyOnWriteArrayList<>();
    private final List<Owned<String>> hidden = new CopyOnWriteArrayList<>();
    private final Map<String, Owned<BooleanSupplier>> flags = new ConcurrentHashMap<>();
    private final List<Sink> sinks = new CopyOnWriteArrayList<>();
    private volatile Live live;

    static String clean(String path) {
        String p = path.trim();
        while (p.startsWith("/")) p = p.substring(1);
        while (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        if (p.isEmpty()) throw new IllegalArgumentException("empty path");
        return p;
    }

    @Override
    public void read(Object owner, String path, String publicSection, Handler handler) {
        ReadEntry e = new ReadEntry(owner, clean(path), publicSection, handler);
        reads.add(e);
        for (Sink s : sinks) s.added(e);
    }

    @Override
    public void admin(Object owner, String method, String path, Handler handler) {
        String m = method.toUpperCase(Locale.ROOT);
        admins.put(m + " /" + clean(path), new AdminEntry(owner, m, "/" + clean(path), handler));
    }

    @Override
    public void profile(Object owner, ProfileContributor contributor) {
        profiles.add(new Owned<>(owner, contributor));
    }

    @Override
    public void hideDataKeys(Object owner, String... keys) {
        for (String k : keys) hidden.add(new Owned<>(owner, k));
    }

    @Override
    public void flag(Object owner, String id, BooleanSupplier on) {
        flags.put(id, new Owned<>(owner, on));
    }

    @Override
    public void notify(String kind, String user, String message, String level) {
        Live l = live;
        if (l != null) l.notify(kind, user, message, level);
    }

    @Override
    public void invalidate(String player) {
        Live l = live;
        if (l != null) l.invalidate(player);
    }

    public void unregisterAll(Object owner) {
        for (ReadEntry e : reads) {
            if (e.owner() != owner) continue;
            reads.remove(e);
            for (Sink s : sinks) s.removed(e);
        }
        admins.values().removeIf(a -> a.owner() == owner);
        profiles.removeIf(p -> p.owner() == owner);
        hidden.removeIf(h -> h.owner() == owner);
        flags.values().removeIf(f -> f.owner() == owner);
    }

    // ------------------------------------------------------------------ uso interno del núcleo

    /** Engancha un receptor y le pasa lo ya registrado. */
    public void attach(Sink sink) {
        sinks.add(sink);
        for (ReadEntry e : reads) sink.added(e);
    }

    public void detach(Sink sink) {
        sinks.remove(sink);
    }

    public void live(Live l) {
        live = l;
    }

    public AdminEntry admin(String method, String action) {
        return admins.get(method + " " + action);
    }

    /** Hay algún endpoint de admin con esa ruta (con otro método). */
    public boolean hasAdminPath(String action) {
        for (AdminEntry a : admins.values()) if (a.path().equals(action)) return true;
        return false;
    }

    public List<ReadEntry> reads() {
        return List.copyOf(reads);
    }

    public void contribute(String name, UUID uuid, String lang, JsonObject out, Consumer<String> warn) {
        for (Owned<ProfileContributor> p : profiles) {
            try {
                p.value().contribute(name, uuid, lang, out);
            } catch (RuntimeException e) {
                warn.accept("Perfil (" + p.owner().getClass().getSimpleName() + "): " + e);
            }
        }
    }

    public Set<String> hiddenKeys() {
        if (hidden.isEmpty()) return Set.of();
        Set<String> s = new java.util.HashSet<>();
        for (Owned<String> h : hidden) s.add(h.value());
        return s;
    }

    public List<Map.Entry<String, Boolean>> flags() {
        List<Map.Entry<String, Boolean>> out = new ArrayList<>(flags.size());
        for (Map.Entry<String, Owned<BooleanSupplier>> f : flags.entrySet()) {
            out.add(Map.entry(f.getKey(), f.getValue().value().getAsBoolean()));
        }
        return out;
    }
}
