package eu.neydev.neyshulker.event;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Вызывается перед тем, как предмет на земле будет засосан автосбором.
 * Отмена события оставляет предмет лежать на земле.
 */
public class ShulkerAutoCollectEvent extends NeyShulkerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Item item;
    private final ItemStack targetShulker;

    private boolean cancelled;

    public ShulkerAutoCollectEvent(@NotNull Player player,
                                   @NotNull Item item,
                                   @NotNull ItemStack targetShulker) {

        this.player = player;
        this.item = item;
        this.targetShulker = targetShulker;

    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull Item getItem() {
        return item;
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
