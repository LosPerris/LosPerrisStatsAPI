package me.gordi.mineapi.module.web;

import com.google.gson.JsonObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.logging.Logger;

/**
 * Registro de auditoría persistente (una línea JSON por acción) de la administración web y la
 * consola. Rota al superar {@code maxBytes}: el anterior queda como {@code .1}. Thread-safe.
 */
public final class AuditLog {

    private final File file;
    private final long maxBytes;
    private final Logger log;
    private boolean warned;

    public AuditLog(File file, long maxBytes, Logger log) {
        this.file = file;
        this.maxBytes = Math.max(16_384, maxBytes);
        this.log = log;
    }

    public synchronized void append(JsonObject entry) {
        try {
            File dir = file.getParentFile();
            if (dir != null && !dir.isDirectory() && !dir.mkdirs()) throw new IOException("mkdirs " + dir);
            if (file.length() > maxBytes) {
                Files.move(file.toPath(), new File(file.getPath() + ".1").toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.writeString(file.toPath(), entry.toString() + "\n", StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            if (!warned) {
                warned = true;
                log.warning("No se pudo escribir " + file.getName() + ": " + e.getMessage());
            }
        }
    }

    public File file() {
        return file;
    }
}
