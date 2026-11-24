package me.gordi.mineapi.module.backups;

import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Cálculos puros de las copias: nombre, próxima ejecución y rotación (JUnit). */
public final class BackupPlan {

    public static final String PREFIX = "data-";
    public static final String SUFFIX = ".zip";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT);
    private static final Pattern NAME = Pattern.compile("data-\\d{8}-\\d{6}(-\\d+)?\\.zip");

    private BackupPlan() {}

    public static String fileName(ZonedDateTime now) {
        return PREFIX + STAMP.format(now) + SUFFIX;
    }

    public static boolean isBackupName(String name) {
        return name != null && NAME.matcher(name).matches();
    }

    /** "HH:mm" -> hora; inválida -> 04:00. */
    public static LocalTime parseTime(String raw) {
        try {
            return LocalTime.parse(raw == null ? "" : raw.trim(), DateTimeFormatter.ofPattern("H:mm", Locale.ROOT));
        } catch (DateTimeParseException e) {
            return LocalTime.of(4, 0);
        }
    }

    /** Próxima ejecución estrictamente posterior a {@code now} (respeta la zona y el horario de verano). */
    public static ZonedDateTime nextRun(ZonedDateTime now, LocalTime at) {
        ZonedDateTime today = now.toLocalDate().atTime(at).atZone(now.getZone());
        return today.isAfter(now) ? today : now.toLocalDate().plusDays(1).atTime(at).atZone(now.getZone());
    }

    /** Copias sobrantes (las más antiguas) cuando hay más de {@code keep}. Ignora archivos ajenos. */
    public static List<String> toDelete(List<String> names, int keep) {
        List<String> ours = new ArrayList<>();
        for (String n : names) if (isBackupName(n)) ours.add(n);
        ours.sort(Comparator.reverseOrder()); // la marca de tiempo ordena por nombre
        int k = Math.max(1, keep);
        return ours.size() <= k ? List.of() : new ArrayList<>(ours.subList(k, ours.size()));
    }
}
