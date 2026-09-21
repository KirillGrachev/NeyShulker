package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.ValidationReason;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.ShulkerOpenService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка слушателя взаимодействия: открытие GUI идет через отложенную
 * задачу главного потока, а отказы валидации не открывают ничего.
 */
class PlayerInteractListenerTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final ShulkerValidationService validationService = mock(ShulkerValidationService.class);
    private final ShulkerOpenService openService = mock(ShulkerOpenService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final eu.neydev.neyshulker.registry.SessionRegistry sessionRegistry =
            mock(eu.neydev.neyshulker.registry.SessionRegistry.class);

    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = TestInventories.playerInventory();

    @org.junit.jupiter.api.BeforeEach
    void bindInventory() {
        when(player.getInventory()).thenReturn(inventory);
    }

    private PlayerInteractListener listener() {

        ServiceContainer container = mock(ServiceContainer.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(container.getConfigManager()).thenReturn(configManager);
        when(container.getValidationService()).thenReturn(validationService);
        when(container.getOpenService()).thenReturn(openService);
        when(container.getMessageService()).thenReturn(messageService);
        when(container.getSessionRegistry()).thenReturn(sessionRegistry);
        when(plugin.getServices()).thenReturn(container);

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getItemOnCursor()).thenReturn(null);

        return new PlayerInteractListener(plugin);

    }

    private PlayerInteractEvent event(Action action) {

        PlayerInteractEvent event = mock(PlayerInteractEvent.class);

        when(event.getAction()).thenReturn(action);
        when(event.getHand()).thenReturn(EquipmentSlot.HAND);
        when(event.getPlayer()).thenReturn(player);

        return event;

    }

    @Test
    @DisplayName("Левый клик и чужая рука игнорируются")
    void ignoresWrongActions() {

        PlayerInteractListener listener = listener();

        listener.onPlayerInteract(event(Action.LEFT_CLICK_AIR));

        verify(validationService, never()).canOpen(any(), any(), any());

    }

    @Test
    @DisplayName("Отказ валидации без сообщения просто отдает клик ванили")
    void methodMismatchPassesToVanilla() {

        FakeItemStack shulker = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(0, shulker);

        when(validationService.canOpen(eq(player), any(), any()))
                .thenReturn(ValidationResult.denied(ValidationReason.METHOD_MISMATCH));

        PlayerInteractEvent event = event(Action.RIGHT_CLICK_BLOCK);

        listener().onPlayerInteract(event);

        verify(event, never()).setCancelled(true);
        verify(openService, never()).open(any(), any(), org.mockito.ArgumentMatchers.anyInt());

    }

    @Test
    @DisplayName("Отказ с сообщением озвучивается игроку")
    void deniedWithMessageIsSent() {

        FakeItemStack shulker = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(0, shulker);

        when(validationService.canOpen(eq(player), any(), any()))
                .thenReturn(ValidationResult.denied(ValidationReason.BLACKLISTED));

        listener().onPlayerInteract(event(Action.RIGHT_CLICK_AIR));

        verify(messageService).send(player, MessageKey.BLACKLISTED);
        verify(openService, never()).open(any(), any(), org.mockito.ArgumentMatchers.anyInt());

    }

    @Test
    @DisplayName("Разрешенное открытие уходит в задачу главного потока")
    void allowedOpenIsDeferredToMainThreadTask() {

        FakeItemStack shulker = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(0, shulker);

        when(player.getInventory().getHeldItemSlot()).thenReturn(0);
        when(player.getInventory().getItemInMainHand()).thenReturn(shulker);
        when(validationService.canOpen(eq(player), any(), any()))
                .thenReturn(ValidationResult.allowed());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        NeyShulker plugin = mock(NeyShulker.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            PlayerInteractEvent event = event(Action.RIGHT_CLICK_AIR);

            listener().onPlayerInteract(event);

            verify(event).setCancelled(true);
            verify(scheduler).runTask(any(Plugin.class), any(Runnable.class));

        }

    }

    @Test
    @DisplayName("Открытие поддерживается из второй руки")
    void offHandOpenUsesOffHandSlot() {

        FakeItemStack shulker = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(40, shulker);

        when(inventory.getItemInOffHand()).thenReturn(shulker);
        when(player.isOnline()).thenReturn(true);
        when(validationService.canOpen(eq(player), any(), any()))
                .thenReturn(ValidationResult.allowed());

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        org.mockito.ArgumentCaptor<Runnable> task =
                org.mockito.ArgumentCaptor.forClass(Runnable.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);

            PlayerInteractEvent event = event(Action.RIGHT_CLICK_AIR);

            when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);

            listener().onPlayerInteract(event);

            verify(scheduler).runTask(any(org.bukkit.plugin.Plugin.class), task.capture());

        }

        task.getValue().run();

        verify(openService).open(player, shulker, 40);

    }

    @Test
    @DisplayName("Живая сессия глотает повторное открытие молча")
    void openSessionSwallowsSecondOpen() {

        FakeItemStack shulker = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(0, shulker);

        when(sessionRegistry.hasSession(player.getUniqueId())).thenReturn(true);

        listener().onPlayerInteract(event(Action.RIGHT_CLICK_AIR));

        verify(validationService, never()).canOpen(any(), any(), any());
        verify(openService, never()).open(any(), any(), org.mockito.ArgumentMatchers.anyInt());

    }

    @Test
    @DisplayName("Предмет на курсоре блокирует открытие")
    void cursorItemBlocksOpen() {

        PlayerInteractListener listener = listener();

        when(player.getItemOnCursor()).thenReturn(new FakeItemStack(Material.STONE, 1));

        listener.onPlayerInteract(event(Action.RIGHT_CLICK_AIR));

        verify(validationService, never()).canOpen(any(), any(), any());

    }
}
