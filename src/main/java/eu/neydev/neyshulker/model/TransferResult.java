package eu.neydev.neyshulker.model;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Результат перемещения предметов между двумя слотами.
 * Исходные предметы не изменяются - результат содержит новые экземпляры.
 *
 * @param source      предмет в источнике после перемещения (null - слот пуст)
 * @param destination предмет в назначении после перемещения (null - слот пуст)
 * @param transferred количество перемещенных предметов
 */
public record TransferResult(
        @Nullable ItemStack source,
        @Nullable ItemStack destination,
        int transferred
) {

    /**
     * Пустой результат: ничего не перемещено, слоты не изменились.
     */
    public static @Nullable TransferResult nothing(@Nullable ItemStack source,
                                                   @Nullable ItemStack destination) {
        return new TransferResult(clone(source), clone(destination), 0);
    }

    public boolean isSuccess() {
        return transferred > 0;
    }

    public boolean isSourceEmptied() {
        return source == null;
    }

    private static @Nullable ItemStack clone(@Nullable ItemStack itemStack) {
        return itemStack == null ? null : itemStack.clone();
    }
}
