package eu.neydev.neyshulker.placeholder;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Проверка PlaceholderAPI-расширения: идентификатор, ключи и безопасные
 * пустые ответы. Запросы выполняются в главном потоке (как при ordinary
 * разборе плейсхолдеров чата); async-путь отдает кэш и здесь не требует
 * живых инвентарей.
 */
class NeyShulkerExpansionTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final AutoCollectService autoCollectService = mock(AutoCollectService.class);

    private final Player player = player();

    private static Player player() {

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;

    }

    private NeyShulkerExpansion expansion() {

        NeyShulker plugin = mock(NeyShulker.class);

        // Плагин "выключен": фоновая задача обновления кэша не планируется
        when(plugin.isEnabled()).thenReturn(false);
        return new NeyShulkerExpansion(plugin, configManager,
                sessionRegistry, autoCollectService);

    }

    @Test
    @DisplayName("Идентификатор и флаги")
    void identifierAndFlags() {

        NeyShulkerExpansion expansion = expansion();

        assertEquals("neyshulker", expansion.getIdentifier());
        assertEquals("Ney", expansion.getAuthor());
        assertTrue(expansion.persist());

    }

    @Test
    @DisplayName("Ключи сессии без открытого бокса")
    void sessionKeysWithoutSession() {

        when(sessionRegistry.getSession(player)).thenReturn(null);
        when(autoCollectService.isRunning()).thenReturn(true);
        when(autoCollectService.isEnabledFor(player)).thenReturn(true);

        NeyShulkerExpansion expansion = expansion();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            assertEquals("false", expansion.onRequest(player, "open"));
            assertEquals("", expansion.onRequest(player, "name"));
            assertEquals("true", expansion.onRequest(player, "autocollect"));
            assertEquals("27", expansion.onRequest(player, "total_slots"));
            assertNull(expansion.onRequest(player, "unknown_key"));

        }

    }

    @Test
    @DisplayName("Ключи сессии с открытым боксом")
    void sessionKeysWithSession() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getType()).thenReturn(org.bukkit.Material.WHITE_SHULKER_BOX);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 4);

        when(sessionRegistry.getSession(player)).thenReturn(session);
        NeyShulkerExpansion expansion = expansion();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            assertEquals("true", expansion.onRequest(player, "open"));
            assertEquals("4", expansion.onRequest(player, "slot"));
            assertEquals("27", expansion.onRequest(player, "free_slots"));
            assertEquals("0", expansion.onRequest(player, "used_slots"));
            assertEquals("0", expansion.onRequest(player, "items"));

        }

    }

    @Test
    @DisplayName("Async-запрос отдает кэш и не трогает живой инвентарь")
    void asyncRequestUsesCacheWithoutTouchingInventory() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getType()).thenReturn(org.bukkit.Material.WHITE_SHULKER_BOX);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 4);

        when(sessionRegistry.getSession(player)).thenReturn(session);
        when(player.getUniqueId()).thenReturn(session.playerId());

        NeyShulkerExpansion expansion = expansion();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            // Главный поток: кэш наполняется
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals("true", expansion.onRequest(player, "open"));

            // Чужой поток: ответ из кэша, реестр и инвентарь не читаются
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertEquals("true", expansion.onRequest(player, "open"));
            assertEquals("27", expansion.onRequest(player, "free_slots"));

        }

    }


    @Test
    @DisplayName("version и autocollect_permitted не требуют игрока")
    void globalKeys() {

        NeyShulker plugin = mock(NeyShulker.class);
        org.bukkit.plugin.PluginDescriptionFile description =
                mock(org.bukkit.plugin.PluginDescriptionFile.class);

        when(plugin.isEnabled()).thenReturn(false);
        when(plugin.getDescription()).thenReturn(description);
        when(description.getVersion()).thenReturn("2.15.0");
        when(configManager.isAutoCollectEnabled()).thenReturn(true);

        NeyShulkerExpansion expansion =
                new NeyShulkerExpansion(plugin, configManager, sessionRegistry, autoCollectService);

        assertEquals("2.15.0", expansion.onRequest(player, "version"));
        assertEquals("2.15.0", expansion.getVersion());
        assertEquals("true", expansion.onRequest(player, "autocollect_permitted"));

    }

    @Test
    @DisplayName("OfflinePlayer без живой сущности получает пустую строку")
    void offlinePlayerGetsEmpty() {

        org.bukkit.OfflinePlayer offline = mock(org.bukkit.OfflinePlayer.class);
        NeyShulkerExpansion expansion = expansion();

        assertEquals("", expansion.onRequest(offline, "open"));
        assertEquals("", expansion.onRequest(offline, "name"));

    }

    @Test
    @DisplayName("Async с холодным кэшем отдает пустые строки, не трогая инвентарь")
    void asyncColdCacheIsSafe() {

        ItemStack shulker = mock(ItemStack.class);
        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getType()).thenReturn(org.bukkit.Material.WHITE_SHULKER_BOX);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 4);
        when(sessionRegistry.getSession(player)).thenReturn(session);

        NeyShulkerExpansion expansion = expansion();

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                     org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {

            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(false);

            assertEquals("false", expansion.onRequest(player, "open"),
                    "Кэш пуст: async-запрос не читает инвентарь и отвечает дефолтом");
            assertEquals("", expansion.onRequest(player, "name"));

        }

        org.mockito.Mockito.verify(sessionRegistry, org.mockito.Mockito.never())
                .getSession(player);

    }

    @Test
    @DisplayName("Имя без display name приводится к читаемому имени материала")
    void nameKeyPrettifiesMaterial() {

        eu.neydev.neyshulker.util.FakeItemStack shulker =
                new eu.neydev.neyshulker.util.FakeItemStack(org.bukkit.Material.WHITE_SHULKER_BOX, 1);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 4);
        when(sessionRegistry.getSession(player)).thenReturn(session);

        NeyShulkerExpansion expansion = expansion();

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                     org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {

            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
            assertEquals("White Shulker Box", expansion.onRequest(player, "name"));

        }

    }


    @Test
    @DisplayName("Фоновая задача наполняет кэш, shutdown гасит ее")
    void backgroundRefreshFillsCacheAndShutdownStops() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getType()).thenReturn(org.bukkit.Material.WHITE_SHULKER_BOX);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 4);

        when(sessionRegistry.getSession(player)).thenReturn(session);

        NeyShulker plugin = mock(NeyShulker.class);
        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);

        when(plugin.isEnabled()).thenReturn(true);
        when(scheduler.runTaskTimer(org.mockito.ArgumentMatchers.any(org.bukkit.plugin.Plugin.class),
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong()))
                .thenReturn(task);

        org.mockito.ArgumentCaptor<Runnable> refresh =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        NeyShulkerExpansion expansion;

        try (org.mockito.MockedStatic<org.bukkit.Bukkit> bukkit =
                     org.mockito.Mockito.mockStatic(org.bukkit.Bukkit.class)) {

            bukkit.when(org.bukkit.Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(org.bukkit.Bukkit::getOnlinePlayers)
                    .thenReturn(java.util.List.of(player));

            expansion = new NeyShulkerExpansion(plugin, configManager,
                    sessionRegistry, autoCollectService);

            // Исполняем тело фоновой задачи: кэш наполняется без запроса
            org.mockito.Mockito.verify(scheduler).runTaskTimer(
                    org.mockito.ArgumentMatchers.any(org.bukkit.plugin.Plugin.class),
                    refresh.capture(),
                    org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
            refresh.getValue().run();

            // Async-запрос теперь видит данные из кэша
            bukkit.when(org.bukkit.Bukkit::isPrimaryThread).thenReturn(false);
            org.junit.jupiter.api.Assertions.assertEquals("true",
                    expansion.onRequest(player, "open"));
            org.junit.jupiter.api.Assertions.assertEquals("4",
                    expansion.onRequest(player, "slot"));

        }

        expansion.shutdown();
        org.mockito.Mockito.verify(task).cancel();

    }

}
