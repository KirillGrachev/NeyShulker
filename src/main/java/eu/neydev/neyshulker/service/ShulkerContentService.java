package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис содержимого: переносит предметы между шалкер-боксом (метаданные предмета)
 * и GUI-инвентарем, а также делает снимки инвентаря.
 */
public class ShulkerContentService {

    /**
     * Загружает содержимое шалкер-бокса в GUI-инвентарь.
     *
     * @param shulker   предмет шалкер-бокса
     * @param inventory целевой инвентарь
     */
    public void loadInto(@Nullable ItemStack shulker, @NotNull Inventory inventory) {

        inventory.clear();

        ItemStack[] contents = ShulkerUtil.readContents(shulker);

        if (contents == null) {
            return;
        }

        for (int i = 0; i < contents.length && i < inventory.getSize(); i++) {

            if (!ShulkerUtil.isEmpty(contents[i])) {
                inventory.setItem(i, contents[i]);
            }

        }

    }

    /**
     * Делает снимок инвентаря: массив из 27 независимых копий предметов.
     * Используется при сохранении, чтобы не читать инвентарь повторно.
     *
     * @param inventory исходный инвентарь
     * @return снимок содержимого
     */
    public ItemStack @NotNull [] snapshot(@NotNull Inventory inventory) {

        ItemStack[] snapshot = new ItemStack[ShulkerUtil.SHULKER_SIZE];

        for (int i = 0; i < snapshot.length; i++) {

            ItemStack item = inventory.getItem(i);

            if (!ShulkerUtil.isEmpty(item)) {
                snapshot[i] = item.clone();
            }

        }

        return snapshot;

    }
}
