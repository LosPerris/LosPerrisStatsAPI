package me.gordi.mineapi.core.event;

/**
 * El núcleo terminó un {@code /lpa reload} (config, idiomas y módulos ya recargados). Los addons
 * pueden recargar lo suyo aquí. Posted after the core finished reloading.
 */
public record CoreReloadedEvent(boolean ok) {
}
