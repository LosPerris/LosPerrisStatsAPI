package me.gordi.mineapi.core.storage;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/** Lectura/escritura de archivos JSON pequeños de los módulos (escritura atómica: tmp + move). */
public final class JsonFiles {

    private static final Gson COMPACT = new GsonBuilder().disableHtmlEscaping().create();
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private JsonFiles() {
    }

    /** Objeto del archivo o null si no existe o está dañado. */
    public static JsonObject read(File file) {
        if (!file.isFile()) return null;
        try {
            JsonElement e = JsonParser.parseString(Files.readString(file.toPath(), StandardCharsets.UTF_8));
            return e.isJsonObject() ? e.getAsJsonObject() : null;
        } catch (Exception e) {
            return null;
        }
    }

    public static void write(File file, JsonElement json, boolean pretty) throws IOException {
        File dir = file.getParentFile();
        if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("No se pudo crear " + dir);
        File tmp = new File(dir, file.getName() + ".tmp");
        Files.writeString(tmp.toPath(), (pretty ? PRETTY : COMPACT).toJson(json), StandardCharsets.UTF_8);
        try {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
