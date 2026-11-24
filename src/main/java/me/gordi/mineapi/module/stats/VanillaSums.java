package me.gordi.mineapi.module.stats;

import com.google.gson.JsonObject;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Estadísticas calculadas a partir de las vanilla: bloques colocados (suma de USE_ITEM de cada
 * material que es bloque) y minerales (MINE_BLOCK de cada mena, con desglose por tipo).
 *
 * <p>Rendimiento: la lista de materiales se calcula una sola vez. Los minerales son ~19 lecturas
 * (baratas) y se actualizan en cada sincronización; los bloques colocados recorren ~800 materiales,
 * así que se recalculan como mucho una vez cada {@code computed-stats-interval} segundos por jugador
 * (y siempre al salir). Solo hilo principal.</p>
 */
final class VanillaSums {

    /** Tipo de mineral → nombres de material (se ignoran los que no existan en la versión). */
    static final Map<String, String[]> ORE_GROUPS = new LinkedHashMap<>();

    static {
        ORE_GROUPS.put("coal", new String[]{"COAL_ORE", "DEEPSLATE_COAL_ORE"});
        ORE_GROUPS.put("iron", new String[]{"IRON_ORE", "DEEPSLATE_IRON_ORE"});
        ORE_GROUPS.put("copper", new String[]{"COPPER_ORE", "DEEPSLATE_COPPER_ORE"});
        ORE_GROUPS.put("gold", new String[]{"GOLD_ORE", "DEEPSLATE_GOLD_ORE", "NETHER_GOLD_ORE"});
        ORE_GROUPS.put("redstone", new String[]{"REDSTONE_ORE", "DEEPSLATE_REDSTONE_ORE"});
        ORE_GROUPS.put("lapis", new String[]{"LAPIS_ORE", "DEEPSLATE_LAPIS_ORE"});
        ORE_GROUPS.put("diamond", new String[]{"DIAMOND_ORE", "DEEPSLATE_DIAMOND_ORE"});
        ORE_GROUPS.put("emerald", new String[]{"EMERALD_ORE", "DEEPSLATE_EMERALD_ORE"});
        ORE_GROUPS.put("quartz", new String[]{"NETHER_QUARTZ_ORE"});
        ORE_GROUPS.put("ancient_debris", new String[]{"ANCIENT_DEBRIS"});
    }

    /** Clave JSON del desglose de minerales. */
    static final String ORES_KEY = "ores";

    private final String[] oreKeys;
    private final Material[][] ores;
    private Material[] placeable;
    private final Map<UUID, Long> lastPlaced = new HashMap<>();
    private long placedIntervalMs = 60_000L;

    VanillaSums() {
        oreKeys = ORE_GROUPS.keySet().toArray(new String[0]);
        ores = new Material[oreKeys.length][];
        for (int i = 0; i < oreKeys.length; i++) {
            List<Material> found = new ArrayList<>();
            for (String name : ORE_GROUPS.get(oreKeys[i])) {
                Material m = Material.getMaterial(name);
                if (m != null) found.add(m);
            }
            ores[i] = found.toArray(new Material[0]);
        }
    }

    void setPlacedInterval(long seconds) {
        placedIntervalMs = Math.max(0L, seconds) * 1000L;
    }

    /** Fuerza el recálculo completo en la próxima sincronización (al salir, tras un reset...). */
    void invalidate(UUID uuid) {
        lastPlaced.remove(uuid);
    }

    void forget(UUID uuid) {
        lastPlaced.remove(uuid);
    }

    /** Escribe ores_mined + desglose "ores". */
    void contributeOres(Player p, JsonObject data) {
        JsonObject breakdown = new JsonObject();
        long total = 0;
        for (int i = 0; i < oreKeys.length; i++) {
            long sum = 0;
            for (Material m : ores[i]) sum += p.getStatistic(Statistic.MINE_BLOCK, m);
            breakdown.addProperty(oreKeys[i], sum);
            total += sum;
        }
        data.add(ORES_KEY, breakdown);
        data.addProperty(StatType.ORES_MINED.key, total);
    }

    /** Escribe blocks_placed si toca (throttle por jugador). */
    void contributePlaced(Player p, JsonObject data, long now) {
        Long last = lastPlaced.get(p.getUniqueId());
        if (last != null && now - last < placedIntervalMs && data.has(StatType.BLOCKS_PLACED.key)) return;
        lastPlaced.put(p.getUniqueId(), now);
        data.addProperty(StatType.BLOCKS_PLACED.key, placed(p));
    }

    long placed(Player p) {
        Material[] list = placeable();
        long sum = 0;
        for (Material m : list) {
            try {
                sum += p.getStatistic(Statistic.USE_ITEM, m);
            } catch (IllegalArgumentException e) {
                prune(m); // material que esta versión no acepta: se quita para siempre
            }
        }
        return sum;
    }

    /** Pone a cero las estadísticas vanilla de origen (reset de un jugador conectado). */
    void reset(Player p, boolean placedToo, boolean oresToo) {
        if (placedToo) {
            for (Material m : placeable()) {
                try {
                    if (p.getStatistic(Statistic.USE_ITEM, m) != 0) p.setStatistic(Statistic.USE_ITEM, m, 0);
                } catch (IllegalArgumentException ignored) {
                    // material no válido para la estadística
                }
            }
        }
        if (oresToo) {
            for (Material[] group : ores) for (Material m : group) p.setStatistic(Statistic.MINE_BLOCK, m, 0);
        }
        invalidate(p.getUniqueId());
    }

    private Material[] placeable() {
        Material[] list = placeable;
        if (list == null) {
            List<Material> out = new ArrayList<>();
            for (Material m : Material.values()) {
                if (m.name().startsWith("LEGACY_") || m.isAir()) continue;
                if (m.isBlock() && m.isItem()) out.add(m);
            }
            list = placeable = out.toArray(new Material[0]);
        }
        return list;
    }

    private void prune(Material bad) {
        List<Material> out = new ArrayList<>(placeable.length);
        for (Material m : placeable) if (m != bad) out.add(m);
        placeable = out.toArray(new Material[0]);
    }

    /** Tipos de mineral en orden (para GUI y dashboard). */
    static String[] oreKeys() {
        return ORE_GROUPS.keySet().toArray(new String[0]);
    }
}
