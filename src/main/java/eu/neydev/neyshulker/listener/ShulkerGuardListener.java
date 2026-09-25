package eu.neydev.neyshulker.listener;

import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.inventory.NeyShulkerViewer;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.util.SessionTagger;
import eu.neydev.neyshulker.util.ShulkerUtil;
import eu.neydev.neyshulker.util.ViewSlotUtil;
import org.bukkit.Material;
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

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Слушатель-охранник открытого шалкер-бокса.
 *
 * Закрывает все способы вытащить или переместить сам бокс, пока GUI открыт,
 * а также не пускает внутрь запрещенные предметы (черный список, вложенные шалкеры).
 * Именно отсутствие этих проверок приводило к дюпу при быстром перебирании предметов.
 * Содержимое бокса и посторонние предметы выбрасываются из GUI свободно:
 * охранник трогает только сам открытый бокс и ничего лишнего.
 *
 * Быстрый гейт: обработчики кликов и drag-ов сначала сверяют holder инвентаря
 * с маркером {@link NeyShulkerViewer} (O(1)), поэтому чужие инвентари сервера
 * не платят ни за поиск в реестре, ни за валидацию.
 */
public class ShulkerGuardListener implements Listener {

    /**
     * Окно жизни метки пропущенного клика: ванильный PlayerDropItemEvent
     * приходит следом за кликом на том же тике, большего окна не нужно.
     */
    private static final long DROP_MARKER_WINDOW_MILLIS = 250L;

    /**
     * Метка пропущенного клика с отпечатком ожидаемого дропа: тип и потолок
     * количества. Маркер без отпечатка разрешал бы ЛЮБОЙ дроп в окне 250 мс,
     * включая выброшенный чужим путем открытый бокс; с отпечатком совпасть
     * может только предмет того же типа, что и пропущенный клик.
     *
     * Открытый бокс совпасть не может в принципе: вложенные шалкеры в GUI
     * не пускает canEnterShulker, а на самом боксе стоит метка сессии -
     * ее проверка выполняется до маркера и блокирует выброс безусловно.
     */
    private record DropMarker(long markedAt, @NotNull Material type, int maxAmount) {
    }

    private final Map<UUID, DropMarker> allowedDrops = new ConcurrentHashMap<>();

    private final SessionRegistry sessionRegistry;
    private final ShulkerValidationService validationService;
    private final MessageService messageService;

    public ShulkerGuardListener(@NotNull SessionRegistry sessionRegistry,
                                @NotNull ShulkerValidationService validationService,
                                @NotNull MessageService messageService) {

        this.sessionRegistry = sessionRegistry;
        this.validationService = validationService;
        this.messageService = messageService;

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {

        if (!isPluginGui(event.getInventory().getHolder())) {
            return;
        }

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
            return;
        }

        rememberAllowedDrop(player, event);

    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {

        if (!isPluginGui(event.getInventory().getHolder())) {
            return;
        }

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
     * Выброс предмета при живом GUI почти всегда легален: содержимое бокса
     * и посторонние предметы выбрасываются из GUI свободно, а сам бокс
     * заблокирован еще на уровне клика. Порядок проверок:
     *
     * 1. метка сессии на выброшенном предмете - безусловная блокировка:
     *    открытый бокс не отпускается ни при каких маркерах;
     * 2. маркер пропущенного клика с совпадающим отпечатком - легальный
     *    ванильный выброс из GUI или нижнего инвентаря;
     * 3. legacy-сверка немеченой сессии со стеком слота (страховка).
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {

        Player player = event.getPlayer();

        ShulkerSession session = sessionRegistry.getSession(player.getUniqueId());

        if (session == null) {
            return;
        }

        ItemStack dropped = event.getItemDrop().getItemStack();

        if (SessionTagger.isSession(dropped, session.sessionId())) {
            event.setCancelled(true);
            messageService.send(player, MessageKey.DROP_BLOCKED);
            return;
        }

        if (consumeAllowedDrop(player, dropped)) {
            return;
        }

        if (validationService.isDroppedOpenShulker(player, dropped)) {
            event.setCancelled(true);
            messageService.send(player, MessageKey.DROP_BLOCKED);
        }

    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerSwapHands(@NotNull PlayerSwapHandItemsEvent event) {

        Player player = event.getPlayer();

        boolean touchesShulker = validationService.isSwapTouchingOpenShulker(player)
                || validationService.isOpenShulker(player, event.getOffHandItem())
                || validationService.isOpenShulker(player, event.getMainHandItem());

        if (touchesShulker) {
            event.setCancelled(true);
            messageService.send(player, MessageKey.SWAP_BLOCKED);
        }

    }

    private boolean isPluginGui(@Nullable org.bukkit.inventory.InventoryHolder holder) {
        return holder instanceof NeyShulkerViewer;
    }

    /**
     * Запоминает ванильный выброс, который guard пропустил: Q, Ctrl+Q по слоту
     * или клик мимо окна с предметом на курсоре. Маркер несет отпечаток
     * ожидаемого дропа (тип и количество), поэтому следующий за кликом
     * PlayerDropItemEvent сверяется не только по времени.
     */
    private void rememberAllowedDrop(@NotNull Player player, @NotNull InventoryClickEvent event) {

        ClickType click = event.getClick();

        ItemStack source = null;
        int maxAmount = 0;

        if (click == ClickType.DROP || click == ClickType.CONTROL_DROP) {

            source = event.getCurrentItem();
            maxAmount = click == ClickType.DROP ? 1 : amountOf(source);

        } else if (event.getRawSlot() < 0 && !ShulkerUtil.isEmpty(event.getCursor())) {

            source = event.getCursor();
            maxAmount = amountOf(source);

        }

        if (source == null || ShulkerUtil.isEmpty(source) || maxAmount <= 0) {
            return;
        }

        long now = System.currentTimeMillis();

        allowedDrops.put(player.getUniqueId(), new DropMarker(now, source.getType(), maxAmount));
        allowedDrops.values().removeIf(marker -> now - marker.markedAt() > DROP_MARKER_WINDOW_MILLIS);

    }

    /**
     * Снимает метку пропущенного клика: выброс пришел из GUI и легален,
     * если отпечаток дропа совпадает с ожидаемым.
     */
    private boolean consumeAllowedDrop(@NotNull Player player, @Nullable ItemStack dropped) {

        DropMarker marker = allowedDrops.remove(player.getUniqueId());

        if (marker == null || dropped == null) {
            return false;
        }

        if (System.currentTimeMillis() - marker.markedAt() > DROP_MARKER_WINDOW_MILLIS) {
            return false;
        }

        return dropped.getType() == marker.type()
                && dropped.getAmount() <= marker.maxAmount();

    }

    private int amountOf(@Nullable ItemStack item) {
        return item == null ? 0 : item.getAmount();
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

    private boolean isDisallowed(@NotNull Player player, @Nullable ItemStack item) {
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
