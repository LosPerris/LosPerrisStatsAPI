package me.gordi.mineapi.module.stats;

import com.google.gson.JsonObject;
import me.gordi.mineapi.core.data.PlayerDataService;
import org.bukkit.entity.Player;

/** Copia las estadísticas vanilla activas (tracked-stats) y las calculadas al JSON del jugador. */
final class StatsContributor implements PlayerDataService.Contributor {

    private final StatsModule stats;
    private final VanillaSums sums;

    StatsContributor(StatsModule stats, VanillaSums sums) {
        this.stats = stats;
        this.sums = sums;
    }

    @Override
    public void contribute(Player player, JsonObject data) {
        if (stats.isWorldDisabled(player.getWorld())) return; // se conservan los últimos valores válidos
        for (StatType t : StatType.ALL) {
            if (!stats.isTracked(t)) continue;
            if (t == StatType.ORES_MINED) {
                sums.contributeOres(player, data);
            } else if (t == StatType.BLOCKS_PLACED) {
                sums.contributePlaced(player, data, System.currentTimeMillis());
            } else if (t.statistic == null) {
                if (!data.has(t.key)) data.addProperty(t.key, 0);
            } else if (t.decimal) {
                data.addProperty(t.key, t.stored(player.getStatistic(t.statistic)));
            } else {
                data.addProperty(t.key, player.getStatistic(t.statistic));
            }
        }
    }
}
