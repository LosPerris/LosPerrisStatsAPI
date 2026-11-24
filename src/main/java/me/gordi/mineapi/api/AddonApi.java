package me.gordi.mineapi.api;

import org.bukkit.plugin.Plugin;

/**
 * Addons que se integran en el núcleo: sus módulos se ven y se gestionan con /lpa modules y el dashboard, se
 * recargan con /lpa reload y sus interruptores viven en config.yml del núcleo, agrupados bajo
 * {@code # ===== <Addon> =====}. Addons whose modules become part of the core.
 */
public interface AddonApi {

    /**
     * Registra el addon y activa sus módulos encendidos. Los interruptores que falten se añaden a config.yml del
     * núcleo ({@link Addon#initialSwitches()} o false) sin tocar los que ya estén. Se deshace con
     * {@link LosPerrisApi#unregisterAll(Object)} ({@code owner}).
     *
     * @return true si config.yml tiene ya todos sus interruptores (false: no se pudo escribir y se usan los
     *         valores iniciales hasta el próximo intento)
     */
    boolean register(Plugin owner, Addon addon);
}
