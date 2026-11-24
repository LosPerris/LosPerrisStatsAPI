package me.gordi.mineapi.module.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.gordi.mineapi.MineAPI;
import me.gordi.mineapi.module.stats.StatText;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/** Solo se instancia si PlaceholderAPI está presente. */
final class LosPerrisExpansion extends PlaceholderExpansion {

    private final MineAPI plugin;
    private final StatText text;

    LosPerrisExpansion(MineAPI plugin, StatText text) {
        this.plugin = plugin;
        this.text = text;
    }

    @Override public @NotNull String getIdentifier() { return "losperrisapi"; }
    @Override public @NotNull String getAuthor() { return String.join(", ", plugin.getDescription().getAuthors()); }
    @Override public @NotNull String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return text.parse(player, params);
    }
}
