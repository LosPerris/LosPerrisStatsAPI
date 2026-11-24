package me.gordi.mineapi.core.event;

import org.bukkit.entity.Player;

/** Un jugador alcanzó un hito de settings/milestones.yml (hilo principal). message = texto plano del anuncio. */
public record MilestoneReachedEvent(Player player, String stat, int value, String message) {
}
