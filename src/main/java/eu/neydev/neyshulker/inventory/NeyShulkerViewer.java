package eu.neydev.neyshulker.inventory;

import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Вьюер шалкер-бокса: держатель GUI-инвентаря открытой сессии.
 *
 * Единственная задача - быть маркером: по holder'у любой слушатель однозначно
 * отличает наш инвентарь от сундука, воронки или инвентаря другого плагина
 * за O(1), без поиска по реестру сессий. Сама сессия хранится в SessionRegistry
 * и ищется по инвентарю, поэтому здесь нет ссылок на изменяемое состояние.
 */
public class NeyShulkerViewer implements InventoryHolder {

    private final Inventory inventory;

    // Передача this в Bukkit.createInventory - каноническая идиома
    // InventoryHolder: Bukkit копирует ссылку на держателя и не вызывает
    // переопределяемых методов до завершения конструктора.
    @SuppressWarnings("this-escape")
    public NeyShulkerViewer(@NotNull String title) {
        this.inventory = Bukkit.createInventory(this, ShulkerUtil.SHULKER_SIZE, title);
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

}
