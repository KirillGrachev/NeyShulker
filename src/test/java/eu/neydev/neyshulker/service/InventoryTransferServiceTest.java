package eu.neydev.neyshulker.service;

import static org.mockito.Mockito.when;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Вставка предметов в инвентарь: дозаполнение стеков и занятие пустых слотов.
 * Переносы между слотами остались ванильными, поэтому здесь только insert.
 */
class InventoryTransferServiceTest {

    private final InventoryTransferService transferService = new InventoryTransferService();

    @Test
    @DisplayName("insert дозаполняет похожие стеки и занимает пустые слоты")
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
    @DisplayName("insert в полный инвентарь ничего не вставляет")
    void insertIntoFullInventory() {

        Inventory destination = TestInventories.inventory(1);
        destination.setItem(0, new FakeItemStack(Material.DIAMOND, 64));
        int inserted = transferService.insert(null, destination, new FakeItemStack(Material.DIAMOND, 5));

        assertEquals(0, inserted);
        assertEquals(64, destination.getItem(0).getAmount());

    }


    @Test
    @DisplayName("Фиксация трогает только измененные слоты")
    void onlyChangedSlotsAreWrittenBack() {

        Inventory destination = TestInventories.inventory(3);

        destination.setItem(0, new FakeItemStack(Material.DIAMOND, 64));
        destination.setItem(1, new FakeItemStack(Material.DIAMOND, 60));

        // Раскладка - часть arrange: считаем только записи самой фиксации
        org.mockito.Mockito.clearInvocations(destination);

        int inserted = transferService.insert(null, destination, new FakeItemStack(Material.DIAMOND, 10));

        assertEquals(10, inserted);

        org.mockito.Mockito.verify(destination, org.mockito.Mockito.never())
                .setItem(org.mockito.ArgumentMatchers.eq(0), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(destination)
                .setItem(org.mockito.ArgumentMatchers.eq(1), org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.verify(destination)
                .setItem(org.mockito.ArgumentMatchers.eq(2), org.mockito.ArgumentMatchers.any());

    }

    @Test
    @DisplayName("Нулевая вставка не трогает инвентарь и не синхронизирует клиента")
    void zeroInsertIsNoOp() {

        Inventory destination = TestInventories.inventory(1);
        destination.setItem(0, new FakeItemStack(Material.DIAMOND, 64));

        org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);
        when(player.isOnline()).thenReturn(true);

        assertEquals(0, transferService.insert(player, destination,
                new FakeItemStack(Material.DIAMOND, 1)));

        org.mockito.Mockito.verify(player, org.mockito.Mockito.never()).updateInventory();

    }

    @Test
    @DisplayName("Онлайн-игрок получает resync после успешной вставки")
    void onlinePlayerIsResynced() {

        Inventory destination = TestInventories.inventory(3);
        org.bukkit.entity.Player player = org.mockito.Mockito.mock(org.bukkit.entity.Player.class);

        when(player.isOnline()).thenReturn(true);

        assertEquals(2, transferService.insert(player, destination,
                new FakeItemStack(Material.DIAMOND, 2)));

        org.mockito.Mockito.verify(player).updateInventory();

    }

}
