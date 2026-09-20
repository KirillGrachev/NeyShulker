package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.ShulkerOpenService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель взаимодействия: открывает шалкер-бокс из руки игрока.
 *
 * Режим SHIFT (по умолчанию) решает главную проблему оригинала:
 * крадущийся игрок не размещает блок, поэтому шалкер можно спокойно поставить,
 * а открыть - зажав Shift.
 */
public class PlayerInteractListener implements Listener {

    private final NeyShulker plugin;
    private final ConfigManager configManager;
    private final ShulkerValidationService validationService;
    private final ShulkerOpenService openService;
    private final MessageService messageService;

    public PlayerInteractListener(@NotNull NeyShulker plugin) {

        this.plugin = plugin;
        this.configManager = plugin.getServices().getConfigManager();
        this.validationService = plugin.getServices().getValidationService();
        this.openService = plugin.getServices().getOpenService();
        this.messageService = plugin.getServices().getMessageService();

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {

        if (!isOpeningAction(event)) {
            return;
        }

        Player player = event.getPlayer();

        // Предмет на курсоре означает незавершенное действие с инвентарем:
        // открытие GUI в этот момент приводит к рассинхрону окна у клиента
        if (!ShulkerUtil.isEmpty(player.getItemOnCursor())) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();

        ValidationResult result = validationService.canOpen(player, item,
                event.getAction(), event.getClickedBlock());

        // Режим не совпал или предмет не шалкер - отдаем обработку ванили
        if (!result.isAllowed() && result.getMessageKey() == null) {
            return;
        }

        if (!result.isAllowed()) {
            messageService.send(player, result.getMessageKey());
            return;
        }

        event.setCancelled(true);

        // GUI открывается на следующем тике: событие взаимодействия должно
        // полностью завершиться, иначе клиент может получить рассинхрон окна
        int slot = player.getInventory().getHeldItemSlot();
        ItemStack shulker = item.clone();

        Bukkit.getScheduler().runTask(plugin, () -> {

            if (!player.isOnline() || !shulker.equals(player.getInventory().getItem(slot))) {
                return;
            }

            if (!openService.open(player, shulker, slot)) {
                messageService.send(player, MessageKey.OPEN_ERROR);
            }

        });

    }

    private boolean isOpeningAction(@NotNull PlayerInteractEvent event) {

        if (!configManager.isPluginEnabled()) {
            return false;
        }

        if (event.getHand() != EquipmentSlot.HAND) {
            return false;
        }

        return event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK;

    }
}
