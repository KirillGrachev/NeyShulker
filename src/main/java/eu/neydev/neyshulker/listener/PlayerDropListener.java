package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель памятки защиты авто-сбора.
 *
 * Записывает дроп в момент броска игрока, чтобы авто-сбор впоследствии
 * его не трогал: выброшенное и передаваемое принадлежит игрокам.
 */
public final class PlayerDropListener implements Listener {

    private final NeyShulker plugin;

    public PlayerDropListener(@NotNull NeyShulker plugin) {
        this.plugin = plugin;
    }

    /**
     * Записывает брошенный игроком дроп.
     *
     * @param event событие броска
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropItem(@NotNull PlayerDropItemEvent event) {
        plugin.getServices().getDropTracker().mark(event.getItemDrop());
    }
}
