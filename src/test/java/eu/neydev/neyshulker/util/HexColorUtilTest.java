package eu.neydev.neyshulker.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Проверка обработки цветов и форматирования имен.
 */
class HexColorUtilTest {

    @Test
    @DisplayName("HEX-код преобразуется в legacy-формат")
    void convertsHexToLegacy() {

        String result = HexColorUtil.color("#ff0000Text");

        assertTrue(result.contains("§x"), "Ожидался HEX-маркер");
        assertTrue(result.endsWith("Text"));

    }

    @Test
    @DisplayName("Обычные цветовые коды тоже обрабатываются")
    void convertsLegacyCodes() {

        assertEquals("§cText", HexColorUtil.color("&cText"));

    }

    @Test
    @DisplayName("Формат &#RRGGBB не оставляет лишнего амперсанда")
    void convertsAmpersandHexWithoutResidue() {

        String result = HexColorUtil.color("&#ff0000Text");

        assertFalse(result.contains("&"), "В строке остался амперсанд");
        assertTrue(result.startsWith("§x"));
        assertTrue(result.endsWith("Text"));

    }

    @Test
    @DisplayName("Смешанный HEX и legacy работают в одной строке")
    void convertsMixed() {

        String result = HexColorUtil.color("&#00ff00Green &cRed");

        assertTrue(result.contains("§x"));
        assertTrue(result.contains("§c"));

    }

    @Test
    @DisplayName("Неполный HEX-код не ломает строку")
    void ignoresInvalidHex() {

        assertEquals("§f#gggggg", HexColorUtil.color("&f#gggggg"));

    }

    @Test
    @DisplayName("null и пустая строка безопасны")
    void handlesNullAndEmpty() {

        assertEquals("", HexColorUtil.color(null));
        assertEquals("", HexColorUtil.color(""));
        assertEquals("", HexColorUtil.strip(null));

    }

    @Test
    @DisplayName("strip убирает все коды")
    void stripsColors() {

        assertEquals("Text", HexColorUtil.strip("&#ff0000Text"));
        assertFalse(HexColorUtil.strip("&cText").contains("§"));

    }
}
