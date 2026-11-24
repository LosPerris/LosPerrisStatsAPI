package me.gordi.mineapi.module.milestones;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.data.PlayerDataService;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Galería de logros (parte de los hitos): cada nivel de settings/milestones.yml es un logro
 * ({@code <stat>:<valor>}). Cada {@code gallery.update-interval} (hilo asíncrono) calcula qué % de
 * jugadores lo tiene y su rareza. Sin coste por evento: usa el campo {@code milestones} y las stats
 * ya guardadas. Antes era el módulo "achievements" (v1.3).
 */
public final class MilestoneGallery {

    /** Definición de un logro. */
    public record Def(String id, String stat, int value, String message) {
    }

    private record Gallery(List<Def> defs, int[] owners, int players) {
        static final Gallery EMPTY = new Gallery(List.of(), new int[0], 0);
    }

    private final MineAPI plugin;
    private final JsonObject info;
    private final Rarity.Thresholds thresholds;
    private volatile Gallery gallery = Gallery.EMPTY;

    /** {@code section}: {@code gallery} de milestones.yml (tiers.*). {@code info}: hitos configurados. */
    MilestoneGallery(MineAPI plugin, JsonObject info, ConfigurationSection section) {
        this.plugin = plugin;
        this.info = info;
        thresholds = section == null ? Rarity.Thresholds.DEFAULT : new Rarity.Thresholds(
                section.getDouble("tiers.uncommon", 50),
                section.getDouble("tiers.rare", 20),
                section.getDouble("tiers.epic", 5),
                section.getDouble("tiers.legendary", 1));
    }

    /** Placeholders {@code achievements_obtained|total|percent}. */
    String placeholder(org.bukkit.OfflinePlayer p, String token) {
        if (p == null) return "";
        int[] c = counts(p.getUniqueId());
        return switch (token) {
            case "achievements_obtained" -> String.valueOf(c[0]);
            case "achievements_total" -> String.valueOf(c[1]);
            case "achievements_percent" -> String.valueOf(Rarity.percent(c[0], c[1]));
            default -> null;
        };
    }

    private List<Def> defs() {
        List<Def> out = new ArrayList<>();
        for (Map.Entry<String, JsonElement> e : info.entrySet()) {
            for (JsonElement le : e.getValue().getAsJsonArray()) {
                JsonObject lvl = le.getAsJsonObject();
                int value = lvl.get("value").getAsInt();
                String msg = lvl.has("message") ? lvl.get("message").getAsString() : "";
                out.add(new Def(e.getKey() + ":" + value, e.getKey(), value, msg));
            }
        }
        return out;
    }

    /** ¿Tiene el logro? Hito registrado o valor actual ya por encima (mismo criterio que el perfil). */
    static boolean has(JsonObject json, Def d) {
        if (json.has("milestones") && json.get("milestones").isJsonObject()) {
            JsonObject m = json.getAsJsonObject("milestones");
            if (m.has(d.stat) && m.get(d.stat).getAsDouble() >= d.value) return true;
        }
        return PlayerDataService.num(json, d.stat) >= d.value;
    }

    /** Recalcula la rareza (hilo asíncrono). */
    void recompute() {
        List<Def> defs = defs();
        int[] owners = new int[defs.size()];
        int[] players = {0};
        plugin.data().forEach(json -> {
            players[0]++;
            for (int i = 0; i < defs.size(); i++) if (has(json, defs.get(i))) owners[i]++;
        });
        gallery = new Gallery(List.copyOf(defs), owners, players[0]);
    }

    /** [obtenidos, total] de un jugador. */
    private int[] counts(UUID uuid) {
        Gallery g = gallery;
        Integer got = plugin.data().read(uuid, json -> {
            if (json == null) return 0;
            int n = 0;
            for (Def d : g.defs) if (has(json, d)) n++;
            return n;
        });
        return new int[]{got == null ? 0 : got, g.defs.size()};
    }

    /** Logros obtenidos por un jugador (para la GUI). */
    public List<String> obtained(UUID uuid) {
        Gallery g = gallery;
        List<String> out = plugin.data().read(uuid, json -> {
            List<String> l = new ArrayList<>();
            if (json != null) for (Def d : g.defs) if (has(json, d)) l.add(d.id);
            return l;
        });
        return out == null ? List.of() : out;
    }

    public List<Def> definitions() {
        return gallery.defs;
    }

    /** Rareza de un logro (o COMMON si no existe). */
    public Rarity.Tier tier(String id) {
        Gallery g = gallery;
        for (int i = 0; i < g.defs.size(); i++) {
            if (g.defs.get(i).id.equals(id)) return Rarity.tier(Rarity.percent(g.owners[i], g.players), thresholds);
        }
        return Rarity.Tier.COMMON;
    }

    public double percent(String id) {
        Gallery g = gallery;
        for (int i = 0; i < g.defs.size(); i++) {
            if (g.defs.get(i).id.equals(id)) return Rarity.percent(g.owners[i], g.players);
        }
        return 0;
    }

    /**
     * JSON de /api/achievements: {players, achievements:[{id, stat, value, message, owners,
     * percent, tier}]}; con {@code playerName} añade {player:{name, obtained:[id], count, total}}.
     */
    public JsonObject json(String playerName) {
        Gallery g = gallery;
        JsonObject out = new JsonObject();
        out.addProperty("players", g.players);
        JsonArray arr = new JsonArray();
        for (int i = 0; i < g.defs.size(); i++) {
            Def d = g.defs.get(i);
            double pct = Rarity.percent(g.owners[i], g.players);
            JsonObject o = new JsonObject();
            o.addProperty("id", d.id);
            o.addProperty("stat", d.stat);
            o.addProperty("value", d.value);
            o.addProperty("message", d.message);
            o.addProperty("owners", g.owners[i]);
            o.addProperty("percent", pct);
            o.addProperty("tier", Rarity.tier(pct, thresholds).name().toLowerCase(Locale.ROOT));
            arr.add(o);
        }
        out.add("achievements", arr);
        if (playerName != null) {
            JsonObject me = plugin.data().read(playerName, json -> {
                if (json == null) return null;
                JsonObject p = new JsonObject();
                String n = PlayerDataService.str(json, "name");
                p.addProperty("name", n == null ? playerName : n);
                JsonArray got = new JsonArray();
                for (Def d : g.defs) if (has(json, d)) got.add(d.id);
                p.add("obtained", got);
                p.addProperty("count", got.size());
                p.addProperty("total", g.defs.size());
                return p;
            });
            if (me != null) out.add("player", me);
        }
        return out;
    }
}
