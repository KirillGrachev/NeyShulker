package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Проверка перемещения предметов между инвентарями: фиксация слотов,
 * дозаполнение стеков, поиск целевого слота.
 */
class InventoryTransferServiceTest {

    private final InventoryTransferService transferService = new InventoryTransferService();

    @Test
    @DisplayName("Перенос в пустой слот очищает источник")
    void movesIntoEmptySlot() {

        Inventory source = TestInventories.inventory(9);
        Inventory destination = TestInventories.inventory(27);

        source.setItem(2, new FakeItemStack(Material.DIAMOND, 10));

        int moved = transferService.moveSlot(null, source, 2, destination, 5, 64);

        assertEquals(10, moved);
        assertNull(source.getItem(2));
        assertEquals(10, destination.getItem(5).getAmount());

    }

    @Test
    @DisplayName("Перенос дозируется свободным местом стека")
    void movesOnlyAvailableSpace() {

        Inventory source = TestInventories.inventory(9);
        Inventory destination = TestInventories.inventory(27);

        source.setItem(0, new FakeItemStack(Material.DIAMOND, 40));
        destination.setItem(1, new FakeItemStack(Material.DIAMOND, 60));

        int moved = transferService.moveSlot(null, source, 0, destination, 1, 64);

        assertEquals(4, moved);
        assertEquals(36, source.getItem(0).getAmount());
        assertEquals(64, destination.getItem(1).getAmount());

    }

    @Test
    @DisplayName("Один и тот же слот не переносится сам в себя")
    void sameSlotIsNoOp() {

        Inventory inventory = TestInventories.inventory(9);

        inventory.setItem(1, new FakeItemStack(Material.DIAMOND, 5));

        assertEquals(0, transferService.moveSlot(null, inventory, 1, inventory, 1, 64));
        assertEquals(5, inventory.getItem(1).getAmount());

    }

    @Test
    @DisplayName("Некорректные слоты игнорируются")
    void invalidSlotsAreIgnored() {

        Inventory inventory = TestInventories.inventory(9);

        inventory.setItem(0, new FakeItemStack(Material.DIAMOND, 5));

        assertEquals(0, transferService.moveSlot(null, inventory, -1, inventory, 0, 64));
        assertEquals(0, transferService.moveSlot(null, inventory, 0, inventory, 99, 64));
        assertEquals(0, transferService.moveSlot(null, inventory, 4, inventory, 0, 64));

    }

    @Test
    @DisplayName("moveFirst дозирует в похожий стек")
    void moveFirstFillsSimilarStack() {

        Inventory source = TestInventories.inventory(9);
        Inventory destination = TestInventories.inventory(27);

        source.setItem(0, new FakeItemStack(Material.DIAMOND, 20));
        destination.setItem(3, new FakeItemStack(Material.DIAMOND, 50));

        int moved = transferService.moveFirst(null, source, 0, destination, 64);

        // Весь стек ушел в первый пустой слот: похожий не вмещал требуемое количество
        assertEquals(20, moved);
        assertNull(source.getItem(0));
        assertEquals(20, destination.getItem(0).getAmount());
        assertEquals(50, destination.getItem(3).getAmount(), "Похожий стек не тронут");

    }

    @Test
    @DisplayName("insert заполняет инвентарь и возвращает количество")
    void insertFillsInventory() {

        Inventory destination = TestInventories.inventory(3);

        destination.setItem(0, new FakeItemStack(Material.DIAMOND, 64));
        destination.setItem(1, new FakeItemStack(Material.DIAMOND, 60));

        int inserted = transferService.insert(null, destination, new FakeItemStack(Material.DIAMOND, 10));

        assertEquals(10, inserted);
        assertEquals(64, destination.getItem(0).getAmount());
        assertEquals(64, destination.getItem(1).getAmount(), "Дозаполнен похожий стек");
        assertEquals(6, destination.getItem(2).getAmount(), "Остаток ушел в пустой слот");

    }

    @Test
    @DisplayName("findSlot предпочитает похожий неполный стек пустому")
    void findSlotPrefersPartialStack() {

        Inventory inventory = TestInventories.inventory(4);

        inventory.setItem(0, new FakeItemStack(Material.STONE, 10));
        inventory.setItem(1, new FakeItemStack(Material.DIAMOND, 60));

        assertEquals(1, transferService.findSlot(inventory, new FakeItemStack(Material.DIAMOND, 4), 4));
        assertEquals(2, transferService.findSlot(inventory, new FakeItemStack(Material.EMERALD, 4), 4));

    }

    @Test
    @DisplayName("Пустой источник не переносится")
    void emptySourceMovesNothing() {

        Inventory source = TestInventories.inventory(9);
        Inventory destination = TestInventories.inventory(9);

        assertEquals(0, transferService.moveSlot(null, source, 0, destination, 0, 64));
        assertEquals(0, transferService.moveFirst(null, source, 0, destination, 64));

    }

    @Test
    @DisplayName("Снимок слотов не влияет на исходные предметы инвентаря")
    void transferKeepsItemIndependence() {

        Inventory source = TestInventories.inventory(9);
        Inventory destination = TestInventories.inventory(9);

        ItemStack original = new FakeItemStack(Material.DIAMOND, 7);
        source.setItem(0, original);

        transferService.moveSlot(null, source, 0, destination, 0, 64);

        assertEquals(7, destination.getItem(0).getAmount());
        assertEquals(7, original.getAmount(), "Исходный экземпляр не мутирован");

    }
}
