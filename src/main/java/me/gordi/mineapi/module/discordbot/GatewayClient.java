package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonObject;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Conexión al gateway de Discord con el WebSocket del JDK (sin librerías). Hace HELLO →
 * IDENTIFY/RESUME, latidos, reconexión con espera creciente (máx. 60 s) y entrega los dispatch
 * al {@link Handler} en su propio hilo (nunca en el hilo principal del servidor).
 */
public final class GatewayClient {

    /** Receptor de eventos (hilo del gateway). */
    public interface Handler {
        void onReady(JsonObject data);

        void onDispatch(String type, JsonObject data);
    }

    /** Estado visible en la web. */
    public enum Status { STOPPED, CONNECTING, READY, RECONNECTING, FAILED }

    private final String token;
    private final int intents;
    private final String baseUrl;
    private final Handler handler;
    private final Consumer<String> log;
    private final ScheduledExecutorService exec;
    private final HttpClient http;

    private volatile Status status = Status.STOPPED;
    private volatile String failure;
    private volatile WebSocket socket;
    private volatile ScheduledFuture<?> beat;
    private volatile boolean acked = true;
    private volatile boolean stopped;
    private volatile Long seq;
    private volatile String session;
    private volatile String resumeUrl;
    private volatile long lastBeatSent;
    private volatile long ping = -1;
    private int failures;

    public GatewayClient(String token, int intents, String baseUrl, Handler handler, Consumer<String> log) {
        this.token = token;
        this.intents = intents;
        this.baseUrl = baseUrl;
        this.handler = handler;
        this.log = log;
        this.exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LosPerris-DiscordGateway");
            t.setDaemon(true);
            return t;
        });
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).executor(exec).build();
    }

    public void start() {
        stopped = false;
        exec.execute(() -> connect(GatewayProtocol.url(baseUrl)));
    }

    public void stop() {
        stopped = true;
        status = Status.STOPPED;
        ScheduledFuture<?> b = beat;
        if (b != null) b.cancel(false);
        WebSocket ws = socket;
        if (ws != null) {
            try {
                ws.sendClose(1000, "shutdown");
            } catch (RuntimeException ignored) {
                // ya cerrado
            }
            ws.abort();
        }
        exec.shutdownNow();
    }

    public Status status() {
        return status;
    }

    /** Motivo del último fallo grave (sin datos sensibles) o null. */
    public String failure() {
        return failure;
    }

    /** Latencia del último latido en ms (-1 si no hay). */
    public long ping() {
        return ping;
    }

    private void connect(String url) {
        if (stopped) return;
        status = failures == 0 ? Status.CONNECTING : Status.RECONNECTING;
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
                .buildAsync(URI.create(url), new Listener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        log.accept("Discord: no se pudo conectar al gateway: " + rootMessage(err));
                        retry(session != null);
                    }
                });
    }

    private void retry(boolean resume) {
        if (stopped) return;
        ScheduledFuture<?> b = beat;
        if (b != null) b.cancel(false);
        if (!resume) {
            session = null;
            seq = null;
        }
        failures++;
        long wait = Math.min(60_000L, 1000L * (1L << Math.min(6, failures - 1))) + ThreadLocalRandom.current().nextLong(500);
        status = Status.RECONNECTING;
        String url = resume && resumeUrl != null ? GatewayProtocol.url(resumeUrl) : GatewayProtocol.url(baseUrl);
        try {
            exec.schedule(() -> connect(url), wait, TimeUnit.MILLISECONDS);
        } catch (RuntimeException ignored) {
            // ejecutor parado
        }
    }

    private void send(String text) {
        WebSocket ws = socket;
        if (ws != null) ws.sendText(text, true);
    }

    private void handle(String text) {
        GatewayProtocol.Frame f;
        try {
            f = GatewayProtocol.parse(text);
        } catch (RuntimeException e) {
            return;
        }
        if (f.seq() != null) seq = f.seq();
        switch (f.op()) {
            case GatewayProtocol.OP_HELLO -> {
                long interval = f.object().get("heartbeat_interval").getAsLong();
                ScheduledFuture<?> old = beat;
                if (old != null) old.cancel(false);
                acked = true;
                beat = exec.scheduleAtFixedRate(this::heartbeat, (long) (interval * ThreadLocalRandom.current().nextDouble()),
                        interval, TimeUnit.MILLISECONDS);
                if (session != null && seq != null) send(GatewayProtocol.resume(token, session, seq));
                else send(GatewayProtocol.identify(token, intents));
            }
            case GatewayProtocol.OP_HEARTBEAT -> send(GatewayProtocol.heartbeat(seq));
            case GatewayProtocol.OP_HEARTBEAT_ACK -> {
                acked = true;
                ping = System.currentTimeMillis() - lastBeatSent;
            }
            case GatewayProtocol.OP_RECONNECT -> reconnect(true);
            case GatewayProtocol.OP_INVALID_SESSION -> {
                boolean resumable = f.data() != null && f.data().isJsonPrimitive() && f.data().getAsBoolean();
                reconnect(resumable);
            }
            case GatewayProtocol.OP_DISPATCH -> dispatch(f);
            default -> { }
        }
    }

    private void dispatch(GatewayProtocol.Frame f) {
        if ("READY".equals(f.type())) {
            JsonObject d = f.object();
            session = d.has("session_id") ? d.get("session_id").getAsString() : null;
            resumeUrl = d.has("resume_gateway_url") ? d.get("resume_gateway_url").getAsString() : null;
            failures = 0;
            failure = null;
            status = Status.READY;
        } else if ("RESUMED".equals(f.type())) {
            failures = 0;
            status = Status.READY;
            return;
        }
        try {
            if ("READY".equals(f.type())) handler.onReady(f.object());
            else if (f.type() != null) handler.onDispatch(f.type(), f.object());
        } catch (RuntimeException e) {
            log.accept("Discord: error procesando " + f.type() + ": " + e);
        }
    }

    private void heartbeat() {
        if (!acked) { // sin respuesta al latido anterior: conexión zombi
            reconnect(true);
            return;
        }
        acked = false;
        lastBeatSent = System.currentTimeMillis();
        send(GatewayProtocol.heartbeat(seq));
    }

    private void reconnect(boolean resume) {
        WebSocket ws = socket;
        socket = null;
        if (ws != null) ws.abort();
        retry(resume);
    }

    private static String rootMessage(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getClass().getSimpleName() + (t.getMessage() == null ? "" : ": " + t.getMessage());
    }

    private final class Listener implements WebSocket.Listener {
        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            socket = ws; // antes de pedir mensajes: HELLO necesita poder responder
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String text = buffer.toString();
                buffer.setLength(0);
                handle(text);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int code, String reason) {
            if (socket != ws) return null; // cierre de una conexión ya sustituida
            socket = null;
            if (GatewayProtocol.fatalClose(code)) {
                stopped = true;
                failure = code + (reason == null || reason.isBlank() ? "" : " " + reason);
                status = Status.FAILED; // después de failure: quien vea FAILED ya lee el motivo
                ScheduledFuture<?> b = beat;
                if (b != null) b.cancel(false);
                log.accept("Discord: el gateway cerró la conexión (" + failure + "). "
                        + (code == 4004 ? "Token inválido." : code == 4014 ? "Active el intent MESSAGE CONTENT en el portal de desarrolladores." : "Revise la configuración."));
                return null;
            }
            retry(code != 4007 && code != 4009); // 4007/4009: sesión no reanudable
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            if (socket != ws && socket != null) return;
            socket = null;
            log.accept("Discord: error en el gateway: " + rootMessage(error));
            retry(true);
        }
    }
}
