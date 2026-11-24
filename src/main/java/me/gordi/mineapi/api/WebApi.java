package me.gordi.mineapi.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * Endpoints de la API web y datos del dashboard que añaden los addons. Todo lo registrado se mantiene
 * aunque el módulo web se recargue, y se quita con {@link LosPerrisApi#unregisterAll(Object)}.
 * Web API extension points: read endpoints, admin actions, profile fields and dashboard flags.
 */
public interface WebApi {

    Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    Pattern PLAYER_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

    /** Respuesta JSON. */
    record Reply(int status, String json) {
        public static Reply ok(JsonElement body) { return new Reply(200, GSON.toJson(body)); }
        public static Reply ok(String json) { return new Reply(200, json); }
        public static Reply of(int status, JsonElement body) { return new Reply(status, GSON.toJson(body)); }
        public static Reply of(int status, String json) { return new Reply(status, json); }

        /** {@code {"error": "..."}} */
        public static Reply error(int status, String message) {
            JsonObject o = new JsonObject();
            o.addProperty("error", message);
            return new Reply(status, GSON.toJson(o));
        }

        /** 404 {@code Module 'id' disabled}. */
        public static Reply moduleOff(String id) {
            return error(404, "Module '" + id + "' disabled");
        }
    }

    /** Petición HTTP (hilo del servidor web, no el principal). */
    interface Request {
        /** Parámetros de la URL. */
        Map<String, String> query();

        default String param(String key) { return query().get(key); }

        /** Número de la URL entre {@code min} y {@code max} ({@code def} si falta o no es válido). */
        int intParam(String key, int def, int min, int max);

        /** Cuerpo JSON (solo POST de administración; vacío si no hay). */
        JsonObject body();

        /** IP del cliente. */
        String ip();

        /** true en la página pública (sin API key): no devuelva datos privados. */
        boolean isPublic();

        /** Ejecuta en el hilo principal del servidor y espera (máx. 10 s). */
        <T> T onMainThread(Callable<T> task) throws Exception;

        /** Anota la acción en el registro de administración (solo admin; en lectura no hace nada). */
        void log(String action, String detail, boolean ok);
    }

    @FunctionalInterface
    interface Handler {
        Reply handle(Request request) throws Exception;
    }

    /** Añade datos al perfil ({@code /api/profile}): {@code out} es la respuesta. Hilo del servidor web. */
    @FunctionalInterface
    interface ProfileContributor {
        void contribute(String name, UUID uuid, String lang, JsonObject out);
    }

    /**
     * GET {@code /api/<path>} con API key (como los del núcleo).
     * @param publicSection sección de la página pública que lo abre en {@code /api/public/<path>} (null = nunca público)
     */
    void read(Object owner, String path, String publicSection, Handler handler);

    /** {@code /api/admin/<path>} con la clave de administración. {@code method}: GET o POST. */
    void admin(Object owner, String method, String path, Handler handler);

    /** Campos extra en {@code /api/profile}. */
    void profile(Object owner, ProfileContributor contributor);

    /** Claves internas de los datos del jugador que no deben salir en {@code /api/profile} → stats. */
    void hideDataKeys(Object owner, String... keys);

    /** Interruptor en {@code summary.modules} (el dashboard muestra la pestaña si es true). */
    void flag(Object owner, String id, BooleanSupplier on);

    /** Aviso en el panel en vivo del dashboard (si el módulo web está activo). */
    void notify(String kind, String user, String message, String level);

    /** Vacía las cachés de la API (tras cambiar datos). {@code player} puede ser null. */
    void invalidate(String player);

    static boolean validName(String name) {
        return name != null && PLAYER_NAME.matcher(name).matches();
    }
}
