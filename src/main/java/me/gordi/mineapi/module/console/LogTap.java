package me.gordi.mineapi.module.console;

/** Fuente de líneas de la consola. Se engancha al activar el módulo y se suelta al desactivarlo. */
interface LogTap {

    /** Recibe cada línea (cualquier hilo; no debe bloquear ni registrar nada en el log). */
    interface Sink {
        void line(long time, String level, String message);
    }

    void detach();
}
