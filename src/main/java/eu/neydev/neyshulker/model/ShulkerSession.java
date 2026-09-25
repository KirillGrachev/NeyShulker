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
import java.util.function.Supplier;

/**
 * Сессия открытого шалкер-бокса.
 * Хранит точную привязку к слоту инвентаря игрока и состояние сохранения.
 *
 * Осознанно обычный final-класс, а не record: состояние сессии изменяемо
 * (слот может переехать, флаги сохранения переключаются), а record
 * декларирует прозрачного неизменяемого носителя и генерирует
 * equals/hashCode по компонентам, которые здесь никому не нужны -
 * сессии везде сравниваются по ссылке.
 *
 * Идентификатор сессии одновременно пишется в PersistentDataContainer
 * предмета (см. {@link eu.neydev.neyshulker.util.SessionTagger}):
 * метка, а не слот, является источником истины о том, какой именно
 * предмет принадлежит этой сессии.
 */
public final class ShulkerSession {

    private final UUID sessionId;
    private final UUID playerId;
    private final ItemStack shulkerItem;
    private final Inventory inventory;
    private final long openedAt;

    private final AtomicInteger slot;
    private final AtomicBoolean saving = new AtomicBoolean(false);
    private final AtomicBoolean saveScheduled = new AtomicBoolean(false);
    private final AtomicBoolean detached = new AtomicBoolean(false);

    private ShulkerSession(@NotNull UUID sessionId,
                           @NotNull UUID playerId,
                           @NotNull ItemStack shulkerItem,
                           @NotNull Inventory inventory,
                           long openedAt,
                           int slot) {

        this.sessionId = sessionId;
        this.playerId = playerId;
        this.shulkerItem = shulkerItem;
        this.inventory = inventory;
        this.openedAt = openedAt;
        this.slot = new AtomicInteger(slot);

    }

    /**
     * Создает сессию. Инвентарь поставляется фабрикой,
     * которая вызывается ровно один раз.
     *
     * Внимание: фабрика создает Bukkit-инвентарь, поэтому вызов допустим
     * только из главного потока.
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
                                                 @NotNull Supplier<Inventory> inventoryFactory,
                                                 int slot) {

        return new ShulkerSession(
                sessionId,
                player.getUniqueId(),
                shulkerItem.clone(),
                inventoryFactory.get(),
                System.currentTimeMillis(),
                slot
        );

    }

    public @NotNull UUID sessionId() {
        return sessionId;
    }

    public @NotNull UUID playerId() {
        return playerId;
    }

    /**
     * Слепок предмета на момент открытия. Может устаревать по содержимому
     * (автосохранения переписывают мету живого предмета), поэтому для
     * опознания бокса используется метка сессии, а не сравнение слепков.
     */
    public @NotNull ItemStack shulkerItem() {
        return shulkerItem;
    }

    public @NotNull Inventory inventory() {
        return inventory;
    }

    public long openedAt() {
        return openedAt;
    }

    public int getSlot() {
        return slot.get();
    }

    public void setSlot(int newSlot) {
        slot.set(newSlot);
    }

    public @NotNull AtomicBoolean saving() {
        return saving;
    }

    public @NotNull AtomicBoolean saveScheduled() {
        return saveScheduled;
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

    /**
     * Живой игрок сессии. Удобство для слушателей; обращение к статике
     * Bukkit осознанное: сессия не должна таскать ссылку на Player
     * (игрок может переподключиться, а UUID остается стабильным).
     */
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
