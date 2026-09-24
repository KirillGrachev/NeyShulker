package eu.neydev.neyshulker.registry;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка реестра сессий: одна сессия на игрока, поиск по инвентарю, снятие.
 */
class SessionRegistryTest {

    private final SessionRegistry registry = new SessionRegistry();

    private Player player() {

        Player player = mock(Player.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        return player;

    }

    private ItemStack shulker() {

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.clone()).thenReturn(shulker);

        return shulker;

    }

    @Test
    @DisplayName("Вторая сессия игрока не создается")
    void oneSessionPerPlayer() {

        Player player = player();
        Inventory gui = TestInventories.inventory(27);

        ShulkerSession first = registry.createSession(player, shulker(), 0, () -> gui);

        assertNull(registry.createSession(player, shulker(), 1, () -> gui));
        assertSame(first, registry.getSession(player));
        assertTrue(registry.hasSession(player.getUniqueId()));

    }

    @Test
    @DisplayName("Сессия находится по GUI-инвентарю")
    void sessionFoundByInventory() {

        Player player = player();
        Inventory gui = TestInventories.inventory(27);
        Inventory foreign = TestInventories.inventory(27);

        ShulkerSession session = registry.createSession(player, shulker(), 0, () -> gui);

        assertSame(session, registry.getSessionByInventory(gui));
        assertNull(registry.getSessionByInventory(foreign));
        assertNull(registry.getSessionByInventory(null));

    }

    @Test
    @DisplayName("closeSession снимает сессию, clear снимает все")
    void closeAndClear() {

        Player first = player();
        Player second = player();

        registry.createSession(first, shulker(), 0, () -> TestInventories.inventory(27));
        registry.createSession(second, shulker(), 0, () -> TestInventories.inventory(27));

        assertNotNull(registry.closeSession(first.getUniqueId()));
        assertNull(registry.getSession(first));
        assertTrue(registry.getSession(second) != null);

        registry.clear();

        assertTrue(registry.isEmpty());

    }

    private static void assertNotNull(Object value) {
        org.junit.jupiter.api.Assertions.assertNotNull(value);
    }
}
