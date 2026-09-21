package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.TitleMode;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка заголовка GUI: плейсхолдер имени и цвета.
 */
class ShulkerTitleServiceTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final ShulkerTitleService titleService = new ShulkerTitleService(configManager);

    private ItemStack namedShulker(String displayName) {

        ItemStack shulker = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(displayName != null);
        when(meta.getDisplayName()).thenReturn(displayName);

        return shulker;

    }

    @Test
    @DisplayName("Плейсхолдер имени подставляется с цветами")
    void substitutesNamePlaceholder() {

        // ConfigManager кэширует шаблон уже с примененными цветами
        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("§x§f§f§0§0§f§f{shulker_name}");

        assertEquals("§x§f§f§0§0§f§f§cMy Box", titleService.resolve(namedShulker("§cMy Box")));

    }

    @Test
    @DisplayName("ORIGINAL возвращает имя самого шалкера")
    void originalModeUsesShulkerOwnName() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.ORIGINAL);
        when(configManager.getTitleFormat()).thenReturn("ignored {shulker_name}");

        assertEquals("§cMy Box", titleService.resolve(namedShulker("§cMy Box")));
        assertEquals("White Shulker Box", titleService.resolve(namedShulker(null)));

    }

    @Test
    @DisplayName("Без имени предмета используется имя материала")
    void fallsBackToMaterialName() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");

        assertEquals("White Shulker Box", titleService.resolve(namedShulker(null)));

    }

    @Test
    @DisplayName("Пустой формат в CUSTOM возвращает имя предмета")
    void blankFormatReturnsName() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("  ");

        assertEquals("§cMy Box", titleService.resolve(namedShulker("§cMy Box")));

    }
}
