package me.gordi.mineapi.module.discordbot;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Mensajes del gateway de Discord (v10, JSON, sin compresión). Puro: se prueba con JUnit y con
 * un gateway simulado. Nunca devuelve el token en {@link #describe}.
 */
public final class GatewayProtocol {

    public static final int OP_DISPATCH = 0, OP_HEARTBEAT = 1, OP_IDENTIFY = 2, OP_RESUME = 6,
            OP_RECONNECT = 7, OP_INVALID_SESSION = 9, OP_HELLO = 10, OP_HEARTBEAT_ACK = 11;

    public static final int INTENT_GUILDS = 1;
    public static final int INTENT_GUILD_MESSAGES = 1 << 9;
    public static final int INTENT_MESSAGE_CONTENT = 1 << 15;

    /** Códigos de cierre tras los que no tiene sentido reconectar (token o intents mal). */
    public static boolean fatalClose(int code) {
        return code == 4004 || code == 4010 || code == 4011 || code == 4012 || code == 4013 || code == 4014;
    }

    /** Mensaje recibido. {@code seq} null si no trae; {@code type} solo en dispatch. */
    public record Frame(int op, Long seq, String type, JsonElement data) {

        public JsonObject object() {
            return data != null && data.isJsonObject() ? data.getAsJsonObject() : new JsonObject();
        }
    }

    private GatewayProtocol() {
    }

    public static int intents(boolean bridge) {
        return INTENT_GUILDS | (bridge ? INTENT_GUILD_MESSAGES | INTENT_MESSAGE_CONTENT : 0);
    }

    public static Frame parse(String text) {
        JsonObject o = JsonParser.parseString(text).getAsJsonObject();
        int op = o.get("op").getAsInt();
        Long s = o.has("s") && !o.get("s").isJsonNull() ? o.get("s").getAsLong() : null;
        String t = o.has("t") && !o.get("t").isJsonNull() ? o.get("t").getAsString() : null;
        return new Frame(op, s, t, o.has("d") ? o.get("d") : JsonNull.INSTANCE);
    }

    public static String heartbeat(Long seq) {
        JsonObject o = new JsonObject();
        o.addProperty("op", OP_HEARTBEAT);
        if (seq == null) o.add("d", JsonNull.INSTANCE);
        else o.addProperty("d", seq);
        return o.toString();
    }

    public static String identify(String token, int intents) {
        JsonObject props = new JsonObject();
        props.addProperty("os", System.getProperty("os.name", "linux").toLowerCase(java.util.Locale.ROOT));
        props.addProperty("browser", "LosPerrisStats");
        props.addProperty("device", "LosPerrisStats");
        JsonObject d = new JsonObject();
        d.addProperty("token", token);
        d.addProperty("intents", intents);
        d.add("properties", props);
        JsonObject o = new JsonObject();
        o.addProperty("op", OP_IDENTIFY);
        o.add("d", d);
        return o.toString();
    }

    public static String resume(String token, String session, long seq) {
        JsonObject d = new JsonObject();
        d.addProperty("token", token);
        d.addProperty("session_id", session);
        d.addProperty("seq", seq);
        JsonObject o = new JsonObject();
        o.addProperty("op", OP_RESUME);
        o.add("d", d);
        return o.toString();
    }

    /** Resumen para el registro de depuración (op y tipo; nunca el contenido ni el token). */
    public static String describe(Frame f) {
        return "op=" + f.op() + (f.type() == null ? "" : " t=" + f.type()) + (f.seq() == null ? "" : " s=" + f.seq());
    }

    /** Añade {@code v=10&encoding=json} si la URL del gateway no lo trae. */
    public static String url(String base) {
        String b = base == null || base.isBlank() ? "wss://gateway.discord.gg" : base.trim();
        if (b.contains("encoding=")) return b;
        return b + (b.contains("?") ? "&" : (b.endsWith("/") ? "?" : "/?")) + "v=10&encoding=json";
    }
}
