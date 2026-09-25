package eu.neydev.neyshulker.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка арифметики слотов InventoryView: верх, низ, служебные слоты.
 */
class ViewSlotUtilTest {

    private final Inventory top = TestInventories.inventory(27);
    private final PlayerInventory bottom = TestInventories.playerInventory();
    private final InventoryView view = TestInventories.view(top, bottom);

    @Test
    @DisplayName("Слоты верха считаются до размера верхнего инвентаря")
    void resolvesTopSlots() {

        assertEquals(0, ViewSlotUtil.topSlot(view, 0));
        assertEquals(26, ViewSlotUtil.topSlot(view, 26));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.topSlot(view, 27));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.topSlot(view, -1));

    }

    @Test
    @DisplayName("Слоты низа смещены на размер верха")
    void resolvesBottomSlots() {

        assertEquals(0, ViewSlotUtil.bottomSlot(view, 27));
        assertEquals(14, ViewSlotUtil.bottomSlot(view, 41));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.bottomSlot(view, 26));

    }

    @Test
    @DisplayName("Служебные слоты игрока не считаются слотами хранения")
    void armorSlotsAreOutsideStorage() {

        // 36..40 - броня и вторая рука: ванильная логика, не наша
        assertEquals(35, ViewSlotUtil.bottomStorageSlot(view, 27 + 35));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.bottomStorageSlot(view, 27 + 36));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.bottomStorageSlot(view, 27 + 40));

    }

    @Test
    @DisplayName("Обычный инвентарь без PlayerInventory считает хранением весь размер")
    void nonPlayerInventoryUsesFullSize() {

        Inventory plainBottom = TestInventories.inventory(9);
        InventoryView plainView = TestInventories.view(top, plainBottom);

        assertEquals(8, ViewSlotUtil.bottomStorageSlot(plainView, 27 + 8));

    }

    @Test
    @DisplayName("resolve возвращает инвентарь владельца слота")
    void resolvesOwnerInventory() {

        assertSame(top, ViewSlotUtil.resolve(view, 5));
        assertSame(bottom, ViewSlotUtil.resolve(view, 30));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.topSlot(view, 200));

    }

    @Test
    @DisplayName("isViewing сверяет инвентарь с открытым окном игрока")
    void checksViewingState() {

        Player player = mock(Player.class);

        when(player.isOnline()).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);

        assertTrue(ViewSlotUtil.isViewing(player, top));
        assertFalse(ViewSlotUtil.isViewing(player, TestInventories.inventory(9)));
        assertFalse(ViewSlotUtil.isViewing(null, top));
        assertFalse(ViewSlotUtil.isViewing(player, null));

    }

}
