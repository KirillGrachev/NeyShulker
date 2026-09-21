package eu.neydev.neyshulker.placeholder;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка PlaceholderAPI-расширения: идентификатор, ключи и безопасные пустые ответы.
 */
class NeyShulkerExpansionTest {

    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final AutoCollectService autoCollectService = mock(AutoCollectService.class);

    private final Player player = mock(Player.class);

    private NeyShulkerExpansion expansion() {

        ServiceContainer container = mock(ServiceContainer.class);
        NeyShulker plugin = mock(NeyShulker.class);

        when(container.getSessionRegistry()).thenReturn(sessionRegistry);
        when(container.getAutoCollectService()).thenReturn(autoCollectService);
        when(plugin.getServices()).thenReturn(container);

        return new NeyShulkerExpansion(plugin);

    }

    @Test
    @DisplayName("Идентификатор и флаги")
    void identifierAndFlags() {

        NeyShulkerExpansion expansion = expansion();

        assertEquals("neyshulker", expansion.getIdentifier());
        assertEquals("Ney", expansion.getAuthor());
        org.junit.jupiter.api.Assertions.assertTrue(expansion.persist());

    }

    @Test
    @DisplayName("Ключи сессии без открытого бокса")
    void sessionKeysWithoutSession() {

        when(sessionRegistry.getSession(player)).thenReturn(null);
        when(autoCollectService.isRunning()).thenReturn(true);
        when(autoCollectService.isEnabledFor(player)).thenReturn(true);

        NeyShulkerExpansion expansion = expansion();

        assertEquals("false", expansion.onRequest(player, "open"));
        assertEquals("", expansion.onRequest(player, "name"));
        assertEquals("true", expansion.onRequest(player, "autocollect"));
        assertEquals("27", expansion.onRequest(player, "total_slots"));
        assertNull(expansion.onRequest(player, "unknown_key"));

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

        assertEquals("true", expansion.onRequest(player, "open"));
        assertEquals("4", expansion.onRequest(player, "slot"));
        assertEquals("27", expansion.onRequest(player, "free_slots"));
        assertEquals("0", expansion.onRequest(player, "used_slots"));
        assertEquals("0", expansion.onRequest(player, "items"));

    }
}
