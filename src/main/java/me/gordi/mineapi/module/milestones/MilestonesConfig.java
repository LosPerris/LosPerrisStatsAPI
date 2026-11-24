package me.gordi.mineapi.module.milestones;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.gordi.mineapi.core.text.Text;
import me.gordi.mineapi.core.text.VariableContext;
import me.gordi.mineapi.core.text.VariableValidator;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.logging.Logger;

/** settings/milestones.yml interpretado y validado una sola vez (al activar o recargar). */
final class MilestonesConfig {

    final int checkInterval;
    final int cooldownSeconds;
    final boolean soundEnabled;
    final String sound;
    final boolean soundBroadcast;
    final boolean fireworkEnabled;
    final boolean titleEnabled;
    final String titleMain;
    final int fadeIn, stay, fadeOut;
    final boolean particlesEnabled;
    final String particle;
    final int particleCount;
    final boolean actionbarEnabled;
    final String actionbarFormat;
    /** stat -> umbral -> hito. */
    final Map<String, NavigableMap<Integer, Milestone>> byStat;
    /** Descripción para el dashboard: stat -> [{value, message}] sin colores. */
    final JsonObject info;

    MilestonesConfig(FileConfiguration cfg, Logger log) {
        checkInterval = cfg.getInt("check-interval", 60);
        cooldownSeconds = Math.max(0, cfg.getInt("cooldown", 0));
        soundEnabled = cfg.getBoolean("sound.enabled", true);
        sound = cfg.getString("sound.effect", "ENTITY_PLAYER_LEVELUP");
        soundBroadcast = cfg.getBoolean("sound.broadcast", false);
        fireworkEnabled = cfg.getBoolean("firework.enabled", true);
        titleEnabled = cfg.getBoolean("title.enabled", true);
        titleMain = cfg.getString("title.main", "&6&l¡LOGRO DESBLOQUEADO!");
        fadeIn = cfg.getInt("title.fade-in", 10);
        stay = cfg.getInt("title.stay", 70);
        fadeOut = cfg.getInt("title.fade-out", 20);
        particlesEnabled = cfg.getBoolean("particles.enabled", false);
        particle = cfg.getString("particles.effect", "VILLAGER_HAPPY");
        particleCount = cfg.getInt("particles.count", 30);
        actionbarEnabled = cfg.getBoolean("actionbar.enabled", false);
        actionbarFormat = cfg.getString("actionbar.format", "&e✦ %message% ✦");

        VariableValidator v = new VariableValidator(log, "settings/milestones.yml");
        v.check(VariableContext.MILESTONES, "title.main", titleMain);
        v.check(VariableContext.MILESTONES, "actionbar.format", actionbarFormat);

        Map<String, NavigableMap<Integer, Milestone>> map = new HashMap<>();
        JsonObject info = new JsonObject();
        ConfigurationSection stats = cfg.getConfigurationSection("stats");
        if (stats != null) {
            for (String stat : stats.getKeys(false)) {
                ConfigurationSection section = stats.getConfigurationSection(stat);
                if (section == null) continue;
                NavigableMap<Integer, Milestone> list = new TreeMap<>();
                for (String key : section.getKeys(false)) {
                    int value;
                    try {
                        value = Integer.parseInt(key.trim());
                    } catch (NumberFormatException e) {
                        log.warning("settings/milestones.yml: '" + stat + "." + key + "' no es un número, se ignora.");
                        continue;
                    }
                    Milestone m = parse(section, key, stat, value, v);
                    if (m != null) list.put(value, m);
                }
                if (list.isEmpty()) continue;
                map.put(stat, list);
                JsonArray arr = new JsonArray();
                for (Milestone m : list.values()) {
                    JsonObject o = new JsonObject();
                    o.addProperty("value", m.value());
                    o.addProperty("message", Text.strip(Text.color(m.message())));
                    arr.add(o);
                }
                info.add(stat, arr);
            }
        }
        this.byStat = Collections.unmodifiableMap(map);
        this.info = info;
    }

    private static Milestone parse(ConfigurationSection section, String key, String stat, int value, VariableValidator v) {
        String path = "stats." + stat + "." + key;
        if (section.isString(key)) {
            String msg = section.getString(key, "");
            if (msg.isEmpty()) return null;
            v.check(VariableContext.MILESTONES, path, msg);
            return new Milestone(stat, value, msg, msg, List.of(), null, null, null, null, null);
        }
        ConfigurationSection s = section.getConfigurationSection(key);
        if (s == null) return null;
        String msg = s.getString("message", "");
        if (msg == null || msg.isEmpty()) return null;
        String broadcast = msg;
        if (s.contains("broadcast")) {
            broadcast = s.isBoolean("broadcast") ? (s.getBoolean("broadcast") ? msg : null) : s.getString("broadcast");
        }
        List<String> rewards = List.copyOf(s.getStringList("rewards"));
        v.check(VariableContext.MILESTONES, path + ".message", msg);
        if (broadcast != null) v.check(VariableContext.MILESTONES, path + ".broadcast", broadcast);
        for (String r : rewards) v.check(VariableContext.REWARDS, path + ".rewards", r);
        String title = s.contains("title") ? s.getString("title") : null;
        String actionbar = s.contains("actionbar") ? s.getString("actionbar") : null;
        if (title != null) v.check(VariableContext.MILESTONES, path + ".title", title);
        if (actionbar != null) v.check(VariableContext.MILESTONES, path + ".actionbar", actionbar);
        return new Milestone(stat, value, msg, broadcast, rewards, title,
                s.contains("sound") ? s.getString("sound") : null,
                s.contains("firework") ? s.getBoolean("firework") : null,
                s.contains("particles") ? s.getString("particles") : null,
                actionbar);
    }
}
