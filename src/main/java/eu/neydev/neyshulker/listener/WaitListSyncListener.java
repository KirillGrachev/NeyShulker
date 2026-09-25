package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.service.AutoCollectService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemBreakEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель синхронизации листа ожидания автосбора.
 *
 * Любое изменение инвентаря игрока немедленно сверяет очередь переноса
 * с реальностью: предмет, покинувший свое место, убирается из листа
 * и не может быть перенесен случайно между волнами. Работает на MONITOR
 * и читает в том числе отмененные события (ignoreCancelled = false,
 * та же политика, что в ShulkerSyncListener): синхронизация идемпотентна
 * и только убирает элементы, поэтому на неизмененном инвентаре сверка
 * просто ничего не находит, зато изменения от плагинов, которые отменяют
 * событие, но правят инвентарь сами (кастомные GUI, антидюп), не теряются.
 * Перепроверка элементов на фазе слива остается страховкой для остальных
 * экзотических путей изменения инвентаря (команды, нестандартные события).
 */
public class WaitListSyncListener implements Listener {

    private final AutoCollectService autoCollectService;

    public WaitListSyncListener(@NotNull NeyShulker plugin) {
        this.autoCollectService = plugin.getServices().getAutoCollectService();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            autoCollectService.syncWaitList(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            autoCollectService.syncWaitList(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onDropItem(@NotNull PlayerDropItemEvent event) {
        autoCollectService.syncWaitList(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onItemConsume(@NotNull PlayerItemConsumeEvent event) {
        autoCollectService.syncWaitList(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        autoCollectService.syncWaitList(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onItemBreak(@NotNull PlayerItemBreakEvent event) {
        autoCollectService.syncWaitList(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSwapHands(@NotNull PlayerSwapHandItemsEvent event) {
        autoCollectService.syncWaitList(event.getPlayer());
    }

    /**
     * Смерть очищает очередь целиком: инвентарь выпал на землю
     * и будет заново обнаружен волнами как обычный дроп.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(@NotNull PlayerDeathEvent event) {
        autoCollectService.clearWaitList(event.getEntity());
    }

}
