package eu.neydev.neyshulker.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Базовое событие NeyShulker.
 * Все собственные события плагина наследуются от него.
 */
public abstract class NeyShulkerEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    protected NeyShulkerEvent() {
        super(true);
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static @NotNull HandlerList getHandlerList() {
        return HANDLERS;
    }
}
