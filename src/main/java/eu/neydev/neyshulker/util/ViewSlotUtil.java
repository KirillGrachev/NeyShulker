package eu.neydev.neyshulker.util;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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
     * Размер области хранения инвентаря: для игрока это 36 слотов,
     * для остальных типов берется полный размер.
     */
    private static int storageSize(@NotNull Inventory inventory) {

        if (inventory instanceof org.bukkit.inventory.PlayerInventory playerInventory) {
            return playerInventory.getStorageContents().length;
        }

        return inventory.getSize();

    }

    /**
     * Возвращает локальный индекс слота в верхнем инвентаре.
     *
     * @param view   представление инвентаря
     * @param rawSlot сырой индекс слота из события
     * @return индекс слота или OUTSIDE
     */
    public static int topSlot(@NotNull InventoryView view, int rawSlot) {

        if (rawSlot < 0) {
            return OUTSIDE;
        }

        int topSize = view.getTopInventory().getSize();

        return rawSlot < topSize ? rawSlot : OUTSIDE;

    }

    /**
     * Возвращает локальный индекс слота в нижнем инвентаре игрока.
     *
     * @param view   представление инвентаря
     * @param rawSlot сырой индекс слота из события
     * @return индекс слота или OUTSIDE
     */
    public static int bottomSlot(@NotNull InventoryView view, int rawSlot) {

        int topSize = view.getTopInventory().getSize();

        if (rawSlot < topSize) {
            return OUTSIDE;
        }

        int bottomSlot = rawSlot - topSize;
        int bottomSize = view.getBottomInventory().getSize();

        return bottomSlot < bottomSize ? bottomSlot : OUTSIDE;

    }

    /**
     * Возвращает локальный индекс слота хранения (0..35) в нижнем инвентаре.
     * Служебные слоты (броня, вторая рука, крафт, результат) считаются внешними:
     * их перемещение нужно оставлять ванильной логике.
     *
     * @param view    представление инвентаря
     * @param rawSlot сырой индекс слота из события
     * @return индекс слота хранения или OUTSIDE
     */
    public static int bottomStorageSlot(@NotNull InventoryView view, int rawSlot) {

        int bottomSlot = bottomSlot(view, rawSlot);

        if (bottomSlot == OUTSIDE) {
            return OUTSIDE;
        }

        int storageSize = storageSize(view.getBottomInventory());

        return bottomSlot < storageSize ? bottomSlot : OUTSIDE;

    }

    /**
     * Определяет, относится ли сырой слот к нижнему инвентарю.
     *
     * @param view   представление инвентаря
     * @param rawSlot сырой индекс слота
     * @return true если слот принадлежит инвентарю игрока
     */
    public static boolean isBottom(@NotNull InventoryView view, int rawSlot) {
        return bottomSlot(view, rawSlot) != OUTSIDE;
    }

    /**
     * Возвращает инвентарь, которому принадлежит сырой слот.
     *
     * @param view   представление инвентаря
     * @param rawSlot сырой индекс слота
     * @return найденный инвентарь или null
     */
    public static @Nullable Inventory resolve(@NotNull InventoryView view, int rawSlot) {

        if (topSlot(view, rawSlot) != OUTSIDE) {
            return view.getTopInventory();
        }

        if (bottomSlot(view, rawSlot) != OUTSIDE) {
            return view.getBottomInventory();
        }

        return null;

    }

    /**
     * Проверяет, что игрок действительно видит указанный инвентарь сверху.
     * Защита от гонок, когда инвентарь уже закрыт или заменен.
     *
     * @param player    игрок
     * @param inventory ожидаемый верхний инвентарь
     * @return true если инвентарь открыт у игрока
     */
    public static boolean isViewing(@Nullable Player player, @Nullable Inventory inventory) {

        if (player == null || inventory == null || !player.isOnline()) {
            return false;
        }

        return inventory.equals(player.getOpenInventory().getTopInventory());

    }

    /**
     * Достает представление инвентаря из события клика.
     *
     * @param event событие клика
     * @return представление инвентаря
     */
    public static @NotNull InventoryView getView(@NotNull InventoryClickEvent event) {
        return event.getView();
    }
}
