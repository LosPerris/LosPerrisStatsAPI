package me.gordi.mineapi.module.console;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/**
 * Appender de Log4j 2 (Paper/Spigot) en el logger raíz: ve toda la consola, incluidos otros
 * plugins y el servidor. Esta clase solo se carga si el módulo console está activo.
 */
final class Log4jTap extends AbstractAppender implements LogTap {

    private final Sink sink;
    private final Logger root;

    private Log4jTap(Sink sink, Logger root) {
        super("LosPerrisWebConsole", null, null, true, new Property[0]);
        this.sink = sink;
        this.root = root;
    }

    /** @throws ClassCastException si el logger raíz no es de log4j-core */
    static LogTap attach(Sink sink) {
        Logger root = (Logger) LogManager.getRootLogger();
        Log4jTap tap = new Log4jTap(sink, root);
        tap.start();
        root.addAppender(tap);
        return tap;
    }

    @Override
    public void append(LogEvent event) {
        String msg = event.getMessage() == null ? "" : event.getMessage().getFormattedMessage();
        String logger = event.getLoggerName();
        if (logger != null && !logger.isEmpty() && !logger.startsWith("net.minecraft") && !logger.startsWith("com.mojang")
                && !msg.startsWith("[") && logger.indexOf('.') < 0) {
            msg = "[" + logger + "] " + msg; // logger de plugin: "[NombrePlugin] mensaje"
        }
        Throwable thrown = event.getThrown();
        if (thrown != null) msg = msg + " | " + thrown;
        sink.line(event.getTimeMillis(), event.getLevel().name(), msg);
    }

    @Override
    public void detach() {
        root.removeAppender(this);
        stop();
    }
}
