package eu.neydev.neyshulker.util;

import eu.neydev.neyshulker.model.TransferResult;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Чистая транзакционная логика перемещения предметов.
 * Методы не трогают инвентари Bukkit и не изменяют переданные предметы -
 * они возвращают новые экземпляры, поэтому их можно безопасно отменить.
 * Именно это исключает дюп при быстром перебирании предметов.
 */
public final class ItemStackTransaction {

    private ItemStackTransaction() {
    }

    /**
     * Перемещает предмет из источника в один слот назначения.
     *
     * @param source      исходный стек
     * @param destination стек в слоте назначения (null - слот пуст)
     * @param maxAmount   сколько предметов переместить
     * @return результат транзакции
     */
    public static @NotNull TransferResult move(@Nullable ItemStack source,
                                               @Nullable ItemStack destination,
                                               int maxAmount) {

        if (ShulkerUtil.isEmpty(source) || maxAmount <= 0) {
            return new TransferResult(clone(source), clone(destination), 0);
        }

        int amount = Math.min(maxAmount, source.getAmount());

        ItemStack newSource = clone(source);
        ItemStack newDestination = clone(destination);

        if (ShulkerUtil.isEmpty(newDestination)) {

            newDestination = clone(source);
            newDestination.setAmount(Math.min(amount, newDestination.getMaxStackSize()));

            int moved = newDestination.getAmount();
            newSource.setAmount(source.getAmount() - moved);
            return new TransferResult(normalize(newSource), normalize(newDestination), moved);

        }

        if (!newDestination.isSimilar(source)) {
            return new TransferResult(clone(source), clone(destination), 0);
        }

        int space = newDestination.getMaxStackSize() - newDestination.getAmount();

        if (space <= 0) {
            return new TransferResult(clone(source), clone(destination), 0);
        }

        int moved = Math.min(space, amount);
        newDestination.setAmount(newDestination.getAmount() + moved);

        newSource.setAmount(newSource.getAmount() - moved);
        return new TransferResult(normalize(newSource), normalize(newDestination), moved);

    }

    /**
     * Вставляет предмет в массив слотов: сначала дозаполняет похожие стеки,
     * затем занимает пустые слоты.
     *
     * @param slots   слоты назначения (изменяются внутри метода)
     * @param item    вставляемый предмет
     * @param maxSlots максимальное число слотов для обработки
     * @return остаток, который не поместился (null - поместилось все)
     */
    public static @Nullable ItemStack insert(ItemStack @NotNull [] slots,
                                             @NotNull ItemStack item,
                                             int maxSlots) {

        ItemStack rest = item.clone();
        int limit = Math.min(maxSlots, slots.length);

        // Фаза 1: дозаполняем существующие похожие стеки
        for (int i = 0; i < limit && !ShulkerUtil.isEmpty(rest); i++) {

            ItemStack slot = slots[i];

            if (ShulkerUtil.isEmpty(slot) || !slot.isSimilar(rest)) {
                continue;
            }

            int space = slot.getMaxStackSize() - slot.getAmount();

            if (space <= 0) {
                continue;
            }

            int moved = Math.min(space, rest.getAmount());

            ItemStack updated = slot.clone();
            updated.setAmount(updated.getAmount() + moved);

            slots[i] = updated;
            rest.setAmount(rest.getAmount() - moved);

        }

        // Фаза 2: занимаем пустые слоты
        for (int i = 0; i < limit && !ShulkerUtil.isEmpty(rest); i++) {

            if (!ShulkerUtil.isEmpty(slots[i])) {
                continue;
            }

            ItemStack placed = rest.clone();
            placed.setAmount(Math.min(placed.getAmount(), placed.getMaxStackSize()));

            slots[i] = placed;
            rest.setAmount(rest.getAmount() - placed.getAmount());

        }

        return normalize(rest);

    }

    private static @Nullable ItemStack normalize(@Nullable ItemStack itemStack) {
        return ShulkerUtil.isEmpty(itemStack) ? null : itemStack;
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }

}
