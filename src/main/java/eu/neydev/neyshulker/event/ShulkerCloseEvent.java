package eu.neydev.neyshulker.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Вызывается после закрытия GUI шалкер-бокса,
 * когда содержимое уже записано обратно в предмет.
 *
 * {@link #getSavedItem()} отдает фактический предмет после финального
 * сохранения (без служебной метки сессии), а не слепок открытия.
 */
public class ShulkerCloseEvent extends NeyShulkerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack savedItem;
    private final int slot;

    public ShulkerCloseEvent(@NotNull Player player,
                             @NotNull ItemStack savedItem,
                             int slot) {

        this.player = player;
        this.savedItem = savedItem.clone();
        this.slot = slot;

    }

    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * @return слепок предмета с финальным содержимым
     */
    public @NotNull ItemStack getSavedItem() {
        return savedItem.clone();
    }

    /**
     * @return слот инвентаря, в котором лежал бокс
     */
    public int getSlot() {
        return slot;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

}
