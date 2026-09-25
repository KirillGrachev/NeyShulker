package eu.neydev.neyshulker.event;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * Регрессионные тесты контрактов Bukkit для собственных событий.
 *
 * Падение "may only be triggered asynchronously" на сервере возникало из-за
 * флага async в базовом конструкторе, а общий HandlerList рассылал бы
 * слушателю одного события все события семейства.
 */
class NeyShulkerEventTest {

    private ShulkerSession session() {

        Player player = mock(Player.class);
        Inventory inventory = TestInventories.inventory(27);

        return ShulkerSession.create(UUID.randomUUID(), player,
                mock(ItemStack.class), () -> inventory, 0);

    }

    @Test
    @DisplayName("Все события плагина синхронные")
    void eventsAreSynchronous() {

        Player player = mock(Player.class);
        ShulkerSession session = session();

        assertFalse(new ShulkerOpenEvent(player, session, mock(ItemStack.class)).isAsynchronous());
        assertFalse(new ShulkerCloseEvent(player, session, mock(ItemStack.class)).isAsynchronous());
        assertFalse(new ShulkerAutoCollectEvent(player, mock(Item.class),
                mock(ItemStack.class)).isAsynchronous());

    }

    @Test
    @DisplayName("Каждое событие владеет собственным HandlerList")
    void handlerListsAreIsolated() {

        assertNotSame(ShulkerOpenEvent.getHandlerList(), ShulkerCloseEvent.getHandlerList());
        assertNotSame(ShulkerOpenEvent.getHandlerList(), ShulkerAutoCollectEvent.getHandlerList());
        assertNotSame(ShulkerCloseEvent.getHandlerList(), ShulkerAutoCollectEvent.getHandlerList());

    }

    @Test
    @DisplayName("getHandlers возвращает список своего класса")
    void getHandlersReturnsOwnList() {

        Player player = mock(Player.class);
        ShulkerSession session = session();

        assertSame(ShulkerOpenEvent.getHandlerList(),
                new ShulkerOpenEvent(player, session, mock(ItemStack.class)).getHandlers());
        assertSame(ShulkerCloseEvent.getHandlerList(),
                new ShulkerCloseEvent(player, session, mock(ItemStack.class)).getHandlers());
        assertSame(ShulkerAutoCollectEvent.getHandlerList(),
                new ShulkerAutoCollectEvent(player, mock(Item.class),
                        mock(ItemStack.class)).getHandlers());

    }

}
