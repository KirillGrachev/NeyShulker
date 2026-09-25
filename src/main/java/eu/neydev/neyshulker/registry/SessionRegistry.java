package eu.neydev.neyshulker.registry;

import eu.neydev.neyshulker.model.ShulkerSession;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Реестр открытых шалкер-боксов.
 * Один игрок - одна сессия. Потокобезопасен: обращения возможны из разных задач.
 */
public class SessionRegistry {

    private final Map<UUID, ShulkerSession> sessions = new ConcurrentHashMap<>();

    /**
     * Создает и регистрирует новую сессию игрока.
     * Инвентарь поставляется фабрикой и создается ровно один раз.
     *
     * @param player           владелец сессии
     * @param shulkerItem      предмет шалкер-бокса
     * @param slot             слот, в котором лежит шалкер-бокс
     * @param inventoryFactory фабрика GUI-инвентаря
     * @return зарегистрированная сессия или null, если у игрока уже есть открытая сессия
     */
    public @Nullable ShulkerSession createSession(@NotNull Player player,
                                                  @NotNull ItemStack shulkerItem,
                                                  int slot,
                                                  @NotNull Supplier<Inventory> inventoryFactory) {

        UUID playerId = player.getUniqueId();

        if (sessions.containsKey(playerId)) {
            return null;
        }

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player,
                shulkerItem, inventoryFactory, slot);

        sessions.put(playerId, session);
        return session;

    }

    public @Nullable ShulkerSession getSession(@NotNull UUID playerId) {
        return sessions.get(playerId);
    }

    public @Nullable ShulkerSession getSession(@NotNull Player player) {
        return sessions.get(player.getUniqueId());
    }

    public boolean hasSession(@NotNull UUID playerId) {
        return sessions.containsKey(playerId);
    }

    /**
     * Ищет сессию по GUI-инвентарю.
     *
     * @param inventory инвентарь, который мог быть открыт плагином
     * @return найденная сессия или null
     */
    public @Nullable ShulkerSession getSessionByInventory(@Nullable Inventory inventory) {

        if (inventory == null) {
            return null;
        }

        for (ShulkerSession session : sessions.values()) {
            if (session.isInventory(inventory)) {
                return session;
            }
        }

        return null;

    }

    public @Nullable ShulkerSession closeSession(@NotNull UUID playerId) {
        return sessions.remove(playerId);
    }

    public @NotNull Collection<ShulkerSession> getSessions() {
        return sessions.values();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public void clear() {
        sessions.clear();
    }

}
