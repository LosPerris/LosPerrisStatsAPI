package me.gordi.mineapi.core.event;

import org.bukkit.entity.Player;

/** Los datos del jugador ya están en caché (hilo principal, tras entrar). */
public record PlayerDataReadyEvent(Player player) {
}
