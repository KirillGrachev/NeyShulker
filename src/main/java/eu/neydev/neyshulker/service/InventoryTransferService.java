package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.model.TransferResult;
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
     * Перемещает предмет между двумя слотами разных инвентарей.
     *
     * @param player          игрок, которому нужно обновить клиент
     * @param source          исходный инвентарь
     * @param sourceSlot      слот источника
     * @param destination     целевой инвентарь
     * @param destinationSlot слот назначения
     * @param maxAmount       максимальное число предметов для переноса
     * @return количество перемещенных предметов
     */
    public int moveSlot(@Nullable Player player,
                        @NotNull Inventory source, int sourceSlot,
                        @NotNull Inventory destination, int destinationSlot,
                        int maxAmount) {

        if (source.equals(destination) && sourceSlot == destinationSlot) {
            return 0;
        }

        if (!isValidSlot(source, sourceSlot) || !isValidSlot(destination, destinationSlot)) {
            return 0;
        }

        ItemStack sourceItem = source.getItem(sourceSlot);

        if (ShulkerUtil.isEmpty(sourceItem)) {
            return 0;
        }

        ItemStack destinationItem = destination.getItem(destinationSlot);
        TransferResult result = ItemStackTransaction.move(sourceItem, destinationItem, maxAmount);

        if (!result.isSuccess()) {
            return 0;
        }

        // Фиксация: оба слота записываются одним неделимым шагом
        source.setItem(sourceSlot, result.source());
        destination.setItem(destinationSlot, result.destination());

        resync(player);

        return result.transferred();

    }

    /**
     * Перемещает предмет из слота в первый подходящий слот инвентаря.
     *
     * @param player      игрок, которому нужно обновить клиент
     * @param source      исходный инвентарь
     * @param sourceSlot  слот источника
     * @param destination целевой инвентарь
     * @param maxAmount   максимальное число предметов для переноса
     * @return количество перемещенных предметов
     */
    public int moveFirst(@Nullable Player player,
                         @NotNull Inventory source, int sourceSlot,
                         @NotNull Inventory destination, int maxAmount) {

        if (!isValidSlot(source, sourceSlot)) {
            return 0;
        }

        ItemStack sourceItem = source.getItem(sourceSlot);

        if (ShulkerUtil.isEmpty(sourceItem)) {
            return 0;
        }

        int destinationSlot = findSlot(destination, sourceItem, maxAmount);

        if (destinationSlot < 0) {
            return 0;
        }

        return moveSlot(player, source, sourceSlot, destination, destinationSlot, maxAmount);

    }

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

    /**
     * Ищет слот, способный принять предмет: сначала похожий неполный стек, затем пустой.
     *
     * @param destination целевой инвентарь
     * @param item        предмет для размещения
     * @param maxAmount   требуемое место
     * @return индекс слота или -1
     */
    public int findSlot(@NotNull Inventory destination,
                        @NotNull ItemStack item,
                        int maxAmount) {

        int firstEmpty = -1;
        int required = Math.min(maxAmount, item.getAmount());

        for (int i = 0; i < destination.getSize(); i++) {

            ItemStack slot = destination.getItem(i);

            if (ShulkerUtil.isEmpty(slot)) {

                if (firstEmpty < 0) {
                    firstEmpty = i;
                }

                continue;

            }

            if (slot.isSimilar(item) && slot.getMaxStackSize() - slot.getAmount() >= required) {
                return i;
            }

        }

        return firstEmpty;

    }

    private boolean isValidSlot(@NotNull Inventory inventory, int slot) {
        return slot >= 0 && slot < inventory.getSize();
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
