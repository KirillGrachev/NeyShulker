package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.registry.SessionRegistry;
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
 * Режим AIR (по умолчанию) решает главную проблему оригинала: клик по блоку
 * остается ванильным, поэтому шалкер можно поставить, а открыть - по воздуху.
 */
public class PlayerInteractListener implements Listener {

    private final NeyShulker plugin;
    private final NeyShulkerConfig config;
    private final ShulkerValidationService validationService;
    private final ShulkerOpenService openService;
    private final MessageService messageService;
    private final SessionRegistry sessionRegistry;

    public PlayerInteractListener(@NotNull NeyShulker plugin,
                                  @NotNull NeyShulkerConfig config,
                                  @NotNull ShulkerValidationService validationService,
                                  @NotNull ShulkerOpenService openService,
                                  @NotNull MessageService messageService,
                                  @NotNull SessionRegistry sessionRegistry) {

        this.plugin = plugin;
        this.config = config;
        this.validationService = validationService;
        this.openService = openService;
        this.messageService = messageService;
        this.sessionRegistry = sessionRegistry;

    }

    /**
     * Открытие GUI не изменяет мир, поэтому клик читается даже когда интеракт
     * отменен другим плагином (регионы, античит, vanish): ignoreCancelled
     * здесь заставил бы молча проглатывать такие клики, и открытие по воздуху
     * "ломалось" на серверах с защитой.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {

        if (!isOpeningAction(event)) {
            return;
        }

        Player player = event.getPlayer();

        // Живая сессия: второе открытие (вторая рука, повторный клик) молча игнорируем
        if (sessionRegistry.hasSession(player.getUniqueId())) {
            return;
        }

        // Предмет на курсоре означает незавершенное действие с инвентарем:
        // открытие GUI в этот момент приводит к рассинхрону окна у клиента
        if (!ShulkerUtil.isEmpty(player.getItemOnCursor())) {
            return;
        }

        boolean mainHand = event.getHand() == EquipmentSlot.HAND;
        ItemStack item = mainHand
                ? player.getInventory().getItemInMainHand()
                : player.getInventory().getItemInOffHand();
        int slot = mainHand
                ? player.getInventory().getHeldItemSlot()
                : ShulkerUtil.OFF_HAND_SLOT;

        ValidationResult result = validationService.canOpen(player, item, event.getAction());

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
        ItemStack shulker = item.clone();

        Bukkit.getScheduler().runTask(plugin, () -> {

            if (!player.isOnline() || !shulker.equals(player.getInventory().getItem(slot))) {
                return;
            }

            // Интеракт приходит на обе руки и планируется двумя задачами:
            // если сессия уже открыта первой из них, вторая молча выходит
            // без пугающего OPEN_ERROR
            if (sessionRegistry.hasSession(player.getUniqueId())) {
                return;
            }

            if (!openService.open(player, shulker, slot)) {
                messageService.send(player, MessageKey.OPEN_ERROR);
            }

        });

    }

    private boolean isOpeningAction(@NotNull PlayerInteractEvent event) {

        if (!config.isPluginEnabled()) {
            return false;
        }

        // Поддерживаются обе руки: предмет и слот выбираются по event.getHand()
        if (event.getHand() == null) {
            return false;
        }

        return event.getAction() == Action.RIGHT_CLICK_AIR
                || event.getAction() == Action.RIGHT_CLICK_BLOCK;

    }

}
