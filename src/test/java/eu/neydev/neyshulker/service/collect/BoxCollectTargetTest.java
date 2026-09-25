package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.block.ShulkerBox;
import org.bukkit.inventory.Inventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Бокс в инвентаре: накопление вставок в рабочую копию, физическая
 * запись в flush и консервация предметов, если бокс покинул слот
 * (возврат дропом вместо тихого испарения).
 */
class BoxCollectTargetTest {

    private final InventoryTransferService transferService = new InventoryTransferService();

    private ItemStack shulker(Inventory boxInventory) {

        BlockStateMeta meta = mock(BlockStateMeta.class);
        ShulkerBox box = mock(ShulkerBox.class);

        when(box.getSnapshotInventory()).thenReturn(boxInventory);
        when(boxInventory.getContents()).thenAnswer(answer -> {

            ItemStack[] contents = new ItemStack[27];

            for (int i = 0; i < contents.length; i++) {
                contents[i] = boxInventory.getItem(i);
            }

            return contents;

        });
        when(meta.getBlockState()).thenReturn(box);

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenAnswer(answer -> shulker);
        when(shulker.getItemMeta()).thenReturn(meta);
        return shulker;

    }

    private Player player(PlayerInventory inventory, World world) {

        Player player = mock(Player.class);

        when(player.getInventory()).thenReturn(inventory);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(mock(Location.class));
        when(player.getName()).thenReturn("Tester");
        return player;

    }

    @Test
    @DisplayName("Вставки копятся в рабочей копии, modified отмечается")
    void insertsAccumulateInWorkingCopy() {

        PlayerInventory inventory = TestInventories.playerInventory();
        ItemStack box = shulker(TestInventories.inventory(27));

        inventory.setItem(5, box);

        BoxCollectTarget target = new BoxCollectTarget(
                player(inventory, mock(World.class)), 5, new ItemStack[27], transferService);

        assertFalse(target.isModified());
        assertEquals(3, target.insert(new FakeItemStack(Material.DIAMOND, 3)));
        assertTrue(target.isModified());
        assertEquals(2, target.insert(new FakeItemStack(Material.DIAMOND, 2)));
        assertEquals(5, target.workingContents()[0].getAmount(),
                "Последовательные вставки мерджатся в один стек");

    }

    @Test
    @DisplayName("flush записывает накопленное в мету бокса в слоте")
    void flushWritesBackIntoSlot() {

        PlayerInventory inventory = TestInventories.playerInventory();
        Inventory boxContents = TestInventories.inventory(27);
        ItemStack box = shulker(boxContents);

        inventory.setItem(5, box);

        BoxCollectTarget target = new BoxCollectTarget(
                player(inventory, mock(World.class)), 5, new ItemStack[27], transferService);

        target.insert(new FakeItemStack(Material.DIAMOND, 3));
        target.flush();

        assertEquals(3, boxContents.getItem(0).getAmount(),
                "Содержимое рабочей копии ушло в снапшот-инвентарь бокса");
        // setItem(5, ...) был и в раскладке теста: проверяем, что flush дописал
        verify(inventory, org.mockito.Mockito.atLeast(2)).setItem(eq(5), any(ItemStack.class));

    }

    @Test
    @DisplayName("Бокс покинул слот: собранное возвращается дропом, записи нет")
    void flushWithoutBoxDropsCollectedBack() {

        PlayerInventory inventory = TestInventories.playerInventory();
        World world = mock(World.class);

        // Слот пуст: бокс исчез между вставкой и flush
        BoxCollectTarget target = new BoxCollectTarget(
                player(inventory, world), 5, new ItemStack[27], transferService);

        target.insert(new FakeItemStack(Material.DIAMOND, 3));
        target.insert(new FakeItemStack(Material.EMERALD, 2));
        target.flush();

        verify(inventory, never()).setItem(eq(5), any());

        ArgumentCaptor<ItemStack> dropped = ArgumentCaptor.forClass(ItemStack.class);
        verify(world, org.mockito.Mockito.times(2)).dropItemNaturally(any(Location.class), dropped.capture());

        assertEquals(Material.DIAMOND, dropped.getAllValues().get(0).getType());
        assertEquals(3, dropped.getAllValues().get(0).getAmount());
        assertEquals(Material.EMERALD, dropped.getAllValues().get(1).getType());
        assertEquals(2, dropped.getAllValues().get(1).getAmount());

    }

    @Test
    @DisplayName("Неизмененная цель во flush ничего не пишет")
    void unmodifiedFlushIsNoOp() {

        PlayerInventory inventory = TestInventories.playerInventory();
        BoxCollectTarget target = new BoxCollectTarget(
                player(inventory, mock(World.class)), 5, new ItemStack[27], transferService);

        target.flush();

        verify(inventory, never()).setItem(eq(5), any());

    }

    @Test
    @DisplayName("Пустой слот: shulkerItem отдает AIR вместо null")
    void shulkerItemOfEmptySlotIsAir() {

        PlayerInventory inventory = TestInventories.playerInventory();
        BoxCollectTarget target = new BoxCollectTarget(
                player(inventory, mock(World.class)), 5, new ItemStack[27], transferService);

        assertEquals(Material.AIR, target.shulkerItem().getType());
        assertEquals(5, target.getSlot());

    }

}
