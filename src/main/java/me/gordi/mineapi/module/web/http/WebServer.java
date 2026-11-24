package me.gordi.mineapi.module.web.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import me.gordi.mineapi.core.log.DebugLog;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/** Envoltorio fino del HttpServer del JDK con un pool fijo de hilos daemon. */
public final class WebServer {

    /** Manejador de una ruta. Las excepciones se convierten en 500. */
    @FunctionalInterface
    public interface Route {
        void handle(HttpExchange t) throws Exception;
    }

    private final HttpServer server;
    private final ExecutorService pool;
    private final Logger log;
    private final DebugLog debug;

    public WebServer(String bindAddress, int port, int threads, Logger log, DebugLog debug) throws IOException {
        InetSocketAddress address = bindAddress == null || bindAddress.isEmpty() || bindAddress.equals("0.0.0.0")
                ? new InetSocketAddress(port) : new InetSocketAddress(bindAddress, port);
        this.server = HttpServer.create(address, 0);
        this.pool = Executors.newFixedThreadPool(threads, daemonFactory("LosPerris-Web"));
        this.log = log;
        this.debug = debug;
        server.setExecutor(pool);
    }

    public static ThreadFactory daemonFactory(String prefix) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, prefix + "-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    public WebServer route(String path, Route route) {
        server.createContext(path, t -> {
            try {
                route.handle(t);
            } catch (Exception e) {
                log.warning("[Web] Error en " + t.getRequestURI().getPath() + ": " + e);
                debug.log(DebugLog.Category.WEB, "Traza: " + e);
                try {
                    Http.json(t, 500, Http.error("Internal error"), null);
                } catch (Exception ignored) {
                    // la respuesta ya había empezado
                }
            } finally {
                // SSE mantiene el intercambio abierto: lo cierra el propio hub
                if (!"text/event-stream; charset=UTF-8".equals(t.getResponseHeaders().getFirst("Content-Type"))) t.close();
            }
        });
        return this;
    }

    /** Quita una ruta (módulos que se desactivan en caliente). */
    public void unroute(String path) {
        try {
            server.removeContext(path);
        } catch (IllegalArgumentException ignored) {
            // no existía
        }
    }

    public void start() {
        server.start();
    }

    public void stop() {
        server.stop(0);
        pool.shutdownNow();
    }
}
