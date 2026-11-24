package me.gordi.mineapi.module.afk;

import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.module.AbstractModule;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Detecta jugadores AFK (afk-timeout). Los AFK no reciben hitos por tiempo jugado. */
public final class AfkModule extends AbstractModule implements Listener {

    public static final String ID = "afk";

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private long timeoutMillis;

    public AfkModule(MineAPI plugin) {
        super(plugin, ID);
    }

    @Override
    protected void onEnable() {
        timeoutMillis = Math.max(1L, Durations.seconds(config(), 300L, 1, "timeout")) * 1000L;
        long now = System.currentTimeMillis();
        for (Player p : plugin.getServer().getOnlinePlayers()) lastActivity.put(p.getUniqueId(), now);
        listen(this);
    }

    @Override
    protected void onDisable() {
        lastActivity.clear();
    }

    public boolean isAfk(Player player) {
        Long last = lastActivity.get(player.getUniqueId());
        return last != null && System.currentTimeMillis() - last > timeoutMillis;
    }

    private void touch(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) { touch(e.getPlayer()); }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { lastActivity.remove(e.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) { touch(e.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInteract(PlayerInteractEvent e) { touch(e.getPlayer()); }

    /** Solo cuenta al cambiar de bloque (barato: tres comparaciones de int). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()) {
            touch(e.getPlayer());
        }
    }
}
