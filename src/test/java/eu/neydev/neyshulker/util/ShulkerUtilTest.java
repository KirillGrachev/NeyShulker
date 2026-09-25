package eu.neydev.neyshulker.util;

import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import org.bukkit.inventory.Inventory;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка определения шалкер-боксов и форматирования имен материалов.
 */
class ShulkerUtilTest {

    @Test
    @DisplayName("Все 17 шалкер-боксов распознаются")
    void detectsAllShulkerBoxes() {

        assertTrue(ShulkerUtil.isShulkerBox(Material.SHULKER_BOX));
        assertTrue(ShulkerUtil.isShulkerBox(Material.WHITE_SHULKER_BOX));
        assertTrue(ShulkerUtil.isShulkerBox(Material.BLACK_SHULKER_BOX));
        assertTrue(ShulkerUtil.isShulkerBox(Material.LIGHT_BLUE_SHULKER_BOX));

    }

    @Test
    @DisplayName("Обычные блоки не считаются шалкер-боксами")
    void ignoresOtherMaterials() {

        assertFalse(ShulkerUtil.isShulkerBox(Material.CHEST));
        assertFalse(ShulkerUtil.isShulkerBox(Material.ENDER_CHEST));
        assertFalse(ShulkerUtil.isShulkerBox((Material) null));
        assertFalse(ShulkerUtil.isShulkerBox((org.bukkit.inventory.ItemStack) null));

    }

    @Test
    @DisplayName("Пустой стек определяется без обращения к реестрам Bukkit")
    void detectsEmptyStack() {

        assertTrue(ShulkerUtil.isEmpty(null));
        assertTrue(ShulkerUtil.isEmpty(new org.bukkit.inventory.ItemStack(Material.AIR)));

    }

    @Test
    @DisplayName("Имя материала приводится к читаемому виду")
    void prettifiesMaterialName() {

        assertEquals("White Shulker Box", ShulkerUtil.prettifyMaterial(Material.WHITE_SHULKER_BOX));
        assertEquals("Diamond", ShulkerUtil.prettifyMaterial(Material.DIAMOND));
        assertEquals("Netherite Ingot", ShulkerUtil.prettifyMaterial(Material.NETHERITE_INGOT));

    }

    @Test
    @DisplayName("Размер шалкер-бокса фиксирован")
    void shulkerSizeIsConstant() {
        assertEquals(27, ShulkerUtil.SHULKER_SIZE);
    }


    @Test
    @DisplayName("isEmpty: нулевое количество и служебные виды воздуха")
    void isEmptyEdgeCases() {

        assertTrue(ShulkerUtil.isEmpty(null));
        assertTrue(ShulkerUtil.isEmpty(new FakeItemStack(Material.DIAMOND, 0)));
        assertTrue(ShulkerUtil.isEmpty(new FakeItemStack(Material.CAVE_AIR, 1)));
        assertTrue(ShulkerUtil.isEmpty(new FakeItemStack(Material.VOID_AIR, 1)));
        assertFalse(ShulkerUtil.isEmpty(new FakeItemStack(Material.DIAMOND, 1)));

    }

    @Test
    @DisplayName("readContents/writeContents на не-шалкерах и без меты безопасны")
    void contentsIoIsSafeOnWrongItems() {

        assertNull(ShulkerUtil.readContents(new FakeItemStack(Material.STONE, 1)));
        assertNull(ShulkerUtil.readContents(null));
        assertNull(ShulkerUtil.writeContents(new FakeItemStack(Material.STONE, 1), new ItemStack[27]));
        // FakeItemStack без меты: запись содержимого невозможна
        assertNull(ShulkerUtil.writeContents(
                new FakeItemStack(Material.WHITE_SHULKER_BOX, 1), new ItemStack[27]));

    }

    @Test
    @DisplayName("Счетчики по массиву и живому инвентарю")
    void countersOverArrayAndInventory() {

        assertEquals(0, ShulkerUtil.countFreeSlots((ItemStack[]) null));
        assertEquals(0, ShulkerUtil.countItems((ItemStack[]) null));

        ItemStack[] contents = new ItemStack[27];
        contents[0] = new FakeItemStack(Material.DIAMOND, 5);
        contents[1] = new FakeItemStack(Material.STONE, 64);

        assertEquals(25, ShulkerUtil.countFreeSlots(contents));
        assertEquals(69, ShulkerUtil.countItems(contents));

        Inventory inventory = TestInventories.inventory(27);

        assertEquals(27, ShulkerUtil.countFreeSlots(inventory));
        assertEquals(0, ShulkerUtil.countItems(inventory));

        inventory.setItem(2, new FakeItemStack(Material.DIAMOND, 7));

        assertEquals(26, ShulkerUtil.countFreeSlots(inventory));
        assertEquals(7, ShulkerUtil.countItems(inventory));
        assertEquals(0, ShulkerUtil.countFreeSlots((Inventory) null));
        assertEquals(0, ShulkerUtil.countItems((Inventory) null));

    }

    @Test
    @DisplayName("getShulkerName: display name важнее имени материала")
    void shulkerNameResolution() {

        assertEquals("White Shulker Box",
                ShulkerUtil.getShulkerName(new FakeItemStack(Material.WHITE_SHULKER_BOX, 1)));

        ItemStack named = mock(ItemStack.class);
        org.bukkit.inventory.meta.ItemMeta meta = mock(org.bukkit.inventory.meta.ItemMeta.class);

        when(named.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(true);
        when(meta.getDisplayName()).thenReturn("§dLoot Box");

        assertEquals("§dLoot Box", ShulkerUtil.getShulkerName(named));

    }

    @Test
    @DisplayName("prettifyMaterial не зависит от дефолтной локали JVM")
    void prettifyMaterialIsLocaleSafe() {

        java.util.Locale previous = java.util.Locale.getDefault();

        try {

            java.util.Locale.setDefault(new java.util.Locale("tr", "TR"));
            assertEquals("White Shulker Box",
                    ShulkerUtil.prettifyMaterial(Material.WHITE_SHULKER_BOX));

        } finally {
            java.util.Locale.setDefault(previous);
        }

    }

}
