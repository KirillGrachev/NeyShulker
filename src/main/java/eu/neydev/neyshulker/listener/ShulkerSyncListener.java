package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.ShulkerTransferService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель синхронизации содержимого.
 *
 * Работает на MONITOR и не отменяет события: он лишь отмечает сессию измененной
 * и планирует сохранение на следующий тик, когда ванильная логика уже применила клик.
 */
public class ShulkerSyncListener implements Listener {

    private final SessionRegistry sessionRegistry;
    private final ShulkerTransferService transferService;

    public ShulkerSyncListener(@NotNull NeyShulker plugin) {

        this.sessionRegistry = plugin.getServices().getSessionRegistry();
        this.transferService = plugin.getServices().getTransferService();

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        transferService.markChanged(sessionRegistry.getSessionByInventory(event.getInventory()));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        transferService.markChanged(sessionRegistry.getSessionByInventory(event.getInventory()));
    }

}
