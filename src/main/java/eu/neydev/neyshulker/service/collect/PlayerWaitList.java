package eu.neydev.neyshulker.service.collect;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.function.Predicate;

/**
 * Лист ожидания игрока: обнаруженные, но еще не погруженные предметы.
 *
 * Ограничен сверху: переполненный лист не растет бесконечно, а источники
 * будут обнаружены повторно на следующих волнах, пока лежат на месте.
 */
public final class PlayerWaitList {

    private final Deque<CollectEntry> entries = new ArrayDeque<>();
    private final int capacity;

    public PlayerWaitList(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    /**
     * Добавляет элемент, если есть место.
     *
     * @param entry элемент листа ожидания
     * @return true если элемент принят
     */
    public boolean offer(@NotNull CollectEntry entry) {

        if (entries.size() >= capacity) {
            return false;
        }

        entries.addLast(entry);

        return true;

    }

    /**
     * Возвращает элемент в начало листа: источник еще не готов
     * (например, pickup delay), но терять его не нужно.
     *
     * @param entry элемент
     */
    public void offerFirst(@NotNull CollectEntry entry) {

        if (entries.size() < capacity) {
            entries.addFirst(entry);
        }

    }

    /**
     * Удаляет элементы, удовлетворяющие фильтру.
     *
     * Точка немедленной чистки: когда инвентарь игрока изменился,
     * покинувшие его предметы убираются из очереди, не дожидаясь
     * перепроверки на фазе слива.
     *
     * @param filter предикат удаления
     * @return сколько элементов удалено
     */
    public int removeIf(@NotNull Predicate<CollectEntry> filter) {

        int before = entries.size();

        entries.removeIf(filter);

        return before - entries.size();

    }

    public @Nullable CollectEntry poll() {
        return entries.pollFirst();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    public int size() {
        return entries.size();
    }
}
