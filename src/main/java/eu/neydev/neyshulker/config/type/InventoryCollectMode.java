package eu.neydev.neyshulker.config.type;

/**
 * Режим досортировки предметов из инвентаря игрока в шалкер-боксы.
 * Наземный сбор (дроп в радиусе) работает независимо от этого режима.
 */
public enum InventoryCollectMode {

    /**
     * Инвентарь не трогается: сбор только с земли.
     */
    OFF,

    /**
     * Из инвентаря перемещаются только те типы предметов, которые уже
     * лежат в каком-то боксе: безопасная досортировка без сюрпризов.
     */
    MATCHING,

    /**
     * Из инвентаря перемещается любой допустимый предмет, для которого
     * нашлось место: инвентарь работает как транзитный буфер.
     */
    ALL;

    /**
     * Безопасно разбирает строку из конфигурации.
     *
     * @param value       строковое значение
     * @param defaultMode значение по умолчанию
     * @return найденный режим или defaultMode
     */
    public static InventoryCollectMode fromString(String value, InventoryCollectMode defaultMode) {

        if (value == null || value.isBlank()) {
            return defaultMode;
        }

        for (InventoryCollectMode mode : values()) {

            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }

        }

        return defaultMode;

    }
}
