package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Цель «открытое GUI»: вставка помечает цель использованной,
 * resync клиента не выполняется на каждую вставку (его делает flush скана).
 */
class SessionCollectTargetTest {

    private final Inventory gui = TestInventories.inventory(27);
    private final ShulkerSession session = session();

    private ShulkerSession session() {

        Player owner = mock(Player.class);
        ItemStack shulker = mock(ItemStack.class);

        when(owner.getUniqueId()).thenReturn(UUID.randomUUID());
        when(shulker.clone()).thenReturn(shulker);
        return ShulkerSession.create(UUID.randomUUID(), owner, shulker, () -> gui, 2);

    }

    @Test
    @DisplayName("Успешная вставка помечает цель использованной")
    void insertMarksUsed() {

        SessionCollectTarget target = new SessionCollectTarget(session, new InventoryTransferService());

        assertFalse(target.isUsed());

        int inserted = target.insert(new FakeItemStack(Material.DIAMOND, 3));

        assertEquals(3, inserted);
        assertTrue(target.isUsed());
        assertEquals(3, gui.getItem(0).getAmount());

    }

    @Test
    @DisplayName("Нулевая вставка не помечает цель")
    void zeroInsertKeepsUnused() {

        for (int i = 0; i < gui.getSize(); i++) {
            gui.setItem(i, new FakeItemStack(Material.STONE, 64));
        }

        SessionCollectTarget target = new SessionCollectTarget(session, new InventoryTransferService());

        assertEquals(0, target.insert(new FakeItemStack(Material.DIAMOND, 1)));
        assertFalse(target.isUsed());

    }

    @Test
    @DisplayName("Доступы к сессии и слепку предмета")
    void exposesSessionAndItem() {

        SessionCollectTarget target = new SessionCollectTarget(session, new InventoryTransferService());

        assertSame(session, target.getSession());
        assertSame(session.shulkerItem(), target.shulkerItem());

    }

}
