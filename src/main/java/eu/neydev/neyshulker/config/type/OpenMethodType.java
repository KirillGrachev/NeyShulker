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
     * Умный режим: с Shift - везде, без Shift - только по воздуху.
     * Клик по блоку без Shift всегда ванильный: шалкер можно ставить куда угодно.
     */
    SMART,

    /**
     * ПКМ по воздуху открывает GUI, любой клик по блоку остается ванильным.
     * По воздуху нет ванильного взаимодействия, поэтому открытие ничего не крадет,
     * а установка шалкера на блок работает всегда и без модификаторов.
     */
    AIR;

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
