package me.gordi.mineapi.module.milestones;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.core.event.MilestoneReachedEvent;
import me.gordi.mineapi.core.event.PlayerDataReadyEvent;
import me.gordi.mineapi.core.event.PlayerResetEvent;
import me.gordi.mineapi.core.event.StatChangedEvent;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.module.afk.AfkModule;
import me.gordi.mineapi.module.discordwebhook.DiscordWebhookModule;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.UUID;

/**
 * Hitos (settings/milestones.yml): mensajes, recompensas y efectos al cruzar un umbral de una estadística.
 * Escucha {@link StatChangedEvent}; para el tiempo jugado revisa cada {@code check-interval} s.
 * Todo ocurre en el hilo principal, así que las cachés son mapas simples.
 */
public final class MilestonesModule extends AbstractModule implements Listener {

    public static final String ID = "milestones";

    /** uuid -> stat -> último umbral conseguido (persistido en el campo "milestones"). */
    private final Map<UUID, Map<String, Integer>> achieved = new HashMap<>();
    /** uuid -> stat -> último valor visto en esta sesión (detecta umbrales saltados). */
    private final Map<UUID, Map<String, Integer>> lastSeen = new HashMap<>();
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Set<String> pendingRetries = new HashSet<>();

    private MilestonesConfig cfg;
    private MilestoneEffects effects;
    private volatile JsonObject info = new JsonObject();
    private volatile MilestoneGallery gallery;

    public MilestonesModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() {
        cfg = new MilestonesConfig(config(), log());
        info = cfg.info;
        if (config().getBoolean("gallery.enabled", true)) startGallery();
        if (cfg.byStat.isEmpty()) return; // sin hitos: no se registra nada

        effects = new MilestoneEffects(plugin, log());
        listen(this);
        listen(effects);
        subscribe(StatChangedEvent.class, e -> check(e.player(), e.stat(), e.value()));
        subscribe(PlayerDataReadyEvent.class, e -> loadAchieved(e.player()));
        subscribe(PlayerResetEvent.class, e -> forget(e.uuid()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (plugin.data().isLoaded(p.getUniqueId())) loadAchieved(p);
        }
        if (cfg.byStat.containsKey(StatType.HOURS_PLAYED.key)) {
            long period = Math.max(1, cfg.checkInterval) * 20L;
            syncTimer("playtime", period, period, this::checkPlaytime);
        }
    }

    @Override
    protected void onDisable() {
        achieved.clear();
        lastSeen.clear();
        cooldownUntil.clear();
        pendingRetries.clear();
        effects = null;
        gallery = null;
    }

    /** Galería de logros con rareza ({@code gallery:} en milestones.yml; antes módulo achievements). */
    private void startGallery() {
        ConfigurationSection g = config().getConfigurationSection("gallery");
        MilestoneGallery mg = new MilestoneGallery(plugin, info.deepCopy(), g);
        gallery = mg;
        long period = Math.max(30L, Durations.seconds(g, 300L, 1, "update-interval")) * 1000L;
        asyncTimer("rarity", 1_000L, period, mg::recompute);
        placeholder("achievements_", mg::placeholder);
    }

    /** Galería de logros (null si los hitos o la galería están apagados). */
    public MilestoneGallery gallery() {
        return isEnabled() ? gallery : null;
    }

    /** Hitos configurados (stat -> [{value, message}]) para el dashboard. Copia segura. */
    public JsonObject info() {
        return info.deepCopy();
    }

    // ------------------------------------------------------------------ caché por jugador

    private void loadAchieved(Player player) {
        Map<String, Integer> map = plugin.data().read(player.getUniqueId(), json -> {
            Map<String, Integer> m = new HashMap<>();
            if (json != null && json.has("milestones") && json.get("milestones").isJsonObject()) {
                for (Map.Entry<String, JsonElement> e : json.getAsJsonObject("milestones").entrySet()) {
                    try {
                        m.put(e.getKey(), e.getValue().getAsInt());
                    } catch (Exception ignored) {
                    }
                }
            }
            return m;
        });
        achieved.put(player.getUniqueId(), map == null ? new HashMap<>() : map);
    }

    private void forget(UUID uuid) {
        Map<String, Integer> a = achieved.get(uuid);
        if (a != null) a.clear();
        lastSeen.remove(uuid);
        cooldownUntil.remove(uuid);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        achieved.remove(uuid);
        lastSeen.remove(uuid);
        cooldownUntil.remove(uuid);
    }

    private void checkPlaytime() {
        AfkModule afk = plugin.modules().get(AfkModule.class);
        StatsModule stats = plugin.modules().get(StatsModule.class);
        boolean afkOn = afk != null && afk.isEnabled();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (afkOn && afk.isAfk(p)) continue;
            if (stats != null && stats.isWorldDisabled(p.getWorld())) continue;
            check(p, StatType.HOURS_PLAYED.key, (int) StatType.HOURS_PLAYED.live(p));
        }
    }

    // ------------------------------------------------------------------ lógica

    /**
     * Comprueba si se cruzó algún umbral. Detecta umbrales saltados (p. ej. +10 desde el GUI) y,
     * si hay cooldown, reintenta al terminar en lugar de perder el hito.
     */
    void check(Player player, String stat, int value) {
        NavigableMap<Integer, Milestone> list = cfg.byStat.get(stat);
        if (list == null) return;
        UUID uuid = player.getUniqueId();
        Map<String, Integer> seen = lastSeen.computeIfAbsent(uuid, k -> new HashMap<>());
        int previous = seen.getOrDefault(stat, value - 1); // primera vez en la sesión: solo el valor exacto
        if (value <= previous) {
            seen.put(stat, value);
            return;
        }
        Map<String, Integer> done = achieved.computeIfAbsent(uuid, k -> new HashMap<>());
        for (Milestone m : list.subMap(previous, false, value, true).values()) {
            Integer got = done.get(stat);
            if (got != null && got >= m.value()) continue;
            if (onCooldown(uuid)) {
                seen.put(stat, m.value() - 1);
                scheduleRetry(uuid, stat);
                return;
            }
            award(player, m);
            done.put(stat, m.value());
            plugin.data().mutate(player, json -> {
                JsonObject ms = json.has("milestones") && json.get("milestones").isJsonObject()
                        ? json.getAsJsonObject("milestones") : new JsonObject();
                ms.addProperty(stat, m.value());
                json.add("milestones", ms);
            });
            plugin.data().queueSave(uuid, true);
            if (cfg.cooldownSeconds > 0) cooldownUntil.put(uuid, System.currentTimeMillis() + cfg.cooldownSeconds * 1000L);
        }
        seen.put(stat, value);
    }

    private boolean onCooldown(UUID uuid) {
        Long until = cooldownUntil.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    private void scheduleRetry(UUID uuid, String stat) {
        String key = uuid + ":" + stat;
        if (!pendingRetries.add(key)) return;
        long remaining = Math.max(0, cooldownUntil.getOrDefault(uuid, 0L) - System.currentTimeMillis());
        scheduler().later(Math.max(1L, remaining / 50L + 1L), () -> {
            pendingRetries.remove(key);
            if (!isEnabled()) return;
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) check(online, stat, currentValue(online, stat));
        });
    }

    private int currentValue(Player player, String stat) {
        StatType type = StatType.parse(stat);
        if (type == null) return (int) plugin.data().getNumber(player.getUniqueId(), stat);
        if (type.statistic != null) return (int) type.live(player);
        return (int) plugin.data().getNumber(player.getUniqueId(), type.key);
    }

    private void award(Player player, Milestone m) {
        String name = player.getName();
        String self = Text.color(m.message().replace("%player%", name));
        String prefix = i18n().prefix();
        player.sendMessage(prefix + self);

        String broadcast = null;
        if (m.broadcast() != null && !m.broadcast().isEmpty()) {
            broadcast = Text.color(m.broadcast().replace("%player%", name));
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getUniqueId().equals(player.getUniqueId())) p.sendMessage(prefix + broadcast);
            }
        }
        String plain = Text.strip(broadcast != null ? broadcast : self);
        log().info(plain);
        plugin.debug().log(DebugLog.Category.STATS, "Hito " + m.stat() + " -> " + m.value() + " para " + name);

        if (!m.rewards().isEmpty()) giveRewards(player, m);
        if (events().hasSubscribers(MilestoneReachedEvent.class)) {
            events().post(new MilestoneReachedEvent(player, m.stat(), m.value(), plain));
        }

        DiscordWebhookModule discord = plugin.modules().get(DiscordWebhookModule.class);
        if (discord != null && discord.isEnabled()) discord.sendMilestone(name, m.stat(), m.value(), plain);

        if (cfg.soundEnabled) effects.sound(player, m.sound() != null ? m.sound() : cfg.sound, cfg.soundBroadcast);
        if (m.firework() != null ? m.firework() : cfg.fireworkEnabled) effects.firework(player);
        if (cfg.titleEnabled) effects.title(player, m.title() != null ? m.title() : cfg.titleMain, self, cfg);
        if (cfg.particlesEnabled || m.particles() != null) {
            effects.particles(player, m.particles() != null ? m.particles() : cfg.particle, cfg.particleCount);
        }
        if (cfg.actionbarEnabled || m.actionbar() != null) {
            effects.actionbar(player, m.actionbar() != null ? m.actionbar() : cfg.actionbarFormat, self);
        }
    }

    private void giveRewards(Player player, Milestone m) {
        String money = plugin.rewards().giveLines(player, player.getName(), m.rewards(), "settings/milestones.yml " + m.stat() + "." + m.value());
        if (money != null) i18n().send(player, "milestones.money-received", "amount", money);
    }
}
