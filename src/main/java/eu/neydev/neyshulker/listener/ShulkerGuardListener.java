package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.util.ViewSlotUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Слушатель-охранник открытого шалкер-бокса.
 *
 * Закрывает все способы вытащить или переместить сам бокс, пока GUI открыт,
 * а также не пускает внутрь запрещенные предметы (черный список, вложенные шалкеры).
 * Именно отсутствие этих проверок приводило к дюпу при быстром перебирании предметов.
 */
public class ShulkerGuardListener implements Listener {

    private final SessionRegistry sessionRegistry;
    private final ShulkerValidationService validationService;
    private final MessageService messageService;

    public ShulkerGuardListener(@NotNull NeyShulker plugin) {

        this.sessionRegistry = plugin.getServices().getSessionRegistry();
        this.validationService = plugin.getServices().getValidationService();
        this.messageService = plugin.getServices().getMessageService();

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {

        ShulkerSession session = sessionRegistry.getSessionByInventory(event.getInventory());

        if (session == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // 1. Слот, в котором лежит сам открытый шалкер-бокс, полностью заблокирован
        if (isShulkerSlot(event, session)) {
            cancel(event, player, MessageKey.SELF_REMOVE);
            return;
        }

        boolean clickInShulker = ViewSlotUtil.topSlot(event.getView(), event.getRawSlot())
                != ViewSlotUtil.OUTSIDE;

        // 2. Предмет на курсоре не может попасть внутрь
        if (clickInShulker && isDisallowed(player, event.getCursor())) {
            cancel(event, player, validationService.canEnterShulker(player, event.getCursor()));
            return;
        }

        // 3. Беремый предмет не может быть шалкер-боксом или запрещенным
        if (clickInShulker && isDisallowed(player, event.getCurrentItem())) {
            cancel(event, player, validationService.canEnterShulker(player, event.getCurrentItem()));
        }

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {

        ShulkerSession session = sessionRegistry.getSessionByInventory(event.getInventory());

        if (session == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        // Перетаскивание по слоту с открытым шалкер-боксом запрещено
        for (Integer rawSlot : event.getRawSlots()) {

            if (rawSlot != null && isShulkerSlot(player, rawSlot, session)) {
                cancel(event, player, MessageKey.SELF_REMOVE);
                return;
            }

        }

        if (isDisallowed(player, event.getCursor())) {
            cancel(event, player, validationService.canEnterShulker(player, event.getCursor()));
            return;
        }

        for (ItemStack item : event.getNewItems().values()) {

            if (isDisallowed(player, item)) {
                cancel(event, player, validationService.canEnterShulker(player, item));
                return;
            }

        }

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {

        Player player = event.getPlayer();

        if (validationService.isOpenShulker(player, event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            messageService.send(player, MessageKey.DROP_BLOCKED);
        }

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHands(@NotNull PlayerSwapHandItemsEvent event) {

        Player player = event.getPlayer();

        boolean touchesShulker = validationService.isOpenShulker(player, event.getMainHandItem())
                || validationService.isOpenShulker(player, event.getOffHandItem());

        if (touchesShulker) {
            event.setCancelled(true);
            messageService.send(player, MessageKey.SWAP_BLOCKED);
        }

    }

    /**
     * Проверяет, указывает ли сырой слот клика на сам открытый шалкер-бокс.
     */
    private boolean isShulkerSlot(@NotNull InventoryClickEvent event, @NotNull ShulkerSession session) {

        int bottomSlot = ViewSlotUtil.bottomSlot(event.getView(), event.getRawSlot());

        return validationService.isShulkerSlot(session, bottomSlot);

    }

    private boolean isShulkerSlot(@NotNull Player player, int rawSlot, @NotNull ShulkerSession session) {

        int bottomSlot = ViewSlotUtil.bottomSlot(player.getOpenInventory(), rawSlot);

        return validationService.isShulkerSlot(session, bottomSlot);

    }

    private boolean isDisallowed(@NotNull Player player, ItemStack item) {
        return !validationService.canEnterShulker(player, item).isAllowed();
    }

    private void cancel(@NotNull InventoryClickEvent event,
                        @NotNull Player player,
                        @NotNull ValidationResult result) {

        event.setCancelled(true);

        if (result.getMessageKey() != null) {
            messageService.send(player, result.getMessageKey());
        }

    }

    private void cancel(@NotNull InventoryClickEvent event,
                        @NotNull Player player,
                        @NotNull MessageKey key) {

        event.setCancelled(true);
        messageService.send(player, key);

    }

    private void cancel(@NotNull InventoryDragEvent event,
                        @NotNull Player player,
                        @NotNull MessageKey key) {

        event.setCancelled(true);
        messageService.send(player, key);

    }

    private void cancel(@NotNull InventoryDragEvent event,
                        @NotNull Player player,
                        @NotNull ValidationResult result) {

        event.setCancelled(true);

        if (result.getMessageKey() != null) {
            messageService.send(player, result.getMessageKey());
        }

    }
}
