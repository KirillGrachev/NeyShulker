package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.inventory.NeyShulkerViewer;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Регрессии охранника открытого бокса: номер-клавиша меняет слот хотбара,
 * а не кликовый слот (открытый бокс уезжал в GUI через hotbarButton),
 * drag и обмен руками не должны пропускать ни сам бокс, ни черный список,
 * а маркер пропущенного клика обязан сверять отпечаток предмета.
 */
class ShulkerGuardListenerTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

    private final ShulkerValidationService validationService =
            new ShulkerValidationService(configManager, permissionService, sessionRegistry);

    private final MessageService messageService = new MessageService(configManager);

    private final Player player = mock(Player.class);
    private final UUID playerId = UUID.randomUUID();
    private final PlayerInventory bottom = TestInventories.playerInventory();
    private final Inventory gui = TestInventories.inventory(27);
    private final NeyShulkerViewer viewer = mock(NeyShulkerViewer.class);

    @BeforeEach
    void bindPlayer() {

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getInventory()).thenReturn(bottom);
        when(player.isOnline()).thenReturn(true);

        // Держатель GUI - маркер плагина: без него holder-гейт не пустит клик
        when(gui.getHolder()).thenReturn(viewer);

        when(configManager.areMessagesEnabled()).thenReturn(true);
        when(configManager.getMessagePrefix()).thenReturn("");
        when(configManager.getMessages(any())).thenReturn(List.of("blocked"));

    }

    private ShulkerGuardListener listener() {
        return new ShulkerGuardListener(sessionRegistry, validationService, messageService);
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
        when(otherShulker.getType()).thenReturn(Material.BLACK_SHULKER_BOX);

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
        when(event.getCurrentItem()).thenReturn(new FakeItemStack(Material.DIAMOND, 3));

        listener().onInventoryClick(event);
        verify(event, never()).setCancelled(true);

    }

    @Test
    @DisplayName("NUMBER_KEY с черносписочным предметом в хотбаре отменяется")
    void numberKeyWithBlacklistedHotbarItemIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        when(configManager.isBlacklistEnabled()).thenReturn(true);
        when(configManager.isBlacklisted(Material.BARRIER)).thenReturn(true);

        bottom.setItem(7, new FakeItemStack(Material.BARRIER, 64));

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

    @Test
    @DisplayName("Клик в чужом инвентаре guard не обрабатывает вовсе")
    void foreignInventoryIsNotProcessed() {

        Inventory chest = TestInventories.inventory(27);
        InventoryClickEvent event = mock(InventoryClickEvent.class);

        when(event.getInventory()).thenReturn(chest);
        listener().onInventoryClick(event);

        verify(event, never()).setCancelled(true);
        verify(sessionRegistry, never()).getSessionByInventory(any());

    }

    // ------------------------------------------------------------------
    // Drag: исторический дюп «через shift/F» закрывался и здесь
    // ------------------------------------------------------------------

    private InventoryDragEvent drag(Set<Integer> rawSlots,
                                    ItemStack cursor,
                                    Map<Integer, ItemStack> newItems) {

        InventoryDragEvent event = mock(InventoryDragEvent.class);
        InventoryView view = TestInventories.view(gui, bottom);

        when(event.getInventory()).thenReturn(gui);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getView()).thenReturn(view);
        when(event.getRawSlots()).thenReturn(rawSlots);
        when(event.getCursor()).thenReturn(cursor);
        when(event.getNewItems()).thenReturn(newItems);
        return event;

    }

    @Test
    @DisplayName("Drag по слоту открытого бокса отменяется")
    void dragOntoShulkerSlotIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        InventoryView dragView = TestInventories.view(gui, bottom);
        when(player.getOpenInventory()).thenReturn(dragView);

        // rawSlot 31 = нижний слот 4 (27 + 4) - слот открытого бокса
        InventoryDragEvent event = drag(Set.of(31), new FakeItemStack(Material.DIAMOND, 1),
                Map.of(31, new FakeItemStack(Material.DIAMOND, 1)));

        listener().onInventoryDrag(event);
        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Drag шалкер-бокса в GUI отменяется: вложенность не пролезает")
    void dragOfShulkerIntoGuiIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        InventoryView dragView = TestInventories.view(gui, bottom);
        when(player.getOpenInventory()).thenReturn(dragView);

        ItemStack dragged = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        InventoryDragEvent event = drag(Set.of(3), null, Map.of(3, dragged));

        listener().onInventoryDrag(event);
        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Drag черносписочного курсора в GUI отменяется")
    void dragOfBlacklistedCursorIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        InventoryView dragView = TestInventories.view(gui, bottom);
        when(player.getOpenInventory()).thenReturn(dragView);
        when(configManager.isBlacklistEnabled()).thenReturn(true);
        when(configManager.isBlacklisted(Material.BEDROCK)).thenReturn(true);

        InventoryDragEvent event = drag(Set.of(5),
                new FakeItemStack(Material.BEDROCK, 1), Map.of());

        listener().onInventoryDrag(event);
        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Drag разрешенного предмета проходит")
    void dragOfAllowedItemPasses() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        InventoryView dragView = TestInventories.view(gui, bottom);
        when(player.getOpenInventory()).thenReturn(dragView);

        InventoryDragEvent event = drag(Set.of(5),
                new FakeItemStack(Material.DIAMOND, 2),
                Map.of(5, new FakeItemStack(Material.DIAMOND, 1)));

        listener().onInventoryDrag(event);
        verify(event, never()).setCancelled(true);

    }

    // ------------------------------------------------------------------
    // Обмен руками (F)
    // ------------------------------------------------------------------

    private PlayerSwapHandItemsEvent swap(ItemStack mainHand, ItemStack offHand) {

        PlayerSwapHandItemsEvent event = mock(PlayerSwapHandItemsEvent.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getMainHandItem()).thenReturn(mainHand);
        when(event.getOffHandItem()).thenReturn(offHand);
        return event;

    }

    @Test
    @DisplayName("Обмен рук с открытым боксом в основной руке отменяется")
    void swapWithOpenBoxInMainHandIsCancelled() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSession(player)).thenReturn(session);
        when(bottom.getHeldItemSlot()).thenReturn(4);
        bottom.setItem(4, new FakeItemStack(Material.WHITE_SHULKER_BOX, 1));

        PlayerSwapHandItemsEvent event = swap(
                new FakeItemStack(Material.WHITE_SHULKER_BOX, 1),
                new FakeItemStack(Material.DIAMOND, 1));

        listener().onPlayerSwapHands(event);
        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Обмен рук с открытым боксом во второй руке отменяется")
    void swapWithOpenBoxInOffHandIsCancelled() {

        ShulkerSession session = session(40);
        when(sessionRegistry.getSession(player)).thenReturn(session);
        bottom.setItem(40, new FakeItemStack(Material.WHITE_SHULKER_BOX, 1));

        PlayerSwapHandItemsEvent event = swap(
                new FakeItemStack(Material.DIAMOND, 1),
                new FakeItemStack(Material.WHITE_SHULKER_BOX, 1));

        listener().onPlayerSwapHands(event);
        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Обмен рук без сессии проходит")
    void swapWithoutSessionPasses() {

        PlayerSwapHandItemsEvent event = swap(
                new FakeItemStack(Material.DIAMOND, 1),
                new FakeItemStack(Material.STONE, 1));

        listener().onPlayerSwapHands(event);
        verify(event, never()).setCancelled(true);

    }

    // ------------------------------------------------------------------
    // Выбросы
    // ------------------------------------------------------------------

    private PlayerDropItemEvent drop(ItemStack stack) {

        Item item = mock(Item.class);
        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);

        when(event.getPlayer()).thenReturn(player);
        when(event.getItemDrop()).thenReturn(item);
        when(item.getItemStack()).thenReturn(stack);
        return event;

    }

    private void openSession(int slot) {

        ShulkerSession session = session(slot);

        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);
        when(sessionRegistry.getSession(player)).thenReturn(session);
        when(sessionRegistry.getSession(playerId)).thenReturn(session);
        when(sessionRegistry.hasSession(playerId)).thenReturn(true);

    }

    @Test
    @DisplayName("Выброс содержимого из GUI не блокируется")
    void dropOfGuiContentPasses() {

        openSession(4);
        PlayerDropItemEvent event = drop(new FakeItemStack(Material.STONE, 2));
        listener().onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
        verify(player, never()).sendMessage(anyString());

    }

    @Test
    @DisplayName("Выброс чужого бокса из GUI не блокируется")
    void dropOfOtherShulkerFromGuiPasses() {

        openSession(4);
        bottom.setItem(4, new FakeItemStack(Material.WHITE_SHULKER_BOX, 1));

        PlayerDropItemEvent event = drop(new FakeItemStack(Material.BLACK_SHULKER_BOX, 1));
        listener().onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
        verify(player, never()).sendMessage(anyString());

    }

    @Test
    @DisplayName("Выброс бокса-близнеца после пропущенного клика не блокируется")
    void dropOfTwinShulkerAfterAllowedClickPasses() {

        openSession(4);

        ItemStack twin = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        bottom.setItem(5, twin);

        ShulkerGuardListener listener = listener();

        // Q по слоту близнеца внизу: guard клик пропускает и ставит маркер с отпечатком
        InventoryClickEvent click = click(27 + 5, 9, ClickType.DROP);
        when(click.getCurrentItem()).thenReturn(twin);
        listener.onInventoryClick(click);

        PlayerDropItemEvent event = drop(twin.clone());
        listener.onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
        verify(player, never()).sendMessage(anyString());

    }

    @Test
    @DisplayName("Маркер чужого клика не оправдывает выброс открытого бокса")
    void markerOfOtherItemDoesNotExcuseOpenBox() {

        openSession(4);

        ItemStack box = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        bottom.setItem(4, box);

        ShulkerGuardListener listener = listener();

        // Легальный Q по содержимому GUI (камень) ставит маркер STONE
        ItemStack stone = new FakeItemStack(Material.STONE, 2);
        gui.setItem(3, stone);

        InventoryClickEvent click = click(3, 9, ClickType.DROP);
        when(click.getCurrentItem()).thenReturn(stone);
        listener.onInventoryClick(click);

        // Следом внешний путь выбрасывает сам открытый бокс: отпечаток не совпадает
        PlayerDropItemEvent event = drop(box.clone());
        listener.onPlayerDropItem(event);

        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("Q по слоту открытого бокса не ставит метку: выброс остается закрыт")
    void dropClickOnShulkerSlotLeavesNoMarker() {

        openSession(4);

        ItemStack box = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        bottom.setItem(4, box);

        ShulkerGuardListener listener = listener();
        listener.onInventoryClick(click(27 + 4, 9, ClickType.DROP));
        PlayerDropItemEvent event = drop(box.clone());
        listener.onPlayerDropItem(event);

        verify(event).setCancelled(true);
        // Сообщение клика (SELF_REMOVE) плюс сообщение выброса (DROP_BLOCKED)
        verify(player, times(2)).sendMessage(anyString());

    }

    @Test
    @DisplayName("Выброс самого открытого бокса чужим путем блокируется")
    void dropOfOpenShulkerWithoutClickIsCancelled() {

        openSession(4);

        ItemStack box = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        bottom.setItem(4, box);

        PlayerDropItemEvent event = drop(box.clone());
        listener().onPlayerDropItem(event);

        verify(event).setCancelled(true);
        verify(player).sendMessage(anyString());

    }

    @Test
    @DisplayName("Без сессии выброс шалкера легален: бокс закрыт и metaData цела")
    void dropOfClosedShulkerPasses() {

        when(sessionRegistry.getSession(playerId)).thenReturn(null);
        PlayerDropItemEvent event = drop(new FakeItemStack(Material.WHITE_SHULKER_BOX, 1));
        listener().onPlayerDropItem(event);

        verify(event, never()).setCancelled(true);
        verify(player, never()).sendMessage(anyString());

    }


    @Test
    @DisplayName("Клик мимо окна с курсором ставит маркер на выброс стека курсора")
    void cursorThrowOutsideWindowIsMarked() {

        openSession(4);

        ItemStack cursor = new FakeItemStack(Material.STONE, 5);
        ShulkerGuardListener listener = listener();

        InventoryClickEvent click = click(-1, 9, ClickType.LEFT);
        when(click.getCursor()).thenReturn(cursor);
        listener.onInventoryClick(click);

        // Ванильный выброс стека с курсора: тип совпал - не блокируется
        PlayerDropItemEvent allowed = drop(new FakeItemStack(Material.STONE, 5));
        listener.onPlayerDropItem(allowed);
        verify(allowed, never()).setCancelled(true);

    }

    @Test
    @DisplayName("Маркер курсора не оправдывает выброс другого типа")
    void cursorMarkerDoesNotExcuseOtherType() {

        openSession(4);

        ItemStack box = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        bottom.setItem(4, box);

        ItemStack cursor = new FakeItemStack(Material.STONE, 5);
        ShulkerGuardListener listener = listener();

        InventoryClickEvent click = click(-1, 9, ClickType.LEFT);
        when(click.getCursor()).thenReturn(cursor);
        listener.onInventoryClick(click);

        // Внешний путь выбрасывает открытый бокс: отпечаток STONE не совпадает
        PlayerDropItemEvent event = drop(box.clone());
        listener.onPlayerDropItem(event);
        verify(event).setCancelled(true);

    }

    @Test
    @DisplayName("NUMBER_KEY с hotbarButton=-1 не блокирует клик")
    void numberKeyWithoutHotbarSourcePasses() {

        ShulkerSession session = session(4);
        when(sessionRegistry.getSessionByInventory(gui)).thenReturn(session);

        InventoryClickEvent event = click(5, -1, ClickType.NUMBER_KEY);
        listener().onInventoryClick(event);

        verify(event, never()).setCancelled(true);

    }

}
