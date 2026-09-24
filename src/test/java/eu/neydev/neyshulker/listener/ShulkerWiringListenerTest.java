package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.service.ShulkerCloseService;
import eu.neydev.neyshulker.service.ShulkerTransferService;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка wiring-а слушателей синхронизации, переноса и очистки:
 * каждый реагирует ровно на свои события и дергает нужный сервис.
 */
class ShulkerWiringListenerTest {

    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final ShulkerTransferService transferService = mock(ShulkerTransferService.class);
    private final ShulkerCloseService closeService = mock(ShulkerCloseService.class);
    private final AutoCollectService autoCollectService = mock(AutoCollectService.class);

    private final Player player = mock(Player.class);
    private final Inventory gui = TestInventories.inventory(27);

    private final ShulkerSession session = session();

    private ShulkerSession session() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);

        return ShulkerSession.create(UUID.randomUUID(), player, shulker, () -> gui, 2);

    }

    private NeyShulker plugin() {

        ServiceContainer container = mock(ServiceContainer.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(container.getSessionRegistry()).thenReturn(sessionRegistry);
        when(container.getTransferService()).thenReturn(transferService);
        when(container.getCloseService()).thenReturn(closeService);
        when(container.getAutoCollectService()).thenReturn(autoCollectService);
        when(plugin.getServices()).thenReturn(container);

        return plugin;

    }

    @Test
    @DisplayName("SyncListener помечает сессию измененной на клике и drag")
    void syncListenerMarksSession() {

        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        ShulkerSyncListener listener = new ShulkerSyncListener(plugin());

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getInventory()).thenReturn(gui);

        InventoryDragEvent drag = mock(InventoryDragEvent.class);
        when(drag.getInventory()).thenReturn(gui);

        listener.onInventoryClick(click);
        listener.onInventoryDrag(drag);

        verify(transferService, org.mockito.Mockito.times(2)).markChanged(session);

    }

    @Test
    @DisplayName("SyncListener молчит на чужих инвентарях")
    void syncListenerIgnoresForeignInventories() {

        Inventory chest = TestInventories.inventory(27);

        ShulkerSyncListener listener = new ShulkerSyncListener(plugin());

        InventoryClickEvent click = mock(InventoryClickEvent.class);
        when(click.getInventory()).thenReturn(chest);

        listener.onInventoryClick(click);

        verify(transferService, never()).markChanged(session);

    }

    @Test
    @DisplayName("CleanupListener закрывает сессию на close и quit")
    void cleanupListenerClosesSession() {

        eu.neydev.neyshulker.inventory.NeyShulkerViewer viewer;

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), anyString()))
                    .thenReturn(gui);

            viewer = new eu.neydev.neyshulker.inventory.NeyShulkerViewer("title");

        }

        when(gui.getHolder()).thenReturn(viewer);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        ShulkerCleanupListener listener = new ShulkerCleanupListener(plugin());

        InventoryCloseEvent close = mock(InventoryCloseEvent.class);

        when(close.getInventory()).thenReturn(gui);
        when(close.getPlayer()).thenReturn(player);

        listener.onInventoryClose(close);

        verify(closeService).close(player, session);

        PlayerQuitEvent quit = mock(PlayerQuitEvent.class);

        when(quit.getPlayer()).thenReturn(player);

        listener.onPlayerQuit(quit);

        verify(closeService).close(player);
        verify(autoCollectService).forget(player);

    }
}
