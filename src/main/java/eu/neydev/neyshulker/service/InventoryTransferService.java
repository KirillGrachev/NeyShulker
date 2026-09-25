package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.util.ItemStackTransaction;
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
     * @param player      игрок, которому нужно обновить клиент (null - resync
     *                    не выполняется; пакетные операции синхронизируют окно
     *                    сами, один раз на пачку)
     * @param destination целевой инвентарь
     * @param item        вставляемый предмет (не изменяется)
     * @return количество вставленных предметов
     */
    public int insert(@Nullable Player player,
                      @NotNull Inventory destination,
                      @NotNull ItemStack item) {

        int size = destination.getSize();
        ItemStack[] snapshot = new ItemStack[size];
        ItemStack[] originals = new ItemStack[size];

        for (int i = 0; i < size; i++) {
            snapshot[i] = destination.getItem(i);
            originals[i] = snapshot[i];
        }

        int inserted = insertInto(snapshot, item);

        if (inserted <= 0) {
            return 0;
        }

        // Фиксация только измененных слотов: нетронутые ячейки не перезаписываются
        for (int i = 0; i < size; i++) {
            if (snapshot[i] != originals[i]) {
                destination.setItem(i, snapshot[i]);
            }
        }

        resync(player);
        return inserted;

    }

    /**
     * Вставляет предмет в массив слотов (исходит из того, что массив может
     * заменять элементы; сам переданный предмет не изменяется).
     *
     * @param slots массив слотов, изменяется на месте
     * @param item  вставляемый предмет
     * @return количество вставленных предметов
     */
    public int insertInto(ItemStack @NotNull [] slots, @NotNull ItemStack item) {

        // ItemStackTransaction.insert сам работает с клоном предмета,
        // поэтому дополнительная копия на входе не нужна
        ItemStack rest = ItemStackTransaction.insert(slots, item, slots.length);
        return item.getAmount() - (rest == null ? 0 : rest.getAmount());

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
