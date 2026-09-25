package eu.neydev.neyshulker.event;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Вызывается перед тем, как предмет будет перенесен автосбором в бокс.
 * Отмена события оставляет предмет на месте (дроп - на земле,
 * инвентарный источник - в своем слоте).
 *
 * Событие симметрично для обоих источников автосбора: дроп на земле
 * ({@link #isFromGround()} == true, доступна сущность {@link #getItem()})
 * и досортировка из инвентаря игрока (доступен {@link #getSlot()}).
 */
public class ShulkerAutoCollectEvent extends NeyShulkerEvent implements Cancellable {

    /** Значение {@link #getSlot()} для дропа на земле. */
    public static final int GROUND_SLOT = -1;

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Item item;
    private final ItemStack sourceItem;
    private final int slot;
    private final ItemStack targetShulker;

    private boolean cancelled;

    public ShulkerAutoCollectEvent(@NotNull Player player,
                                   @Nullable Item item,
                                   @NotNull ItemStack sourceItem,
                                   int slot,
                                   @NotNull ItemStack targetShulker) {

        this.player = player;
        this.item = item;
        this.sourceItem = sourceItem.clone();
        this.slot = slot;
        this.targetShulker = targetShulker.clone();

    }

    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * @return сущность дропа или null для источника из инвентаря
     */
    public @Nullable Item getItem() {
        return item;
    }

    /**
     * @return слепок переносимого стека
     */
    public @NotNull ItemStack getSourceItem() {
        return sourceItem.clone();
    }

    /**
     * @return true если источник - дроп на земле
     */
    public boolean isFromGround() {
        return item != null;
    }

    /**
     * @return слот инвентаря-источника или {@link #GROUND_SLOT} для дропа
     */
    public int getSlot() {
        return slot;
    }

    public @NotNull ItemStack getTargetShulker() {
        return targetShulker.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }

}
