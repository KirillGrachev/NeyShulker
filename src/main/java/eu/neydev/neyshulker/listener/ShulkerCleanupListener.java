package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.inventory.ShulkerInventoryHolder;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.service.ShulkerCloseService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель закрытия и выхода: гарантирует, что содержимое всегда
 * возвращается в предмет, даже если игрок вышел из игры с открытым GUI.
 */
public class ShulkerCleanupListener implements Listener {

    private final SessionRegistry sessionRegistry;
    private final ShulkerCloseService closeService;
    private final AutoCollectService autoCollectService;

    public ShulkerCleanupListener(@NotNull NeyShulker plugin) {

        this.sessionRegistry = plugin.getServices().getSessionRegistry();
        this.closeService = plugin.getServices().getCloseService();
        this.autoCollectService = plugin.getServices().getAutoCollectService();

    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(@NotNull InventoryCloseEvent event) {

        if (!(event.getInventory().getHolder() instanceof ShulkerInventoryHolder)) {
            return;
        }

        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        ShulkerSession session = sessionRegistry.getSessionByInventory(event.getInventory());

        closeService.close(player, session);

    }

    /**
     * Содержимое сохраняется как можно раньше: другие плагины
     * могут очистить или изменить инвентарь на более поздних приоритетах.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {

        Player player = event.getPlayer();

        closeService.close(player);
        autoCollectService.forget(player);

    }
}
