package me.gordi.mineapi.module.gui;

import me.gordi.mineapi.core.module.Perms;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;

/** Menús de inventario: estadísticas propias/ajenas y edición rápida para admins. */
public final class GuiModule extends AbstractModule implements Listener {

    public static final String ID = "gui";
    public static final String INSPECT = "losperris.inspect";
    private static final int SIZE = 27;

    /** Ítem del menú de estadísticas. */
    private record Slot(int slot, StatType type, Material icon) {
    }

    /** Ítems editables del menú admin. */
    private static final Slot[] ADMIN_MENU = {
            new Slot(11, StatType.PLAYER_KILLS, Material.DIAMOND_SWORD),
            new Slot(13, StatType.MOB_KILLS, Material.ROTTEN_FLESH),
            new Slot(15, StatType.DEATHS, Material.BONE),
    };

    private StatsModule stats;
    private StatsMenu menu;

    public GuiModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() {
        stats = plugin.modules().get(StatsModule.class);
        menu = new StatsMenu(plugin, stats);
        listen(this);
        plugin.api().menuRegistry().opener(this::openPage);
        command(SubCommand.of("menu").playerOnly()
                .executes((s, a) -> openStats((Player) s, (Player) s)).build());
        command(SubCommand.of("view").aliases("inspect").permission(INSPECT).playerOnly().minArgs(1)
                .executes((s, a) -> withOnline(s, a[0], t -> openStats((Player) s, t)))
                .completes((s, a) -> null).build());
        command(SubCommand.of("admin").aliases("edit").permission(Perms.GUI_ADMIN).playerOnly().minArgs(1)
                .executes((s, a) -> withOnline(s, a[0], t -> openAdmin((Player) s, t)))
                .completes((s, a) -> null).build());
    }

    @Override
    protected void onDisable() {
        plugin.api().menuRegistry().opener(null);
        // Cierra los menús abiertos: sus manejadores de clic dejan de existir
        for (Player p : Bukkit.getOnlinePlayers()) {
            InventoryHolder h = p.getOpenInventory().getTopInventory().getHolder();
            if (h instanceof MenuHolder || h instanceof AdminHolder) p.closeInventory();
        }
    }

    private void withOnline(org.bukkit.command.CommandSender s, String name, java.util.function.Consumer<Player> action) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null) i18n().send(s, "general.player-offline", "player", name);
        else action.accept(target);
    }

    // ------------------------------------------------------------------ menús

    public void openStats(Player viewer, Player target) {
        menu.open(viewer, target, MenuHolder.Page.MAIN, 0);
    }

    /** Página de un addon ({@link me.gordi.mineapi.api.MenuApi#open}). */
    public boolean openPage(Player viewer, Player target, String pageId, int page) {
        return menu.openExtra(viewer, target, pageId, page);
    }

    public void openAdmin(Player viewer, Player target) {
        AdminHolder holder = new AdminHolder(target.getUniqueId());
        Inventory inv = Bukkit.createInventory(holder, SIZE,
                title(i18n().tr(viewer, "gui.admin.title", "player", target.getName())));
        holder.inventory = inv;
        fillAdmin(inv, viewer, target);
        fill(inv);
        viewer.openInventory(inv);
    }

    private void fillAdmin(Inventory inv, Player viewer, Player target) {
        for (Slot s : ADMIN_MENU) {
            int value = (int) stats.current(target, s.type());
            inv.setItem(s.slot(), item(s.icon(), i18n().tr(viewer, "gui.items." + s.type().key),
                    i18n().trList(viewer, "gui.admin.lore", "value", value)));
        }
    }

    private static String title(String text) {
        return text.length() > 64 ? text.substring(0, 64) : text;
    }

    private static ItemStack item(Material material, String name, List<String> lore) {
        return StatsMenu.item(material, name, lore);
    }

    private static void fill(Inventory inv) {
        StatsMenu.fill(inv);
    }

    private static StatType adminStat(int slot) {
        for (Slot s : ADMIN_MENU) if (s.slot() == slot) return s.type();
        return null;
    }

    // ------------------------------------------------------------------ eventos

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player admin)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof MenuHolder) && !(holder instanceof AdminHolder)) return;
        event.setCancelled(true); // cualquier clic con un menú nuestro abierto
        int raw = event.getRawSlot();
        if (holder instanceof MenuHolder m) {
            Runnable action = m.buttons.get(raw);
            if (action == null) return;
            Player target = Bukkit.getPlayer(m.target);
            if (target == null) {
                admin.closeInventory();
                i18n().send(admin, "general.player-offline", "player", m.targetName);
                return;
            }
            // Abrir otro inventario dentro del evento de clic no es seguro: al siguiente tick
            scheduler().later(1L, action);
            return;
        }
        if (!(holder instanceof AdminHolder h)) return;
        if (raw < 0 || raw >= event.getView().getTopInventory().getSize()) return;
        StatType type = adminStat(raw);
        if (type == null) return;
        if (!admin.hasPermission(Perms.GUI_ADMIN)) {
            admin.closeInventory();
            return;
        }
        Player target = Bukkit.getPlayer(h.target);
        if (target == null) {
            admin.closeInventory();
            i18n().send(admin, "gui.admin.offline");
            return;
        }
        int change = event.isLeftClick() ? (event.isShiftClick() ? 10 : 1)
                : event.isRightClick() ? (event.isShiftClick() ? -10 : -1) : 0;
        if (change == 0) return;
        int value = (int) Math.max(0, Math.min(type.maxValue(), target.getStatistic(type.statistic) + (long) change));
        target.setStatistic(type.statistic, value);
        plugin.data().sync(target);
        stats.post(target, type, value);
        fillAdmin(event.getView().getTopInventory(), admin, target); // sin reabrir el menú
        h.dirty = true;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder h = event.getView().getTopInventory().getHolder();
        if (h instanceof MenuHolder || h instanceof AdminHolder) event.setCancelled(true);
    }

    /** Un único guardado al cerrar en lugar de uno por clic. */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof AdminHolder h) || !h.dirty) return;
        h.dirty = false;
        Player target = Bukkit.getPlayer(h.target);
        if (target != null) plugin.data().savePlayer(target, true);
    }

    /** Identifica el menú admin y a quién se edita. */
    static final class AdminHolder implements InventoryHolder {
        final UUID target;
        Inventory inventory;
        boolean dirty;

        AdminHolder(UUID target) {
            this.target = target;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
