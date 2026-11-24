package me.gordi.mineapi.core.event;

import org.bukkit.entity.Player;

/** Una estadística de un jugador conectado cambió (hilo principal). value = valor nuevo entero. */
public record StatChangedEvent(Player player, String stat, int value) {
}
