package me.gordi.mineapi.core.data;

import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.event.PlayerDataReadyEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Ciclo de vida de los datos del jugador: precarga, listo y guardado al salir. */
public final class PlayerDataListener implements Listener {

    private final MineAPI plugin;
    private final PlayerDataService data;

    public PlayerDataListener(MineAPI plugin, PlayerDataService data) {
        this.plugin = plugin;
        this.data = data;
    }

    /** Hilo asíncrono de Bukkit: la E/S no toca el hilo principal. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        data.preload(event.getUniqueId(), event.getName());
    }

    /** LOW: antes que los módulos (NORMAL), para que encuentren los datos listos. */
    @EventHandler(priority = EventPriority.LOW)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        if (data.isLoaded(p.getUniqueId())) ready(p);
        else data.loadAsync(p, this::ready);
    }

    /** Llamado también por MineAPI para los jugadores ya conectados tras un /reload. */
    public void ready(Player p) {
        String locale = data.read(p.getUniqueId(), json -> json == null ? null : PlayerDataService.str(json, "locale"));
        plugin.i18n().setOverride(p.getUniqueId(), locale);
        data.sync(p);
        plugin.events().post(new PlayerDataReadyEvent(p));
    }

    /** MONITOR: después de los módulos, que pueden haber cambiado datos al salir. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        data.savePlayer(p, true);
        data.unload(p.getUniqueId());
        plugin.i18n().forget(p.getUniqueId());
        // Un tick después para que el jugador ya no cuente como conectado
        plugin.scheduler().later(1L, () -> data.saveServerInfo(true));
    }
}
