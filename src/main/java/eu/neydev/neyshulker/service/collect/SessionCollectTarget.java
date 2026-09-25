package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Цель - открытый GUI шалкер-бокса: вставки идут прямо в живой инвентарь
 * сессии, сохранение отмечается один раз при сбросе скана.
 *
 * Клиентский resync на каждую вставку здесь сознательно не делается:
 * {@link CollectScan#flush()} синхронизирует окно один раз на волну.
 */
public final class SessionCollectTarget implements CollectTarget {

    private final ShulkerSession session;
    private final InventoryTransferService transferService;

    private boolean used;

    public SessionCollectTarget(@NotNull ShulkerSession session,
                                @NotNull InventoryTransferService transferService) {

        this.session = session;
        this.transferService = transferService;

    }

    @Override
    public @NotNull ItemStack shulkerItem() {
        return session.shulkerItem();
    }

    @Override
    public int insert(@NotNull ItemStack item) {

        int inserted = transferService.insert(null, session.inventory(), item);

        if (inserted > 0) {
            used = true;
        }

        return inserted;

    }

    public boolean isUsed() {
        return used;
    }

    public @NotNull ShulkerSession getSession() {
        return session;
    }

}
