package eu.neydev.neyshulker.event;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Регрессионные тесты контрактов Bukkit для собственных событий.
 *
 * Падение "may only be triggered asynchronously" на сервере возникало из-за
 * флага async в базовом конструкторе, а общий HandlerList рассылал бы
 * слушателю одного события все события семейства.
 */
class NeyShulkerEventTest {

    private ItemStack item() {

        ItemStack item = mock(ItemStack.class);
        when(item.clone()).thenReturn(item);
        return item;

    }

    @Test
    @DisplayName("Все события плагина синхронные")
    void eventsAreSynchronous() {

        Player player = mock(Player.class);

        assertFalse(new ShulkerOpenEvent(player, item(), 3).isAsynchronous());
        assertFalse(new ShulkerCloseEvent(player, item(), 3).isAsynchronous());
        assertFalse(new ShulkerAutoCollectEvent(player, mock(Item.class), item(),
                ShulkerAutoCollectEvent.GROUND_SLOT, item()).isAsynchronous());

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

        assertSame(ShulkerOpenEvent.getHandlerList(),
                new ShulkerOpenEvent(player, item(), 3).getHandlers());
        assertSame(ShulkerCloseEvent.getHandlerList(),
                new ShulkerCloseEvent(player, item(), 3).getHandlers());
        assertSame(ShulkerAutoCollectEvent.getHandlerList(),
                new ShulkerAutoCollectEvent(player, mock(Item.class), item(),
                        ShulkerAutoCollectEvent.GROUND_SLOT, item()).getHandlers());

    }

    @Test
    @DisplayName("Событие автосбора различает источник: земля и инвентарь")
    void autoCollectEventDistinguishesSource() {

        Player player = mock(Player.class);

        ShulkerAutoCollectEvent ground = new ShulkerAutoCollectEvent(player,
                mock(Item.class), item(), ShulkerAutoCollectEvent.GROUND_SLOT, item());

        ShulkerAutoCollectEvent fromInventory = new ShulkerAutoCollectEvent(player,
                null, item(), 7, item());

        org.junit.jupiter.api.Assertions.assertTrue(ground.isFromGround());
        org.junit.jupiter.api.Assertions.assertFalse(fromInventory.isFromGround());
        org.junit.jupiter.api.Assertions.assertEquals(7, fromInventory.getSlot());

    }


    @Test
    @DisplayName("Геттеры событий отдают проекции и защитные копии")
    void gettersExposeProjections() {

        Player player = mock(Player.class);
        ItemStack shulker = item();
        Item entity = mock(Item.class);

        ShulkerOpenEvent open = new ShulkerOpenEvent(player, shulker, 7);

        org.junit.jupiter.api.Assertions.assertSame(player, open.getPlayer());
        org.junit.jupiter.api.Assertions.assertEquals(7, open.getSlot());
        org.junit.jupiter.api.Assertions.assertSame(shulker, open.getShulkerItem(),
                "clone() мока возвращает себя - сверяем сам контракт вызова");
        org.junit.jupiter.api.Assertions.assertFalse(open.isCancelled());
        open.setCancelled(true);
        org.junit.jupiter.api.Assertions.assertTrue(open.isCancelled());

        ShulkerCloseEvent close = new ShulkerCloseEvent(player, shulker, 9);

        org.junit.jupiter.api.Assertions.assertSame(player, close.getPlayer());
        org.junit.jupiter.api.Assertions.assertEquals(9, close.getSlot());
        org.junit.jupiter.api.Assertions.assertNotNull(close.getSavedItem());

        ShulkerAutoCollectEvent ground = new ShulkerAutoCollectEvent(player, entity,
                shulker, ShulkerAutoCollectEvent.GROUND_SLOT, shulker);

        org.junit.jupiter.api.Assertions.assertSame(entity, ground.getItem());
        org.junit.jupiter.api.Assertions.assertSame(player, ground.getPlayer());
        org.junit.jupiter.api.Assertions.assertEquals(ShulkerAutoCollectEvent.GROUND_SLOT,
                ground.getSlot());
        org.junit.jupiter.api.Assertions.assertNotNull(ground.getSourceItem());
        org.junit.jupiter.api.Assertions.assertNotNull(ground.getTargetShulker());
        org.junit.jupiter.api.Assertions.assertFalse(ground.isCancelled());
        ground.setCancelled(true);
        org.junit.jupiter.api.Assertions.assertTrue(ground.isCancelled());

        ShulkerAutoCollectEvent fromInventory = new ShulkerAutoCollectEvent(player,
                null, shulker, 12, shulker);

        org.junit.jupiter.api.Assertions.assertNull(fromInventory.getItem());
        org.junit.jupiter.api.Assertions.assertEquals(12, fromInventory.getSlot());

    }

}
