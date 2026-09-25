package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Цель - открытый GUI шалкер-бокса: вставки идут прямо в живой инвентарь
 * сессии, сохранение отмечается один раз при сбросе скана.
 */
public final class SessionCollectTarget implements CollectTarget {

    private final Player player;
    private final ShulkerSession session;
    private final InventoryTransferService transferService;

    private boolean used;

    public SessionCollectTarget(@NotNull Player player,
                                @NotNull ShulkerSession session,
                                @NotNull InventoryTransferService transferService) {

        this.player = player;
        this.session = session;
        this.transferService = transferService;

    }

    @Override
    public @NotNull ItemStack shulkerItem() {
        return session.shulkerItem();
    }

    @Override
    public int insert(@NotNull ItemStack item) {

        int inserted = transferService.insert(player, session.inventory(), item);

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
