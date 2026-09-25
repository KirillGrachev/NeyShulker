package eu.neydev.neyshulker.service.collect;

import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Лист ожидания: потолок емкости, возврат в голову, фильтрация.
 */
class PlayerWaitListTest {

    private Item item(Material material) {

        Item item = mock(Item.class);
        ItemStack stack = mock(ItemStack.class);

        when(stack.getType()).thenReturn(material);
        when(item.getItemStack()).thenReturn(stack);
        return item;

    }

    @Test
    @DisplayName("offer принимает до capacity и отказывает дальше")
    void offerRespectsCapacity() {

        PlayerWaitList waitList = new PlayerWaitList(2);

        assertTrue(waitList.offer(CollectEntry.inventory(0, Material.DIRT)));
        assertTrue(waitList.offer(CollectEntry.inventory(1, Material.STONE)));
        assertFalse(waitList.offer(CollectEntry.inventory(2, Material.SAND)));
        assertEquals(2, waitList.size());

    }

    @Test
    @DisplayName("Емкость не падает ниже единицы")
    void capacityFlooredToOne() {

        PlayerWaitList waitList = new PlayerWaitList(0);

        assertTrue(waitList.offer(CollectEntry.inventory(0, Material.DIRT)));
        assertFalse(waitList.offer(CollectEntry.inventory(1, Material.STONE)));

    }

    @Test
    @DisplayName("offerFirst возвращает элемент в голову в порядке FIFO-слива")
    void offerFirstPutsBackToHead() {

        PlayerWaitList waitList = new PlayerWaitList(4);

        CollectEntry first = CollectEntry.inventory(0, Material.DIRT);
        CollectEntry second = CollectEntry.inventory(1, Material.STONE);

        waitList.offer(first);
        waitList.offer(second);

        assertSame(first, waitList.poll());

        // Вернули первый в голову: следующий poll снова отдаст его
        waitList.offerFirst(first);
        assertSame(first, waitList.poll());
        assertSame(second, waitList.poll());
        assertNull(waitList.poll(), "Пустой лист отдает null");

    }

    @Test
    @DisplayName("offerFirst на полном листе молча отбрасывает элемент")
    void offerFirstOnFullListDrops() {

        PlayerWaitList waitList = new PlayerWaitList(1);

        waitList.offer(CollectEntry.inventory(0, Material.DIRT));
        waitList.offerFirst(CollectEntry.inventory(1, Material.STONE));

        assertEquals(1, waitList.size());

    }

    @Test
    @DisplayName("removeIf убирает по предикату и возвращает счет")
    void removeIfCountsRemoved() {

        PlayerWaitList waitList = new PlayerWaitList(8);

        waitList.offer(CollectEntry.ground(item(Material.DIAMOND)));
        waitList.offer(CollectEntry.inventory(3, Material.DIRT));
        waitList.offer(CollectEntry.inventory(4, Material.DIRT));

        int removed = waitList.removeIf(entry -> !entry.isGround()
                && entry.material() == Material.DIRT);

        assertEquals(2, removed);
        assertEquals(1, waitList.size());
        assertTrue(waitList.poll().isGround());
        assertTrue(waitList.isEmpty());

    }

}
