package me.gordi.mineapi.module.gui;

import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.api.MenuApi;
import me.gordi.mineapi.api.MenuItems;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.i18n.I18n;
import me.gordi.mineapi.module.milestones.MilestoneGallery;
import me.gordi.mineapi.module.milestones.MilestonesModule;
import me.gordi.mineapi.module.milestones.Rarity;
import me.gordi.mineapi.module.periods.PeriodKind;
import me.gordi.mineapi.module.periods.PeriodsModule;
import me.gordi.mineapi.module.stats.DeathCause;
import me.gordi.mineapi.module.stats.Leaderboards;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Construye las páginas del menú /lpa menu (54 huecos): resumen con puestos, desglose de
 * minerales, causas de muerte, galería de logros y tops del periodo. Las páginas opcionales
 * solo aparecen si su módulo está activo. Todo se lee de la caché: sin E/S.
 */
final class StatsMenu {

    static final int SIZE = 54;
    private static final int HEAD = 4;
    private static final int[] STAT_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
    private static final int NAV_ORES = 47, NAV_DEATHS = 48, NAV_CLOSE = 49, NAV_ACH = 50, NAV_PERIODS = 51;
    /** Botones de las páginas de los addons (en este orden). */
    private static final int[] NAV_EXTRA = {52, 46, 53, 45};
    private static final int PREV = 45, NEXT = 53;
    private static final int PER_PAGE = 36;

    private static final Map<String, String> ORE_ICONS = Map.ofEntries(
            Map.entry("coal", "COAL"), Map.entry("iron", "RAW_IRON"), Map.entry("copper", "RAW_COPPER"),
            Map.entry("gold", "RAW_GOLD"), Map.entry("redstone", "REDSTONE"), Map.entry("lapis", "LAPIS_LAZULI"),
            Map.entry("diamond", "DIAMOND"), Map.entry("emerald", "EMERALD"), Map.entry("quartz", "QUARTZ"),
            Map.entry("ancient_debris", "ANCIENT_DEBRIS"));

    private static final Map<String, String> DEATH_ICONS = Map.ofEntries(
            Map.entry("player", "IRON_SWORD"), Map.entry("mob", "ZOMBIE_HEAD"), Map.entry("fall", "FEATHER"),
            Map.entry("lava", "LAVA_BUCKET"), Map.entry("fire", "BLAZE_POWDER"), Map.entry("drowning", "WATER_BUCKET"),
            Map.entry("explosion", "TNT"), Map.entry("void", "ENDER_EYE"), Map.entry("suffocation", "GRAVEL"),
            Map.entry("starvation", "ROTTEN_FLESH"), Map.entry("magic", "POTION"), Map.entry("freeze", "POWDER_SNOW_BUCKET"),
            Map.entry("contact", "CACTUS"), Map.entry("lightning", "LIGHTNING_ROD"), Map.entry("other", "PAPER"));

    private static final Map<Rarity.Tier, Material> TIER_ICONS = new EnumMap<>(Map.of(
            Rarity.Tier.COMMON, Material.IRON_INGOT, Rarity.Tier.UNCOMMON, Material.GOLD_INGOT,
            Rarity.Tier.RARE, Material.DIAMOND, Rarity.Tier.EPIC, Material.AMETHYST_SHARD,
            Rarity.Tier.LEGENDARY, Material.NETHER_STAR));

    private final MineAPI plugin;
    private final StatsModule stats;

    StatsMenu(MineAPI plugin, StatsModule stats) {
        this.plugin = plugin;
        this.stats = stats;
    }

    private I18n i18n() {
        return plugin.i18n();
    }

    // ------------------------------------------------------------------ páginas

    void open(Player viewer, Player target, MenuHolder.Page page, int number) {
        open(viewer, target, page, number, null);
    }

    /** Página de un addon; false si no existe o no se puede ver. */
    boolean openExtra(Player viewer, Player target, String id, int number) {
        MenuApi.Page p = plugin.api().menuRegistry().find(id);
        if (p == null || !p.visible(viewer, target)) return false;
        open(viewer, target, MenuHolder.Page.EXTRA, number, p);
        return true;
    }

    private void open(Player viewer, Player target, MenuHolder.Page page, int number, MenuApi.Page extra) {
        MenuHolder h = new MenuHolder(target.getUniqueId(), target.getName(), page, number, extra == null ? null : extra.id());
        String title = switch (page) {
            case MAIN -> i18n().tr(viewer, "gui.stats.title", "player", target.getName());
            case ORES -> i18n().tr(viewer, "gui.ores.title", "player", target.getName());
            case DEATHS -> i18n().tr(viewer, "gui.deaths.title", "player", target.getName());
            case ACHIEVEMENTS -> i18n().tr(viewer, "gui.achievements.title", "player", target.getName());
            case PERIODS -> i18n().tr(viewer, "gui.periods.title", "player", target.getName());
            case EXTRA -> extra.title(viewer, target);
        };
        Inventory inv = Bukkit.createInventory(h, SIZE, title.length() > 64 ? title.substring(0, 64) : title);
        if (page == MenuHolder.Page.MAIN) plugin.data().sync(target); // desgloses al día
        JsonObject data = plugin.data().read(target.getUniqueId(), j -> j == null ? new JsonObject() : j.deepCopy());
        switch (page) {
            case MAIN -> main(inv, h, viewer, target, data);
            case ORES -> ores(inv, viewer, data);
            case DEATHS -> deaths(inv, viewer, data);
            case ACHIEVEMENTS -> achievements(inv, h, viewer, target, number);
            case PERIODS -> periods(inv, viewer, target);
            case EXTRA -> extra(extra, inv, h, viewer, target, number);
        }
        if (page != MenuHolder.Page.MAIN) {
            inv.setItem(NAV_CLOSE, item(Material.ARROW, i18n().tr(viewer, "gui.nav.back"), List.of()));
            h.buttons.put(NAV_CLOSE, () -> open(viewer, target, MenuHolder.Page.MAIN, 0));
        }
        fill(inv);
        viewer.openInventory(inv);
    }

    private void main(Inventory inv, MenuHolder h, Player viewer, Player target, JsonObject data) {
        PeriodsModule pm = plugin.modules().get(PeriodsModule.class);
        double[] weekly = pm != null && pm.isActive(PeriodKind.WEEKLY) ? pm.gains(PeriodKind.WEEKLY, target.getUniqueId()) : null;
        Set<StatType> inPeriods = pm == null ? Set.of() : Set.copyOf(pm.tracked());
        int i = 0;
        for (StatType t : StatType.ALL) {
            List<String> lore = new ArrayList<>();
            lore.add(i18n().tr(viewer, "gui.lore.total", "value", t.format(stats.current(target, t))));
            lore.add(i18n().tr(viewer, "gui.lore.rank", "rank", rank(t, target.getName())));
            if (weekly != null && inPeriods.contains(t)) {
                lore.add(i18n().tr(viewer, "gui.lore.weekly", "value", t.format(weekly[t.ordinal()])));
            }
            inv.setItem(STAT_SLOTS[i++], item(MenuItems.icon(t), i18n().tr(viewer, "gui.items." + t.key), lore));
        }
        // Cabeza con KDR y tiempo
        double kills = stats.current(target, StatType.PLAYER_KILLS);
        double deaths = stats.current(target, StatType.DEATHS);
        double kdr = deaths == 0 ? kills : kills / deaths;
        ItemStack head = item(Material.PLAYER_HEAD, i18n().tr(viewer, "gui.head.name", "player", target.getName()), List.of(
                i18n().tr(viewer, "gui.lore.kdr", "value", String.format(Locale.US, "%.2f", kdr)),
                i18n().tr(viewer, "gui.lore.total", "value", StatType.HOURS_PLAYED.format(stats.current(target, StatType.HOURS_PLAYED)))));
        if (head.getItemMeta() instanceof SkullMeta sm) {
            sm.setOwningPlayer(target);
            head.setItemMeta(sm);
        }
        inv.setItem(HEAD, head);

        String click = i18n().tr(viewer, "gui.nav.click");
        inv.setItem(NAV_ORES, item(Material.DIAMOND_ORE, i18n().tr(viewer, "gui.nav.ores"), List.of(click)));
        h.buttons.put(NAV_ORES, () -> open(viewer, target, MenuHolder.Page.ORES, 0));
        if (data.has(DeathCause.KEY) || plugin.configs().module("stats").getBoolean("tracked.death_causes", true)) {
            inv.setItem(NAV_DEATHS, item(Material.SKELETON_SKULL, i18n().tr(viewer, "gui.nav.deaths"), List.of(click)));
            h.buttons.put(NAV_DEATHS, () -> open(viewer, target, MenuHolder.Page.DEATHS, 0));
        }
        inv.setItem(NAV_CLOSE, item(Material.BARRIER, i18n().tr(viewer, "gui.nav.close"), List.of()));
        h.buttons.put(NAV_CLOSE, viewer::closeInventory);
        if (gallery() != null) {
            inv.setItem(NAV_ACH, item(Material.NETHER_STAR, i18n().tr(viewer, "gui.nav.achievements"), List.of(click)));
            h.buttons.put(NAV_ACH, () -> open(viewer, target, MenuHolder.Page.ACHIEVEMENTS, 0));
        }
        if (pm != null) {
            inv.setItem(NAV_PERIODS, item(Material.GOLDEN_HELMET, i18n().tr(viewer, "gui.nav.periods"), List.of(click)));
            h.buttons.put(NAV_PERIODS, () -> open(viewer, target, MenuHolder.Page.PERIODS, 0));
        }
        int n = 0;
        for (MenuApi.Page p : plugin.api().menuRegistry().pages()) {
            if (n >= NAV_EXTRA.length) break;
            if (!p.visible(viewer, target)) continue;
            int slot = NAV_EXTRA[n++];
            inv.setItem(slot, item(p.icon(), p.button(viewer), List.of(click)));
            h.buttons.put(slot, () -> open(viewer, target, MenuHolder.Page.EXTRA, 0, p));
        }
    }

    private void ores(Inventory inv, Player viewer, JsonObject data) {
        JsonObject ores = data.has("ores") && data.get("ores").isJsonObject() ? data.getAsJsonObject("ores") : new JsonObject();
        int slot = 10;
        long total = 0;
        for (String key : StatsModule.oreKeys()) {
            long v = (long) PlayerDataService.num(ores, key);
            total += v;
            inv.setItem(slot, item(material(ORE_ICONS.get(key), Material.STONE), i18n().tr(viewer, "gui.ores." + key),
                    List.of(i18n().tr(viewer, "gui.lore.total", "value", v))));
            slot = next(slot);
        }
        inv.setItem(HEAD, item(Material.DIAMOND_PICKAXE, i18n().tr(viewer, "gui.items.ores_mined"),
                List.of(i18n().tr(viewer, "gui.lore.total", "value", total))));
    }

    private void deaths(Inventory inv, Player viewer, JsonObject data) {
        JsonObject causes = data.has(DeathCause.KEY) && data.get(DeathCause.KEY).isJsonObject() ? data.getAsJsonObject(DeathCause.KEY) : new JsonObject();
        int slot = 10;
        for (String key : DeathCause.CATEGORIES) {
            long v = (long) PlayerDataService.num(causes, key);
            inv.setItem(slot, item(material(DEATH_ICONS.get(key), Material.PAPER), i18n().tr(viewer, "gui.deaths." + key),
                    List.of(i18n().tr(viewer, "gui.lore.total", "value", v))));
            slot = next(slot);
        }
        inv.setItem(HEAD, item(Material.BONE, i18n().tr(viewer, "gui.items.deaths"),
                List.of(i18n().tr(viewer, "gui.lore.total", "value", (long) PlayerDataService.num(data, StatType.DEATHS.key)))));
    }

    private void achievements(Inventory inv, MenuHolder h, Player viewer, Player target, int page) {
        MilestoneGallery am = gallery();
        if (am == null) return;
        List<MilestoneGallery.Def> defs = am.definitions();
        Set<String> got = Set.copyOf(am.obtained(target.getUniqueId()));
        int pages = Math.max(1, (defs.size() + PER_PAGE - 1) / PER_PAGE);
        int p = Math.max(0, Math.min(page, pages - 1));
        inv.setItem(HEAD, item(Material.NETHER_STAR, i18n().tr(viewer, "gui.achievements.summary", "obtained", got.size(), "total", defs.size()),
                List.of(i18n().tr(viewer, "gui.page", "page", (p + 1) + "/" + pages))));
        int slot = 9;
        for (int i = p * PER_PAGE; i < Math.min(defs.size(), (p + 1) * PER_PAGE); i++) {
            MilestoneGallery.Def d = defs.get(i);
            Rarity.Tier tier = am.tier(d.id());
            boolean has = got.contains(d.id());
            StatType st = StatType.parse(d.stat());
            String statName = st == null ? d.stat() : i18n().tr(viewer, "stats.names." + st.key);
            String name = i18n().tr(viewer, "gui.achievements.name", "stat", statName, "value", d.value());
            List<String> lore = new ArrayList<>();
            if (!d.message().isEmpty()) lore.add("§7" + d.message().replace("%player%", target.getName()).replaceAll("&[0-9a-fk-or]", ""));
            lore.add(i18n().tr(viewer, "gui.achievements.rarity",
                    "tier", i18n().tr(viewer, "gui.achievements.tiers." + tier.name().toLowerCase(Locale.ROOT)),
                    "rarity", am.percent(d.id())));
            lore.add(i18n().tr(viewer, has ? "gui.achievements.obtained" : "gui.achievements.locked"));
            ItemStack it = item(has ? TIER_ICONS.get(tier) : Material.GRAY_DYE, name, lore);
            if (has) glow(it);
            inv.setItem(slot++, it);
        }
        if (defs.isEmpty()) inv.setItem(22, item(Material.BARRIER, i18n().tr(viewer, "gui.achievements.empty"), List.of()));
        if (p > 0) {
            inv.setItem(PREV, item(Material.ARROW, i18n().tr(viewer, "gui.nav.previous"), List.of()));
            h.buttons.put(PREV, () -> open(viewer, target, MenuHolder.Page.ACHIEVEMENTS, p - 1));
        }
        if (p < pages - 1) {
            inv.setItem(NEXT, item(Material.ARROW, i18n().tr(viewer, "gui.nav.next"), List.of()));
            h.buttons.put(NEXT, () -> open(viewer, target, MenuHolder.Page.ACHIEVEMENTS, p + 1));
        }
    }

    private void periods(Inventory inv, Player viewer, Player target) {
        PeriodsModule pm = plugin.modules().get(PeriodsModule.class);
        if (pm == null) return;
        String lang = i18n().locale(viewer);
        int row = 1;
        for (PeriodKind kind : PeriodKind.values()) {
            if (!pm.isActive(kind)) continue;
            double[] g = pm.gains(kind, target.getUniqueId());
            int[] r = pm.ranks(kind, target.getUniqueId());
            String period = i18n().tr(viewer, "periods.names." + kind.id);
            inv.setItem(row * 9, item(kind == PeriodKind.WEEKLY ? Material.CLOCK : Material.GOLDEN_HELMET,
                    i18n().tr(viewer, "gui.periods.header", "period", period),
                    List.of(i18n().tr(viewer, "gui.periods.ends", "time", pm.remaining(lang, pm.endsAt(kind) - System.currentTimeMillis())))));
            int col = 1;
            for (StatType t : pm.tracked()) {
                if (col > 8) break;
                int rank = r[t.ordinal()];
                inv.setItem(row * 9 + col++, item(MenuItems.icon(t), i18n().tr(viewer, "gui.items." + t.key), List.of(
                        i18n().tr(viewer, "gui.periods.points", "value", t.format(g[t.ordinal()])),
                        i18n().tr(viewer, "gui.lore.rank", "rank", rank > 0 ? String.valueOf(rank) : "-"))));
            }
            row += 2;
        }
    }

    /** Página de un addon: le da un lienzo para dibujar y registrar botones. */
    private void extra(MenuApi.Page page, Inventory inv, MenuHolder h, Player viewer, Player target, int number) {
        page.render(new MenuApi.Canvas() {
            private int current = Math.max(0, number);

            public Player viewer() { return viewer; }
            public Player target() { return target; }
            public int page() { return current; }
            public int perPage() { return PER_PAGE; }
            public void set(int slot, ItemStack item) { inv.setItem(slot, item); }

            public void button(int slot, ItemStack item, Runnable onClick) {
                inv.setItem(slot, item);
                h.buttons.put(slot, onClick);
            }

            public int paginate(int total) {
                int pages = Math.max(1, (total + PER_PAGE - 1) / PER_PAGE);
                current = Math.max(0, Math.min(current, pages - 1));
                int p = current;
                if (p > 0) button(PREV, item(Material.ARROW, i18n().tr(viewer, "gui.nav.previous"), List.of()),
                        () -> open(viewer, target, MenuHolder.Page.EXTRA, p - 1, page));
                if (p < pages - 1) button(NEXT, item(Material.ARROW, i18n().tr(viewer, "gui.nav.next"), List.of()),
                        () -> open(viewer, target, MenuHolder.Page.EXTRA, p + 1, page));
                return p;
            }

            public void reopen(int p) { open(viewer, target, MenuHolder.Page.EXTRA, p, page); }
        });
    }

    static String bar(double ratio) {
        return MenuItems.bar(ratio);
    }

    // ------------------------------------------------------------------ utilidades

    private MilestoneGallery gallery() {
        MilestonesModule mm = plugin.modules().get(MilestonesModule.class);
        return mm == null ? null : mm.gallery();
    }

    /** Puesto en el top (caché de leaderboards); "100+" si no está entre los primeros. */
    private String rank(StatType t, String name) {
        List<Leaderboards.Entry> top = stats.leaderboards().top(t);
        for (int i = 0; i < top.size(); i++) if (top.get(i).name.equalsIgnoreCase(name)) return String.valueOf(i + 1);
        return Leaderboards.MAX_ENTRIES + "+";
    }

    /** Siguiente hueco dentro de un marco (columnas 1-7). */
    private static int next(int slot) {
        int s = slot + 1;
        return s % 9 == 8 ? s + 2 : s;
    }

    private static Material material(String name, Material def) {
        return MenuItems.material(name, def);
    }

    static ItemStack item(Material material, String name, List<String> lore) {
        return MenuItems.item(material, name, lore);
    }

    private static void glow(ItemStack it) {
        MenuItems.glow(it);
    }

    static void fill(Inventory inv) {
        MenuItems.fill(inv);
    }
}
