package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.FillOrderType;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.service.ShulkerPersistenceService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние одного скана автосбора.
 *
 * Держит рабочее содержимое боксов в памяти: мета читается один раз на бокс
 * за скан, последовательные вставки видят накопленный результат, физическая
 * запись и синхронизация клиента происходят один раз в {@link #flush()}.
 *
 * Выбор цели детерминирован: боксы просматриваются в порядке слотов инвентаря
 * (вторая рука последняя), а между кандидатами внутри яруса решают явные
 * правила {@link #targetFor(ItemStack)}. Один и тот же расклад инвентаря
 * всегда дает один и тот же бокс, от волны к волне и от перезапуска к
 * перезапуску, — поведение можно объяснить игроку и проверить тестом.
 * Открытое GUI приоритетнее боксов для типов, которые оно принимает по гейту
 * режима: в MATCHING незнакомый сессии тип уходит на ярусы боксов, а не
 * отказывает скану целиком.
 */
public final class CollectScan {

    private final Player player;
    private final ConfigManager configManager;
    private final InventoryTransferService transferService;
    private final ShulkerPersistenceService persistenceService;

    private final SessionCollectTarget sessionTarget;
    private final Map<Integer, BoxCollectTarget> boxes = new HashMap<>();
    private final List<Integer> slots = new ArrayList<>();
    private boolean boxesBuilt;

    public CollectScan(@NotNull Player player,
                       @Nullable ShulkerSession session,
                       @NotNull ConfigManager configManager,
                       @NotNull InventoryTransferService transferService,
                       @NotNull ShulkerPersistenceService persistenceService) {

        this.player = player;
        this.configManager = configManager;
        this.transferService = transferService;
        this.persistenceService = persistenceService;

        this.sessionTarget = session == null
                ? null
                : new SessionCollectTarget(player, session, transferService);

        if (session == null) {
            ensureBoxes();
        }

    }

    /**
     * Есть ли вообще приемники: без них скан сущностей мира не запускается.
     */
    public boolean hasTargets() {
        return sessionTarget != null || !boxes.isEmpty();
    }

    /**
     * Подбирает цель под предмет по ярусам предпочтения.
     *
     * Ярус 1 (rules.merge_into_existing): бокс с частичным стеком того же
     * типа - побеждает стек с наибольшим остатком места, дроп вливается
     * без нового слота и без распыления по боксам.
     * Ярус 2: бокс со свободным слотом, уже хранящий тип - побеждает бокс
     * с наибольшим количеством предмета того же типа, так тип стягивается
     * в один бокс, а не размазывается по всем.
     * Ярус 3: любой бокс со свободным слотом (в MATCHING - только если тип
     * уже известен какому-то боксу) - ранжирует стратегия rules.fill_order:
     * BALANCED размазывает нагрузку, COMPACT заполняет боксы по одному,
     * INVENTORY держит порядок слотов инвентаря.
     *
     * При равенстве внутри яруса решает порядок слотов: побеждает меньший слот,
     * поэтому выбор всегда воспроизводим. Открытое GUI приоритетнее боксов для
     * типов, которые оно принимает по гейту режима: в MATCHING незнакомый сессии
     * тип падает на ярусы боксов, а не отказывает скану целиком.
     *
     * @param stack предмет
     * @return цель или null, если допуска или места нет
     */
    public @Nullable CollectTarget targetFor(@NotNull ItemStack stack) {

        if (sessionTarget != null) {

            if (sessionAccepts(stack)) {
                return sessionTarget;
            }

            // Незнакомый сессии тип в MATCHING падает на ярусы боксов
            ensureBoxes();

        }

        if (configManager.isAutoCollectMergeIntoExisting()) {

            BoxCollectTarget mergeTarget = null;
            int bestRoom = 0;

            for (Integer slot : slots) {

                int room = mergeRoom(boxes.get(slot), stack);

                if (room > bestRoom) {
                    mergeTarget = boxes.get(slot);
                    bestRoom = room;
                }

            }

            if (mergeTarget != null) {
                return mergeTarget;
            }

        }

        BoxCollectTarget typeTarget = null;
        int bestAmount = 0;

        for (Integer slot : slots) {

            BoxCollectTarget target = boxes.get(slot);

            if (ShulkerUtil.countFreeSlots(contentsOf(target)) <= 0) {
                continue;
            }

            int amount = amountOf(target, stack);

            if (amount > bestAmount) {
                typeTarget = target;
                bestAmount = amount;
            }

        }

        if (typeTarget != null) {
            return typeTarget;
        }

        boolean typeKnown = configManager.getAutoCollectMode() != CollectMode.MATCHING
                || holdsType(stack);

        if (!typeKnown) {
            return null;
        }

        return freeTarget();

    }

    /**
     * Нижний ярус: бокс со свободным слотом по стратегии fill_order.
     *
     * @return цель или null, если свободных слотов нет ни в одном боксе
     */
    private @Nullable BoxCollectTarget freeTarget() {

        FillOrderType fillOrder = configManager.getAutoCollectFillOrder();
        BoxCollectTarget best = null;
        int bestScore = 0;

        for (Integer slot : slots) {

            BoxCollectTarget target = boxes.get(slot);
            int freeSlots = ShulkerUtil.countFreeSlots(contentsOf(target));

            if (freeSlots <= 0) {
                continue;
            }

            if (fillOrder == FillOrderType.INVENTORY) {
                return target;
            }

            int score = fillOrder == FillOrderType.BALANCED ? freeSlots : -freeSlots;

            if (best == null || score > bestScore) {
                best = target;
                bestScore = score;
            }

        }

        return best;

    }

    /**
     * Принимает ли открытое GUI предмет по гейту режима: в MATCHING сессия
     * работает только с типами, которые уже хранит, а незнакомый тип уходит
     * на ярусы боксов вместо отказа всего скана.
     */
    private boolean sessionAccepts(@NotNull ItemStack stack) {
        return configManager.getAutoCollectMode() != CollectMode.MATCHING
                || sessionHoldsType(stack);
    }

    /**
     * Есть ли физически место под предмет хотя бы в одном приемнике: частичный
     * стек того же типа или свободный слот.
     *
     * Отказ по гейту режима (MATCHING не знает тип) местом не считается: такой
     * дроп собирается молча, как любой другой игнор, и не требует от игрока
     * сообщения о полном боксе.
     *
     * @param stack предмет
     * @return true если приемники скана способны принять предмет
     */
    public boolean hasSpaceFor(@NotNull ItemStack stack) {

        if (sessionTarget != null && sessionAccepts(stack)) {
            return sessionHasSpace(stack);
        }

        if (sessionTarget != null) {
            ensureBoxes();
        }

        for (Integer slot : slots) {

            BoxCollectTarget target = boxes.get(slot);

            if (mergeRoom(target, stack) > 0
                    || ShulkerUtil.countFreeSlots(contentsOf(target)) > 0) {
                return true;
            }

        }

        return false;

    }

    /**
     * Есть ли место в открытом GUI: свободный слот или частичный стек того же типа.
     */
    private boolean sessionHasSpace(@NotNull ItemStack stack) {

        org.bukkit.inventory.Inventory inventory = sessionTarget.getSession().inventory();

        for (int i = 0; i < inventory.getSize(); i++) {

            ItemStack slot = inventory.getItem(i);

            if (ShulkerUtil.isEmpty(slot)) {
                return true;
            }

            if (slot.isSimilar(stack) && slot.getAmount() < slot.getMaxStackSize()) {
                return true;
            }

        }

        return false;

    }

    /**
     * Есть ли в открытом GUI предмет того же типа (MATCHING-гейт источника).
     */
    private boolean sessionHoldsType(@NotNull ItemStack stack) {

        org.bukkit.inventory.Inventory inventory = sessionTarget.getSession().inventory();

        for (int i = 0; i < inventory.getSize(); i++) {

            ItemStack slot = inventory.getItem(i);

            if (slot != null && slot.isSimilar(stack)) {
                return true;
            }

        }

        return false;

    }

    /**
     * Известен ли тип предмета какому-либо приемнику скана.
     */
    private boolean holdsType(@NotNull ItemStack stack) {

        for (BoxCollectTarget target : boxes.values()) {

            if (amountOf(target, stack) > 0) {
                return true;
            }

        }

        return false;

    }

    /**
     * Сколько предметов того же типа уже хранит бокс (сумма по стекам).
     */
    private int amountOf(@NotNull BoxCollectTarget target, @NotNull ItemStack stack) {

        int amount = 0;

        for (ItemStack slot : contentsOf(target)) {

            if (slot != null && slot.isSimilar(stack)) {
                amount += slot.getAmount();
            }

        }

        return amount;

    }

    /**
     * Наибольший остаток места в частичном стеке того же типа.
     *
     * @return место в предметах или 0, если вливать некуда
     */
    private int mergeRoom(@NotNull BoxCollectTarget target, @NotNull ItemStack stack) {

        int room = 0;

        for (ItemStack slot : contentsOf(target)) {

            if (slot == null || !slot.isSimilar(stack) || slot.getAmount() >= slot.getMaxStackSize()) {
                continue;
            }

            room = Math.max(room, slot.getMaxStackSize() - slot.getAmount());

        }

        return room;

    }

    /**
     * Записывает измененные боксы и планирует сохранение сессии; resync клиента - один раз.
     */
    public void flush() {

        if (sessionTarget != null && sessionTarget.isUsed()) {
            persistenceService.scheduleSave(sessionTarget.getSession());
        }

        boolean anyModified = false;

        for (BoxCollectTarget target : boxes.values()) {

            if (target.isModified()) {
                target.flush();
                anyModified = true;
            }

        }

        if (anyModified) {
            player.updateInventory();
        }

    }


    private void buildBoxes() {

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();

        for (int slot = 0; slot < contents.length; slot++) {
            addBox(slot, contents[slot]);
        }

        // Вторая рука - полноценное место для шалкера; слот 40 и так последний
        addBox(ShulkerUtil.OFF_HAND_SLOT, inventory.getItem(ShulkerUtil.OFF_HAND_SLOT));

    }

    /**
     * Строит боксы не раньше, чем они реально понадобятся: пока открытое GUI
     * принимает предметы, чтение меты каждого бокса инвентаря - лишняя работа
     * волны. Точка входа для FALL-THROUGH ветки MATCHING и проверки места.
     */
    private void ensureBoxes() {

        if (boxesBuilt) {
            return;
        }

        boxesBuilt = true;
        buildBoxes();

    }

    private void addBox(int slot, @Nullable ItemStack box) {

        if (!ShulkerUtil.isShulkerBox(box)) {
            return;
        }

        ItemStack[] boxContents = ShulkerUtil.readContents(box);

        if (boxContents == null) {
            return;
        }

        boxes.put(slot, new BoxCollectTarget(player, slot, boxContents, transferService));
        slots.add(slot);

    }

    private ItemStack @NotNull [] contentsOf(@NotNull BoxCollectTarget target) {
        return target.workingContents();
    }
}
