package eu.neydev.neyshulker.service;

import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertEquals;
import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.event.ShulkerOpenEvent;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка сервиса открытия: успешный путь регистрирует сессию и запускает
 * автосохранение, отмененное событие откатывает открытие.
 */
class ShulkerOpenServiceTest {

    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final ConfigManager configManager = mock(ConfigManager.class);
    private final ShulkerPersistenceService persistenceService = mock(ShulkerPersistenceService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final SoundService soundService = mock(SoundService.class);

    private final org.bukkit.inventory.PlayerInventory playerInventory =
            TestInventories.playerInventory();

    private final org.bukkit.inventory.Inventory gui = TestInventories.inventory(27);
    private final Player player = player();

    private Player player() {

        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(player.getInventory()).thenReturn(playerInventory);
        return player;

    }

    private ItemStack shulker() {

        BlockStateMeta meta = mock(BlockStateMeta.class);
        ShulkerBox box = mock(ShulkerBox.class);
        org.bukkit.inventory.Inventory contents = TestInventories.inventory(27);

        when(box.getSnapshotInventory()).thenReturn(contents);
        when(contents.getContents()).thenReturn(new org.bukkit.inventory.ItemStack[27]);
        when(meta.getBlockState()).thenReturn(box);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("Box");

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getItemMeta()).thenReturn(meta);
        return shulker;

    }

    private ShulkerOpenService openService(NeyShulker plugin) {
        return new ShulkerOpenService(plugin, sessionRegistry, new ShulkerContentService(),
                new ShulkerTitleService(configManager), persistenceService,
                messageService, soundService);
    }

    private NeyShulker pluginWithEvents() {

        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("open-test"));
        return plugin;

    }

    @Test
    @DisplayName("Успешное открытие регистрирует сессию и включает автосохранение")
    void openRegistersSessionAndAutoSave() {

        when(configManager.getTitleMode()).thenReturn(eu.neydev.neyshulker.config.type.TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");

        NeyShulker plugin = pluginWithEvents();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), anyString()))
                    .thenReturn(gui);

            assertTrue(openService(plugin).open(player, shulker(), 3));

        }

        assertNotNull(sessionRegistry.getSession(player));
        verify(persistenceService).scheduleAutoSave(any());
        verify(soundService).playOpen(player);

    }

    @Test
    @DisplayName("Отмененное событие открытия откатывает сессию")
    void cancelledEventRollsBack() {

        when(configManager.getTitleMode()).thenReturn(eu.neydev.neyshulker.config.type.TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");

        PluginManager pluginManager = mock(PluginManager.class);
        Server server = mock(Server.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(server.getPluginManager()).thenReturn(pluginManager);
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("open-test"));

        doAnswer(invocation -> {

            ShulkerOpenEvent event = invocation.getArgument(0);
            event.setCancelled(true);
            return null;

        }).when(pluginManager).callEvent(any(Event.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), anyString()))
                    .thenReturn(gui);

            assertFalse(openService(plugin).open(player, shulker(), 3));

        }

        assertNull(sessionRegistry.getSession(player));
        verify(player, never()).openInventory(any(org.bukkit.inventory.Inventory.class));
        verify(persistenceService, never()).scheduleAutoSave(any());

    }

    @Test
    @DisplayName("Повторное открытие при живой сессии отклоняется")
    void secondOpenIsRejected() {

        when(configManager.getTitleMode()).thenReturn(eu.neydev.neyshulker.config.type.TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");

        NeyShulker plugin = pluginWithEvents();
        ShulkerOpenService service = openService(plugin);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), anyString()))
                    .thenReturn(gui);

            assertTrue(service.open(player, shulker(), 3));
            assertFalse(service.open(player, shulker(), 3));

        }

    }


    @Test
    @DisplayName("open(slot) с не-шалкером отправляет OPEN_ERROR")
    void openSlotWithNonShulkerSendsError() {

        playerInventory.setItem(2, new eu.neydev.neyshulker.util.FakeItemStack(Material.STONE, 1));

        NeyShulker plugin = pluginWithEvents();

        assertFalse(openService(plugin).open(player, 2));
        verify(messageService).send(player, MessageKey.OPEN_ERROR, java.util.Map.of());

    }

    @Test
    @DisplayName("RuntimeException при открытии: severe-лог, OPEN_ERROR, сессия снята")
    void runtimeExceptionRollsBack() {

        ItemStack exploding = mock(ItemStack.class);

        when(exploding.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(exploding.clone()).thenThrow(new IllegalStateException("boom"));

        java.util.logging.Logger logger = java.util.logging.Logger.getLogger("open-ex-" + System.nanoTime());
        java.util.List<java.util.logging.LogRecord> records = new java.util.ArrayList<>();

        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {

            @Override
            public void publish(java.util.logging.LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        NeyShulker plugin = pluginWithEvents();
        when(plugin.getLogger()).thenReturn(logger);

        assertFalse(openService(plugin).open(player, exploding, 3));

        assertNull(sessionRegistry.getSession(player));
        verify(messageService).send(player, MessageKey.OPEN_ERROR, java.util.Map.of());
        assertEquals(1, records.size());
        assertEquals(java.util.logging.Level.SEVERE, records.get(0).getLevel());
        assertNotNull(records.get(0).getThrown(), "Стектрейс исключения обязателен");

    }

    @Test
    @DisplayName("При открытии предмет в слоте помечается меткой сессии")
    void openTagsLiveSlotItem() {

        when(configManager.getTitleMode()).thenReturn(eu.neydev.neyshulker.config.type.TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");
        when(configManager.getTitleNames()).thenReturn(java.util.Map.of());

        // Мок с рабочей PDC-цепочкой: метка реально пишется
        org.bukkit.inventory.meta.BlockStateMeta meta = mock(org.bukkit.inventory.meta.BlockStateMeta.class);
        org.bukkit.block.ShulkerBox box = mock(org.bukkit.block.ShulkerBox.class);
        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);
        org.bukkit.inventory.Inventory contents = TestInventories.inventory(27);

        java.util.Map<org.bukkit.NamespacedKey, String> store = new java.util.HashMap<>();

        when(box.getSnapshotInventory()).thenReturn(contents);
        when(contents.getContents()).thenReturn(new ItemStack[27]);
        when(meta.getBlockState()).thenReturn(box);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(meta.hasDisplayName()).thenReturn(false);
        org.mockito.Mockito.doAnswer(invocation -> {
            store.put(invocation.getArgument(0), invocation.getArgument(2));
            return null;
        }).when(pdc).set(org.mockito.ArgumentMatchers.any(org.bukkit.NamespacedKey.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.persistence.PersistentDataType.STRING),
                org.mockito.ArgumentMatchers.any(String.class));

        ItemStack shulker = mock(ItemStack.class);
        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenAnswer(answer -> shulker);
        when(shulker.getItemMeta()).thenReturn(meta);

        NeyShulker plugin = pluginWithEvents();

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.createInventory(any(), anyInt(), anyString()))
                    .thenReturn(gui);

            assertTrue(openService(plugin).open(player, shulker, 5));

        }

        // В слот записан предмет с меткой, и метка совпадает с id сессии
        ShulkerSession session = sessionRegistry.getSession(player);
        assertNotNull(session);
        assertEquals(session.sessionId().toString(),
                store.get(org.bukkit.NamespacedKey.fromString("neyshulker:session")));
        verify(playerInventory).setItem(eq(5), any(ItemStack.class));

    }

}
