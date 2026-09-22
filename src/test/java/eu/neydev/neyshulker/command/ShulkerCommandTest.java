package eu.neydev.neyshulker.command;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.service.ShulkerOpenService;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка команды /shulker: права, ветки подкоманд и tab-completer.
 */
class ShulkerCommandTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final ShulkerOpenService openService = mock(ShulkerOpenService.class);
    private final AutoCollectService autoCollectService = mock(AutoCollectService.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

    private final Player player = mock(Player.class);
    private final PlayerInventory inventory = TestInventories.playerInventory();
    private final Command command = mock(Command.class);

    @org.junit.jupiter.api.BeforeEach
    void bindInventory() {
        when(player.getInventory()).thenReturn(inventory);
    }

    private ShulkerCommand commandExecutor() {

        ServiceContainer container = mock(ServiceContainer.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(container.getConfigManager()).thenReturn(configManager);
        when(container.getPermissionService()).thenReturn(permissionService);
        when(container.getMessageService()).thenReturn(messageService);
        when(container.getOpenService()).thenReturn(openService);
        when(container.getAutoCollectService()).thenReturn(autoCollectService);
        when(container.getSessionRegistry()).thenReturn(sessionRegistry);
        when(plugin.getServices()).thenReturn(container);
        when(plugin.getConfigManager()).thenReturn(configManager);

        when(player.getInventory()).thenReturn(inventory);

        return new ShulkerCommand(plugin);

    }

    @Test
    @DisplayName("Без аргументов печатается справка")
    void noArgsPrintsUsage() {

        commandExecutor().onCommand(player, command, "shulker", new String[0]);

        verify(messageService).send((org.bukkit.command.CommandSender) player, MessageKey.USAGE, java.util.Map.of());

    }

    @Test
    @DisplayName("reload без права отклоняется, с правом перечитывает конфиг")
    void reloadRespectsPermission() {

        when(permissionService.has((org.bukkit.command.CommandSender) player, PermissionNode.RELOAD))
                .thenReturn(false);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"reload"});

        verify(messageService).send((org.bukkit.command.CommandSender) player, MessageKey.NO_PERMISSION, java.util.Map.of());
        verify(configManager, never()).reload();

        when(permissionService.has((org.bukkit.command.CommandSender) player, PermissionNode.RELOAD))
                .thenReturn(true);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"reload"});

        verify(configManager).reload();
        verify(messageService).send((org.bukkit.command.CommandSender) player, MessageKey.RELOAD, java.util.Map.of());

    }

    @Test
    @DisplayName("open без шалкера в руке говорит про руку")
    void openWithoutShulker_mentionsHand() {

        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);
        inventory.setItem(0, null);
        when(player.getInventory().getHeldItemSlot()).thenReturn(0);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"open"});

        verify(messageService).send(player, MessageKey.NO_SHULKER_IN_HAND);
        verify(openService, never()).open(any(), any(), anyInt());

    }

    @Test
    @DisplayName("open с шалкером открывает его из руки")
    void openWithShulkerOpensIt() {

        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);
        FakeItemStack shulker = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(0, shulker);
        when(player.getInventory().getHeldItemSlot()).thenReturn(0);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"open"});

        verify(openService).open(player, shulker, 0);

    }

    @Test
    @DisplayName("open находит шалкер во второй руке")
    void openFindsShulkerInOffHand() {

        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);

        FakeItemStack offhand = new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
        inventory.setItem(0, null);
        inventory.setItem(40, offhand);
        when(player.getInventory().getHeldItemSlot()).thenReturn(0);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"open"});

        verify(openService).open(player, offhand, 40);

    }

    @Test
    @DisplayName("autocollect переключает состояние и озвучивает его")
    void autocollectToggles() {

        when(permissionService.has(player, PermissionNode.AUTO_COLLECT)).thenReturn(true);
        when(autoCollectService.toggle(player)).thenReturn(false);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"autocollect"});

        verify(messageService).send(player, MessageKey.AUTO_COLLECT_OFF);

    }

    @Test
    @DisplayName("info без сессии шлет настраиваемое сообщение info_idle")
    void infoWithoutSessionSendsConfigMessage() {

        when(sessionRegistry.getSession(player)).thenReturn(null);
        when(configManager.isAutoCollectEnabled()).thenReturn(true);
        when(autoCollectService.isEnabledFor(player)).thenReturn(true);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"info"});

        verify(messageService).send(eq(player), eq(MessageKey.INFO_IDLE), anyMap());

    }

    @Test
    @DisplayName("info с сессией шлет настраиваемое сообщение info_session")
    void infoWithSessionSendsConfigMessage() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenReturn(shulker);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 3);

        when(sessionRegistry.getSession(player)).thenReturn(session);

        commandExecutor().onCommand(player, command, "shulker", new String[]{"info"});

        verify(messageService).send(eq(player), eq(MessageKey.INFO_SESSION), anyMap());

    }

    @Test
    @DisplayName("Tab-completer предлагает подкоманды по префиксу")
    void tabCompletesSubcommands() {

        List<String> all = commandExecutor().onTabComplete(player, command, "shulker", new String[]{""});

        assertEquals(List.of("reload", "open", "info", "autocollect"), all);

        List<String> filtered = commandExecutor().onTabComplete(player, command, "shulker", new String[]{"au"});

        assertTrue(filtered.contains("autocollect"));
        assertEquals(1, filtered.size());

    }
}
