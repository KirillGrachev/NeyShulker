package eu.neydev.neyshulker.event;

import eu.neydev.neyshulker.model.ShulkerSession;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Вызывается после закрытия GUI шалкер-бокса,
 * когда содержимое уже записано обратно в предмет.
 */
public class ShulkerCloseEvent extends NeyShulkerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ShulkerSession session;
    private final ItemStack savedItem;
    private final long durationMillis;

    public ShulkerCloseEvent(@NotNull Player player,
                             @NotNull ShulkerSession session,
                             @NotNull ItemStack savedItem) {

        this.player = player;
        this.session = session;
        this.savedItem = savedItem;
        this.durationMillis = System.currentTimeMillis() - session.openedAt();

    }

    public @NotNull Player getPlayer() {
        return player;
    }

    public @NotNull ShulkerSession getSession() {
        return session;
    }

    public @NotNull ItemStack getSavedItem() {
        return savedItem.clone();
    }

    /**
     * @return сколько миллисекунд шалкер-бокс был открыт
     */
    public long getDurationMillis() {
        return durationMillis;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
