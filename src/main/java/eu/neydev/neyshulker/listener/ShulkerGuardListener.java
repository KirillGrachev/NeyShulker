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
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

        // 1b. Номер-клавиша меняет местами кликовый слот и слот хотбара:
        //     при наведенном курсоре на GUI источник переноса - hotbarButton,
        //     который не равен rawSlot и без этой проверки уходил бы в обход
        if (event.getClick() == ClickType.NUMBER_KEY
                && validationService.isShulkerSlot(session, event.getHotbarButton())) {
            cancel(event, player, MessageKey.MOVE_BLOCKED);
            return;
        }

        // Shift-клик тянет предмет из нижней клетки в GUI ванильным путем:
        // источник проверяется здесь, иначе шалкер или черный список пролезали
        // в обход кликовых проверок (включая слот второй руки)
        if (event.isShiftClick()) {

            int sourceSlot = ViewSlotUtil.bottomSlot(event.getView(), event.getRawSlot());

            if (sourceSlot != ViewSlotUtil.OUTSIDE) {

                ValidationResult enter =
                        validationService.canEnterShulker(player, event.getCurrentItem());

                if (!enter.isAllowed()) {
                    cancel(event, player, enter);
                    return;
                }

            }

        }

        boolean clickInShulker = ViewSlotUtil.topSlot(event.getView(), event.getRawSlot())
                != ViewSlotUtil.OUTSIDE;

        // 2. Предмет, входящий в GUI, не может быть запрещенным: курсор,
        //    хотбар-слот номер-клавиши или вторая рука при SWAP_OFFHAND
        if (clickInShulker) {

            ItemStack entering = enteringItem(player, event);

            if (isDisallowed(player, entering)) {
                cancel(event, player, validationService.canEnterShulker(player, entering));
                return;
            }

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

    /**
     * Q выбрасывает предмет из выбранного слота хотбара - того самого,
     * в котором лежит открытый бокс. Сравнение по isSimilar здесь неприменимо:
     * после автосохранения мета предмета уже отличается от слепка сессии.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {

        Player player = event.getPlayer();

        if (validationService.isHeldOpenShulker(player)) {
            event.setCancelled(true);
            messageService.send(player, MessageKey.DROP_BLOCKED);
        }

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHands(@NotNull PlayerSwapHandItemsEvent event) {

        Player player = event.getPlayer();

        boolean touchesShulker = validationService.isSwapTouchingOpenShulker(player)
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

    /**
     * Определяет предмет, который ваниль поместит в GUI этим кликом:
     * курсор, источник номер-клавиши или вторая рука. Без этой проверки
     * барьер и прочие черносписочные предметы пролетали в шалкер через
     * NUMBER_KEY и SWAP_OFFHAND, минуя кликовые проверки.
     */
    private @Nullable ItemStack enteringItem(@NotNull Player player,
                                             @NotNull InventoryClickEvent event) {

        return switch (event.getClick()) {

            case NUMBER_KEY -> event.getHotbarButton() >= 0
                    ? player.getInventory().getItem(event.getHotbarButton())
                    : null;

            case SWAP_OFFHAND -> player.getInventory().getItemInOffHand();

            default -> event.getCursor();

        };

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
