package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.util.ItemStackTransaction;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис перемещения предметов между инвентарями и массивами слотов.
 * Работает строго синхронно и по схеме "снимок -> транзакция -> фиксация":
 * исходные стеки не мутируются, поэтому отмена или прерывание не приводят к дюпу.
 */
public class InventoryTransferService {

    /**
     * Вставляет предмет в инвентарь.
     *
     * @param player      игрок, которому нужно обновить клиент
     * @param destination целевой инвентарь
     * @param item        вставляемый предмет
     * @return количество вставленных предметов
     */
    public int insert(@Nullable Player player,
                      @NotNull Inventory destination,
                      @NotNull ItemStack item) {

        ItemStack[] snapshot = snapshotOf(destination.getSize(), destination::getItem);
        int inserted = insertInto(snapshot, item);

        if (inserted <= 0) {
            return 0;
        }

        // Фиксация снимка обратно в инвентарь одним проходом
        for (int i = 0; i < snapshot.length; i++) {
            destination.setItem(i, snapshot[i]);
        }

        resync(player);

        return inserted;

    }

    /**
     * Вставляет предмет в массив слотов (используется для шалкер-боксов,
     * которые лежат в инвентаре и не имеют собственного Inventory).
     *
     * @param slots массив слотов, изменяется на месте
     * @param item  вставляемый предмет
     * @return количество вставленных предметов
     */
    public int insertInto(ItemStack @NotNull [] slots, @NotNull ItemStack item) {

        ItemStack rest = ItemStackTransaction.insert(slots, item.clone(), slots.length);

        return item.getAmount() - (rest == null ? 0 : rest.getAmount());

    }

    private ItemStack @NotNull [] snapshotOf(int size, @NotNull java.util.function.IntFunction<ItemStack> getter) {

        ItemStack[] snapshot = new ItemStack[size];

        for (int i = 0; i < size; i++) {
            snapshot[i] = getter.apply(i);
        }

        return snapshot;

    }

    /**
     * Синхронизирует клиент игрока с серверным состоянием инвентаря.
     * Вызывается сразу после фиксации, чтобы клиент не успел отправить
     * повторный клик по устаревшим данным.
     */
    private void resync(@Nullable Player player) {

        if (player == null || !player.isOnline()) {
            return;
        }

        player.updateInventory();

    }
}
