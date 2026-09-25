package eu.neydev.neyshulker.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Вызывается до открытия GUI шалкер-бокса.
 * Отмена события предотвращает открытие: GUI игроку не показывается.
 *
 * Событие намеренно не отдает внутреннюю сессию плагина: потребителям
 * доступна неизменяемая проекция (игрок, слепок предмета, слот).
 */
public class ShulkerOpenEvent extends NeyShulkerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ItemStack shulkerItem;
    private final int slot;

    private boolean cancelled;

    public ShulkerOpenEvent(@NotNull Player player,
                            @NotNull ItemStack shulkerItem,
                            int slot) {

        this.player = player;
        this.shulkerItem = shulkerItem.clone();
        this.slot = slot;

    }

    public @NotNull Player getPlayer() {
        return player;
    }

    /**
     * @return слепок предмета шалкер-бокса, который открывается
     */
    public @NotNull ItemStack getShulkerItem() {
        return shulkerItem.clone();
    }

    /**
     * @return слот инвентаря игрока, в котором лежит бокс
     */
    public int getSlot() {
        return slot;
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
