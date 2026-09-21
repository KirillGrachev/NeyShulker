package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.service.ConsoleService;
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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

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
}
