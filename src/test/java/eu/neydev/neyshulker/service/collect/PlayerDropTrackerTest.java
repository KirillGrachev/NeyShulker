package eu.neydev.neyshulker.service.collect;

import org.bukkit.entity.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Памятка брошенных игроком дропов: запись, срок жизни и очистка.
 */
class PlayerDropTrackerTest {

    private final AtomicLong clock = new AtomicLong();
    private final PlayerDropTracker tracker = new PlayerDropTracker(clock::get);

    private Item item() {

        Item item = mock(Item.class);

        when(item.getUniqueId()).thenReturn(UUID.randomUUID());

        return item;
    }

    @Test
    @DisplayName("Записанный дроп считается выброшенным игроком")
    void markedDropIsPlayerDropped() {

        Item drop = item();

        tracker.mark(drop);

        assertTrue(tracker.isPlayerDropped(drop));
    }

    @Test
    @DisplayName("Незаписанный дроп не считается выброшенным игроком")
    void unmarkedDropIsNotPlayerDropped() {
        assertFalse(tracker.isPlayerDropped(item()));
    }

    @Test
    @DisplayName("По истечении срока жизни предмета запись истекает")
    void recordExpiresWithItemLifetime() {

        Item drop = item();

        tracker.mark(drop);
        clock.addAndGet(300_001L);

        assertFalse(tracker.isPlayerDropped(drop));
    }

    @Test
    @DisplayName("Чистка удаляет истекшие записи и не трогает живые")
    void purgeRemovesOnlyExpired() {

        Item expired = item();

        tracker.mark(expired);
        clock.addAndGet(300_001L);

        Item fresh = item();

        tracker.mark(fresh);
        tracker.purge();

        assertFalse(tracker.isPlayerDropped(expired));
        assertTrue(tracker.isPlayerDropped(fresh));
    }

    @Test
    @DisplayName("Очистка опустошает памятку")
    void clearEmptiesTracker() {

        Item drop = item();

        tracker.mark(drop);
        tracker.clear();

        assertFalse(tracker.isPlayerDropped(drop));
    }
}
