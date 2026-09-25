package eu.neydev.neyshulker.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

/**
 * Утилита для безопасной работы со слотами InventoryView.
 * Позволяет однозначно отличать верхний инвентарь от нижнего
 * и получать локальный индекс слота без магии чисел.
 */
public final class ViewSlotUtil {

    /** Индекс вне инвентарей (клик мимо, крафт, броня). */
    public static final int OUTSIDE = -1;

    private ViewSlotUtil() {
    }

    /**
     * Возвращает локальный индекс слота в верхнем инвентаре.
     *
     * @param view    представление инвентаря
     * @param rawSlot сырой индекс слота из события
     * @return индекс слота или OUTSIDE
     */
    public static int topSlot(@NotNull InventoryView view, int rawSlot) {

        if (rawSlot < 0) {
            return OUTSIDE;
        }

        int topSize = sizeOf(view.getTopInventory());
        return rawSlot < topSize ? rawSlot : OUTSIDE;

    }

    /**
     * Возвращает локальный индекс слота в нижнем инвентаре игрока.
     *
     * @param view    представление инвентаря
     * @param rawSlot сырой индекс слота из события
     * @return индекс слота или OUTSIDE
     */
    public static int bottomSlot(@NotNull InventoryView view, int rawSlot) {

        int topSize = sizeOf(view.getTopInventory());

        if (rawSlot < topSize) {
            return OUTSIDE;
        }

        int bottomSlot = rawSlot - topSize;
        int bottomSize = sizeOf(view.getBottomInventory());
        return bottomSlot < bottomSize ? bottomSlot : OUTSIDE;

    }

    private static int sizeOf(@NotNull Inventory inventory) {
        return inventory.getSize();
    }

}
