package eu.neydev.neyshulker.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Проверка арифметики слотов InventoryView: верх и низ.
 */
class ViewSlotUtilTest {

    private final Inventory top = TestInventories.inventory(27);
    private final Inventory bottom = TestInventories.playerInventory();
    private final InventoryView view = TestInventories.view(top, bottom);

    @Test
    @DisplayName("Слоты верха считаются до размера верхнего инвентаря")
    void resolvesTopSlots() {

        assertEquals(0, ViewSlotUtil.topSlot(view, 0));
        assertEquals(26, ViewSlotUtil.topSlot(view, 26));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.topSlot(view, 27));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.topSlot(view, -1));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.topSlot(view, 200));

    }

    @Test
    @DisplayName("Слоты низа смещены на размер верха")
    void resolvesBottomSlots() {

        assertEquals(0, ViewSlotUtil.bottomSlot(view, 27));
        assertEquals(14, ViewSlotUtil.bottomSlot(view, 41));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.bottomSlot(view, 26));
        assertEquals(ViewSlotUtil.OUTSIDE, ViewSlotUtil.bottomSlot(view, 27 + 41));

    }

}
