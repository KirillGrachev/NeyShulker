package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.service.AutoCollectService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка wiring-а слушателя синхронизации листа ожидания:
 * каждое изменение инвентаря игрока дергает syncWaitList,
 * смерть очищает очередь целиком, чужие сущности игнорируются.
 */
class WaitListSyncListenerTest {

    private final AutoCollectService autoCollectService = mock(AutoCollectService.class);
    private final Player player = mock(Player.class);

    private NeyShulker plugin() {

        ServiceContainer container = mock(ServiceContainer.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(container.getAutoCollectService()).thenReturn(autoCollectService);
        when(plugin.getServices()).thenReturn(container);

        return plugin;

    }

    private WaitListSyncListener listener() {
        return new WaitListSyncListener(plugin());
    }

    @Test
    @DisplayName("Клик и drag по инвентарю синхронизируют очередь игрока")
    void clickAndDragSyncWaitList() {

        WaitListSyncListener listener = listener();

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(player);

        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getWhoClicked()).thenReturn(player);

        listener.onInventoryClick(click);
        listener.onInventoryDrag(drag);

        verify(autoCollectService, org.mockito.Mockito.times(2)).syncWaitList(player);

    }

    @Test
    @DisplayName("Клик не-игрока игнорируется")
    void nonPlayerClickIgnored() {

        WaitListSyncListener listener = listener();

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getWhoClicked()).thenReturn(mock(HumanEntity.class));

        listener.onInventoryClick(click);

        verify(autoCollectService, never()).syncWaitList(player);

    }

    @Test
    @DisplayName("Выброс, поедание, установка блока, поломка и обмен рук синхронизируют очередь")
    void itemLifecycleEventsSyncWaitList() {

        WaitListSyncListener listener = listener();

        PlayerDropItemEvent drop = mock(PlayerDropItemEvent.class);
        when(drop.getPlayer()).thenReturn(player);

        PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
        when(consume.getPlayer()).thenReturn(player);

        BlockPlaceEvent place = mock(BlockPlaceEvent.class);
        when(place.getPlayer()).thenReturn(player);

        PlayerItemBreakEvent breakEvent = mock(PlayerItemBreakEvent.class);
        when(breakEvent.getPlayer()).thenReturn(player);

        PlayerSwapHandItemsEvent swap = mock(PlayerSwapHandItemsEvent.class);
        when(swap.getPlayer()).thenReturn(player);

        listener.onDropItem(drop);
        listener.onItemConsume(consume);
        listener.onBlockPlace(place);
        listener.onItemBreak(breakEvent);
        listener.onSwapHands(swap);

        verify(autoCollectService, org.mockito.Mockito.times(5)).syncWaitList(player);

    }

    @Test
    @DisplayName("Смерть очищает очередь целиком")
    void deathClearsWaitList() {

        WaitListSyncListener listener = listener();

        PlayerDeathEvent death = mock(PlayerDeathEvent.class);
        when(death.getEntity()).thenReturn(player);

        listener.onDeath(death);

        verify(autoCollectService).clearWaitList(player);
        verify(autoCollectService, never()).syncWaitList(player);

    }

    @Test
    @DisplayName("Все обработчики на MONITOR и не пропускают отмененные события")
    void handlersRunOnMonitorAndReadCancelledEvents() {

        List<Method> handlers = Arrays.stream(WaitListSyncListener.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(EventHandler.class))
                .toList();

        assertEquals(8, handlers.size());

        for (Method handler : handlers) {

            EventHandler annotation = handler.getAnnotation(EventHandler.class);

            assertEquals(EventPriority.MONITOR, annotation.priority(), handler.getName());
            assertFalse(annotation.ignoreCancelled(), handler.getName());

        }

    }
}
