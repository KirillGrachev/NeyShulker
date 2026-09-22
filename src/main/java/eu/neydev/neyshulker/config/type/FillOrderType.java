package eu.neydev.neyshulker.config.type;

/**
 * Стратегия выбора бокса на нижнем ярусе подбора цели автосбора:
 * когда дропу подходит любой бокс со свободным слотом.
 */
public enum FillOrderType {

    /**
     * Побеждает бокс с наибольшим числом свободных слотов:
     * нагрузка равномерно размазывается по всем боксам.
     */
    BALANCED,

    /**
     * Побеждает бокс с наименьшим числом свободных слотов:
     * боксы заполняются по очереди, а не все сразу.
     */
    COMPACT,

    /**
     * Побеждает бокс с наименьшим номером слота:
     * порядок инвентаря слева направо, без учета заполненности.
     */
    INVENTORY;

    /**
     * Безопасно разбирает строку из конфигурации.
     *
     * @param value        строковое значение
     * @param defaultOrder значение по умолчанию
     * @return найденную стратегию или defaultOrder
     */
    public static FillOrderType fromString(String value, FillOrderType defaultOrder) {

        if (value == null || value.isBlank()) {
            return defaultOrder;
        }

        for (FillOrderType order : values()) {

            if (order.name().equalsIgnoreCase(value.trim())) {
                return order;
            }

        }

        return defaultOrder;

    }
}
