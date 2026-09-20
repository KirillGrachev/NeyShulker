package eu.neydev.neyshulker.event;

import eu.neydev.neyshulker.model.ShulkerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Вызывается до открытия GUI шалкер-бокса.
 * Отмена события предотвращает открытие.
 */
public class ShulkerOpenEvent extends NeyShulkerEvent implements Cancellable {

    private final Player player;
    private final ShulkerSession session;
    private final ItemStack shulkerItem;

    private boolean cancelled;

    public ShulkerOpenEvent(@NotNull Player player,
                            @NotNull ShulkerSession session,
                            @NotNull ItemStack shulkerItem) {

        this.player = player;
        this.session = session;
        this.shulkerItem = shulkerItem;

    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ShulkerSession getSession() {
        return session;
    }

    public @NotNull ItemStack getShulkerItem() {
        return shulkerItem.clone();
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancelled) {
        this.cancelled = cancelled;
    }
}
