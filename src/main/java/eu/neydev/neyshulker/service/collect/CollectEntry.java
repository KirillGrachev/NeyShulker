package eu.neydev.neyshulker.service.collect;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Элемент листа ожидания автосбора.
 *
 * Хранит легковесную ссылку на источник: сущность дропа на земле
 * или слот инвентаря. Тяжелая работа (выбор цели, вставка, запись меты)
 * отложена до фазы слива, где она дозируется бюджетом действий на волну.
 *
 * @param item     сущность дропа (null для источника из инвентаря)
 * @param slot     слот инвентаря (-1 для источника с земли)
 * @param material тип предмета на момент детекции
 */
public record CollectEntry(@Nullable Item item, int slot, @NotNull Material material) {

    /**
     * Источник - дроп на земле.
     *
     * @param item сущность дропа
     * @return элемент листа ожидания
     */
    public static @NotNull CollectEntry ground(@NotNull Item item) {
        return new CollectEntry(item, -1, item.getItemStack().getType());
    }

    /**
     * Источник - слот инвентаря игрока.
     *
     * @param slot     слот хранения
     * @param material тип предмета в слоте
     * @return элемент листа ожидания
     */
    public static @NotNull CollectEntry inventory(int slot, @NotNull Material material) {
        return new CollectEntry(null, slot, material);
    }

    public boolean isGround() {
        return item != null;
    }
}
