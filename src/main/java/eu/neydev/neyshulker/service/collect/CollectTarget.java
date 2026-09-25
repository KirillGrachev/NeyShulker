package eu.neydev.neyshulker.service.collect;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Цель автосбора: то, куда складываются подобранные предметы.
 * Реализации скрывают разницу между открытым GUI и боксом в инвентаре.
 */
public interface CollectTarget {

    /**
     * Предмет шалкер-бокса этой цели (для событий и диагностики).
     */
    @NotNull ItemStack shulkerItem();

    /**
     * Вставляет предметы в цель.
     *
     * @param item вставляемый стек (не изменяется вызовом)
     * @return количество вставленных предметов
     */
    int insert(@NotNull ItemStack item);

}
