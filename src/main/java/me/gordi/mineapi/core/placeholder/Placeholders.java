package me.gordi.mineapi.core.placeholder;

import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Placeholders que aportan los módulos (weekly_*, achievements_*...). El scoreboard y la expansión
 * de PlaceholderAPI preguntan aquí por los tokens que no son de estadísticas. Un módulo
 * desactivado no registra nada (y se borra solo al desactivarse), así que no cuesta nada.
 * Los resolvers pueden llamarse desde cualquier hilo: deben leer datos ya calculados.
 */
public final class Placeholders {

    /** Resuelve un token sin delimitadores; null si no lo reconoce. */
    @FunctionalInterface
    public interface Resolver {
        String resolve(OfflinePlayer player, String token);
    }

    private record Entry(Object owner, String prefix, Resolver resolver) {
    }

    private final List<Entry> entries = new CopyOnWriteArrayList<>();

    /** Sube con cada alta o baja: quien compila textos con variables sabe cuándo recompilar. */
    private final java.util.concurrent.atomic.AtomicInteger version = new java.util.concurrent.atomic.AtomicInteger();

    public void register(Object owner, String prefix, Resolver resolver) {
        entries.add(new Entry(owner, prefix, resolver));
        version.incrementAndGet();
    }

    public void unregisterAll(Object owner) {
        if (entries.removeIf(e -> e.owner == owner)) version.incrementAndGet();
    }

    /** Cambia cada vez que se registran o quitan variables (p. ej. al activar un addon). */
    public int version() {
        return version.get();
    }

    /** true si algún módulo activo atiende tokens con ese prefijo. */
    public boolean handles(String token) {
        for (Entry e : entries) if (token.startsWith(e.prefix)) return true;
        return false;
    }

    /** Valor del token o null si nadie lo reconoce. */
    public String resolve(OfflinePlayer player, String token) {
        for (Entry e : entries) {
            if (!token.startsWith(e.prefix)) continue;
            String v = e.resolver.resolve(player, token);
            if (v != null) return v;
        }
        return null;
    }
}
