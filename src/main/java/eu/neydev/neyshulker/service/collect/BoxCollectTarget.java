package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Цель - бокс в инвентаре игрока. Вставки идут в рабочее содержимое скана,
 * физическая запись в мету предмета выполняется один раз в {@link #flush()}.
 *
 * Консервация предметов: все успешные вставки запоминаются, и если к моменту
 * flush бокс покинул слот (внешний плагин подвинул инвентарь во время
 * синхронного события), собранные предметы возвращаются в мир дропом
 * под ноги игрока вместо тихого испарения - дроп с земли к этому моменту
 * уже удален, и без возврата лут исчез бы без следа.
 */
public final class BoxCollectTarget implements CollectTarget {

    private static final Logger LOGGER = Logger.getLogger("NeyShulker");

    private final Player player;
    private final int slot;
    private final ItemStack[] workingContents;
    private final InventoryTransferService transferService;
    private final List<ItemStack> inserted = new ArrayList<>();

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

        int count = transferService.insertInto(workingContents, item);

        if (count > 0) {

            modified = true;

            // Клон с фактическим числом принятых предметов: если flush не
            // найдет бокс в слоте, ровно этот объем вернется в мир дропом
            ItemStack recorded = item.clone();
            recorded.setAmount(count);
            inserted.add(recorded);

        }

        return count;

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
     * воссоздание предмета из пустоты дюпало копии. Вместо тихой потери
     * вставленные предметы возвращаются в мир дропом.
     */
    public void flush() {

        if (!modified) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack saved = ShulkerUtil.writeContents(inventory.getItem(slot), workingContents);

        if (saved != null) {
            inventory.setItem(slot, saved);
            return;
        }

        dropCollectedBack();

    }

    private void dropCollectedBack() {

        LOGGER.log(Level.SEVERE,
                "Shulker box left slot {0} of player {1} mid-collect: returning {2} collected "
                        + "item stack(s) to the ground instead of losing them.",
                new Object[]{slot, playerName(), inserted.size()});

        for (ItemStack stack : inserted) {

            try {
                player.getWorld().dropItemNaturally(player.getLocation(), stack);
            } catch (RuntimeException exception) {
                LOGGER.log(Level.SEVERE, "Failed to drop collected items back", exception);
            }

        }

    }

    private @NotNull String playerName() {

        try {
            return player.getName();
        } catch (RuntimeException exception) {
            return "<unknown>";
        }

    }

}
