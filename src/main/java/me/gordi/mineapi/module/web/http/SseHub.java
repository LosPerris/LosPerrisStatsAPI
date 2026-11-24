package me.gordi.mineapi.module.web.http;

import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Clientes Server-Sent Events. Un único hilo escribe en todos los streams (nunca hay escrituras
 * concurrentes) y quien publica solo encola: jamás bloquea al hilo principal ni al del chat.
 */
public final class SseHub {

    private static final byte[] PING = ": ping\n\n".getBytes(StandardCharsets.UTF_8);
    private static final byte[] HELLO = ": connected\n\n".getBytes(StandardCharsets.UTF_8);

    private final List<Client> clients = new CopyOnWriteArrayList<>();
    private final int maxClients;
    private final ScheduledExecutorService writer;

    private record Client(HttpExchange exchange, OutputStream os) {
        void close() {
            try { os.close(); } catch (Exception ignored) { }
            try { exchange.close(); } catch (Exception ignored) { }
        }
    }

    public SseHub(int maxClients) {
        this.maxClients = maxClients;
        this.writer = Executors.newSingleThreadScheduledExecutor(WebServer.daemonFactory("LosPerris-SSE"));
        // Ping cada 10 s: mantiene vivas las conexiones y limpia las muertas
        writer.scheduleAtFixedRate(() -> writeAll(PING), 10, 10, TimeUnit.SECONDS);
    }

    public boolean isEmpty() {
        return clients.isEmpty();
    }

    /** Registra el cliente y libera el hilo HTTP de inmediato. */
    public void accept(HttpExchange t, String corsOrigin) throws IOException {
        if (clients.size() >= maxClients) {
            Http.json(t, 503, "{\"error\": \"Too many stream clients\"}", corsOrigin);
            return;
        }
        t.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        t.getResponseHeaders().set("Cache-Control", "no-cache");
        t.getResponseHeaders().set("Connection", "keep-alive");
        if (corsOrigin != null) t.getResponseHeaders().set("Access-Control-Allow-Origin", corsOrigin);
        t.sendResponseHeaders(200, 0);
        Client c = new Client(t, t.getResponseBody());
        clients.add(c);
        try {
            writer.execute(() -> write(c, HELLO));
        } catch (RejectedExecutionException e) {
            drop(c);
        }
    }

    /** Encola un evento para todos los clientes. */
    public void publish(JsonObject event) {
        if (clients.isEmpty()) return;
        byte[] bytes = ("data: " + Http.GSON.toJson(event) + "\n\n").getBytes(StandardCharsets.UTF_8);
        try {
            writer.execute(() -> writeAll(bytes));
        } catch (RejectedExecutionException ignored) {
            // apagando
        }
    }

    private void writeAll(byte[] bytes) {
        for (Client c : clients) write(c, bytes);
    }

    private void write(Client c, byte[] bytes) {
        try {
            c.os.write(bytes);
            c.os.flush();
        } catch (IOException e) {
            drop(c);
        }
    }

    private void drop(Client c) {
        clients.remove(c);
        c.close();
    }

    public void shutdown() {
        writer.shutdownNow();
        for (Client c : clients) c.close();
        clients.clear();
    }
}
