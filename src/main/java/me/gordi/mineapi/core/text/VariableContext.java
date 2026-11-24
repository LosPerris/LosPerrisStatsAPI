package me.gordi.mineapi.core.text;

/**
 * Contextos donde se pueden usar variables. Cada contexto tiene su sintaxis y su lista de
 * variables válidas en {@link Variables}. La documentación (docs/es|en/variables) se genera de aquí.
 */
public enum VariableContext {
    LANG("lang", Syntax.BRACES,
            "Mensajes de idioma", "Language messages",
            "lang/es.yml, lang/en.yml (y cualquier lang/<código>.yml)", "lang/es.yml, lang/en.yml (and any lang/<code>.yml)"),
    MILESTONES("milestones", Syntax.PERCENT,
            "Mensajes de hitos", "Milestone messages",
            "settings/milestones.yml: message, broadcast, title, actionbar", "settings/milestones.yml: message, broadcast, title, actionbar"),
    REWARDS("rewards", Syntax.PERCENT,
            "Recompensas", "Rewards",
            "config.yml → periods y settings/<extra>.yml: rewards en milestones, quests, periods, streaks, playtime, seasons y events (y presets.<id>.rewards en events)",
            "config.yml → periods and settings/<extra>.yml: rewards in milestones, quests, periods, streaks, playtime, seasons and events (and presets.<id>.rewards in events)"),
    DISCORD("discord-webhook", Syntax.BRACES,
            "Discord", "Discord",
            "settings/discord.yml → discord-webhook.title", "settings/discord.yml → discord-webhook.title"),
    SCOREBOARD("scoreboard", Syntax.PERCENT,
            "Scoreboard", "Scoreboard",
            "settings/scoreboard.yml → title, lines y event-lines", "settings/scoreboard.yml → title, lines and event-lines"),
    PLACEHOLDERAPI("placeholderapi", Syntax.PERCENT,
            "PlaceholderAPI", "PlaceholderAPI",
            "Cualquier plugin compatible con PlaceholderAPI", "Any PlaceholderAPI-compatible plugin"),
    COLORS("colors", Syntax.NONE,
            "Códigos de color", "Color codes",
            "Mensajes, hitos, scoreboard, prefix y anuncios del dashboard web", "Messages, milestones, scoreboard, prefix and web dashboard announcements"),
    QUESTS("quests", Syntax.FORMAT,
            "Objetivos de misiones", "Quest objectives",
            "settings/quests.yml: objective", "settings/quests.yml: objective"),
    SEASONS("seasons", Syntax.BRACES,
            "Temporadas", "Seasons",
            "settings/seasons.yml → name-format", "settings/seasons.yml → name-format"),
    TITLES("titles", Syntax.BRACES,
            "Títulos", "Titles",
            "settings/titles.yml → format (los nombres de los títulos admiten colores &)", "settings/titles.yml → format (title names accept & colors)"),
    DISCORDBOT("discord-bot", Syntax.BRACES,
            "Bot de Discord (puente de chat)", "Discord bot (chat bridge)",
            "settings/discord.yml → discord-bot.bridge.to-discord-format y bridge.to-minecraft-format",
            "settings/discord.yml → discord-bot.bridge.to-discord-format and bridge.to-minecraft-format");

    /** Cómo se escriben las variables en el contexto. */
    public enum Syntax { BRACES, PERCENT, NONE, FORMAT }

    private final String id;
    private final Syntax syntax;
    private final Localized title;
    private final Localized files;

    VariableContext(String id, Syntax syntax, String titleEs, String titleEn, String filesEs, String filesEn) {
        this.id = id;
        this.syntax = syntax;
        this.title = Localized.of(titleEs, titleEn);
        this.files = Localized.of(filesEs, filesEn);
    }

    public String id() { return id; }
    public Syntax syntax() { return syntax; }
    public String title(String lang) { return title.get(lang); }
    public String files(String lang) { return files.get(lang); }

    /** Escribe el nombre con la sintaxis del contexto: {player} o %player%. */
    public String wrap(String name) {
        switch (syntax) {
            case BRACES: return "{" + name + "}";
            case PERCENT: return "%" + name + "%";
            default: return name;
        }
    }
}
