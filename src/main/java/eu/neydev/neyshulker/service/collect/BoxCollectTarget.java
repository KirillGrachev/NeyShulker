package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

/**
 * Цель - бокс в инвентаре игрока. Вставки идут в рабочее содержимое скана,
 * физическая запись в мету предмета выполняется один раз в {@link #flush()}.
 */
public final class BoxCollectTarget implements CollectTarget {

    private final Player player;
    private final int slot;
    private final ItemStack[] workingContents;
    private final InventoryTransferService transferService;

    private boolean modified;

    public BoxCollectTarget(@NotNull Player player,
                            int slot,
                            ItemStack @NotNull [] workingContents,
                            @NotNull InventoryTransferService transferService) {

        this.player = player;
        this.slot = slot;
        this.workingContents = workingContents;
        this.transferService = transferService;

    }

    @Override
    public @NotNull ItemStack shulkerItem() {

        ItemStack box = player.getInventory().getItem(slot);

        return box == null ? new ItemStack(Material.AIR) : box;

    }

    @Override
    public int insert(@NotNull ItemStack item) {

        int inserted = transferService.insertInto(workingContents, item);

        if (inserted > 0) {
            modified = true;
        }

        return inserted;

    }

    public boolean isModified() {
        return modified;
    }

    /**
     * Рабочее содержимое скана: живо до flush, затем уходит в мету предмета.
     */
    public ItemStack @NotNull [] workingContents() {
        return workingContents;
    }

    public int getSlot() {
        return slot;
    }

    /**
     * Записывает накопленное содержимое в мету бокса в слоте игрока.
     * Если слот больше не держит бокс, запись не выполняется:
     * воссоздание предмета из пустоты дюпало копии.
     */
    public void flush() {

        if (!modified) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack saved = ShulkerUtil.writeContents(inventory.getItem(slot), workingContents);

        if (saved != null) {
            inventory.setItem(slot, saved);
        }

    }
}
