package eu.neydev.neyshulker.inventory;

import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Держатель GUI-инвентаря шалкер-бокса.
 *
 * Единственная задача - быть маркером: по holder'у любой слушатель однозначно
 * отличает наш инвентарь от сундука, воронки или инвентаря другого плагина.
 * Сама сессия хранится в SessionRegistry и ищется по инвентарю,
 * поэтому здесь нет ссылок на изменяемое состояние.
 */
public class ShulkerInventoryHolder implements InventoryHolder {

    private final Inventory inventory;

    public ShulkerInventoryHolder(@NotNull String title) {
        this.inventory = Bukkit.createInventory(this, ShulkerUtil.SHULKER_SIZE, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
