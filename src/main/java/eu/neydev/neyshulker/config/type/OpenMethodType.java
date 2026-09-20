package eu.neydev.neyshulker.config.type;

/**
 * Способ открытия шалкер-бокса правой кнопкой мыши.
 */
public enum OpenMethodType {

    /**
     * Только при зажатом Shift (Shift + ПКМ).
     * Крадущийся игрок не ставит блок, поэтому обычная установка шалкера остается рабочей.
     */
    SHIFT,

    /**
     * Только без Shift (обычный ПКМ, блок не ставится).
     */
    NO_SHIFT,

    /**
     * При любом ПКМ - поставить шалкер рукой невозможно.
     */
    ALWAYS,

    /**
     * Умный режим: с Shift - всегда, без Shift - только в воздух и на невзаимодействующие блоки.
     */
    SMART;

    /**
     * Безопасно разбирает строку из конфигурации.
     *
     * @param value строковое значение
     * @return найденный тип или defaultType
     */
    public static OpenMethodType fromString(String value, OpenMethodType defaultType) {

        if (value == null || value.isBlank()) {
            return defaultType;
        }

        for (OpenMethodType method : values()) {

            if (method.name().equalsIgnoreCase(value.trim())) {
                return method;
            }

        }

        return defaultType;

    }
}
