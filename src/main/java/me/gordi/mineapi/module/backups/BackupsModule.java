package me.gordi.mineapi.module.backups;

import me.gordi.mineapi.core.config.ConfigLayout;
import me.gordi.mineapi.core.module.Perms;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.core.command.SubCommand;
import me.gordi.mineapi.core.module.AbstractModule;
import me.gordi.mineapi.core.time.Zones;
import org.bukkit.command.CommandSender;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Copias de seguridad diarias en backups/data/: un zip con players.json (exportación de todos
 * los jugadores, sea cual sea el almacenamiento), las carpetas configuradas (data...) y los
 * .yml de configuración. Se conservan las últimas {@code backups.keep}. Todo en un hilo aparte.
 */
public final class BackupsModule extends AbstractModule {

    public static final String ID = "backups";

    private final AtomicBoolean running = new AtomicBoolean();
    private volatile long nextRunMs;
    private volatile JsonObject lastResult;
    private ZoneId zone;
    private LocalTime at;

    public BackupsModule(MineAPI plugin) {
        super(plugin, ID);
    }

    @Override
    protected void onEnable() {
        zone = Zones.of(settings().getString("timezone", ""));
        at = BackupPlan.parseTime(config().getString("time", "04:00"));
        nextRunMs = BackupPlan.nextRun(ZonedDateTime.now(zone), at).toInstant().toEpochMilli();
        // Revisión cada minuto (no hace nada salvo comparar la hora)
        asyncTimer("schedule", 60_000L, 60_000L, this::tick);
        // Si el servidor estuvo apagado a la hora prevista y la última copia tiene más de 25 h
        File newest = newest();
        if (config().getBoolean("catch-up", true)
                && (newest == null ? plugin.data().size() > 0 : System.currentTimeMillis() - newest.lastModified() > 25L * 3600_000L)) {
            scheduler().later(20L * 120, () -> run(null));
        }
        command(SubCommand.of("backup")
                .permission(Perms.BACKUPS_ADMIN)
                .executes(this::onCommand)
                .completes((s, a) -> a.length == 1 ? List.of("now", "list") : List.of()).build());
    }

    @Override
    protected void onDisable() {
        // las copias en curso terminan solas (hilo de I/O); no se cancela un zip a medias
    }

    private void tick() {
        if (System.currentTimeMillis() < nextRunMs) return;
        nextRunMs = BackupPlan.nextRun(ZonedDateTime.now(zone), at).toInstant().toEpochMilli();
        run(null);
    }

    // ------------------------------------------------------------------ copia

    /**
     * Crea una copia en segundo plano. {@code done} recibe el resultado en el hilo principal
     * (puede ser null). @return false si ya había una en curso
     */
    public boolean run(Consumer<JsonObject> done) {
        if (!running.compareAndSet(false, true)) return false;
        // La exportación de jugadores se copia bajo el lock del servicio (rápido); el zip va aparte
        scheduler().async(() -> {
            JsonObject result;
            try {
                result = create();
            } catch (Exception e) {
                result = new JsonObject();
                result.addProperty("ok", false);
                result.addProperty("error", e.getClass().getSimpleName() + ": " + e.getMessage());
                log().warning("No se pudo crear la copia de seguridad: " + e.getMessage());
            } finally {
                running.set(false);
            }
            lastResult = result;
            if (done != null) {
                JsonObject r = result;
                scheduler().sync(() -> done.accept(r));
            }
        });
        return true;
    }

    private JsonObject create() throws IOException {
        long start = System.currentTimeMillis();
        File dir = dir();
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no se pudo crear " + dir);
        String name = BackupPlan.fileName(ZonedDateTime.now(zone));
        File out = new File(dir, name);
        for (int i = 1; out.exists(); i++) out = new File(dir, name.replace(".zip", "-" + i + ".zip"));
        File tmp = new File(dir, out.getName() + ".tmp");
        File root = plugin.getDataFolder();
        int files = 0;
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmp.toPath()))) {
            zip.putNextEntry(new ZipEntry("players.json"));
            byte[] players = plugin.data().nameKeyedSnapshot(false).toString().getBytes(StandardCharsets.UTF_8);
            zip.write(players);
            zip.closeEntry();
            files++;
            if (config().getBoolean("include-configs", true)) {
                File[] ymls = root.listFiles((d, n) -> n.endsWith(".yml"));
                if (ymls != null) {
                    for (File f : ymls) files += add(zip, root.toPath(), f.toPath());
                }
                for (String sub : new String[]{"lang", ConfigLayout.SETTINGS_DIR}) {
                    File[] subYmls = new File(root, sub).listFiles((d, n) -> n.endsWith(".yml"));
                    if (subYmls != null) {
                        for (File f : subYmls) files += add(zip, root.toPath(), f.toPath());
                    }
                }
            }
            Path backupsRoot = dir.toPath().getParent();
            for (String folder : config().getStringList("folders")) {
                Path p = root.toPath().resolve(folder).normalize();
                if (!p.startsWith(root.toPath()) || p.startsWith(backupsRoot)) continue; // nada fuera ni backups/
                if (Files.isDirectory(p)) {
                    try (Stream<Path> walk = Files.walk(p)) {
                        // "." u otra carpeta padre: nunca se copian las propias copias
                        for (Path f : (Iterable<Path>) walk.filter(f -> Files.isRegularFile(f) && !f.startsWith(backupsRoot))::iterator) {
                            files += add(zip, root.toPath(), f);
                        }
                    }
                }
            }
        }
        Files.move(tmp.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        int deleted = rotate();
        JsonObject r = new JsonObject();
        r.addProperty("ok", true);
        r.addProperty("file", out.getName());
        r.addProperty("size", out.length());
        r.addProperty("files", files);
        r.addProperty("deleted", deleted);
        r.addProperty("ms", System.currentTimeMillis() - start);
        r.addProperty("time", System.currentTimeMillis());
        log().info("Copia de seguridad creada: backups/data/" + out.getName() + " (" + files + " archivos, "
                + (out.length() / 1024) + " KB, " + r.get("ms").getAsLong() + " ms)");
        return r;
    }

    private static int add(ZipOutputStream zip, Path root, Path file) throws IOException {
        if (file.getFileName().toString().endsWith(".tmp")) return 0;
        try {
            zip.putNextEntry(new ZipEntry(root.relativize(file).toString().replace('\\', '/')));
            Files.copy(file, (OutputStream) zip);
            zip.closeEntry();
            return 1;
        } catch (IOException e) {
            // archivo bloqueado o borrado mientras se copiaba: se omite
            return 0;
        }
    }

    private int rotate() {
        String[] names = dir().list();
        if (names == null) return 0;
        int n = 0;
        for (String old : BackupPlan.toDelete(Arrays.asList(names), config().getInt("keep", 7))) {
            if (new File(dir(), old).delete()) n++;
        }
        return n;
    }

    // ------------------------------------------------------------------ consultas

    public JsonObject status() {
        JsonObject o = new JsonObject();
        JsonArray arr = new JsonArray();
        for (File f : list()) {
            JsonObject b = new JsonObject();
            b.addProperty("file", f.getName());
            b.addProperty("size", f.length());
            b.addProperty("time", f.lastModified());
            arr.add(b);
        }
        o.add("backups", arr);
        o.addProperty("keep", Math.max(1, config().getInt("keep", 7)));
        o.addProperty("next_run", nextRunMs);
        o.addProperty("running", running.get());
        if (lastResult != null) o.add("last", lastResult);
        return o;
    }

    /** Copias existentes, la más reciente primero. */
    public List<File> list() {
        File[] files = dir().listFiles((d, n) -> BackupPlan.isBackupName(n));
        List<File> out = new ArrayList<>(files == null ? List.of() : Arrays.asList(files));
        out.sort(Comparator.comparing(File::getName).reversed());
        return out;
    }

    private File newest() {
        List<File> l = list();
        return l.isEmpty() ? null : l.get(0);
    }

    private File dir() {
        return new File(plugin.getDataFolder(), "backups/data");
    }

    // ------------------------------------------------------------------ comando

    private void onCommand(CommandSender sender, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            List<File> l = list();
            i18n().send(sender, "backups.list-header", "count", l.size());
            for (File f : l) i18n().send(sender, "backups.list-line", "value", f.getName(), "size", (f.length() / 1024) + " KB");
            return;
        }
        if (!run(r -> {
            if (r.get("ok").getAsBoolean()) {
                i18n().send(sender, "backups.done", "value", r.get("file").getAsString(), "size", (r.get("size").getAsLong() / 1024) + " KB");
            } else {
                i18n().send(sender, "backups.failed", "value", r.get("error").getAsString());
            }
        })) {
            i18n().send(sender, "backups.running");
            return;
        }
        i18n().send(sender, "backups.started");
    }
}
