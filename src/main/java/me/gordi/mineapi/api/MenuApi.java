package me.gordi.mineapi.api;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Páginas extra en el menú de estadísticas ({@code /lpa menu}). Cada página tiene un botón en la página
 * principal (huecos libres de la fila de abajo) y se dibuja en un cofre de 6 filas.
 * Extra pages for the stats menu.
 */
public interface MenuApi {

    /** Una página del menú. */
    interface Page {
        /** Id único (p. ej. {@code quests}). */
        String id();

        /** Icono del botón en la página principal. */
        Material icon();

        /** Nombre del botón (ya traducido y coloreado). */
        String button(Player viewer);

        /** Título del cofre. */
        String title(Player viewer, Player target);

        /** true si se puede abrir para este jugador (si no, no se muestra el botón). */
        default boolean visible(Player viewer, Player target) { return true; }

        /** Dibuja la página. Hilo principal. */
        void render(Canvas canvas);
    }

    /** Dónde se dibuja: huecos 9-44 libres; fila de abajo con volver/anterior/siguiente. */
    interface Canvas {
        Player viewer();

        Player target();

        /** Número de página (0 = primera). */
        int page();

        /** Huecos por página en la zona central (36). */
        int perPage();

        void set(int slot, ItemStack item);

        /** Botón: {@code onClick} se ejecuta en el hilo principal. */
        void button(int slot, ItemStack item, Runnable onClick);

        /**
         * Paginación: con {@code total} elementos, ajusta y devuelve la página actual y pone las flechas
         * anterior/siguiente si hacen falta.
         */
        int paginate(int total);

        /** Vuelve a abrir esta página (p. ej. tras un clic) en la página {@code page}. */
        void reopen(int page);
    }

    /** Registra una página (su botón aparece en la principal). */
    void page(Object owner, Page page);

    /** Abre una página registrada; false si el menú está apagado o no existe. */
    boolean open(Player viewer, Player target, String pageId, int page);
}
