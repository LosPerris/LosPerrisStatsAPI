package me.gordi.mineapi.core.scheduler;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Planificador central.
 * <ul>
 *   <li>Tareas del hilo principal (Bukkit) y asíncronas (pool propio, 2 hilos daemon, creado
 *       solo cuando un módulo lo necesita).</li>
 *   <li>Cada tarea repetitiva tiene un dueño: al desactivar un módulo se cancelan todas las suyas.</li>
 *   <li>Mide el tiempo de cada tarea repetitiva (/lpa timings) con un coste de dos nanoTime().</li>
 * </ul>
 */
public final class TaskScheduler {

    /** Estadísticas acumuladas de una tarea. */
    public static final class Timing {
        private long runs;
        private long totalNanos;
        private long maxNanos;
        public final boolean async;

        Timing(boolean async) {
            this.async = async;
        }

        synchronized void record(long nanos) {
            runs++;
            totalNanos += nanos;
            if (nanos > maxNanos) maxNanos = nanos;
        }

        /** Pone a cero los contadores sin sustituir el objeto (las tareas ya programadas lo siguen usando). */
        synchronized void reset() {
            runs = 0;
            totalNanos = 0;
            maxNanos = 0;
        }

        public synchronized long runs() { return runs; }
        public synchronized double avgMs() { return runs == 0 ? 0 : totalNanos / 1e6 / runs; }
        public synchronized double maxMs() { return maxNanos / 1e6; }
    }

    private final Plugin plugin;
    private final Logger logger;
    private final Map<Object, List<Object>> owned = new ConcurrentHashMap<>();
    private final Map<String, Timing> timings = new ConcurrentHashMap<>();
    private volatile ScheduledExecutorService worker;

    public TaskScheduler(Plugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    private Runnable timed(String name, boolean async, Runnable task) {
        Timing timing = timings.computeIfAbsent(name, k -> new Timing(async));
        return () -> {
            long start = System.nanoTime();
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error en la tarea '" + name + "'", t);
            } finally {
                timing.record(System.nanoTime() - start);
            }
        };
    }

    private void track(Object owner, Object handle) {
        owned.computeIfAbsent(owner, k -> java.util.Collections.synchronizedList(new ArrayList<>())).add(handle);
    }

    /** Tarea repetitiva en el hilo principal (en ticks). */
    public BukkitTask syncTimer(Object owner, String name, long delayTicks, long periodTicks, Runnable task) {
        BukkitTask t = Bukkit.getScheduler().runTaskTimer(plugin, timed(name, false, task), delayTicks, Math.max(1L, periodTicks));
        track(owner, t);
        return t;
    }

    /** Tarea repetitiva asíncrona (en milisegundos). No debe tocar la API de Bukkit. */
    public Future<?> asyncTimer(Object owner, String name, long delayMs, long periodMs, Runnable task) {
        Future<?> f = worker().scheduleWithFixedDelay(timed(name, true, task), delayMs, Math.max(50L, periodMs), TimeUnit.MILLISECONDS);
        track(owner, f);
        return f;
    }

    /** Trabajo puntual asíncrono (cálculos, E/S ligera). */
    public void async(Runnable task) {
        worker().execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                logger.log(Level.WARNING, "Error en tarea asíncrona", t);
            }
        });
    }

    /** Ejecuta en el hilo principal (directamente si ya se está en él). */
    public void sync(Runnable task) {
        if (Bukkit.isPrimaryThread()) task.run();
        else Bukkit.getScheduler().runTask(plugin, task);
    }

    public void later(long ticks, Runnable task) {
        Bukkit.getScheduler().runTaskLater(plugin, task, Math.max(1L, ticks));
    }

    /** Cancela todas las tareas repetitivas de un dueño. */
    public void cancelAll(Object owner) {
        List<Object> list = owned.remove(owner);
        if (list == null) return;
        synchronized (list) {
            for (Object h : list) {
                if (h instanceof BukkitTask) ((BukkitTask) h).cancel();
                else if (h instanceof Future) ((Future<?>) h).cancel(false);
            }
        }
    }

    private ScheduledExecutorService worker() {
        ScheduledExecutorService w = worker;
        if (w == null) {
            synchronized (this) {
                w = worker;
                if (w == null) {
                    AtomicInteger n = new AtomicInteger();
                    ScheduledThreadPoolExecutor ex = new ScheduledThreadPoolExecutor(2, r -> {
                        Thread t = new Thread(r, "LosPerris-Worker-" + n.incrementAndGet());
                        t.setDaemon(true);
                        t.setPriority(Thread.NORM_PRIORITY - 1);
                        return t;
                    });
                    ex.setRemoveOnCancelPolicy(true);
                    worker = w = ex;
                }
            }
        }
        return w;
    }

    /** Copia ordenada de las mediciones. */
    public Map<String, Timing> timings() {
        return new TreeMap<>(timings);
    }

    /**
     * Reinicia las mediciones. No vacía el mapa: cada tarea repetitiva guarda su {@link Timing}
     * al programarse, así que quitarlo del mapa la haría invisible en /lpa timings para siempre.
     */
    public void resetTimings() {
        timings.values().forEach(Timing::reset);
    }

    public void shutdown() {
        for (Object owner : new ArrayList<>(owned.keySet())) cancelAll(owner);
        ScheduledExecutorService w = worker;
        if (w != null) {
            w.shutdown();
            try {
                w.awaitTermination(3, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            worker = null;
        }
    }
}
