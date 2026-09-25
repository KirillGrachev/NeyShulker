package eu.neydev.neyshulker.registry;

import eu.neydev.neyshulker.inventory.NeyShulkerViewer;
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
 * Реестр открытых шалкер-боксов. Один игрок - одна сессия.
 *
 * Потоковая модель: карты конкурентные, операции реестра атомарны
 * (создание идет через computeIfAbsent, гонка «проверил-потом-положил»
 * исключена). При этом создание сессии вызывает фабрику Bukkit-инвентаря,
 * которая допустима только в главном потоке, - реестр не делает вызовы
 * из async-контекста легальными.
 *
 * Поиск по GUI-инвентарю идет через holder-маркер {@link NeyShulkerViewer}
 * за O(1); линейный перебор остается только запасным путем для инвентарей
 * без маркера (тестовые заглушки).
 */
public class SessionRegistry {

    private final Map<UUID, ShulkerSession> sessions = new ConcurrentHashMap<>();
    private final Map<NeyShulkerViewer, ShulkerSession> byViewer = new ConcurrentHashMap<>();

    /**
     * Создает и регистрирует новую сессию игрока с явным идентификатором.
     * Идентификатор заранее пишется в метку предмета (SessionTagger),
     * поэтому реестр не может выбрать его сам.
     *
     * @param sessionId        идентификатор сессии
     * @param player           владелец сессии
     * @param shulkerItem      предмет шалкер-бокса
     * @param slot             слот, в котором лежит шалкер-бокс
     * @param inventoryFactory фабрика GUI-инвентаря (главный поток)
     * @return зарегистрированная сессия или null, если у игрока уже есть открытая сессия
     */
    public @Nullable ShulkerSession createSession(@NotNull UUID sessionId,
                                                  @NotNull Player player,
                                                  @NotNull ItemStack shulkerItem,
                                                  int slot,
                                                  @NotNull Supplier<Inventory> inventoryFactory) {

        ShulkerSession existing = sessions.computeIfAbsent(player.getUniqueId(), key -> {

            ShulkerSession session = ShulkerSession.create(sessionId, player,
                    shulkerItem, inventoryFactory, slot);

            if (session.inventory().getHolder() instanceof NeyShulkerViewer viewer) {
                byViewer.put(viewer, session);
            }

            return session;

        });

        return existing.sessionId().equals(sessionId) ? existing : null;

    }

    /**
     * Создает и регистрирует новую сессию со случайным идентификатором.
     *
     * @param player           владелец сессии
     * @param shulkerItem      предмет шалкер-бокса
     * @param slot             слот, в котором лежит шалкер-бокс
     * @param inventoryFactory фабрика GUI-инвентаря (главный поток)
     * @return зарегистрированная сессия или null, если у игрока уже есть открытая сессия
     */
    public @Nullable ShulkerSession createSession(@NotNull Player player,
                                                  @NotNull ItemStack shulkerItem,
                                                  int slot,
                                                  @NotNull Supplier<Inventory> inventoryFactory) {

        return createSession(UUID.randomUUID(), player, shulkerItem, slot, inventoryFactory);

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
     * Ищет сессию по GUI-инвентарю: сначала O(1) через holder-маркер,
     * затем запасной линейный перебор для инвентарей без маркера.
     *
     * @param inventory инвентарь, который мог быть открыт плагином
     * @return найденная сессия или null
     */
    public @Nullable ShulkerSession getSessionByInventory(@Nullable Inventory inventory) {

        if (inventory == null) {
            return null;
        }

        if (inventory.getHolder() instanceof NeyShulkerViewer viewer) {

            ShulkerSession byHolder = byViewer.get(viewer);

            if (byHolder != null) {
                return byHolder;
            }

        }

        for (ShulkerSession session : sessions.values()) {
            if (session.isInventory(inventory)) {
                return session;
            }
        }

        return null;

    }

    public @Nullable ShulkerSession closeSession(@NotNull UUID playerId) {

        ShulkerSession removed = sessions.remove(playerId);

        if (removed != null
                && removed.inventory().getHolder() instanceof NeyShulkerViewer viewer) {
            byViewer.remove(viewer, removed);
        }

        return removed;

    }

    public @NotNull Collection<ShulkerSession> getSessions() {
        return sessions.values();
    }

    public boolean isEmpty() {
        return sessions.isEmpty();
    }

    public void clear() {
        sessions.clear();
        byViewer.clear();
    }

}
