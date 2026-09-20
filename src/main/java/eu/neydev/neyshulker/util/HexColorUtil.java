package eu.neydev.neyshulker.util;

import org.bukkit.ChatColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилита для обработки HEX-цветов в строках.
 * Поддерживаются форматы #RRGGBB и &#RRGGBB - оба приводятся к нативным цветовым кодам.
 */
public final class HexColorUtil {

    /**
     * Первая группа - необязательный символ-префикс (& или §),
     * чтобы &#RRGGBB не оставлял в строке лишнего амперсанда.
     */
    private static final Pattern HEX_PATTERN = Pattern.compile("([&§])?#[a-fA-F0-9]{6}");

    private HexColorUtil() {

    }

    /**
     * Преобразует HEX-коды в строке в Minecraft-формат.
     *
     * @param text исходная строка с HEX-кодами
     * @return строка с преобразованными цветовыми кодами
     */
    public static @NotNull String color(@Nullable String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        Matcher matcher = HEX_PATTERN.matcher(text);

        if (!matcher.find()) {
            return ChatColor.translateAlternateColorCodes('&', text);
        }

        StringBuilder result = new StringBuilder();
        int lastEnd = 0;

        do {

            result.append(text, lastEnd, matcher.start());
            result.append(toLegacyHex(matcher.group()));

            lastEnd = matcher.end();

        } while (matcher.find());

        result.append(text.substring(lastEnd));

        return ChatColor.translateAlternateColorCodes('&', result.toString());

    }

    /**
     * Убирает все цветовые и форматирующие коды из строки.
     *
     * @param text исходная строка
     * @return текст без форматирования
     */
    public static @NotNull String strip(@Nullable String text) {

        if (text == null || text.isEmpty()) {
            return "";
        }

        String stripped = ChatColor.stripColor(color(text));

        return stripped == null ? "" : stripped;

    }

    /**
     * Преобразует #RRGGBB (с префиксом или без) в &x&R&R&G&G&B&B.
     *
     * @param match найденное совпадение вместе с необязательным префиксом
     * @return строка в формате legacy
     */
    private static @NotNull String toLegacyHex(@NotNull String match) {

        int hashIndex = match.indexOf('#');
        StringBuilder replacement = new StringBuilder("&x");

        for (int i = hashIndex + 1; i < match.length(); i++) {
            replacement.append('&').append(match.charAt(i));
        }

        return replacement.toString();

    }
}
