package me.gordi.mineapi.module.stats;

import com.google.gson.JsonObject;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.event.StatChangedEvent;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Eventos que cambian estadísticas. Bukkit actualiza sus contadores después del evento, por eso
 * se lee un tick más tarde. Solo se publica {@link StatChangedEvent} si alguien escucha.
 */
final class StatsListener implements Listener {

    private final StatsModule stats;
    private final PlayerDataService data;

    StatsListener(StatsModule stats, PlayerDataService data) {
        this.stats = stats;
        this.data = data;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (stats.isWorldDisabled(victim.getWorld())) return;
        Player killer = victim.getKiller();
        if (stats.trackDeathCauses()) countCause(victim, killer);
        stats.later(() -> {
            if (victim.isOnline() && stats.isTracked(StatType.DEATHS)) changed(victim, StatType.DEATHS);
            if (killer != null && killer.isOnline() && stats.isTracked(StatType.PLAYER_KILLS)) changed(killer, StatType.PLAYER_KILLS);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobKill(EntityDeathEvent event) {
        if (event.getEntityType() == EntityType.PLAYER) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !stats.isTracked(StatType.MOB_KILLS) || stats.isWorldDisabled(killer.getWorld())) return;
        stats.later(() -> {
            if (killer.isOnline()) changed(killer, StatType.MOB_KILLS);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!stats.isTracked(StatType.BLOCKS_BROKEN) || stats.isWorldDisabled(event.getBlock().getWorld())) return;
        Player p = event.getPlayer();
        int[] value = new int[1];
        data.mutate(p, json -> {
            value[0] = (int) PlayerDataService.num(json, StatType.BLOCKS_BROKEN.key) + 1;
            json.addProperty(StatType.BLOCKS_BROKEN.key, value[0]);
        });
        stats.post(p, StatType.BLOCKS_BROKEN, value[0]);
    }

    private void countCause(Player victim, Player killer) {
        EntityDamageEvent last = victim.getLastDamageCause();
        String cause = last == null ? null : last.getCause().name();
        boolean byEntity = last instanceof EntityDamageByEntityEvent;
        String category = DeathCause.categorize(cause, killer != null && killer != victim, byEntity);
        data.mutate(victim, json -> {
            JsonObject causes = json.getAsJsonObject(DeathCause.KEY);
            if (causes == null) json.add(DeathCause.KEY, causes = new JsonObject());
            causes.addProperty(category, (long) PlayerDataService.num(causes, category) + 1);
        });
    }

    /** Resets pendientes de estadísticas calculadas (jugador desconectado al resetear). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        stats.applyPendingReset(event.getPlayer());
    }

    /** Antes de que el núcleo guarde (MONITOR): avisa del tiempo jugado final y fuerza las sumas. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        stats.sums().invalidate(p.getUniqueId()); // el guardado final recalcula blocks_placed
        stats.plugin().scheduler().later(2L, () -> stats.sums().forget(p.getUniqueId()));
        if (!stats.isTracked(StatType.HOURS_PLAYED) || stats.isWorldDisabled(p.getWorld())) return;
        stats.post(p, StatType.HOURS_PLAYED, (int) StatType.HOURS_PLAYED.live(p));
    }

    private void changed(Player p, StatType type) {
        data.sync(p);
        stats.post(p, type, p.getStatistic(type.statistic));
    }
}
