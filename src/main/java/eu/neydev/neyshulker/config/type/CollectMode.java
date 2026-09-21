package eu.neydev.neyshulker.config.type;

/**
 * Глобальный режим автосбора: один гейт для обоих источников
 * (дроп на земле и досортировка из инвентаря).
 */
public enum CollectMode {

    /**
     * Пылесос: любой допустимый предмет в радиусе и любой допустимый
     * предмет из инвентаря уходит в боксы.
     */
    ALL,

    /**
     * Курируемый сбор: добираются только типы, уже лежащие в каком-то боксе
     * или в открытом GUI. Пустой бокс молчит, пока его не наполнят вручную:
     * режим не решает за владельца, что собирать.
     */
    MATCHING;

    /**
     * Безопасно разбирает строку из конфигурации.
     *
     * @param value       строковое значение
     * @param defaultMode значение по умолчанию
     * @return найденный режим или defaultMode
     */
    public static CollectMode fromString(String value, CollectMode defaultMode) {

        if (value == null || value.isBlank()) {
            return defaultMode;
        }

        for (CollectMode mode : values()) {

            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }

        }

        return defaultMode;

    }
}
