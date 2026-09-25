package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.ValidationReason;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка сервиса валидации: способы открытия, черный список, вложенность,
 * защита открытого шалкер-бокса.
 */
class ShulkerValidationServiceTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);

    private final ShulkerValidationService validationService =
            new ShulkerValidationService(configManager, permissionService, sessionRegistry);

    private final Player player = mock(Player.class);

    private ItemStack shulker() {
        return new FakeItemStack(Material.WHITE_SHULKER_BOX, 1);
    }

    @Test
    @DisplayName("Выключенный плагин ничего не открывает")
    void disabledPluginBlocksOpen() {

        when(configManager.isPluginEnabled()).thenReturn(false);

        assertEquals(ValidationReason.PLUGIN_DISABLED,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).reason());

    }

    @Test
    @DisplayName("Не шалкер и отсутствие права не открывают GUI")
    void notShulkerAndNoPermission() {

        when(configManager.isPluginEnabled()).thenReturn(true);

        assertEquals(ValidationReason.NOT_SHULKER,
                validationService.canOpen(player, new FakeItemStack(Material.STONE, 1),
                        Action.RIGHT_CLICK_AIR).reason());

        when(permissionService.has(player, PermissionNode.USE)).thenReturn(false);

        assertEquals(ValidationReason.NO_PERMISSION,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).reason());

    }

    @Test
    @DisplayName("Черный список блокирует открытие, bypass снимает блок")
    void blacklistBlocksOpen() {

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);
        when(configManager.isBlacklistEnabled()).thenReturn(true);
        when(configManager.isBlacklisted(Material.WHITE_SHULKER_BOX)).thenReturn(true);
        when(permissionService.canBypassBlacklist(player)).thenReturn(false);

        assertEquals(ValidationReason.BLACKLISTED,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).reason());

        when(permissionService.canBypassBlacklist(player)).thenReturn(true);
        when(configManager.getOpenMethod()).thenReturn(OpenMethodType.ALWAYS);

        assertTrue(validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).isAllowed());

    }

    @Test
    @DisplayName("SHIFT: открывает только крадущийся игрок")
    void shiftMethodRequiresSneak() {

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);
        when(configManager.getOpenMethod()).thenReturn(OpenMethodType.SHIFT);

        when(player.isSneaking()).thenReturn(false);
        assertEquals(ValidationReason.METHOD_MISMATCH,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).reason());

        when(player.isSneaking()).thenReturn(true);
        assertTrue(validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).isAllowed());

    }

    @Test
    @DisplayName("AIR: воздух открывает, блоки остаются ванильными")
    void airMethodOpensOnlyAir() {

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);
        when(configManager.getOpenMethod()).thenReturn(OpenMethodType.AIR);

        when(player.isSneaking()).thenReturn(false);
        assertTrue(validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).isAllowed());
        assertEquals(ValidationReason.METHOD_MISMATCH,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_BLOCK).reason(),
                "Клик по блоку не отменяется: установка шалкера работает");

        when(player.isSneaking()).thenReturn(true);
        assertTrue(validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).isAllowed());
        assertEquals(ValidationReason.METHOD_MISMATCH,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_BLOCK).reason());

    }

    @Test
    @DisplayName("SMART: Shift открывает везде, без Shift только воздух")
    void smartMethodSneakAnywhereAirQuick() {

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(permissionService.has(player, PermissionNode.USE)).thenReturn(true);
        when(configManager.getOpenMethod()).thenReturn(OpenMethodType.SMART);

        when(player.isSneaking()).thenReturn(false);
        assertTrue(validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_AIR).isAllowed());
        assertEquals(ValidationReason.METHOD_MISMATCH,
                validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_BLOCK).reason());
        assertEquals(ValidationReason.METHOD_MISMATCH,
                validationService.canOpen(player, shulker(), Action.LEFT_CLICK_AIR).reason());

        when(player.isSneaking()).thenReturn(true);
        assertTrue(validationService.canOpen(player, shulker(), Action.RIGHT_CLICK_BLOCK).isAllowed());

    }

    @Test
    @DisplayName("Шалкер в шалкере запрещен всегда, без тумблеров")
    void nestedShulkerAlwaysDenied() {

        ItemStack nested = new FakeItemStack(Material.BLACK_SHULKER_BOX, 1);

        assertEquals(ValidationReason.NESTED_SHULKER,
                validationService.canEnterShulker(player, nested).reason());

    }

    @Test
    @DisplayName("Сам открытый шалкер нельзя переложить")
    void openShulkerCannotBeMoved() {

        Inventory inventory = TestInventories.inventory(27);
        ItemStack openItem = shulker();

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, openItem,
                () -> inventory, 3);

        when(sessionRegistry.getSession(player)).thenReturn(session);

        assertEquals(ValidationReason.OPEN_SHULKER,
                validationService.canEnterShulker(player, openItem).reason());

        // Чужой бокс того же типа: не открытый, но вложенность запрещена всегда
        assertEquals(ValidationReason.NESTED_SHULKER,
                validationService.canEnterShulker(player,
                        new FakeItemStack(Material.BLACK_SHULKER_BOX, 1)).reason());

    }

    @Test
    @DisplayName("Черный список предметов работает на вложение")
    void blacklistOnEnter() {

        ItemStack stone = new FakeItemStack(Material.STONE, 5);

        when(configManager.isBlacklistEnabled()).thenReturn(true);
        when(configManager.isBlacklisted(Material.STONE)).thenReturn(true);
        when(permissionService.canBypassBlacklist(player)).thenReturn(false);

        assertEquals(ValidationReason.BLACKLISTED,
                validationService.canEnterShulker(player, stone).reason());

    }

    @Test
    @DisplayName("isOpenShulker узнает открытый бокс игрока")
    void detectsOpenShulker() {

        Inventory inventory = TestInventories.inventory(27);
        ItemStack openItem = shulker();

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, openItem,
                () -> inventory, 0);

        when(sessionRegistry.getSession(player)).thenReturn(session);

        assertTrue(validationService.isOpenShulker(player, openItem));
        assertFalse(validationService.isOpenShulker(player,
                new FakeItemStack(Material.BLACK_SHULKER_BOX, 1)));
        assertFalse(validationService.isOpenShulker(player, new FakeItemStack(Material.STONE, 1)));
        assertFalse(validationService.isOpenShulker(player, null));

    }

    @Test
    @DisplayName("Открытый бокс в руке узнается по слоту, а не по мете")
    void detectsHeldOpenShulkerBySlot() {

        PlayerInventory inventory = TestInventories.playerInventory();
        ItemStack openItem = shulker();

        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getHeldItemSlot()).thenReturn(4);
        inventory.setItem(4, openItem);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, openItem,
                () -> inventory, 4);

        when(sessionRegistry.getSession(player)).thenReturn(session);

        assertTrue(validationService.isHeldOpenShulker(player),
                "Бокс в слоте сессии должен узнаваться независимо от меты");

        // Слот сессии пуст - значит бокс уже не там, защиту не включаем
        inventory.setItem(4, null);
        assertFalse(validationService.isHeldOpenShulker(player));

        // Другой слот хотбара выбран - это не открытый бокс
        inventory.setItem(4, openItem);
        when(inventory.getHeldItemSlot()).thenReturn(7);
        assertFalse(validationService.isHeldOpenShulker(player));

    }

    @Test
    @DisplayName("Без сессии выброс не блокируется")
    void noSessionNoBlock() {

        when(sessionRegistry.getSession(player)).thenReturn(null);
        assertFalse(validationService.isHeldOpenShulker(player));

    }

    @Test
    @DisplayName("Выброшенный открытый бокс узнается по живому стеку слота")
    void detectsDroppedOpenShulkerByLiveSlotStack() {

        PlayerInventory inventory = TestInventories.playerInventory();
        ItemStack box = shulker();

        when(player.getInventory()).thenReturn(inventory);
        inventory.setItem(6, box);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, box,
                () -> inventory, 6);

        when(sessionRegistry.getSession(player)).thenReturn(session);

        assertTrue(validationService.isDroppedOpenShulker(player, box.clone()),
                "Бокс узнается по живому стеку слота, а не по мете сессии");
        assertFalse(validationService.isDroppedOpenShulker(player,
                new FakeItemStack(Material.BLACK_SHULKER_BOX, 1)));
        assertFalse(validationService.isDroppedOpenShulker(player, new FakeItemStack(Material.STONE, 1)));
        assertFalse(validationService.isDroppedOpenShulker(player, null));

        // Слот сессии пуст - значит бокс уже не там, защиту не включаем
        inventory.setItem(6, null);
        assertFalse(validationService.isDroppedOpenShulker(player, box.clone()));

    }

    @Test
    @DisplayName("Без сессии выброшенный шалкер не считается открытым")
    void droppedShulkerWithoutSessionIsNotOpen() {

        when(sessionRegistry.getSession(player)).thenReturn(null);
        assertFalse(validationService.isDroppedOpenShulker(player, shulker()));

    }

    @Test
    @DisplayName("Слот открытого шалкера заблокирован")
    void shulkerSlotIsLocked() {

        Inventory inventory = TestInventories.inventory(27);

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player, shulker(),
                () -> inventory, 7);

        assertTrue(validationService.isShulkerSlot(session, 7));
        assertFalse(validationService.isShulkerSlot(session, 6));

        assertFalse(validationService.isShulkerSlot(session, -1));
        assertFalse(validationService.isShulkerSlot(null, 7));

    }

}
