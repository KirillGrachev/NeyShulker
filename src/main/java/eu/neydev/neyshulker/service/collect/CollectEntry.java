package eu.neydev.neyshulker.service.collect;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;

/**
 * Элемент листа ожидания автосбора.
 *
 * Хранит легковесную ссылку на источник: сущность дропа на земле
 * или слот инвентаря. Тяжелая работа (выбор цели, вставка, запись меты)
 * отложена до фазы слива, где она дозируется бюджетом действий на волну.
 *
 * Sealed-иерархия вместо nullable-поля и sentinel-слота: источник
 * однозначно определяется типом, а switch в фазе слива проверяется
 * компилятором на полноту.
 */
public sealed interface CollectEntry permits CollectEntry.Ground, CollectEntry.Slot {

    /**
     * Тип предмета на момент детекции.
     */
    @NotNull Material material();

    /**
     * Источник - дроп на земле.
     *
     * @param item     сущность дропа
     * @param material тип предмета на момент детекции
     */
    record Ground(@NotNull Item item, @NotNull Material material) implements CollectEntry {
    }

    /**
     * Источник - слот области хранения инвентаря игрока.
     *
     * @param slot     слот хранения (0..35)
     * @param material тип предмета в слоте на момент детекции
     */
    record Slot(int slot, @NotNull Material material) implements CollectEntry {
    }

    /**
     * Создает элемент для дропа на земле.
     *
     * @param item сущность дропа
     * @return элемент листа ожидания
     */
    static @NotNull CollectEntry ground(@NotNull Item item) {
        return new Ground(item, item.getItemStack().getType());
    }

    /**
     * Создает элемент для слота инвентаря игрока.
     *
     * @param slot     слот хранения
     * @param material тип предмета в слоте
     * @return элемент листа ожидания
     */
    static @NotNull CollectEntry inventory(int slot, @NotNull Material material) {
        return new Slot(slot, material);
    }

    /**
     * @return true для источника «дроп на земле»
     */
    default boolean isGround() {
        return this instanceof Ground;
    }

}
