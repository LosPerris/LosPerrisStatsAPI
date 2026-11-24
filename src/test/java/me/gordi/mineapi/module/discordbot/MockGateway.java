package me.gordi.mineapi.module.discordbot;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Servidor WebSocket mínimo (RFC 6455, un cliente a la vez) que hace de gateway de Discord en las pruebas. */
final class MockGateway implements AutoCloseable {

    private final ServerSocket server;
    private final BlockingQueue<String> received = new LinkedBlockingQueue<>();
    private final BlockingQueue<Socket> clients = new LinkedBlockingQueue<>();
    private volatile Socket client;
    /** Responde solo a los latidos (como Discord); si no, el cliente cree que la conexión murió. */
    volatile boolean autoAck = true;
    private final Thread acceptor;

    MockGateway() throws IOException {
        server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress());
        acceptor = new Thread(this::acceptLoop, "mock-gateway");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    String url() {
        return "ws://127.0.0.1:" + server.getLocalPort() + "/";
    }

    private void acceptLoop() {
        while (!server.isClosed()) {
            try {
                Socket s = server.accept();
                handshake(s);
                client = s;
                clients.add(s);
                Thread r = new Thread(() -> readLoop(s), "mock-gateway-read");
                r.setDaemon(true);
                r.start();
            } catch (Exception e) {
                return;
            }
        }
    }

    private static void handshake(Socket s) throws Exception {
        DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        String key = null;
        StringBuilder line = new StringBuilder();
        while (true) {
            int c = in.read();
            if (c < 0) throw new IOException("eof");
            if (c == '\n') {
                String l = line.toString().trim();
                line.setLength(0);
                if (l.isEmpty()) break;
                if (l.toLowerCase().startsWith("sec-websocket-key:")) key = l.substring(18).trim();
            } else {
                line.append((char) c);
            }
        }
        String accept = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1")
                .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.US_ASCII)));
        s.getOutputStream().write(("HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + accept + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        s.getOutputStream().flush();
    }

    private void readLoop(Socket s) {
        try {
            DataInputStream in = new DataInputStream(s.getInputStream());
            while (true) {
                int b0 = in.readUnsignedByte(), b1 = in.readUnsignedByte();
                long len = b1 & 0x7f;
                if (len == 126) len = in.readUnsignedShort();
                else if (len == 127) len = in.readLong();
                byte[] mask = new byte[4];
                if ((b1 & 0x80) != 0) in.readFully(mask);
                byte[] data = new byte[(int) len];
                in.readFully(data);
                for (int i = 0; i < data.length; i++) data[i] ^= mask[i % 4];
                int op = b0 & 0x0f;
                if (op == 1) {
                    String text = new String(data, StandardCharsets.UTF_8);
                    if (autoAck && text.startsWith("{\"op\":1,")) send("{\"op\":11}");
                    received.add(text);
                }
                else if (op == 8) return;
            }
        } catch (IOException ignored) {
            // conexión cerrada
        }
    }

    synchronized void send(String text) throws IOException {
        frame(1, text.getBytes(StandardCharsets.UTF_8));
    }

    synchronized void close(int code, String reason) throws IOException {
        byte[] r = reason.getBytes(StandardCharsets.UTF_8);
        byte[] p = new byte[2 + r.length];
        p[0] = (byte) (code >> 8);
        p[1] = (byte) code;
        System.arraycopy(r, 0, p, 2, r.length);
        frame(8, p);
    }

    /** Corta el socket sin cierre limpio (caída de red). */
    void drop() throws IOException {
        client.close();
    }

    private void frame(int op, byte[] data) throws IOException {
        OutputStream out = client.getOutputStream();
        out.write(0x80 | op);
        if (data.length < 126) {
            out.write(data.length);
        } else {
            out.write(126);
            out.write(data.length >> 8);
            out.write(data.length);
        }
        out.write(data);
        out.flush();
    }

    /** Siguiente mensaje del cliente (o null tras el tiempo de espera). */
    String next(long ms) throws InterruptedException {
        return received.poll(ms, TimeUnit.MILLISECONDS);
    }

    /** Espera una nueva conexión del cliente. */
    boolean awaitConnection(long ms) throws InterruptedException {
        return clients.poll(ms, TimeUnit.MILLISECONDS) != null;
    }

    @Override
    public void close() throws IOException {
        server.close();
        Socket c = client;
        if (c != null) c.close();
    }
}
