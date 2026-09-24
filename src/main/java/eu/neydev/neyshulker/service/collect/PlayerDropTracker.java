package eu.neydev.neyshulker.service.collect;

import org.bukkit.entity.Item;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Памятка предметов, выброшенных игроками.
 *
 * Выброшенный предмет не цель для авто-сбора: его либо скинули намеренно,
 * либо передают другому игроку. Памятка хранит UUID дропа до истечения
 * срока жизни сущности.
 */
public final class PlayerDropTracker {

    /** Срок записи совпадает с ванильным временем жизни предмета - 5 минут. */
    private static final long DROP_TIME_TO_LIVE = 300_000L;

    private final Map<UUID, Long> deadlines = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public PlayerDropTracker(@NotNull LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Записывает дроп, выброшенный игроком.
     *
     * @param item выброшенная сущность
     */
    public void mark(@NotNull Item item) {
        deadlines.put(item.getUniqueId(), clock.getAsLong() + DROP_TIME_TO_LIVE);
    }

    /**
     * Проверяет: выброшен ли дроп игроком и жива ли еще запись.
     *
     * @param item проверяемый дроп
     * @return true, если предмет выброшен игроком
     */
    public boolean isPlayerDropped(@NotNull Item item) {

        Long deadline = deadlines.get(item.getUniqueId());
        return deadline != null && clock.getAsLong() < deadline;
    }

    /**
     * Удаляет истекшие записи.
     */
    public void purge() {

        long now = clock.getAsLong();
        Iterator<Map.Entry<UUID, Long>> iterator = deadlines.entrySet().iterator();

        while (iterator.hasNext()) {
            if (now >= iterator.next().getValue()) {
                iterator.remove();
            }
        }
    }

    /**
     * Очищает памятку.
     */
    public void clear() {
        deadlines.clear();
    }
}
