package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка сервиса закрытия: содержимое сохраняется один раз,
 * сессия снимается, повторное закрытие безопасно.
 */
class ShulkerCloseServiceTest {

    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final ShulkerPersistenceService persistenceService = mock(ShulkerPersistenceService.class);
    private final SoundService soundService = mock(SoundService.class);

    private final Player player = player();

    private static Player player() {

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        return player;

    }

    private ShulkerSession openSession() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);
        when(player.isOnline()).thenReturn(true);

        return sessionRegistry.createSession(player, shulker, 2,
                () -> TestInventories.inventory(27));

    }

    private NeyShulker plugin() {

        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("close-test"));
        return plugin;

    }

    @Test
    @DisplayName("Закрытие сохраняет содержимое и снимает сессию")
    void closePersistsAndRemoves() {

        openSession();

        ShulkerCloseService closeService = new ShulkerCloseService(
                plugin(), sessionRegistry, persistenceService, soundService);

        // Финальный предмет для события закрытия
        ItemStack saved = mock(ItemStack.class);
        when(saved.clone()).thenReturn(saved);
        when(persistenceService.finalizeSession(org.mockito.ArgumentMatchers.any()))
                .thenReturn(saved);

        assertTrue(closeService.close(player));
        assertFalse(closeService.close(player), "Повторное закрытие ничего не делает");

        verify(persistenceService).cancelAutoSave(org.mockito.ArgumentMatchers.any());
        verify(persistenceService).persist(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(false));
        verify(soundService).playClose(player);
        assertTrue(sessionRegistry.isEmpty());

    }

    @Test
    @DisplayName("closeAll закрывает все живые сессии")
    void closeAllDrainsRegistry() {

        openSession();

        Player second = player();
        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);
        when(second.isOnline()).thenReturn(true);

        sessionRegistry.createSession(second, shulker, 0, () -> TestInventories.inventory(27));

        ShulkerCloseService closeService = new ShulkerCloseService(
                plugin(), sessionRegistry, persistenceService, soundService);

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                     org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {

            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(second.getUniqueId())).thenReturn(second);

            closeService.closeAll();

        }

        assertTrue(sessionRegistry.isEmpty());
        verify(persistenceService, times(2)).persist(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(false));

    }


    @Test
    @DisplayName("close(null) и close(player, null) безопасны")
    void nullArgumentsAreSafe() {

        ShulkerCloseService closeService = new ShulkerCloseService(
                plugin(), sessionRegistry, persistenceService, soundService);

        assertFalse(closeService.close(null));
        assertFalse(closeService.close(player, null));

    }

    @Test
    @DisplayName("Чужая сессия не закрывается")
    void foreignSessionIsNotClosed() {

        ShulkerSession live = openSession();

        ItemStack otherShulker = mock(ItemStack.class);
        when(otherShulker.clone()).thenReturn(otherShulker);

        Player stranger = player();
        ShulkerSession foreign = sessionRegistry.createSession(stranger, otherShulker, 0,
                () -> TestInventories.inventory(27));

        ShulkerCloseService closeService = new ShulkerCloseService(
                plugin(), sessionRegistry, persistenceService, soundService);

        assertFalse(closeService.close(player, foreign),
                "Сессия другого игрока нашим player-ом не закрывается");
        assertTrue(sessionRegistry.getSession(player) == live);

    }

    @Test
    @DisplayName("closeAll с оффлайн-игроком сохраняет принудительно")
    void closeAllPersistsForOfflineOwner() {

        ShulkerSession session = openSession();

        when(player.isOnline()).thenReturn(false);

        ShulkerCloseService closeService = new ShulkerCloseService(
                plugin(), sessionRegistry, persistenceService, soundService);

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                     org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {

            // getPlayer не отдает игрока: принудительная ветка closeAll
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(player.getUniqueId())).thenReturn(null);

            closeService.closeAll();

        }

        assertTrue(sessionRegistry.isEmpty());
        verify(persistenceService).persist(session, false);
        verify(player, never()).closeInventory();

    }

}
