package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.service.ShulkerPersistenceService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Состояние одного скана автосбора.
 *
 * Держит рабочее содержимое боксов в памяти: мета читается один раз на бокс
 * за скан, последовательные вставки видят накопленный результат, физическая
 * запись и синхронизация клиента происходят один раз в {@link #flush()}.
 */
public final class CollectScan {

    private final Player player;
    private final ConfigManager configManager;
    private final InventoryTransferService transferService;
    private final ShulkerPersistenceService persistenceService;

    private final SessionCollectTarget sessionTarget;
    private final Map<Integer, BoxCollectTarget> boxes = new HashMap<>();
    private final List<Integer> order = new ArrayList<>();

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
            buildBoxes();
        }

    }

    /**
     * Есть ли вообще приемники: без них скан сущностей мира не запускается.
     */
    public boolean hasTargets() {
        return sessionTarget != null || !boxes.isEmpty();
    }

    /**
     * Подбирает цель под предмет с учетом глобального режима автосбора.
     *
     * Порядок выбора: бокс с частичным стеком того же типа (компактация),
     * затем бокс со свободным слотом, предпочтительно уже хранящий тип.
     * MATCHING гейтует допуск для обоих источников: тип обязан быть уже
     * известным какому-то приемнику; полные стеки типа не блокируют сбор,
     * пока есть свободные слоты.
     *
     * @param stack предмет
     * @return цель или null, если допуска или места нет
     */
    public @Nullable CollectTarget targetFor(@NotNull ItemStack stack) {

        CollectMode mode = configManager.getAutoCollectMode();

        if (sessionTarget != null) {

            if (mode == CollectMode.MATCHING && !sessionHoldsType(stack)) {
                return null;
            }

            return sessionTarget;

        }

        if (configManager.isAutoCollectMergeIntoExisting()) {

            for (Integer slot : order) {

                BoxCollectTarget target = boxes.get(slot);

                if (hasMergeRoom(target, stack)) {
                    return target;
                }

            }

        }

        boolean typeKnown = mode != CollectMode.MATCHING || holdsType(stack);

        if (!typeKnown) {
            return null;
        }

        BoxCollectTarget anyFree = null;

        for (Integer slot : order) {

            BoxCollectTarget target = boxes.get(slot);

            if (ShulkerUtil.countFreeSlots(contentsOf(target)) <= 0) {
                continue;
            }

            if (mode == CollectMode.MATCHING && holdsTypeIn(target, stack)) {
                return target;
            }

            if (anyFree == null) {
                anyFree = target;
            }

        }

        return anyFree;

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

            if (holdsTypeIn(target, stack)) {
                return true;
            }

        }

        return false;

    }

    private boolean holdsTypeIn(@NotNull BoxCollectTarget target, @NotNull ItemStack stack) {

        for (ItemStack slot : contentsOf(target)) {

            if (slot != null && slot.isSimilar(stack)) {
                return true;
            }

        }

        return false;

    }

    /**
     * Записывает измененные боксы и отмечает сессию; resync клиента - один раз.
     */
    public void flush() {

        if (sessionTarget != null && sessionTarget.isUsed()) {
            persistenceService.markAndSchedule(sessionTarget.getSession());
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
        List<int[]> scored = new ArrayList<>();

        for (int slot = 0; slot < contents.length; slot++) {

            if (ShulkerUtil.isShulkerBox(contents[slot])) {
                addBox(scored, slot, contents[slot]);
            }

        }

        // Вторая рука - полноценное место для шалкера
        addBox(scored, ShulkerUtil.OFF_HAND_SLOT,
                inventory.getItem(ShulkerUtil.OFF_HAND_SLOT));

        scored.sort(Comparator.comparingInt((int[] pair) -> pair[1]).reversed());

        for (int[] pair : scored) {
            order.add(pair[0]);
        }

    }

    private void addBox(@NotNull List<int[]> scored, int slot, @Nullable ItemStack box) {

        if (!ShulkerUtil.isShulkerBox(box)) {
            return;
        }

        ItemStack[] boxContents = ShulkerUtil.readContents(box);

        if (boxContents == null) {
            return;
        }

        boxes.put(slot, new BoxCollectTarget(player, slot, boxContents, transferService));
        scored.add(new int[]{slot, score(boxContents)});

    }

    private int score(ItemStack @NotNull [] contents) {
        return ShulkerUtil.countFreeSlots(contents) + (containsPriorityItem(contents) ? 1000 : 0);
    }

    private boolean containsPriorityItem(ItemStack @NotNull [] contents) {

        List<Material> priority = configManager.getAutoCollectPriorityItems();

        if (priority.isEmpty()) {
            return false;
        }

        for (ItemStack item : contents) {

            if (item != null && priority.contains(item.getType())) {
                return true;
            }

        }

        return false;

    }

    private boolean hasMergeRoom(@NotNull BoxCollectTarget target, @NotNull ItemStack stack) {

        for (ItemStack slot : contentsOf(target)) {

            if (slot != null && slot.isSimilar(stack) && slot.getAmount() < slot.getMaxStackSize()) {
                return true;
            }

        }

        return false;

    }

    private ItemStack @NotNull [] contentsOf(@NotNull BoxCollectTarget target) {
        return target.workingContents();
    }
}
