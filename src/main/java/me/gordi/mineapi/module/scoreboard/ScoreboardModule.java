package me.gordi.mineapi.module.scoreboard;

import me.gordi.mineapi.core.data.DataDir;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.event.PlayerDataReadyEvent;
import me.gordi.mineapi.core.log.DebugLog;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.text.VariableContext;
import me.gordi.mineapi.core.text.VariableValidator;
import me.gordi.mineapi.core.hook.PlaceholderApiHook;
import me.gordi.mineapi.module.stats.StatText;
import me.gordi.mineapi.module.stats.StatsModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scoreboard lateral. Las líneas se compilan una vez; en cada actualización solo se envía un
 * paquete si el texto de la línea cambió, y las líneas constantes nunca se reenvían.
 */
public final class ScoreboardModule extends AbstractModule implements Listener {

    public static final String ID = "scoreboard";
    public static final String PERMISSION = "losperris.scoreboard";
    private static final int MAX_PREFIX_LENGTH = 64;

    /** Estado del scoreboard de un jugador. */
    private static final class Board {
        final Scoreboard scoreboard;
        final Team[] teams;
        final String[] last;
        String lastTitle;
        final Objective objective;
        /** true si muestra las líneas de evento ({@code scoreboard.event-lines}). */
        boolean event;

        Board(Scoreboard scoreboard, Objective objective, int lines) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.teams = new Team[lines];
            this.last = new String[lines];
        }
    }

    private final Map<UUID, Board> boards = new HashMap<>();
    /** Jugadores que lo desactivaron con /lpa sb (persistente). */
    private final Set<UUID> disabled = new HashSet<>();
    private File disabledFile;
    private StatText.Line title;
    private StatText.Line[] lines;
    private StatText.Line[] eventLines;
    private List<String> rawLines;
    private List<String> rawEvent;
    private String rawTitle;
    private int compiledFor;
    private boolean showOnJoin;

    public ScoreboardModule(MineAPI plugin) {
        super(plugin, ID, StatsModule.ID);
    }

    @Override
    protected void onEnable() {
        StatText text = plugin.modules().get(StatsModule.class).text();
        rawTitle = config().getString("title", "&e&lLOSPERRIS");
        rawLines = config().getStringList("lines");
        VariableValidator v = new VariableValidator(log(), "settings/scoreboard.yml");
        boolean papi = PlaceholderApiHook.isAvailable();
        v.check(VariableContext.SCOREBOARD, "title", rawTitle, t -> papi);
        for (String l : rawLines) v.check(VariableContext.SCOREBOARD, "lines", l, t -> papi);

        rawEvent = config().getStringList("event-lines");
        for (String l : rawEvent) v.check(VariableContext.SCOREBOARD, "event-lines", l, t -> papi);
        compile(text);
        showOnJoin = config().getBoolean("show-on-join", true);

        disabledFile = new File(plugin.getDataFolder(), DataDir.NAME + "/scoreboard_disabled.yml");
        loadDisabled();

        listen(this);
        subscribe(PlayerDataReadyEvent.class, e -> {
            if (showOnJoin) give(e.player());
        });
        command(SubCommand.of("sb").aliases("scoreboard").permission(PERMISSION).playerOnly()
                .executes((sender, args) -> toggle((Player) sender)).build());

        long interval = Math.max(1L, config().getLong("update-interval", 20L));
        syncTimer("update", interval, interval, this::updateAll);

        // Recarga: se vuelve a dar a quien ya lo tenía (o a todos si show-on-join)
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (showOnJoin && plugin.data().isLoaded(p.getUniqueId())) give(p);
        }
    }

    @Override
    protected void onDisable() {
        Scoreboard main = Bukkit.getScoreboardManager() != null ? Bukkit.getScoreboardManager().getMainScoreboard() : null;
        for (UUID uuid : boards.keySet()) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && main != null) p.setScoreboard(main);
        }
        boards.clear();
        disabled.clear();
    }

    public boolean has(Player p) {
        return boards.containsKey(p.getUniqueId());
    }

    @SuppressWarnings("deprecation")
    public void give(Player player) {
        UUID uuid = player.getUniqueId();
        if (disabled.contains(uuid) || Bukkit.getScoreboardManager() == null) return;
        Scoreboard sb = Bukkit.getScoreboardManager().getNewScoreboard();
        String t = title.render(player);
        Objective obj = sb.registerNewObjective("LosPerris", "dummy", t);
        obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        boolean ev = eventActive();
        StatText.Line[] set = ev ? eventLines : lines;
        Board board = new Board(sb, obj, set.length);
        board.lastTitle = t;
        board.event = ev;
        for (int i = 0; i < set.length; i++) {
            Team team = sb.registerNewTeam("line" + i);
            String entry = entry(i);
            team.addEntry(entry);
            obj.getScore(entry).setScore(set.length - i);
            board.teams[i] = team;
        }
        player.setScoreboard(sb);
        boards.put(uuid, board);
        update(player, board);
    }

    private void toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (boards.remove(uuid) != null) {
            disabled.add(uuid);
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
            saveDisabled();
            i18n().send(player, "scoreboard.off");
            return;
        }
        boolean changed = disabled.remove(uuid);
        give(player);
        if (changed) saveDisabled();
        i18n().send(player, boards.containsKey(uuid) ? "scoreboard.on" : "scoreboard.failed");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        boards.remove(e.getPlayer().getUniqueId());
    }

    /**
     * Compila título y líneas. Las variables de módulos (y de addons) se resuelven al compilar, así que se
     * repite si cambian las registradas ({@link me.gordi.mineapi.core.placeholder.Placeholders#version()}).
     */
    private void compile(StatText text) {
        compiledFor = plugin.placeholders().version();
        title = text.compile(rawTitle);
        lines = new StatText.Line[rawLines.size()];
        for (int i = 0; i < lines.length; i++) lines[i] = text.compile(rawLines.get(i));
        eventLines = new StatText.Line[rawEvent.size()];
        for (int i = 0; i < eventLines.length; i++) eventLines[i] = text.compile(rawEvent.get(i));
    }

    /** Hay un evento en curso (de un addon, {@link me.gordi.mineapi.api.LosPerrisApi#eventMode}) y líneas para él. */
    private boolean eventActive() {
        return eventLines.length > 0 && plugin.api().eventMode();
    }

    private void updateAll() {
        if (compiledFor != plugin.placeholders().version()) { // un addon añadió o quitó variables
            compile(plugin.modules().get(StatsModule.class).text());
            for (UUID uuid : List.copyOf(boards.keySet())) {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) give(p);
            }
            return;
        }
        boolean ev = eventActive();
        List<Player> rebuild = null;
        for (Map.Entry<UUID, Board> e : boards.entrySet()) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) continue;
            if (e.getValue().event != ev) { // empieza o acaba un evento: cambia el número de líneas
                if (rebuild == null) rebuild = new ArrayList<>();
                rebuild.add(p);
            } else {
                update(p, e.getValue());
            }
        }
        if (rebuild != null) for (Player p : rebuild) give(p);
    }

    private void update(Player player, Board board) {
        try {
            if (!title.isConstant()) {
                String t = title.render(player);
                if (!t.equals(board.lastTitle)) {
                    board.objective.setDisplayName(t);
                    board.lastTitle = t;
                }
            }
            StatText.Line[] set = board.event ? eventLines : lines;
            for (int i = 0; i < set.length; i++) {
                if (board.last[i] != null && set[i].isConstant()) continue;
                String text = truncate(set[i].render(player));
                if (text.equals(board.last[i])) continue; // sin cambios: no se envía paquete
                board.teams[i].setPrefix(text);
                board.last[i] = text;
            }
        } catch (Exception ex) {
            plugin.debug().log(DebugLog.Category.STATS, "Error actualizando el scoreboard de " + player.getName() + ": " + ex.getMessage());
        }
    }

    private static String entry(int index) {
        ChatColor[] colors = ChatColor.values();
        if (index < colors.length) return colors[index].toString() + ChatColor.RESET;
        return colors[(index / colors.length) % colors.length].toString() + colors[index % colors.length] + ChatColor.RESET;
    }

    /** Corta a 64 caracteres sin dejar un '§' suelto al final. */
    static String truncate(String text) {
        if (text.length() <= MAX_PREFIX_LENGTH) return text;
        String cut = text.substring(0, MAX_PREFIX_LENGTH);
        return cut.charAt(cut.length() - 1) == ChatColor.COLOR_CHAR ? cut.substring(0, cut.length() - 1) : cut;
    }

    private void loadDisabled() {
        disabled.clear();
        if (!disabledFile.exists()) return;
        for (String s : YamlConfiguration.loadConfiguration(disabledFile).getStringList("disabled")) {
            try {
                disabled.add(UUID.fromString(s));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void saveDisabled() {
        List<String> list = new ArrayList<>();
        for (UUID uuid : disabled) list.add(uuid.toString());
        File file = disabledFile;
        scheduler().async(() -> {
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.set("disabled", list);
            try {
                File parent = file.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();
                yaml.save(file);
            } catch (IOException e) {
                log().warning("No se pudo guardar scoreboard_disabled.yml: " + e.getMessage());
            }
        });
    }
}
