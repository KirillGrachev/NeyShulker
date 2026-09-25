package eu.neydev.neyshulker.config.type;

/**
 * Режим заголовка GUI шалкер-бокса.
 */
public enum TitleMode {

    /**
     * Оригинальное имя самого шалкера: display name предмета,
     * а при его отсутствии - читаемое имя материала.
     */
    ORIGINAL,

    /**
     * Кастомный шаблон из конфигурации с плейсхолдером {shulker_name}.
     */
    CUSTOM;

    /**
     * Безопасно разбирает строку из конфигурации.
     *
     * @param value       строковое значение
     * @param defaultMode значение по умолчанию
     * @return найденный режим или defaultType
     */
    public static TitleMode fromString(String value, TitleMode defaultMode) {

        if (value == null || value.isBlank()) {
            return defaultMode;
        }

        for (TitleMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }

        return defaultMode;

    }

}
