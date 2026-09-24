package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.ServiceContainer;
import eu.neydev.neyshulker.service.collect.PlayerDropTracker;
import org.bukkit.entity.Item;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Слушатель записывает брошенный игроком дроп в памятку защиты авто-сбора.
 */
class PlayerDropListenerTest {

    private final NeyShulker plugin = mock(NeyShulker.class);
    private final ServiceContainer services = mock(ServiceContainer.class);
    private final PlayerDropTracker tracker = new PlayerDropTracker(() -> 0L);
    private final PlayerDropListener listener = listener();

    private PlayerDropListener listener() {

        when(plugin.getServices()).thenReturn(services);
        when(services.getDropTracker()).thenReturn(tracker);

        return new PlayerDropListener(plugin);
    }

    @Test
    @DisplayName("Брошенный дроп записывается в памятку")
    void thrownDropIsMarked() {

        Item drop = mock(Item.class);

        when(drop.getUniqueId()).thenReturn(UUID.randomUUID());

        PlayerDropItemEvent event = mock(PlayerDropItemEvent.class);

        when(event.getItemDrop()).thenReturn(drop);

        listener.onDropItem(event);

        assertTrue(tracker.isPlayerDropped(drop));
    }
}
