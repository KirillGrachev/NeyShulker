package eu.neydev.neyshulker.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка записи содержимого и поведения открепленной сессии.
 *
 * Регрессия: пока бокс отсутствовал в слоте, автосохранение каждые полсекунды
 * печатало предупреждение - консоль заполнялась одинаковыми строками.
 */
class ShulkerPersistenceServiceTest {

    private final NeyShulker plugin = mock(NeyShulker.class);
    private final ConfigManager configManager = mock(ConfigManager.class);
    private final SessionRegistry sessionRegistry = new SessionRegistry();
    private final ShulkerContentService contentService = new ShulkerContentService();
    private final MessageService messageService = mock(MessageService.class);

    private final ShulkerPersistenceService persistenceService =
            new ShulkerPersistenceService(plugin, configManager, sessionRegistry,
                    contentService, messageService);

    private final PlayerInventory playerInventory = TestInventories.playerInventory();
    private final Inventory gui = TestInventories.inventory(27);

    /**
     * Шалкер-бокс как мок: clone возвращает себя, мета отвечает как BlockStateMeta.
     */
    private ItemStack shulkerItem(Inventory boxInventory) {

        BlockStateMeta meta = mock(BlockStateMeta.class);
        org.bukkit.block.ShulkerBox box = mock(org.bukkit.block.ShulkerBox.class);

        when(box.getSnapshotInventory()).thenReturn(boxInventory);
        when(meta.getBlockState()).thenReturn(box);

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenAnswer(answer -> shulker);
        when(shulker.getItemMeta()).thenReturn(meta);
        return shulker;

    }

    private Player player(PlayerInventory inventory) {

        Player player = mock(Player.class);

        when(player.isOnline()).thenReturn(true);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.getInventory()).thenReturn(inventory);
        when(player.getName()).thenReturn("NeyTM");
        return player;

    }

    @Test
    @DisplayName("Содержимое пишется обратно в живой слот")
    void writesBackIntoLiveSlot() {

        PlayerInventory playerInventory = TestInventories.playerInventory();
        Inventory gui = TestInventories.inventory(27);

        gui.setItem(3, new eu.neydev.neyshulker.util.FakeItemStack(Material.DIAMOND, 5));

        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(playerInventory);

        playerInventory.setItem(0, shulker);
        ShulkerSession session = sessionRegistry.createSession(player, shulker, 0, () -> gui);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            assertTrue(persistenceService.persist(session, false));

        }

        assertSame(shulker, playerInventory.getItem(0), "Слот не перезаписан чужим предметом");
        assertFalse(session.isDetached());

    }


    /**
     * Шалкер-бокс с рабочей меткой сессии: meta -> PDC хранит UUID.
     */
    private ItemStack taggedShulker(Inventory boxInventory, UUID sessionId) {

        org.bukkit.inventory.meta.BlockStateMeta meta = mock(org.bukkit.inventory.meta.BlockStateMeta.class);
        org.bukkit.block.ShulkerBox box = mock(org.bukkit.block.ShulkerBox.class);
        org.bukkit.persistence.PersistentDataContainer pdc =
                mock(org.bukkit.persistence.PersistentDataContainer.class);

        java.util.Map<org.bukkit.NamespacedKey, String> store = new java.util.HashMap<>();
        store.put(org.bukkit.NamespacedKey.fromString("neyshulker:session"), sessionId.toString());

        when(box.getSnapshotInventory()).thenReturn(boxInventory);
        when(meta.getBlockState()).thenReturn(box);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(org.mockito.ArgumentMatchers.any(org.bukkit.NamespacedKey.class),
                org.mockito.ArgumentMatchers.eq(org.bukkit.persistence.PersistentDataType.STRING)))
                .thenAnswer(invocation -> store.get(invocation.getArgument(0, org.bukkit.NamespacedKey.class)));

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenAnswer(answer -> shulker);
        when(shulker.getItemMeta()).thenReturn(meta);
        return shulker;

    }

    /**
     * Бокс-близнец того же материала без метки сессии.
     */
    private ItemStack untaggedTwin() {

        ItemStack twin = mock(ItemStack.class);

        when(twin.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(twin.clone()).thenAnswer(answer -> twin);
        when(twin.getItemMeta()).thenReturn(null);
        return twin;

    }

    @Test
    @DisplayName("Меченый бокс переехал: сохранение следует за меткой, а не за материалом")
    void taggedBoxIsFollowedByTag() {

        PlayerInventory playerInventory = TestInventories.playerInventory();
        Inventory gui = TestInventories.inventory(27);

        gui.setItem(1, new eu.neydev.neyshulker.util.FakeItemStack(Material.DIAMOND, 2));

        UUID sessionId = UUID.randomUUID();
        ItemStack shulker = taggedShulker(TestInventories.inventory(27), sessionId);
        Player player = player(playerInventory);

        // Исходный слот 0 пуст, помеченный бокс лежит в слоте 7
        playerInventory.setItem(7, shulker);

        ShulkerSession session = sessionRegistry.createSession(sessionId, player, shulker, 0, () -> gui);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            assertTrue(persistenceService.persist(session, false));

        }

        assertEquals(7, session.getSlot(), "Сессия пере-якорена на слот с меткой");
        assertFalse(session.isDetached());

    }

    @Test
    @DisplayName("Бокс-близнец без метки содержимое не принимает: detach вместо записи")
    void untaggedTwinNeverReceivesContents() {

        PlayerInventory playerInventory = TestInventories.playerInventory();
        Inventory gui = TestInventories.inventory(27);

        gui.setItem(1, new eu.neydev.neyshulker.util.FakeItemStack(Material.DIAMOND, 2));

        UUID sessionId = UUID.randomUUID();
        ItemStack shulker = taggedShulker(TestInventories.inventory(27), sessionId);
        Player player = player(playerInventory);

        // Помеченный оригинал украден: в инвентаре только немеченый близнец
        playerInventory.setItem(7, untaggedTwin());

        ShulkerSession session = sessionRegistry.createSession(sessionId, player, shulker, 0, () -> gui);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            assertFalse(persistenceService.persist(session, false));

        }

        assertTrue(session.isDetached(), "Писать некуда: сессия откреплена");
        assertNull(playerInventory.getItem(0), "Осиротевший слот не перезаписан");

    }

    @Test
    @DisplayName("Бокс покинул слот: один warning, detach и тишина при повторе")
    void detachesSilentlyWhenBoxLeftSlot() {

        PlayerInventory playerInventory = TestInventories.playerInventory();
        Inventory gui = TestInventories.inventory(27);

        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(playerInventory);

        // Слот пуст: бокс исчез внешним вмешательством
        ShulkerSession session = sessionRegistry.createSession(player, shulker, 0, () -> gui);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            assertFalse(persistenceService.persist(session, false));
            assertFalse(persistenceService.persist(session, false));
            assertFalse(persistenceService.persist(session, false));

        }

        assertTrue(session.isDetached());
        verify(player, times(1)).closeInventory();

        // Консольных уведомлений о detach нет: поведение тихое по решению владельца

        verify(player, times(1)).closeInventory();

    }


    @Test
    @DisplayName("Гарды persist: null, async, оффлайн, чужая сессия, detach, re-entry")
    void persistGuardsRejectQuietly() {

        assertFalse(persistenceService.persist(null, false), "null-сессия");

        PlayerInventory playerInventory = TestInventories.playerInventory();
        Inventory gui = TestInventories.inventory(27);
        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(playerInventory);

        ShulkerSession session = sessionRegistry.createSession(player, shulker, 0, () -> gui);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            // Не главный поток
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);
            assertFalse(persistenceService.persist(session, false));

            // Главный поток, но игрок оффлайн
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            when(player.isOnline()).thenReturn(false);
            assertFalse(persistenceService.persist(session, false));
            when(player.isOnline()).thenReturn(true);

            // Игрок жив, но Bukkit.getPlayer не находит его (сессия не в реестре по playerId)
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            sessionRegistry.closeSession(player.getUniqueId());
            assertFalse(persistenceService.persist(session, false));

            // Сессия снова в реестре, но откреплена
            ShulkerSession detached = sessionRegistry.createSession(player, shulker, 0, () -> gui);
            detached.markDetached();
            assertFalse(persistenceService.persist(detached, false));

            // Флаг saving уже занят - повторный вход отклоняется
            Player busyPlayer = player(TestInventories.playerInventory());
            ShulkerSession busy = sessionRegistry.createSession(busyPlayer, shulker, 1, () -> gui);
            bukkit.when(() -> Bukkit.getPlayer(busyPlayer.getUniqueId())).thenReturn(busyPlayer);
            assertTrue(busy.saving().compareAndSet(false, true));
            assertFalse(persistenceService.persist(busy, false));

        }

    }

    @Test
    @DisplayName("persist с notify отправляет SAVED")
    void persistNotifySendsSavedMessage() {

        PlayerInventory playerInventory = TestInventories.playerInventory();
        Inventory gui = TestInventories.inventory(27);
        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(playerInventory);

        playerInventory.setItem(0, shulker);
        ShulkerSession session = sessionRegistry.createSession(player, shulker, 0, () -> gui);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            assertTrue(persistenceService.persist(session, true));

        }

        verify(messageService).send(player, eu.neydev.neyshulker.config.type.MessageKey.SAVED);

    }

    @Test
    @DisplayName("scheduleSave схлопывает повторные вызовы в один тик")
    void scheduleSaveDedupes() {

        Inventory gui = TestInventories.inventory(27);
        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(TestInventories.playerInventory());
        ShulkerSession session = sessionRegistry.createSession(player, shulker, 0, () -> gui);

        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.mockito.ArgumentCaptor<Runnable> task =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            persistenceService.scheduleSave(session);
            persistenceService.scheduleSave(session);
            persistenceService.scheduleSave(session);

            verify(scheduler, times(1)).runTaskLater(eq(plugin), task.capture(), eq(1L));

            // Исполнение задачи сбрасывает флаг: следующее планирование проходит
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            task.getValue().run();

            persistenceService.scheduleSave(session);
            verify(scheduler, times(2)).runTaskLater(eq(plugin), any(Runnable.class), eq(1L));

        }

    }

    @Test
    @DisplayName("Автосохранение: запуск по интервалу, отмена одной и всех задач")
    void autoSaveLifecycle() {

        when(configManager.getSaveInterval()).thenReturn(10);

        Inventory gui = TestInventories.inventory(27);
        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(TestInventories.playerInventory());

        ShulkerSession first = sessionRegistry.createSession(player, shulker, 0, () -> gui);
        Player second = player(TestInventories.playerInventory());
        ShulkerSession secondSession = sessionRegistry.createSession(second, shulker, 0, () -> gui);

        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);

        when(scheduler.runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L)))
                .thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            persistenceService.scheduleAutoSave(first);
            persistenceService.scheduleAutoSave(secondSession);

            verify(scheduler, times(2)).runTaskTimer(eq(plugin), any(Runnable.class), eq(10L), eq(10L));

            persistenceService.cancelAutoSave(first);
            verify(task, times(1)).cancel();

            persistenceService.cancelAllAutoSaves();
            verify(task, times(2)).cancel();

            // Повторная отмена безопасна
            persistenceService.cancelAutoSave(null);
            persistenceService.cancelAllAutoSaves();

        }

    }

    @Test
    @DisplayName("finalizeSession: оффлайн-игрок получает слепок открытия")
    void finalizeFallsBackToSnapshotOffline() {

        Inventory gui = TestInventories.inventory(27);
        ItemStack shulker = shulkerItem(TestInventories.inventory(27));
        Player player = player(TestInventories.playerInventory());

        when(player.isOnline()).thenReturn(false);
        ShulkerSession session = sessionRegistry.createSession(player, shulker, 0, () -> gui);

        ItemStack result;

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);

            result = persistenceService.finalizeSession(session);

        }

        assertNotNull(result, "Оффлайн-игрок: слепок открытия как честный fallback");

    }

}
