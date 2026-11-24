package me.gordi.mineapi.core.event;

import java.util.UUID;

/** Las estadísticas de un jugador se resetearon (hilo principal). */
public record PlayerResetEvent(UUID uuid, String name) {
}
