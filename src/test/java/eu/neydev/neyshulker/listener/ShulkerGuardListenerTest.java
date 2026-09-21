package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Регрессия переноса номер-клавишей: NUMBER_KEY меняет слот хотбара,
 * а не кликовый слот, поэтому открытый бокс уезжал в GUI через hotbarButton.
 */
class ShulkerGuardListenerTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

    private final ShulkerValidationService validationService =
            new ShulkerValidationService(configManager, permissionService, sessionRegistry);

    private final MessageService messageService = new MessageService(configManager);

    private final Player player = mock(Player.class);
    private final PlayerInventory bottom = TestInventories.playerInventory();
    private final Inventory gui = TestInventories.inventory(27);

    private ShulkerGuardListener listener() {

        ServiceContainer container = mock(ServiceContainer.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(container.getSessionRegistry()).thenReturn(sessionRegistry);
        when(container.getValidationService()).thenReturn(validationService);
        when(container.getMessageService()).thenReturn(messageService);
        when(plugin.getServices()).thenReturn(container);

        when(configManager.areMessagesEnabled()).thenReturn(true);
        when(configManager.getMessagePrefix()).thenReturn("");
        when(configManager.getMessages(any())).thenReturn(List.of("blocked"));
        when(player.getInventory()).thenReturn(bottom);

        return new ShulkerGuardListener(plugin);

    }

    private InventoryClickEvent click(int rawSlot, int hotbarButton, ClickType clickType) {

        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = TestInventories.view(gui, bottom);

        when(event.getInventory()).thenReturn(gui);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlot()).thenReturn(rawSlot);
        when(event.getClick()).thenReturn(clickType);
        when(event.getHotbarButton()).thenReturn(hotbarButton);
        when(event.getCursor()).thenReturn(null);
        when(event.getCurrentItem()).thenReturn(null);
        when(event.isShiftClick()).thenReturn(false);

        return event;

    }

    private ShulkerSession session(int slot) {

        ItemStack shulker = mock(ItemStack.class);

        return ShulkerSession.create(UUID.randomUUID(), player, shulker, () -> gui, slot);

    }

    @Test
    @DisplayName("NUMBER_KEY со слотом хотбара открытого бокса отменяется")
    void numberKeyFromShulkerHotbarSlotIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        InventoryClickEvent event = click(5, 4, ClickType.NUMBER_KEY);

        listener().onInventoryClick(event);

        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("NUMBER_KEY с посторонним слотом хотбара не отменяется")
    void numberKeyFromOtherHotbarSlotPasses() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        InventoryClickEvent event = click(5, 7, ClickType.NUMBER_KEY);

        listener().onInventoryClick(event);

        verify(event, never()).setCancelled(true);

    }

    @Test
    @DisplayName("Shift-клик по шалкеру внизу отменяется: вложенность не пролезает")
    void shiftClickWithShulkerSourceIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        ItemStack otherShulker = mock(ItemStack.class);
        when(otherShulker.getType()).thenReturn(org.bukkit.Material.BLACK_SHULKER_BOX);

        InventoryClickEvent event = click(27 + 5, 9, ClickType.SHIFT_LEFT);
        when(event.isShiftClick()).thenReturn(true);
        when(event.getCurrentItem()).thenReturn(otherShulker);

        listener().onInventoryClick(event);

        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Shift-клик с разрешенным предметом не отменяется guard-ом")
    void shiftClickWithAllowedItemPassesGuard() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        InventoryClickEvent event = click(27 + 5, 9, ClickType.SHIFT_LEFT);
        when(event.isShiftClick()).thenReturn(true);
        when(event.getCurrentItem()).thenReturn(
                new eu.neydev.neyshulker.util.FakeItemStack(org.bukkit.Material.DIAMOND, 3));

        listener().onInventoryClick(event);

        verify(event, never()).setCancelled(true);

    }

    @Test
    @DisplayName("NUMBER_KEY с черносписочным предметом в хотбаре отменяется")
    void numberKeyWithBlacklistedHotbarItemIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        when(configManager.isBlacklistEnabled()).thenReturn(true);
        when(configManager.isBlacklisted(org.bukkit.Material.BARRIER)).thenReturn(true);

        bottom.setItem(7, new eu.neydev.neyshulker.util.FakeItemStack(org.bukkit.Material.BARRIER, 64));

        // hotbarButton = 7: номер-клавиша тянет барьер из слота 7 в GUI
        InventoryClickEvent event = click(5, 7, ClickType.NUMBER_KEY);

        listener().onInventoryClick(event);

        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Клик по слоту открытого бокса внизу отменяется")
    void clickOnShulkerSlotIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        InventoryClickEvent event = click(27 + 4, 9, ClickType.LEFT);

        listener().onInventoryClick(event);

        verify(event).setCancelled(true);

    }
}
