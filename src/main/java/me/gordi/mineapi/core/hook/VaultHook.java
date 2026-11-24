package me.gordi.mineapi.core.hook;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;

import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Hook con Vault. La economía se busca de forma perezosa: si el plugin de economía
 * (Essentials, CMI...) se registra después que este plugin, se detecta en el primer uso.
 */
public final class VaultHook {

    private Economy economy = null;
    private final JavaPlugin plugin;
    private final boolean vaultPresent;
    private boolean warnedMissing = false;

    public VaultHook(JavaPlugin plugin) {
        this.plugin = plugin;
        this.vaultPresent = plugin.getServer().getPluginManager().getPlugin("Vault") != null;
        if (!vaultPresent) {
            plugin.getLogger().warning("Vault no encontrado. Las recompensas de dinero estarán desactivadas.");
            return;
        }
        if (resolveEconomy() != null) {
            plugin.getLogger().info("Hook con Vault (Economía) establecido correctamente.");
        } else {
            plugin.getLogger().info("Vault encontrado; la economía se buscará de nuevo al primer uso.");
        }
    }

    private Economy resolveEconomy() {
        if (!vaultPresent) return null;
        if (economy != null) return economy;
        try {
            RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
            if (rsp != null) {
                economy = rsp.getProvider();
            }
        } catch (NoClassDefFoundError ignored) {
            // Vault sin la API de economía
        }
        return economy;
    }

    public boolean isEnabled() {
        boolean ok = resolveEconomy() != null;
        if (!ok && vaultPresent && !warnedMissing) {
            warnedMissing = true;
            plugin.getLogger().warning("No se encontró ningún plugin de economia compatible con Vault.");
        }
        return ok;
    }

    /** Ingresa dinero. @return la cantidad formateada por la economía, o null si falló. */
    public String deposit(Player player, double amount) {
        Economy eco = resolveEconomy();
        if (eco == null) return null;
        EconomyResponse response = eco.depositPlayer(player, amount);
        if (response != null && response.transactionSuccess()) {
            return eco.format(amount);
        } else {
            plugin.getLogger().warning("Vault rechazó el depósito de " + amount + " a " + player.getName()
                    + (response != null && response.errorMessage != null ? ": " + response.errorMessage : ""));
            return null;
        }
    }
}
