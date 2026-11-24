package me.gordi.mineapi.module.console;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Alternativa sin Log4j: handler en el logger raíz de java.util.logging. */
final class JulTap extends Handler implements LogTap {

    private final Sink sink;
    private final Logger root = Logger.getLogger("");

    private JulTap(Sink sink) {
        this.sink = sink;
    }

    static LogTap attach(Sink sink) {
        JulTap tap = new JulTap(sink);
        tap.root.addHandler(tap);
        return tap;
    }

    @Override
    public void publish(LogRecord r) {
        String msg = r.getMessage() == null ? "" : r.getMessage();
        if (r.getParameters() != null && r.getParameters().length > 0) {
            try {
                msg = java.text.MessageFormat.format(msg, r.getParameters());
            } catch (IllegalArgumentException ignored) {
                // se deja el patrón tal cual
            }
        }
        String logger = r.getLoggerName();
        if (logger != null && !logger.isEmpty() && logger.indexOf('.') < 0 && !msg.startsWith("[")) msg = "[" + logger + "] " + msg;
        if (r.getThrown() != null) msg = msg + " | " + r.getThrown();
        Level l = r.getLevel();
        String level = l == Level.SEVERE ? "ERROR" : l == Level.WARNING ? "WARN" : "INFO";
        sink.line(r.getMillis(), level, msg);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public void detach() {
        root.removeHandler(this);
    }
}
