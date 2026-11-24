package me.gordi.mineapi.api;

import me.gordi.mineapi.module.stats.StatType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** Ítems con el estilo del menú de estadísticas (para páginas de addons). Menu item helpers. */
public final class MenuItems {

    private static final Map<StatType, Material> ICONS = new EnumMap<>(StatType.class);
    static {
        ICONS.put(StatType.PLAYER_KILLS, Material.DIAMOND_SWORD);
        ICONS.put(StatType.DEATHS, Material.BONE);
        ICONS.put(StatType.MOB_KILLS, Material.ROTTEN_FLESH);
        ICONS.put(StatType.HOURS_PLAYED, Material.CLOCK);
        ICONS.put(StatType.BLOCKS_BROKEN, Material.DIAMOND_PICKAXE);
        ICONS.put(StatType.BLOCKS_PLACED, Material.BRICKS);
        ICONS.put(StatType.ORES_MINED, Material.DIAMOND_ORE);
        ICONS.put(StatType.JUMPS, Material.RABBIT_FOOT);
        ICONS.put(StatType.DISTANCE_WALKED, Material.LEATHER_BOOTS);
        ICONS.put(StatType.FISH_CAUGHT, Material.FISHING_ROD);
        ICONS.put(StatType.VILLAGER_TRADES, Material.EMERALD);
        ICONS.put(StatType.DAMAGE_DEALT, Material.IRON_AXE);
        ICONS.put(StatType.DAMAGE_TAKEN, Material.SHIELD);
    }


    private MenuItems() {
    }

    /** Icono de una estadística (PAPER si no tiene). */
    public static Material icon(StatType type) {
        return ICONS.getOrDefault(type, Material.PAPER);
    }

    /** Ítem con nombre y descripción (ya coloreados) y sin atributos visibles. */
    public static ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            item.setItemMeta(meta);
        }
        return item;
    }

    /** Barra de 20 segmentos con porcentaje: {@code &a|||||&8||||| &750%} (sin colorear). */
    public static String bar(double ratio) {
        int full = (int) Math.round(Math.max(0, Math.min(1, ratio)) * 20);
        return "&a" + "|".repeat(full) + "&8" + "|".repeat(20 - full) + " &7" + Math.round(ratio * 100) + "%";
    }

    /** Brillo de encantamiento sin mostrar el encantamiento. */
    public static void glow(ItemStack it) {
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return;
        // Por clave: el campo LUCK cambió de nombre en 1.20.5 y fallaría en tiempo de ejecución
        Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("luck_of_the_sea"));
        if (e == null) return;
        meta.addEnchant(e, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(meta);
    }

    /** Rellena los huecos vacíos con cristal negro. */
    public static void fill(Inventory inv) {
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, "§0", List.of());
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, filler);
    }

    /** Material por nombre o {@code def}. */
    public static Material material(String name, Material def) {
        Material m = name == null ? null : Material.getMaterial(name);
        return m == null ? def : m;
    }
}
