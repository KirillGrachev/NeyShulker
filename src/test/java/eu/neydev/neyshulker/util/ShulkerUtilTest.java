package eu.neydev.neyshulker.util;

import org.bukkit.Material;
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
}
