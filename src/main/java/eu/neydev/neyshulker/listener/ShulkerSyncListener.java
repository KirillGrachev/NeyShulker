package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.inventory.NeyShulkerViewer;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.ShulkerTransferService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Слушатель синхронизации содержимого.
 *
 * Работает на MONITOR и не отменяет события: он лишь отмечает сессию измененной
 * и планирует сохранение на следующий тик, когда ванильная логика уже применила клик.
 *
 * Первый гейт - holder-маркер GUI (O(1)): клики в чужих инвентарях сервера
 * не доходят до поиска сессии в реестре.
 */
public class ShulkerSyncListener implements Listener {

    private final SessionRegistry sessionRegistry;
    private final ShulkerTransferService transferService;

    public ShulkerSyncListener(@NotNull SessionRegistry sessionRegistry,
                               @NotNull ShulkerTransferService transferService) {

        this.sessionRegistry = sessionRegistry;
        this.transferService = transferService;

    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        markChanged(event.getInventory().getHolder(), event.getInventory());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        markChanged(event.getInventory().getHolder(), event.getInventory());
    }

    private void markChanged(@Nullable InventoryHolder holder,
                             @NotNull org.bukkit.inventory.Inventory inventory) {

        if (!(holder instanceof NeyShulkerViewer)) {
            return;
        }

        transferService.markChanged(sessionRegistry.getSessionByInventory(inventory));

    }

}
