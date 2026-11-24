package me.gordi.mineapi.module.stats;

import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.core.hook.PlaceholderApiHook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Placeholders de estadísticas con sintaxis {@code %token%} (scoreboard y PlaceholderAPI).
 * Las líneas se compilan una vez ({@link Line}); renderizar no usa regex ni String.replace.
 */
public final class StatText {

    private static final Pattern TOKEN = Pattern.compile("%([A-Za-z0-9_]+)%");
    private static final Pattern TOP = Pattern.compile("top_([a-z_]+)_(\\d+)_(name|value)");

    private final StatsModule stats;

    StatText(StatsModule stats) {
        this.stats = stats;
    }

    /** Pieza dinámica de una línea. */
    private interface Part {
        String value(Player p);
    }

    /** Línea precompilada. */
    public final class Line {
        private final Object[] parts; // String (ya coloreado si no hay PAPI) o Part
        private final boolean external; // quedan %x% desconocidos → PlaceholderAPI
        private final String constant;

        private Line(Object[] parts, boolean external) {
            this.parts = parts;
            this.external = external;
            this.constant = parts.length == 1 && parts[0] instanceof String s && !external ? s : null;
        }

        /** true si nunca cambia (no hace falta volver a enviarla). */
        public boolean isConstant() {
            return constant != null;
        }

        public String render(Player p) {
            if (constant != null) return constant;
            StringBuilder sb = new StringBuilder(64);
            for (Object o : parts) sb.append(o instanceof Part part ? part.value(p) : (String) o);
            if (!external) return sb.toString();
            return Text.color(PlaceholderApiHook.apply(p, sb.toString()));
        }
    }

    /** Compila una línea; los literales se colorean ya salvo que haga falta PAPI (se colorea al final). */
    public Line compile(String raw) {
        if (raw == null) raw = "";
        List<Object> parts = new ArrayList<>();
        boolean external = false;
        Matcher m = TOKEN.matcher(raw);
        StringBuilder literal = new StringBuilder();
        int last = 0;
        while (m.find()) {
            literal.append(raw, last, m.start());
            last = m.end();
            Part part = part(m.group(1).toLowerCase(Locale.ROOT));
            if (part == null) {
                literal.append(m.group());
                external = true;
                continue;
            }
            if (literal.length() > 0) {
                parts.add(literal.toString());
                literal.setLength(0);
            }
            parts.add(part);
        }
        literal.append(raw.substring(last));
        if (literal.length() > 0) parts.add(literal.toString());
        if (!external) parts.replaceAll(o -> o instanceof String s ? Text.color(s) : o);
        return new Line(parts.toArray(), external);
    }

    /** Uso puntual (sin caché): compila y renderiza. */
    public String replace(Player p, String text) {
        return compile(text).render(p);
    }

    private Part part(String token) {
        if (token.equals("player_name")) return Player::getName;
        Matcher top = TOP.matcher(token);
        if (top.matches()) {
            StatType type = StatType.parse(top.group(1));
            if (type == null) return null;
            int rank = Integer.parseInt(top.group(2));
            boolean name = top.group(3).equals("name");
            return p -> top(type, rank, name);
        }
        StatType type = StatType.parse(token);
        if (type != null && type.token.equals(token)) return p -> type.format(value(p, type));
        // Placeholders de otros módulos (weekly_*, achievements_*...): solo si están activos
        var registry = stats.plugin().placeholders();
        if (!registry.handles(token)) return null;
        return p -> {
            String v = registry.resolve(p, token);
            return v == null ? "" : v;
        };
    }

    /** Valor actual: Bukkit en vivo si es posible, si no la caché del plugin. Hilo principal. */
    double value(Player p, StatType type) {
        return type.statistic != null ? type.live(p) : stats.cached(p.getUniqueId(), type);
    }

    String top(StatType type, int rank, boolean name) {
        List<Leaderboards.Entry> top = stats.leaderboards().top(type);
        if (rank <= 0 || rank > top.size()) return "-";
        Leaderboards.Entry e = top.get(rank - 1);
        return name ? e.name : e.formatted(type);
    }

    /**
     * PlaceholderAPI: {@code %losperrisapi_<params>%}. Puede llamarse desde cualquier hilo: Bukkit
     * solo se lee en el hilo principal con el jugador conectado; si no, se usa la caché.
     */
    public String parse(OfflinePlayer player, String params) {
        if (params == null) return null;
        String p = params.toLowerCase(Locale.ROOT);
        Matcher top = TOP.matcher(p);
        if (top.matches() && p.startsWith("top_")) {
            StatType type = StatType.parse(top.group(1));
            if (type == null) return null;
            int rank;
            try {
                rank = Integer.parseInt(top.group(2));
            } catch (NumberFormatException e) {
                return null;
            }
            return top(type, rank, top.group(3).equals("name"));
        }
        if (player == null) {
            String other = stats.plugin().placeholders().resolve(null, p);
            return other != null ? other : "";
        }
        Player online = player.isOnline() && Bukkit.isPrimaryThread() ? player.getPlayer() : null;
        if (p.equals("language")) {
            Player any = player.getPlayer();
            return any != null ? stats.plugin().i18n().locale(any) : stats.plugin().i18n().defaultLanguage();
        }
        StatType type = StatType.parse(p);
        if (type == null || !type.token.equals(p)) return stats.plugin().placeholders().resolve(player, p);
        double v = online != null ? value(online, type) : stats.cached(player.getUniqueId(), type);
        return type.format(v);
    }
}
