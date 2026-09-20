package eu.neydev.neyshulker.util;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Фабрики легких моков инвентарей для тестов логики слотов и переноса.
 * Слоты хранятся в обычной карте, поэтому поведение предсказуемо и быстро.
 */
public final class TestInventories {

    private TestInventories() {

    }

    /**
     * Инвентарь фиксированного размера на базе карты.
     *
     * @param size размер инвентаря
     * @return мок с рабочими getItem / setItem
     */
    public static Inventory inventory(int size) {

        Map<Integer, ItemStack> slots = new HashMap<>();
        Inventory inventory = mock(Inventory.class);

        when(inventory.getSize()).thenReturn(size);
        when(inventory.getItem(anyInt()))
                .thenAnswer(answer -> slots.get(answer.getArgument(0, Integer.class)));

        doAnswer(answer -> {
            slots.put(answer.getArgument(0, Integer.class), answer.getArgument(1, ItemStack.class));
            return null;
        }).when(inventory).setItem(anyInt(), any());

        return inventory;

    }

    /**
     * Инвентарь игрока: 41 слот и область хранения на 36 слотов,
     * как у настоящего PlayerInventory.
     *
     * @return мок PlayerInventory
     */
    public static PlayerInventory playerInventory() {

        PlayerInventory inventory = mock(PlayerInventory.class);

        when(inventory.getSize()).thenReturn(41);
        when(inventory.getStorageContents()).thenReturn(new ItemStack[36]);

        Map<Integer, ItemStack> slots = new HashMap<>();

        when(inventory.getItem(anyInt()))
                .thenAnswer(answer -> slots.get(answer.getArgument(0, Integer.class)));

        doAnswer(answer -> {
            slots.put(answer.getArgument(0, Integer.class), answer.getArgument(1, ItemStack.class));
            return null;
        }).when(inventory).setItem(anyInt(), any());

        return inventory;

    }

    /**
     * Представление инвентаря с заданными верхом и низом.
     *
     * @param top    верхний инвентарь
     * @param bottom нижний инвентарь
     * @return мок InventoryView
     */
    public static InventoryView view(Inventory top, Inventory bottom) {

        InventoryView view = mock(InventoryView.class);

        when(view.getTopInventory()).thenReturn(top);
        when(view.getBottomInventory()).thenReturn(bottom);

        return view;

    }
}
