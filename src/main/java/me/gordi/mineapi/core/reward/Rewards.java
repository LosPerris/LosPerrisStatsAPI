package me.gordi.mineapi.core.reward;

import me.gordi.mineapi.core.hook.VaultHook;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Recompensas compartidas por hitos, periodos, misiones, rachas y tiempo de juego.
 * Cada línea es una de:
 * <ul>
 *   <li>{@code money:<cantidad>}: dinero con Vault (solo si el jugador está conectado)</li>
 *   <li>{@code item:<MATERIAL>[:<cantidad>]}: objeto al inventario (lo que no cabe cae al suelo)</li>
 *   <li>{@code command:<comando>} o cualquier otra cosa: comando de consola; {@code %player%} = nombre del jugador</li>
 * </ul>
 * Vault se busca solo la primera vez que hace falta dinero.
 */
public final class Rewards {

    public enum Kind { MONEY, ITEM, COMMAND }

    /** Recompensa ya interpretada. */
    public record Reward(Kind kind, String value, double amount) {
    }

    private final JavaPlugin plugin;
    private VaultHook vault;
    /** Avisos ya dados (uno por origen), para no repetir el mismo en cada jugador. */
    private final java.util.Set<String> warned = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public Rewards(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /** Interpreta una línea. @return null si es inválida */
    public static Reward parse(String line) {
        if (line == null) return null;
        String s = line.trim();
        if (s.isEmpty()) return null;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("money:")) {
            try {
                double amount = Double.parseDouble(s.substring(6).trim());
                return amount > 0 ? new Reward(Kind.MONEY, "", amount) : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }
        if (lower.startsWith("item:")) {
            String spec = s.substring(5).trim();
            if (spec.toLowerCase(Locale.ROOT).startsWith("minecraft:")) spec = spec.substring(10); // item:minecraft:diamond:2
            String[] parts = spec.split(":");
            Material material = Material.matchMaterial(parts[0].trim());
            if (material == null || !material.isItem() || material.isAir()) return null;
            int amount = 1;
            if (parts.length > 1) {
                try {
                    amount = Integer.parseInt(parts[1].trim());
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return amount > 0 ? new Reward(Kind.ITEM, material.name(), Math.min(amount, 64 * 36)) : null;
        }
        if (lower.startsWith("command:")) s = s.substring(8).trim(); // prefijo opcional (settings/quests.yml lo usa)
        if (s.startsWith("/")) s = s.substring(1);
        return s.isBlank() ? null : new Reward(Kind.COMMAND, s, 0);
    }

    /** Interpreta varias líneas; las inválidas se ignoran y se avisan en {@code where}. */
    public List<Reward> parseAll(List<String> lines, String where) {
        List<Reward> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            Reward r = parse(line);
            if (r == null) plugin.getLogger().warning(where + ": recompensa inválida '" + line + "'");
            else out.add(r);
        }
        return out;
    }

    /**
     * Entrega recompensas. Hilo principal.
     *
     * @param player conectado, o null (entonces solo se ejecutan los comandos)
     * @param name   nombre para %player%
     * @return cantidad de dinero entregada ya formateada por la economía, o null si no hubo
     */
    public String give(Player player, String name, List<Reward> rewards, String where) {
        String money = null;
        for (Reward r : rewards) {
            switch (r.kind()) {
                case MONEY -> {
                    if (player == null) {
                        plugin.getLogger().warning(where + ": dinero para " + name + " omitido (desconectado).");
                        continue;
                    }
                    if (!moneyAvailable()) {
                        if (warned.add(where)) {
                            plugin.getLogger().warning(where + ": recompensa de dinero omitida (Vault/economía no activo). No se repetirá este aviso.");
                        }
                        continue;
                    }
                    String formatted = vault.deposit(player, r.amount());
                    if (formatted != null) money = formatted;
                }
                case ITEM -> {
                    if (player == null) {
                        plugin.getLogger().warning(where + ": objeto para " + name + " omitido (desconectado).");
                        continue;
                    }
                    Material material = Material.getMaterial(r.value());
                    if (material == null) continue;
                    int left = (int) r.amount();
                    while (left > 0) {
                        int n = Math.min(left, material.getMaxStackSize());
                        left -= n;
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(material, n));
                        for (ItemStack drop : overflow.values()) player.getWorld().dropItemNaturally(player.getLocation(), drop);
                    }
                }
                case COMMAND -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), r.value().replace("%player%", name));
            }
        }
        return money;
    }

    /** Atajo: interpreta y entrega líneas de config. */
    public String giveLines(Player player, String name, List<String> lines, String where) {
        return give(player, name, parseAll(lines, where), where);
    }

    /** true si hay economía (Vault) para entregar dinero. Hilo principal la primera vez. */
    public boolean moneyAvailable() {
        if (vault == null) vault = new VaultHook(plugin);
        return vault.isEnabled();
    }

    /** Texto corto para menús y web: "$500, 3x diamond" (los comandos no se muestran). */
    public static String describe(List<Reward> rewards) {
        return describe(rewards, true);
    }

    /** Igual, pero sin el dinero si {@code withMoney} es false (sin Vault no se entrega). */
    public static String describe(List<Reward> rewards, boolean withMoney) {
        StringBuilder sb = new StringBuilder();
        for (Reward r : rewards) {
            String part = switch (r.kind()) {
                case MONEY -> withMoney ? "$" + trim(r.amount()) : null;
                case ITEM -> (int) r.amount() + "x " + r.value().toLowerCase(Locale.ROOT).replace('_', ' ');
                case COMMAND -> null;
            };
            if (part == null) continue;
            if (sb.length() > 0) sb.append(", ");
            sb.append(part);
        }
        return sb.toString();
    }

    private static String trim(double v) {
        return v == Math.rint(v) ? String.valueOf((long) v) : String.valueOf(v);
    }
}
