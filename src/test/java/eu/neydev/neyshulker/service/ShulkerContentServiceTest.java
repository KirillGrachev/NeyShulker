package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Сервис содержимого: загрузка не-шалкера очищает GUI без падений,
 * снимок копирует только непустые слоты.
 */
class ShulkerContentServiceTest {

    private final ShulkerContentService contentService = new ShulkerContentService();

    @Test
    @DisplayName("loadInto с не-шалкером просто очищает GUI")
    void loadIntoNonShulkerClearsOnly() {

        Inventory gui = TestInventories.inventory(27);
        gui.setItem(0, new FakeItemStack(Material.DIAMOND, 1));

        contentService.loadInto(new FakeItemStack(Material.STONE, 1), gui);

        assertNull(gui.getItem(0), "Старое содержимое стерто");

        contentService.loadInto(null, gui);
        assertNull(gui.getItem(0));

    }

    @Test
    @DisplayName("snapshot копирует только непустые слоты")
    void snapshotCopiesNonEmptySlots() {

        Inventory gui = TestInventories.inventory(27);
        gui.setItem(3, new FakeItemStack(Material.DIAMOND, 5));

        ItemStack[] snapshot = contentService.snapshot(gui);

        assertEquals(27, snapshot.length);
        assertNotNull(snapshot[3]);
        assertEquals(5, snapshot[3].getAmount());
        assertNull(snapshot[0]);

    }

}
