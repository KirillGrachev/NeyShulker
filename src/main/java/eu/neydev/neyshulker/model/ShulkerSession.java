package eu.neydev.neyshulker.model;

import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Сессия открытого шалкер-бокса.
 * Хранит точную привязку к слоту инвентаря игрока и состояние сохранения.
 *
 * @param sessionId     уникальный идентификатор сессии
 * @param playerId      идентификатор владельца
 * @param shulkerItem   слепок предмета на момент открытия
 * @param inventory     GUI-инвентарь сессии
 * @param openedAt      время открытия в миллисекундах
 * @param slot          слот инвентаря, в котором лежит шалкер-бокс
 * @param saving        флаг выполняющегося сохранения (защита от повторного входа)
 * @param saveScheduled флаг запланированного сохранения
 * @param detached      сессия откреплена: бокс покинул слот, записи больше не будет
 */
public record ShulkerSession(
        @NotNull UUID sessionId,
        @NotNull UUID playerId,
        @NotNull ItemStack shulkerItem,
        @NotNull Inventory inventory,
        long openedAt,
        @NotNull AtomicInteger slot,
        @NotNull AtomicBoolean saving,
        @NotNull AtomicBoolean saveScheduled,
        @NotNull AtomicBoolean detached
) {

    /**
     * Создает сессию. Инвентарь поставляется фабрикой,
     * которая вызывается ровно один раз.
     *
     * @param sessionId        идентификатор сессии
     * @param player           владелец
     * @param shulkerItem      предмет шалкер-бокса
     * @param inventoryFactory фабрика GUI-инвентаря
     * @param slot             слот с шалкер-боксом
     * @return новая сессия
     */
    public static @NotNull ShulkerSession create(@NotNull UUID sessionId,
                                                 @NotNull Player player,
                                                 @NotNull ItemStack shulkerItem,
                                                 @NotNull java.util.function.Supplier<Inventory> inventoryFactory,
                                                 int slot) {
        return new ShulkerSession(
                sessionId,
                player.getUniqueId(),
                shulkerItem.clone(),
                inventoryFactory.get(),
                System.currentTimeMillis(),
                new AtomicInteger(slot),
                new AtomicBoolean(false),
                new AtomicBoolean(false),
                new AtomicBoolean(false)
        );
    }

    public int getSlot() {
        return slot.get();
    }

    public void setSlot(int newSlot) {
        slot.set(newSlot);
    }

    /**
     * Проверяет, что инвентарь принадлежит этой сессии.
     */
    public boolean isInventory(@Nullable Inventory other) {
        return other != null && inventory.equals(other);
    }

    public @NotNull String getShulkerName() {
        return ShulkerUtil.getShulkerName(shulkerItem);
    }

    public @Nullable Player getPlayer() {
        return Bukkit.getPlayer(playerId);
    }

    /**
     * Одноразово переводит сессию в открепленное состояние.
     *
     * @return true если открепление произошло сейчас, а не раньше
     */
    public boolean markDetached() {
        return detached.compareAndSet(false, true);
    }

    public boolean isDetached() {
        return detached.get();
    }

    public boolean isOnline() {

        Player player = getPlayer();
        return player != null && player.isOnline();

    }

}
