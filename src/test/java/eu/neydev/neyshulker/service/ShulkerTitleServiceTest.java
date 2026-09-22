package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.TitleMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка заголовка GUI: плейсхолдеры, цвета и имена по языку клиента.
 */
class ShulkerTitleServiceTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final ShulkerTitleService titleService = new ShulkerTitleService(configManager);
    private final Player viewer = mock(Player.class);

    private ItemStack namedShulker(String displayName) {

        ItemStack shulker = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.getItemMeta()).thenReturn(meta);
        when(meta.hasDisplayName()).thenReturn(displayName != null);
        when(meta.getDisplayName()).thenReturn(displayName);

        return shulker;

    }

    private void names(String defaultName, String russianName) {
        when(configManager.getTitleNames()).thenReturn(Map.of(
                "default", defaultName,
                "ru_ru", russianName));
    }

    @Test
    @DisplayName("Плейсхолдер имени подставляется с цветами")
    void substitutesNamePlaceholder() {

        // ConfigManager кэширует шаблон уже с примененными цветами
        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("§x§f§f§0§0§f§f{shulker_name}");

        assertEquals("§x§f§f§0§0§f§f§cMy Box", titleService.resolve(viewer, namedShulker("§cMy Box")));

    }

    @Test
    @DisplayName("Безымянный бокс берет имя по языку клиента")
    void unnamedBoxUsesClientLocale() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");
        names("Shulker Box", "Шалкеровый ящик");

        when(viewer.getLocale()).thenReturn("ru_ru");
        assertEquals("Шалкеровый ящик", titleService.resolve(viewer, namedShulker(null)));

        when(viewer.getLocale()).thenReturn("en_us");
        assertEquals("Shulker Box", titleService.resolve(viewer, namedShulker(null)));

    }

    @Test
    @DisplayName("Неизвестный язык падает в default, затем в имя материала")
    void unknownLocaleFallsBackToDefaultThenMaterial() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name}");

        when(viewer.getLocale()).thenReturn("kk_kz");
        names("Shulker Box", "Шалкеровый ящик");
        assertEquals("Shulker Box", titleService.resolve(viewer, namedShulker(null)));

        when(configManager.getTitleNames()).thenReturn(Map.of());
        assertEquals("White Shulker Box", titleService.resolve(viewer, namedShulker(null)));

    }

    @Test
    @DisplayName("ORIGINAL возвращает имя самого шалкера")
    void originalModeUsesShulkerOwnName() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.ORIGINAL);
        when(configManager.getTitleFormat()).thenReturn("ignored {shulker_name}");
        names("Shulker Box", "Шалкеровый ящик");

        when(viewer.getLocale()).thenReturn("ru_ru");
        assertEquals("§cMy Box", titleService.resolve(viewer, namedShulker("§cMy Box")));
        assertEquals("Шалкеровый ящик", titleService.resolve(viewer, namedShulker(null)));

    }

    @Test
    @DisplayName("Плейсхолдер материала дает английское имя материала")
    void materialPlaceholderStaysEnglish() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("{shulker_name} | {shulker_material}");
        names("Shulker Box", "Шалкеровый ящик");

        when(viewer.getLocale()).thenReturn("ru_ru");

        assertEquals("Шалкеровый ящик | White Shulker Box",
                titleService.resolve(viewer, namedShulker(null)));

    }

    @Test
    @DisplayName("Пустой формат в CUSTOM возвращает имя предмета")
    void blankFormatReturnsName() {

        when(configManager.getTitleMode()).thenReturn(TitleMode.CUSTOM);
        when(configManager.getTitleFormat()).thenReturn("  ");

        assertEquals("§cMy Box", titleService.resolve(viewer, namedShulker("§cMy Box")));

    }
}
