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
    private final String title;

    public ShulkerInventoryHolder(@NotNull String title) {

        this.title = title;
        this.inventory = Bukkit.createInventory(this, ShulkerUtil.SHULKER_SIZE, title);

    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public @NotNull String getTitle() {
        return title;
    }

    /**
     * Проверяет, что инвентарь принадлежит плагину.
     *
     * @param inventory проверяемый инвентарь
     * @return true если это GUI шалкер-бокса NeyShulker
     */
    public static boolean isShulkerInventory(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ShulkerInventoryHolder;
    }
}
