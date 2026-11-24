package me.gordi.mineapi.module.stats;

import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.core.data.DataDir;
import me.gordi.mineapi.core.module.Perms;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.data.PlayerDataService;
import me.gordi.mineapi.core.event.PlayerResetEvent;
import me.gordi.mineapi.core.event.StatChangedEvent;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.module.AbstractModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Estadísticas: sincroniza las de Bukkit con los datos del jugador, cuenta las propias
 * (blocks_broken, muertes por causa), calcula las derivadas (blocks_placed, ores_mined), los tops
 * y responde las consultas del dashboard.
 */
public final class StatsModule extends AbstractModule {

    public static final String ID = "stats";

    private final Leaderboards leaderboards = new Leaderboards();
    private final StatText text = new StatText(this);
    private VanillaSums sums;
    private StatsContributor contributor;
    private PendingResets pending;
    private volatile boolean trackDeathCauses = true;
    private volatile Set<String> disabledWorlds = Set.of();
    private volatile Set<StatType> tracked = EnumSet.allOf(StatType.class);

    public StatsModule(MineAPI plugin) {
        super(plugin, ID);
    }

    @Override
    protected void onEnable() {
        if (sums == null) sums = new VanillaSums();
        pending = new PendingResets(new java.io.File(plugin.getDataFolder(), DataDir.NAME + "/pending_resets.json"), log());
        readConfig();
        contributor = new StatsContributor(this, sums);
        plugin.data().addContributor(contributor);
        listen(new StatsListener(this, plugin.data()));

        // leaderboard-cache (segundos), mínimo 5 s; primera ejecución a los 5 s
        long periodMs = Math.max(5L, Durations.seconds(config(), 60L, 1, "leaderboard-cache")) * 1000L;
        asyncTimer("leaderboards", 5_000L, periodMs, () -> {
            int n = leaderboards.update(plugin.data());
            plugin.debug().log(DebugLog.Category.STATS, "Tops actualizados (" + n + " jugadores).");
        });

        command(SubCommand.of("top").minArgs(1)
                .executes((s, a) -> top(s, a[0]))
                .completes((s, a) -> {
                    List<String> out = new ArrayList<>();
                    for (StatType t : StatType.ALL) out.add(t.token);
                    return out;
                }).build());
        command(SubCommand.of("reset").permission(Perms.STATS_RESET).minArgs(1)
                .executes((s, a) -> i18n().send(s, reset(a[0]) ? "stats.reset.done" : "stats.reset.not-found", "player", a[0]))
                .completes((s, a) -> null).build());
    }

    @Override
    protected void onDisable() {
        if (contributor != null) plugin.data().removeContributor(contributor);
        contributor = null;
    }

    private void readConfig() {
        disabledWorlds = Set.copyOf(new HashSet<>(settings().getStringList("disabled-worlds")));
        EnumSet<StatType> set = EnumSet.noneOf(StatType.class);
        for (StatType t : StatType.ALL) if (config().getBoolean("tracked." + t.trackKey, true)) set.add(t);
        tracked = set;
        trackDeathCauses = config().getBoolean("tracked.death_causes", true);
        sums.setPlacedInterval(Math.max(10L, Durations.seconds(config(), 60L, 1, "computed-stats-interval")));
    }

    // ------------------------------------------------------------------ API para otros módulos

    public MineAPI plugin() { return plugin; }
    public Leaderboards leaderboards() { return leaderboards; }
    public StatText text() { return text; }

    public boolean isTracked(StatType type) { return tracked.contains(type); }
    public boolean trackDeathCauses() { return trackDeathCauses; }
    VanillaSums sums() { return sums; }

    /** Tipos de mineral del desglose "ores" (en orden). */
    public static String[] oreKeys() { return VanillaSums.oreKeys(); }

    public boolean isWorldDisabled(World world) {
        Set<String> worlds = disabledWorlds;
        return !worlds.isEmpty() && worlds.contains(world.getName());
    }

    /** Valor guardado en la caché del plugin (0 si no hay). */
    public double cached(UUID uuid, StatType type) {
        return plugin.data().getNumber(uuid, type.key);
    }

    /** Valor actual: en vivo si está conectado (hilo principal), si no el de la caché. */
    public double current(Player p, StatType type) {
        return type.statistic != null ? type.live(p) : cached(p.getUniqueId(), type);
    }

    /** Nombre traducido de la estadística para quien lo lee. */
    public String displayName(CommandSender to, StatType type) {
        return i18n().tr(to, "stats.names." + type.key);
    }

    void later(Runnable task) {
        scheduler().later(1L, task);
    }

    /** Publica {@link StatChangedEvent} solo si alguien escucha (coste cero sin suscriptores). */
    public void post(Player p, StatType type, int value) {
        if (events().hasSubscribers(StatChangedEvent.class)) events().post(new StatChangedEvent(p, type.key, value));
    }

    // ------------------------------------------------------------------ consultas del dashboard

    public JsonObject totals() {
        String[] labels = new String[StatsQueries.ACTIVITY_KEYS.length];
        for (int i = 0; i < labels.length; i++) labels[i] = i18n().trDefault("stats.activity." + StatsQueries.ACTIVITY_KEYS[i]);
        JsonObject out = StatsQueries.totals(StatsQueries.rows(plugin.data()), System.currentTimeMillis(), labels);
        out.add("breakdowns", StatsQueries.breakdowns(plugin.data(), VanillaSums.ORES_KEY, DeathCause.KEY));
        return out;
    }

    public JsonObject ranks(String name) {
        return StatsQueries.ranks(StatsQueries.rows(plugin.data()), name);
    }

    public List<JsonObject> list(String query, String sortKey, boolean desc) {
        return StatsQueries.list(StatsQueries.rows(plugin.data()), query, sortKey, desc);
    }

    // ------------------------------------------------------------------ comandos y administración

    private void top(CommandSender s, String raw) {
        StatType type = StatType.parse(raw);
        if (type == null) {
            i18n().send(s, "stats.top.invalid");
            return;
        }
        List<Leaderboards.Entry> top = leaderboards.top(type);
        if (top.isEmpty()) {
            i18n().send(s, "stats.top.empty");
            return;
        }
        i18n().send(s, "stats.top.header", "stat", displayName(s, type));
        int shown = Math.min(10, top.size());
        for (int i = 0; i < shown; i++) {
            Leaderboards.Entry e = top.get(i);
            s.sendMessage(i18n().tr(s, "stats.top.line", "position", i + 1, "player", e.name, "value", e.formatted(type)));
        }
    }

    private static final StatType[] VANILLA = {StatType.PLAYER_KILLS, StatType.DEATHS, StatType.MOB_KILLS,
            StatType.HOURS_PLAYED, StatType.JUMPS, StatType.DISTANCE_WALKED, StatType.FISH_CAUGHT,
            StatType.VILLAGER_TRADES, StatType.DAMAGE_DEALT, StatType.DAMAGE_TAKEN};

    /** Al entrar: aplica un reset pendiente de las estadísticas calculadas (hilo principal). */
    void applyPendingReset(Player p) {
        if (pending != null && pending.take(p.getUniqueId())) sums.reset(p, true, true);
    }

    /** Resetea a un jugador (conectado o no). Hilo principal. @return false si no hay datos. */
    public boolean reset(String name) {
        PlayerDataService data = plugin.data();
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            for (StatType t : VANILLA) online.setStatistic(t.statistic, 0);
            sums.reset(online, true, true);
            data.mutate(online, json -> {
                json.addProperty(StatType.BLOCKS_BROKEN.key, 0);
                json.addProperty(StatType.BLOCKS_PLACED.key, 0);
                json.remove("milestones");
                json.remove(DeathCause.KEY);
            });
            events().post(new PlayerResetEvent(online.getUniqueId(), online.getName()));
            data.savePlayer(online, true);
            return true;
        }
        UUID uuid = data.resolve(name);
        if (uuid == null) return false;
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        if (offline.hasPlayedBefore()) {
            for (StatType t : VANILLA) {
                try {
                    offline.setStatistic(t.statistic, 0);
                } catch (Exception e) {
                    log().warning("No se pudo resetear " + t.key + " de " + name + ": " + e.getMessage());
                    break;
                }
            }
        }
        // Las calculadas suman cientos de estadísticas: se ponen a cero cuando vuelva a entrar
        pending.add(uuid);
        events().post(new PlayerResetEvent(uuid, name));
        data.delete(uuid);
        return true;
    }

    /** Resultado de {@link #setStat}. */
    public record EditResult(int code, String player, double value, String error) {
    }

    /**
     * Cambia una estadística (modo set/add). Hilo principal. Si el jugador está conectado cambia
     * también la estadística vanilla; si no, la del archivo de estadísticas de Bukkit si existe.
     */
    public EditResult setStat(String name, StatType type, boolean add, double amount) {
        if (!type.editable()) return new EditResult(400, name, 0, "Statistic '" + type.key + "' is computed from vanilla statistics and cannot be edited");
        PlayerDataService data = plugin.data();
        Player online = Bukkit.getPlayerExact(name);
        UUID uuid = online != null ? online.getUniqueId() : data.resolve(name);
        if (uuid == null) return new EditResult(404, name, 0, "Player not found in plugin data");
        String realName = online != null ? online.getName() : data.realName(name);

        double current = online != null ? current(online, type) : cached(uuid, type);
        double target = add ? current + amount : amount;
        if (target < 0 || target > type.maxValue()) {
            return new EditResult(400, realName, 0, "Resulting value out of range (0 - " + (long) type.maxValue() + ")");
        }
        if (!type.decimal) target = Math.floor(target);
        final double value = target;

        if (type.statistic != null) {
            int raw = type.toRaw(value);
            if (online != null) {
                online.setStatistic(type.statistic, raw);
            } else {
                OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
                if (op.hasPlayedBefore()) {
                    try {
                        op.setStatistic(type.statistic, raw);
                    } catch (Exception e) {
                        log().warning("No se pudo cambiar la stat vanilla offline: " + e.getMessage());
                    }
                }
            }
        }
        data.mutate(uuid, json -> {
            if (type.decimal) json.addProperty(type.key, StatsQueries.round2(value));
            else json.addProperty(type.key, (long) value);
            json.addProperty("last_updated", System.currentTimeMillis());
        });
        if (online != null) {
            data.savePlayer(online, true);
            post(online, type, (int) value); // los hitos saltados también se entregan
        } else {
            data.queueSave(uuid, true);
        }
        return new EditResult(200, realName, value, null);
    }
}
