package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.FillOrderType;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.service.ShulkerPersistenceService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние одного скана автосбора.
 *
 * Держит рабочее содержимое боксов в памяти: мета читается один раз на бокс
 * за скан, последовательные вставки видят накопленный результат, физическая
 * запись и синхронизация клиента происходят один раз в {@link #flush()}.
 *
 * Выбор цели детерминирован: боксы просматриваются в порядке слотов
 * инвентаря (LinkedHashMap хранит порядок добавления), а между кандидатами
 * внутри яруса решают явные правила {@link #targetFor(ItemStack)}. Один и тот
 * же расклад инвентаря всегда дает один и тот же бокс, от волны к волне и от
 * перезапуска к перезапуску, - поведение можно объяснить игроку и проверить
 * тестом. Открытое GUI приоритетнее боксов для типов, которые оно принимает
 * по гейту режима: в MATCHING незнакомый сессии тип уходит на ярусы боксов,
 * а не отказывает скану целиком.
 */
public final class CollectScan {

    private final Player player;
    private final NeyShulkerConfig config;
    private final InventoryTransferService transferService;
    private final ShulkerPersistenceService persistenceService;

    private final SessionCollectTarget sessionTarget;
    private final Map<Integer, BoxCollectTarget> boxes = new LinkedHashMap<>();
    private boolean boxesBuilt;

    public CollectScan(@NotNull Player player,
                       @Nullable ShulkerSession session,
                       @NotNull NeyShulkerConfig config,
                       @NotNull InventoryTransferService transferService,
                       @NotNull ShulkerPersistenceService persistenceService) {

        this.player = player;
        this.config = config;
        this.transferService = transferService;
        this.persistenceService = persistenceService;

        this.sessionTarget = session == null
                ? null
                : new SessionCollectTarget(session, transferService);

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
     * поэтому выбор всегда воспроизводим. Статистика боксов (остаток места,
     * количество типа, свободные слоты) считается одним проходом на вызов,
     * а не ресканируется на каждом ярусе.
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

        List<BoxStats> stats = statsFor(stack);

        // Ярус 1: частичный стек того же типа с наибольшим остатком места
        if (config.isAutoCollectMergeIntoExisting()) {

            BoxCollectTarget mergeTarget = null;
            int bestRoom = 0;

            for (BoxStats stat : stats) {

                if (stat.mergeRoom() > bestRoom) {
                    mergeTarget = stat.target();
                    bestRoom = stat.mergeRoom();
                }

            }

            if (mergeTarget != null) {
                return mergeTarget;
            }

        }

        // Ярус 2: бокс со свободным слотом, уже хранящий тип
        BoxCollectTarget typeTarget = null;
        int bestAmount = 0;

        for (BoxStats stat : stats) {

            if (stat.freeSlots() <= 0 || stat.amount() <= bestAmount) {
                continue;
            }

            typeTarget = stat.target();
            bestAmount = stat.amount();

        }

        if (typeTarget != null) {
            return typeTarget;
        }

        // Ярус 3: любой бокс со свободным слотом по стратегии fill_order
        boolean typeKnown = config.getAutoCollectMode() != CollectMode.MATCHING
                || stats.stream().anyMatch(stat -> stat.amount() > 0);

        if (!typeKnown) {
            return null;
        }

        return freeTarget(stats);

    }

    /**
     * Нижний ярус: бокс со свободным слотом по стратегии fill_order.
     *
     * @return цель или null, если свободных слотов нет ни в одном боксе
     */
    private @Nullable BoxCollectTarget freeTarget(@NotNull List<BoxStats> stats) {

        FillOrderType fillOrder = config.getAutoCollectFillOrder();
        BoxCollectTarget best = null;
        int bestScore = 0;

        for (BoxStats stat : stats) {

            if (stat.freeSlots() <= 0) {
                continue;
            }

            if (fillOrder == FillOrderType.INVENTORY) {
                return stat.target();
            }

            int score = fillOrder == FillOrderType.BALANCED
                    ? stat.freeSlots()
                    : -stat.freeSlots();

            if (best == null || score > bestScore) {
                best = stat.target();
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
        return config.getAutoCollectMode() != CollectMode.MATCHING
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

        for (BoxStats stat : statsFor(stack)) {
            if (stat.mergeRoom() > 0 || stat.freeSlots() > 0) {
                return true;
            }
        }

        return false;

    }

    /**
     * Есть ли место в открытом GUI: свободный слот или частичный стек того же типа.
     */
    private boolean sessionHasSpace(@NotNull ItemStack stack) {

        Inventory inventory = sessionTarget.getSession().inventory();

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

        Inventory inventory = sessionTarget.getSession().inventory();

        for (int i = 0; i < inventory.getSize(); i++) {

            ItemStack slot = inventory.getItem(i);

            if (slot != null && slot.isSimilar(stack)) {
                return true;
            }

        }

        return false;

    }

    /**
     * Однократный проход по боксам: вся статистика, нужная ярусам выбора
     * цели, считается здесь вместо трех отдельных сканирований содержимого.
     */
    private @NotNull List<BoxStats> statsFor(@NotNull ItemStack stack) {

        List<BoxStats> stats = new ArrayList<>(boxes.size());

        for (BoxCollectTarget target : boxes.values()) {

            ItemStack[] contents = target.workingContents();

            int mergeRoom = 0;
            int amount = 0;
            int freeSlots = 0;

            for (ItemStack slot : contents) {

                if (ShulkerUtil.isEmpty(slot)) {
                    freeSlots++;
                    continue;
                }

                if (slot.isSimilar(stack)) {

                    amount += slot.getAmount();

                    if (slot.getAmount() < slot.getMaxStackSize()) {
                        mergeRoom = Math.max(mergeRoom, slot.getMaxStackSize() - slot.getAmount());
                    }

                }

            }

            stats.add(new BoxStats(target, mergeRoom, amount, freeSlots));

        }

        return stats;

    }

    /**
     * Записывает измененные боксы и планирует сохранение сессии;
     * resync клиента - ровно один на скан (и для боксов, и для GUI).
     */
    public void flush() {

        boolean sessionUsed = sessionTarget != null && sessionTarget.isUsed();

        if (sessionUsed) {
            persistenceService.scheduleSave(sessionTarget.getSession());
        }

        boolean anyModified = sessionUsed;

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

        // getContents() игрока уже включает хранение, броню и вторую руку
        // (41 слот, индекс 40 - оффхенд): отдельного добавления оффхенда
        // не нужно, повторное добавление дублировало бы слот в порядке обхода
        for (int slot = 0; slot < contents.length; slot++) {
            addBox(slot, contents[slot]);
        }

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

    }

    /**
     * Статистика одного бокса для выбора цели.
     *
     * @param target    бокс-приемник
     * @param mergeRoom наибольший остаток места в частичном стеке того же типа
     * @param amount    сколько предметов того же типа уже хранит бокс
     * @param freeSlots число пустых слотов
     */
    private record BoxStats(BoxCollectTarget target, int mergeRoom, int amount, int freeSlots) {
    }

}
