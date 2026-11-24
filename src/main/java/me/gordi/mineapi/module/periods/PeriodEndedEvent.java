package me.gordi.mineapi.module.periods;

import com.google.gson.JsonObject;

/**
 * Terminó un periodo (hilo de trabajo). {@code result} es la entrada archivada:
 * {@code {id, start, end, ended_at, forced, winners{stat:[{name,value}]}}}.
 */
public record PeriodEndedEvent(PeriodKind kind, JsonObject result) {
}
