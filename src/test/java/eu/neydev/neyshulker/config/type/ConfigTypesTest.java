package eu.neydev.neyshulker.config.type;

import org.bukkit.Sound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Конфиг-типы: безопасный парсинг строк во всех enum-ветках,
 * SoundSettings с колбэком на неизвестное имя, контракты причин отказа.
 */
class ConfigTypesTest {

    @Test
    @DisplayName("CollectMode.fromString: регистр, пробелы, мусор, null")
    void collectModeParsing() {

        assertSame(CollectMode.MATCHING, CollectMode.fromString("matching", null));
        assertSame(CollectMode.ALL, CollectMode.fromString("  ALL ", null));
        assertSame(CollectMode.ALL, CollectMode.fromString("nonsense", CollectMode.ALL));
        assertNull(CollectMode.fromString(null, null));
        assertNull(CollectMode.fromString("  ", null));

    }

    @Test
    @DisplayName("FillOrderType.fromString покрывает все стратегии")
    void fillOrderParsing() {

        assertSame(FillOrderType.BALANCED, FillOrderType.fromString("balanced", null));
        assertSame(FillOrderType.COMPACT, FillOrderType.fromString("Compact", null));
        assertSame(FillOrderType.INVENTORY, FillOrderType.fromString("inventory", null));
        assertSame(FillOrderType.BALANCED, FillOrderType.fromString("?", FillOrderType.BALANCED));
        assertNull(FillOrderType.fromString(null, null));

    }

    @Test
    @DisplayName("OpenMethodType.fromString покрывает все способы")
    void openMethodParsing() {

        assertSame(OpenMethodType.SHIFT, OpenMethodType.fromString("shift", null));
        assertSame(OpenMethodType.NO_SHIFT, OpenMethodType.fromString("NO_SHIFT", null));
        assertSame(OpenMethodType.ALWAYS, OpenMethodType.fromString("always", null));
        assertSame(OpenMethodType.SMART, OpenMethodType.fromString("Smart", null));
        assertSame(OpenMethodType.AIR, OpenMethodType.fromString("air", null));
        assertSame(OpenMethodType.AIR, OpenMethodType.fromString("TELEPORT", OpenMethodType.AIR));

    }

    @Test
    @DisplayName("TitleMode.fromString: обе ветки и мусор")
    void titleModeParsing() {

        assertSame(TitleMode.ORIGINAL, TitleMode.fromString("original", null));
        assertSame(TitleMode.CUSTOM, TitleMode.fromString("CUSTOM", null));
        assertSame(TitleMode.CUSTOM, TitleMode.fromString("RAINBOW", TitleMode.CUSTOM));
        assertNull(TitleMode.fromString("", null));

    }

    @Test
    @DisplayName("SoundSettings.of: валидное имя, неизвестное имя, пустое имя")
    void soundSettingsResolution() {

        SoundSettings valid = SoundSettings.of("BLOCK_NOTE_BLOCK_PLING",
                Sound.BLOCK_SHULKER_BOX_OPEN, true, 1.0f, 1.0f, () -> {
                    throw new AssertionError("callback on valid name");
                });
        assertSame(Sound.BLOCK_NOTE_BLOCK_PLING, valid.sound());

        boolean[] called = {false};
        SoundSettings broken = SoundSettings.of("NOT_A_SOUND",
                Sound.BLOCK_SHULKER_BOX_OPEN, true, 0.5f, 1.2f, () -> called[0] = true);

        assertSame(Sound.BLOCK_SHULKER_BOX_OPEN, broken.sound());
        assertTrue(called[0], "Неизвестное имя вызывает предупреждение");
        assertEquals(0.5f, broken.volume());
        assertEquals(1.2f, broken.pitch());

        SoundSettings blank = SoundSettings.of("  ",
                Sound.ENTITY_ITEM_PICKUP, false, 1.0f, 1.0f, () -> {
                    throw new AssertionError("blank name is not unknown");
                });
        assertSame(Sound.ENTITY_ITEM_PICKUP, blank.sound());
        assertFalse(blank.enabled());

    }

    @Test
    @DisplayName("ValidationReason: озвученные и тихие причины")
    void validationReasonMessages() {

        assertNull(ValidationReason.NONE.getMessageKey());
        assertNull(ValidationReason.PLUGIN_DISABLED.getMessageKey());
        assertNull(ValidationReason.NOT_SHULKER.getMessageKey());
        assertNull(ValidationReason.METHOD_MISMATCH.getMessageKey());
        assertSame(MessageKey.NO_PERMISSION, ValidationReason.NO_PERMISSION.getMessageKey());
        assertSame(MessageKey.BLACKLISTED, ValidationReason.BLACKLISTED.getMessageKey());
        assertSame(MessageKey.NESTED_SHULKER, ValidationReason.NESTED_SHULKER.getMessageKey());
        assertSame(MessageKey.SELF_REMOVE, ValidationReason.OPEN_SHULKER.getMessageKey());

    }

    @Test
    @DisplayName("MessageKey/PermissionNode/SoundKey/ConsoleMessage: ключи конфига стабильны")
    void configKeysAreStable() {

        assertEquals("auto_collect_full", MessageKey.AUTO_COLLECT_FULL.getConfigKey());
        assertFalse(MessageKey.SAVED.isDefaultEnabled(),
                "Служебное saved выключено по умолчанию");
        assertEquals("neyshulker.bypass.blacklist",
                PermissionNode.BYPASS_BLACKLIST.getDefaultPermission());
        assertEquals("collect", SoundKey.COLLECT.getConfigKey());
        assertEquals(Sound.ENTITY_ITEM_PICKUP, SoundKey.COLLECT.getDefaultSound());
        assertTrue(ConsoleMessage.UNKNOWN_GAME_MODE.getDefaultTemplate().contains("game mode"),
                "Консольные шаблоны живут в коде, а не в конфигурации");

    }

    @Test
    @DisplayName("ValidationResult: синглтон allowed и denied-причины")
    void validationResultContract() {

        assertTrue(eu.neydev.neyshulker.model.ValidationResult.allowed().isAllowed());
        assertSame(eu.neydev.neyshulker.model.ValidationResult.allowed(),
                eu.neydev.neyshulker.model.ValidationResult.allowed());

        eu.neydev.neyshulker.model.ValidationResult denied =
                eu.neydev.neyshulker.model.ValidationResult.denied(ValidationReason.BLACKLISTED);

        assertFalse(denied.isAllowed());
        assertSame(MessageKey.BLACKLISTED, denied.getMessageKey());

    }

}
