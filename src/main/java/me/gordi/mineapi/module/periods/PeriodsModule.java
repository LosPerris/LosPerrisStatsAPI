package me.gordi.mineapi.module.periods;

import me.gordi.mineapi.core.time.Durations;
import me.gordi.mineapi.core.data.DataDir;
import me.gordi.mineapi.core.module.Perms;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.event.PlayerDataReadyEvent;
import me.gordi.mineapi.core.event.PlayerResetEvent;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.storage.JsonFiles;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.core.time.Zones;
import me.gordi.mineapi.module.discordwebhook.DiscordWebhookModule;
import me.gordi.mineapi.module.stats.StatSnapshot;
import me.gordi.mineapi.module.stats.StatType;
import me.gordi.mineapi.module.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Tops semanales y mensuales con reinicio automático.
 *
 * <p>Diseño: al empezar un periodo se guarda una "base" (valores de cada jugador) en
 * {@code data/periods/<tipo>.json}; la puntuación del periodo es {@code actual − base}.
 * No hay contadores extra por evento: cero coste en el hilo principal. Cada
 * {@code periods.update-interval} s (hilo asíncrono) se recalculan los tops y se comprueba si
 * el periodo terminó. Al terminar: se anuncian los ganadores (juego, i18n por jugador, y
 * Discord), se dan recompensas opcionales, se archiva el resultado y se toma una base nueva.
 * Si el servidor estaba apagado cuando terminó, se cierra al arrancar.</p>
 */
public final class PeriodsModule extends AbstractModule {

    public static final String ID = "periods";
    public static final int BOARD_SIZE = 100;
    static final String FOLDER = DataDir.NAME + "/periods";
    private static final Pattern POSITION = Pattern.compile("([a-z_]+)_(\\d{1,3})_(name|value)");

    /** Estado de un periodo en curso (la base y los tops viven en {@link GainTracker}). */
    private static final class State {
        final PeriodKind kind;
        volatile String id;
        volatile long start;
        volatile long end;
        final GainTracker gains = new GainTracker();
        volatile JsonArray archive = new JsonArray();

        State(PeriodKind kind) {
            this.kind = kind;
        }
    }

    private final Map<PeriodKind, State> states = new EnumMap<>(PeriodKind.class);
    private final Set<PeriodKind> ending = ConcurrentHashMap.newKeySet();
    private File dir;
    private ZoneId zone;
    private DayOfWeek weekStart;
    private List<StatType> tracked = List.of();
    private int announceTop;
    private boolean announceInGame;
    private boolean announceDiscord;
    private int keepArchive;

    public PeriodsModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() {
        dir = new File(plugin.getDataFolder(), FOLDER);
        zone = Zones.of(settings().getString("timezone", ""));
        weekStart = PeriodMath.weekStart(settings().getString("week-start", "MONDAY"));
        List<StatType> list = new ArrayList<>();
        for (String s : config().getStringList("stats")) {
            StatType t = StatType.parse(s);
            if (t == null) log().warning("config.yml → periods.stats: estadística desconocida '" + s + "'");
            else if (!list.contains(t)) list.add(t);
        }
        if (list.isEmpty()) list.addAll(List.of(StatType.PLAYER_KILLS, StatType.MOB_KILLS, StatType.HOURS_PLAYED, StatType.BLOCKS_BROKEN));
        tracked = List.copyOf(list);
        announceTop = Math.max(1, Math.min(10, config().getInt("announce.top", 3)));
        announceInGame = config().getBoolean("announce.in-game", true);
        announceDiscord = config().getBoolean("announce.discord", true);
        keepArchive = Math.max(1, Math.min(520, config().getInt("history-keep", 12)));

        states.clear();
        StatSnapshot snap = null;
        for (PeriodKind kind : PeriodKind.values()) {
            if (!config().getBoolean("" + kind.id, true)) continue;
            State s = load(kind);
            if (s.id == null) {
                // Primera vez: la base son los valores actuales (el periodo cuenta desde ahora)
                if (snap == null) snap = StatSnapshot.take(plugin.data());
                begin(s, snap, System.currentTimeMillis(), false);
            }
            states.put(kind, s);
        }
        if (states.isEmpty()) return; // ningún periodo activo: nada registrado

        subscribe(PlayerDataReadyEvent.class, e -> onReady(e.player()));
        subscribe(PlayerResetEvent.class, e -> {
            for (State s : states.values()) s.gains.zero(e.uuid());
        });
        long period = Math.max(10L, Durations.seconds(config(), 60L, 1, "update-interval")) * 1000L;
        asyncTimer("update", 3_000L, period, this::update);
        placeholder("weekly_", (p, token) -> resolve(PeriodKind.WEEKLY, p, token.substring(7)));
        placeholder("monthly_", (p, token) -> resolve(PeriodKind.MONTHLY, p, token.substring(8)));
        command(SubCommand.of("period").aliases("periodo", "periods")
                .executes(this::onCommand)
                .completes((sender, args) -> {
                    if (args.length == 1) return sender.hasPermission(Perms.PERIODS_ADMIN) ? List.of("weekly", "monthly", "end") : List.of("weekly", "monthly");
                    if (args.length == 2 && "end".equalsIgnoreCase(args[0])) return List.of("weekly", "monthly");
                    return List.of();
                }).build());
    }

    @Override
    protected void onDisable() {
        synchronized (this) {
            for (State s : states.values()) if (s.gains.isDirty()) save(s);
        }
        states.clear();
    }

    // ------------------------------------------------------------------ estado y archivos

    private File stateFile(PeriodKind kind) {
        return new File(dir, kind.id + ".json");
    }

    private File archiveFile(PeriodKind kind) {
        return new File(dir, kind.id + "_archive.json");
    }

    private State load(PeriodKind kind) {
        State s = new State(kind);
        JsonObject root = JsonFiles.read(stateFile(kind));
        if (root != null && root.has("id")) {
            s.id = root.get("id").getAsString();
            s.start = root.has("start") ? root.get("start").getAsLong() : 0;
            s.end = root.has("end") ? root.get("end").getAsLong() : 0;
            s.gains.read(root);
        }
        JsonObject arch = JsonFiles.read(archiveFile(kind));
        if (arch != null && arch.has("entries") && arch.get("entries").isJsonArray()) s.archive = arch.getAsJsonArray("entries");
        return s;
    }

    private void save(State s) {
        JsonObject root = new JsonObject();
        root.addProperty("id", s.id);
        root.addProperty("start", s.start);
        root.addProperty("end", s.end);
        s.gains.write(root);
        try {
            JsonFiles.write(stateFile(s.kind), root, false);
            s.gains.markSaved();
        } catch (IOException e) {
            log().warning("Periodos: no se pudo guardar " + s.kind.id + ": " + e.getMessage());
        }
    }

    private void begin(State s, StatSnapshot snap, long now, boolean keepId) {
        LocalDate today = LocalDate.now(zone);
        String id = PeriodMath.id(s.kind, today, weekStart);
        s.start = keepId && id.equals(s.id) ? now : (s.id == null ? now : PeriodMath.startMillis(s.kind, today, weekStart, zone));
        s.id = id;
        s.end = PeriodMath.endMillis(s.kind, today, weekStart, zone);
        s.gains.restart(snap);
        save(s);
    }

    private void onReady(Player p) {
        double[] v = plugin.data().read(p.getUniqueId(), StatSnapshot::values);
        if (v == null) return;
        for (State s : states.values()) s.gains.seen(p.getUniqueId(), v);
    }

    // ------------------------------------------------------------------ cálculo periódico

    /** Recalcula los tops y detecta el fin del periodo (hilo asíncrono). */
    private synchronized void update() {
        if (states.isEmpty()) return;
        StatSnapshot snap = StatSnapshot.take(plugin.data());
        String today = LocalDate.now(zone).toString();
        for (State s : states.values()) {
            String currentId = PeriodMath.id(s.kind, LocalDate.parse(today), weekStart);
            if (!currentId.equals(s.id)) {
                endPeriod(s.kind, false, null);
                continue;
            }
            s.gains.compute(snap, tracked, BOARD_SIZE);
            if (s.gains.isDirty()) save(s);
        }
    }

    // ------------------------------------------------------------------ fin de periodo

    /**
     * Termina el periodo: primero vuelca a la caché a los jugadores conectados (hilo principal),
     * luego calcula y archiva en un hilo de trabajo. {@code forced}: sin esperar a la fecha.
     * @return false si ese tipo no está activo o ya se está cerrando
     */
    public boolean endPeriod(PeriodKind kind, boolean forced, CommandSender notify) {
        if (!states.containsKey(kind) || !ending.add(kind)) return false;
        plugin.scheduler().sync(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) plugin.data().sync(p);
            plugin.scheduler().async(() -> {
                try {
                    finish(kind, forced);
                    if (notify != null) plugin.scheduler().sync(() -> i18n().send(notify, "periods.ended-forced", "period", periodName(notify, kind)));
                } finally {
                    ending.remove(kind);
                }
            });
        });
        return true;
    }

    private synchronized void finish(PeriodKind kind, boolean forced) {
        State s = states.get(kind);
        if (s == null) return;
        StatSnapshot snap = StatSnapshot.take(plugin.data());
        GainTracker.Board b = s.gains.compute(snap, tracked, BOARD_SIZE);
        long now = System.currentTimeMillis();

        JsonObject result = new JsonObject();
        result.addProperty("id", s.id);
        result.addProperty("start", s.start);
        result.addProperty("end", forced ? now : s.end);
        result.addProperty("ended_at", now);
        result.addProperty("forced", forced);
        Map<StatType, List<PeriodMath.Row>> podium = new LinkedHashMap<>();
        for (StatType t : tracked) {
            List<PeriodMath.Row> rows = b.top(t);
            podium.put(t, List.copyOf(rows.subList(0, Math.min(announceTop, rows.size()))));
        }
        result.add("winners", GainTracker.winners(b, tracked, 10));

        JsonArray archive = new JsonArray();
        archive.add(result);
        for (JsonElement e : s.archive) {
            if (archive.size() >= keepArchive) break;
            archive.add(e);
        }
        s.archive = archive;
        JsonObject arch = new JsonObject();
        arch.add("entries", archive);
        try {
            JsonFiles.write(archiveFile(kind), arch, true);
        } catch (IOException e) {
            log().warning("Periodos: no se pudo archivar " + kind.id + ": " + e.getMessage());
        }
        String endedId = s.id;
        begin(s, snap, now, forced);
        log().info("Periodo " + kind.id + " " + endedId + " terminado" + (forced ? " (forzado)" : "") + ". Nuevo: " + s.id);
        events().post(new PeriodEndedEvent(kind, result.deepCopy()));
        plugin.scheduler().sync(() -> announce(kind, podium));
    }

    private String periodName(CommandSender to, PeriodKind kind) {
        return i18n().tr(to, "periods.names." + kind.id);
    }

    private String statName(String lang, StatType t) {
        return i18n().tr(lang, "stats.names." + t.key);
    }

    /** Hilo principal: mensajes por jugador en su idioma, Discord y recompensas. */
    private void announce(PeriodKind kind, Map<StatType, List<PeriodMath.Row>> podium) {
        boolean any = podium.values().stream().anyMatch(l -> !l.isEmpty());
        if (announceInGame) {
            List<CommandSender> targets = new ArrayList<>(Bukkit.getOnlinePlayers());
            targets.add(Bukkit.getConsoleSender());
            for (CommandSender to : targets) {
                for (String line : lines(i18n().locale(to), kind, podium, any)) to.sendMessage(Text.color(line));
            }
        }
        if (announceDiscord) {
            DiscordWebhookModule discord = plugin.modules().get(DiscordWebhookModule.class);
            if (discord != null && discord.isEnabled()) {
                String lang = i18n().defaultLanguage();
                List<String> lines = lines(lang, kind, podium, any);
                String title = Text.strip(Text.color(lines.get(0)));
                StringBuilder body = new StringBuilder();
                for (int i = 1; i < lines.size(); i++) body.append(Text.strip(Text.color(lines.get(i)))).append('\n');
                String first = podium.values().stream().filter(l -> !l.isEmpty()).map(l -> l.get(0).name()).findFirst().orElse("MHF_Question");
                discord.send(title, body.toString().trim(), first);
            }
        }
        giveRewards(kind, podium);
    }

    private List<String> lines(String lang, PeriodKind kind, Map<StatType, List<PeriodMath.Row>> podium, boolean any) {
        List<String> out = new ArrayList<>();
        out.add(i18n().tr(lang, "periods.ended.header", "period", i18n().tr(lang, "periods.names." + kind.id)));
        if (!any) {
            out.add(i18n().tr(lang, "periods.ended.empty"));
            return out;
        }
        for (Map.Entry<StatType, List<PeriodMath.Row>> e : podium.entrySet()) {
            if (e.getValue().isEmpty()) continue;
            out.add(i18n().tr(lang, "periods.ended.stat", "stat", statName(lang, e.getKey())));
            int pos = 1;
            for (PeriodMath.Row r : e.getValue()) {
                out.add(i18n().tr(lang, "periods.ended.line", "position", pos++, "player", r.name(), "value", e.getKey().format(r.value())));
            }
        }
        return out;
    }

    private void giveRewards(PeriodKind kind, Map<StatType, List<PeriodMath.Row>> podium) {
        ConfigurationSection sec = config().getConfigurationSection("rewards." + kind.id);
        if (sec == null) return;
        for (List<PeriodMath.Row> rows : podium.values()) {
            for (int i = 0; i < rows.size(); i++) {
                List<String> commands = sec.getStringList(String.valueOf(i + 1));
                PeriodMath.Row r = rows.get(i);
                if (commands.isEmpty()) continue;
                plugin.rewards().giveLines(Bukkit.getPlayer(r.uuid()), r.name(), commands, "periods.rewards." + kind.id);
            }
        }
    }

    // ------------------------------------------------------------------ comando

    private void onCommand(CommandSender sender, String[] args) {
        if (args.length >= 1 && "end".equalsIgnoreCase(args[0])) {
            if (!sender.hasPermission(Perms.PERIODS_ADMIN)) {
                i18n().send(sender, "general.no-permission");
                return;
            }
            PeriodKind kind = args.length >= 2 ? PeriodKind.parse(args[1]) : null;
            if (kind == null || !states.containsKey(kind)) {
                i18n().send(sender, "periods.usage");
                return;
            }
            if (endPeriod(kind, true, sender)) i18n().send(sender, "periods.ending", "period", periodName(sender, kind));
            return;
        }
        List<PeriodKind> kinds = new ArrayList<>();
        if (args.length >= 1) {
            PeriodKind k = PeriodKind.parse(args[0]);
            if (k == null || !states.containsKey(k)) {
                i18n().send(sender, "periods.usage");
                return;
            }
            kinds.add(k);
        } else {
            kinds.addAll(states.keySet());
        }
        String lang = i18n().locale(sender);
        UUID self = sender instanceof Player p ? p.getUniqueId() : null;
        for (PeriodKind k : kinds) {
            State s = states.get(k);
            sender.sendMessage(Text.color(i18n().tr(lang, "periods.show.header", "period", periodName(sender, k),
                    "time", remaining(lang, s.end - System.currentTimeMillis()))));
            GainTracker.Board b = s.gains.board();
            for (StatType t : tracked) {
                List<PeriodMath.Row> rows = b.top(t);
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(3, rows.size()); i++) {
                    if (i > 0) sb.append("&8, ");
                    sb.append("&f").append(rows.get(i).name()).append(" &7(").append(t.format(rows.get(i).value())).append(")");
                }
                String own = "";
                if (self != null) {
                    int[] r = b.ranks().get(self);
                    double[] g = b.gains().get(self);
                    if (r != null && r[t.ordinal()] > 0) own = i18n().tr(lang, "periods.show.you", "position", r[t.ordinal()], "value", t.format(g[t.ordinal()]));
                }
                sender.sendMessage(Text.color(i18n().tr(lang, "periods.show.line", "stat", statName(lang, t),
                        "player", rows.isEmpty() ? "-" : sb.toString()) + own));
            }
        }
    }

    /** "3d 4h" / "5h 10m" en el idioma del jugador. */
    public String remaining(String lang, long ms) {
        long m = Math.max(0, ms) / 60_000L;
        long d = m / 1440, h = (m % 1440) / 60, min = m % 60;
        return d > 0 ? i18n().tr(lang, "periods.time.dh", "days", d, "hours", h)
                : i18n().tr(lang, "periods.time.hm", "hours", h, "minutes", min);
    }

    // ------------------------------------------------------------------ placeholders y API

    /** {@code <stat>_<pos>_<name|value>} o {@code <stat>} (ganancia propia). */
    private String resolve(PeriodKind kind, OfflinePlayer player, String rest) {
        State s = states.get(kind);
        if (s == null) return "";
        Matcher m = POSITION.matcher(rest);
        if (m.matches()) {
            StatType t = StatType.parse(m.group(1));
            if (t == null) return null;
            int pos = Integer.parseInt(m.group(2));
            List<PeriodMath.Row> rows = s.gains.board().top(t);
            if (pos < 1 || pos > rows.size()) return "name".equals(m.group(3)) ? "-" : "0";
            PeriodMath.Row r = rows.get(pos - 1);
            return "name".equals(m.group(3)) ? r.name() : t.format(r.value());
        }
        StatType t = StatType.parse(rest);
        if (t == null) return null;
        if (player == null) return "";
        double[] g = s.gains.board().gains().get(player.getUniqueId());
        return t.format(g == null ? 0 : g[t.ordinal()]);
    }

    public List<StatType> tracked() {
        return tracked;
    }

    public boolean isActive(PeriodKind kind) {
        return states.containsKey(kind);
    }

    /** Ganancia y posición de un jugador en un periodo (para la GUI). null si no está activo. */
    public double[] gains(PeriodKind kind, UUID uuid) {
        State s = states.get(kind);
        return s == null ? null : s.gains.gains(uuid);
    }

    public int[] ranks(PeriodKind kind, UUID uuid) {
        State s = states.get(kind);
        return s == null ? null : s.gains.ranks(uuid);
    }

    public long endsAt(PeriodKind kind) {
        State s = states.get(kind);
        return s == null ? 0 : s.end;
    }

    /**
     * JSON para /api/periods: por tipo {enabled, id, start, end, stats, boards{stat:[{name,value}]},
     * last (último periodo archivado)}; con {@code player} añade {stat:{value, rank}}.
     */
    public JsonObject json(UUID player, int limit) {
        JsonObject out = new JsonObject();
        for (PeriodKind kind : PeriodKind.values()) {
            State s = states.get(kind);
            JsonObject o = new JsonObject();
            o.addProperty("enabled", s != null);
            if (s != null) {
                o.addProperty("id", s.id);
                o.addProperty("start", s.start);
                o.addProperty("end", s.end);
                JsonArray stats = new JsonArray();
                JsonObject boards = new JsonObject();
                GainTracker.Board b = s.gains.board();
                for (StatType t : tracked) {
                    stats.add(t.key);
                    JsonArray arr = new JsonArray();
                    List<PeriodMath.Row> rows = b.top(t);
                    for (PeriodMath.Row r : rows.subList(0, Math.min(limit, rows.size()))) {
                        JsonObject row = new JsonObject();
                        row.addProperty("name", r.name());
                        row.addProperty("value", r.value());
                        arr.add(row);
                    }
                    boards.add(t.key, arr);
                }
                o.add("stats", stats);
                o.add("boards", boards);
                JsonArray arch = s.archive;
                if (arch.size() > 0) o.add("last", arch.get(0).deepCopy());
                if (player != null) {
                    JsonObject me = new JsonObject();
                    double[] g = b.gains().get(player);
                    int[] r = b.ranks().get(player);
                    for (StatType t : tracked) {
                        JsonObject x = new JsonObject();
                        x.addProperty("value", g == null ? 0 : g[t.ordinal()]);
                        x.addProperty("rank", r == null ? 0 : r[t.ordinal()]);
                        me.add(t.key, x);
                    }
                    o.add("player", me);
                }
            }
            out.add(kind.id, o);
        }
        return out;
    }

    /** Top actual de un periodo (para hologramas); vacío si no está activo. */
    public List<PeriodMath.Row> top(PeriodKind kind, StatType t) {
        State s = states.get(kind);
        return s == null ? List.of() : s.gains.board().top(t);
    }

    /** Archivo de periodos terminados (más reciente primero). */
    public JsonArray archive(PeriodKind kind) {
        State s = states.get(kind);
        return s == null ? new JsonArray() : s.archive.deepCopy();
    }


}
