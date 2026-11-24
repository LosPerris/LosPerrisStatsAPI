package me.gordi.mineapi.module.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Identifica un menú de estadísticas: de quién es, qué página y qué hace cada botón. */
final class MenuHolder implements InventoryHolder {

    enum Page { MAIN, ORES, DEATHS, ACHIEVEMENTS, PERIODS, EXTRA }

    final UUID target;
    final String targetName;
    final Page page;
    final int number;
    /** Página de un addon (solo con {@link Page#EXTRA}). */
    final String extra;
    /** slot → acción del botón (navegación). */
    final Map<Integer, Runnable> buttons = new HashMap<>();

    MenuHolder(UUID target, String targetName, Page page, int number, String extra) {
        this.target = target;
        this.targetName = targetName;
        this.page = page;
        this.number = number;
        this.extra = extra;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}
