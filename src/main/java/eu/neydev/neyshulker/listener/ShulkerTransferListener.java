package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.ShulkerTransferService;
import eu.neydev.neyshulker.util.ViewSlotUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель быстрого перемещения (Shift-клик).
 *
 * Ванильное перемещение отменяется, предмет переносится собственной транзакцией
 * с немедленной фиксацией в предмете шалкер-бокса. Так клиент и сервер
 * никогда не расходятся в количестве предметов.
 */
public class ShulkerTransferListener implements Listener {

    private final SessionRegistry sessionRegistry;
    private final ShulkerTransferService transferService;

    public ShulkerTransferListener(@NotNull NeyShulker plugin) {

        this.sessionRegistry = plugin.getServices().getSessionRegistry();
        this.transferService = plugin.getServices().getTransferService();

    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShiftClick(@NotNull InventoryClickEvent event) {

        if (!event.isShiftClick()) {
            return;
        }

        ShulkerSession session = sessionRegistry.getSessionByInventory(event.getInventory());

        if (session == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory bottom = event.getView().getBottomInventory();
        int topSlot = ViewSlotUtil.topSlot(event.getView(), event.getRawSlot());
        int storageSlot = ViewSlotUtil.bottomStorageSlot(event.getView(), event.getRawSlot());

        // Служебные слоты (броня, вторая рука, крафт) отдаем ванильной логике:
        // шалкер-бокс в них попасть не может, а ломать быструю экипировку не стоит
        if (topSlot == ViewSlotUtil.OUTSIDE && storageSlot == ViewSlotUtil.OUTSIDE) {
            return;
        }

        event.setCancelled(true);

        if (storageSlot != ViewSlotUtil.OUTSIDE) {
            transferService.shiftIntoShulker(session, bottom, storageSlot);
            return;
        }

        transferService.shiftFromShulker(session, bottom, topSlot);

    }
}
