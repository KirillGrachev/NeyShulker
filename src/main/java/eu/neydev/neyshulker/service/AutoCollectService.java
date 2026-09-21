package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.InventoryCollectMode;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.event.ShulkerAutoCollectEvent;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.collect.CollectRules;
import eu.neydev.neyshulker.service.collect.CollectScan;
import eu.neydev.neyshulker.service.collect.CollectTarget;
import eu.neydev.neyshulker.service.collect.NearbyItemsFinder;
import eu.neydev.neyshulker.task.RepeatingTask;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оркестратор автосбора: жизненный цикл задачи, per-player тумблер
 * и композиция шагов скана.
 *
 * Механика вынесена в пакет service.collect: CollectRules (политики допуска),
 * NearbyItemsFinder (поиск дропов), CollectScan (рабочее содержимое боксов
 * и цели), CollectTarget (приемники). Сам оркестратор остается тонким.
 */
public class AutoCollectService {

    private final NeyShulker plugin;
    private final ConfigManager configManager;
    private final SessionRegistry sessionRegistry;
    private final InventoryTransferService transferService;
    private final ShulkerPersistenceService persistenceService;
    private final MessageService messageService;
    private final SoundService soundService;

    private final CollectRules rules;
    private final NearbyItemsFinder nearbyItemsFinder;
    private final RepeatingTask collectTask;

    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();

    public AutoCollectService(@NotNull NeyShulker plugin,
                              @NotNull ConfigManager configManager,
                              @NotNull SessionRegistry sessionRegistry,
                              @NotNull InventoryTransferService transferService,
                              @NotNull ShulkerPersistenceService persistenceService,
                              @NotNull MessageService messageService,
                              @NotNull SoundService soundService,
                              @NotNull PermissionService permissionService) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.sessionRegistry = sessionRegistry;
        this.transferService = transferService;
        this.persistenceService = persistenceService;
        this.messageService = messageService;
        this.soundService = soundService;

        this.rules = new CollectRules(configManager, permissionService);
        this.nearbyItemsFinder = new NearbyItemsFinder(configManager);
        this.collectTask = new RepeatingTask(plugin, "auto-collect", this::tick);

        configManager.onReload(this::restart);

    }

    /**
     * Запускает автосбор, если он включен в конфигурации.
     */
    public void start() {

        if (!configManager.isPluginEnabled() || !configManager.isAutoCollectEnabled()) {
            plugin.getLogger().info("Автосбор отключен в конфигурации.");
            return;
        }

        collectTask.start(20L, configManager.getAutoCollectInterval());

        plugin.getLogger().info("Автосбор запущен (интервал: "
                + configManager.getAutoCollectInterval() + " тиков).");

    }

    /**
     * Останавливает автосбор.
     */
    public void stop() {
        collectTask.stop();
    }

    /**
     * Перезапускает задачу после изменения конфигурации.
     */
    public void restart() {

        stop();
        start();

    }

    /**
     * Переключает автосбор для конкретного игрока.
     *
     * @param player игрок
     * @return true если автосбор теперь включен
     */
    public boolean toggle(@NotNull Player player) {

        UUID playerId = player.getUniqueId();

        if (disabledPlayers.remove(playerId)) {
            return true;
        }

        disabledPlayers.add(playerId);

        return false;

    }

    public boolean isEnabledFor(@NotNull Player player) {
        return !disabledPlayers.contains(player.getUniqueId());
    }

    public void forget(@NotNull Player player) {
        disabledPlayers.remove(player.getUniqueId());
    }

    public boolean isRunning() {
        return collectTask.isRunning();
    }

    // --- Скан ---

    private void tick() {

        for (Player player : Bukkit.getOnlinePlayers()) {
            collectAround(player);
        }

    }

    /**
     * Обрабатывает одного игрока за один проход задачи.
     *
     * @param player игрок
     */
    public void collectAround(@Nullable Player player) {

        if (!rules.canCollectPlayer(player, player != null && isEnabledFor(player))) {
            return;
        }

        if (!rules.passesInventoryGate(player)) {
            return;
        }

        // Предмет на курсоре - игрок активно работает с инвентарем, не мешаем
        if (!ShulkerUtil.isEmpty(player.getItemOnCursor())) {
            return;
        }

        ShulkerSession session = sessionRegistry.getSession(player);
        CollectScan scan = new CollectScan(player, session, configManager,
                transferService, persistenceService);

        // Нет ни одного бокса-приемника - нечего и сканировать сущности мира
        if (!scan.hasTargets()) {
            return;
        }

        List<Item> candidates = nearbyItemsFinder.find(player);
        ScanOutcome outcome = collectCandidates(player, scan, candidates);

        // Дроп, который ванильный магнит уже донес до инвентаря, досортировывается
        // в боксы следом - в зависимости от режима источника "инвентарь"
        int fromInventory = collectFromInventory(player, scan,
                limitLeft(outcome, configManager.getAutoCollectMaxItemsPerTick()));

        scan.flush();

        int collected = outcome.collected() + fromInventory;

        if (collected > 0) {
            soundService.playCollect(player);
        }

        notify(player, collected, outcome.noSpace());

    }

    private int limitLeft(@NotNull ScanOutcome outcome, int limit) {
        return Math.max(0, limit - outcome.collected());
    }

    /**
     * Досортировка предметов из области хранения инвентаря в приемники скана.
     *
     * @param player игрок
     * @param scan   состояние скана
     * @param budget остаток лимита предметов за скан
     * @return сколько предметов перемещено
     */
    private int collectFromInventory(@NotNull Player player,
                                     @NotNull CollectScan scan,
                                     int budget) {

        InventoryCollectMode mode = configManager.getAutoCollectInventoryMode();

        if (mode == InventoryCollectMode.OFF || budget <= 0) {
            return 0;
        }

        boolean mergeOnly = mode == InventoryCollectMode.MATCHING;
        PlayerInventory inventory = player.getInventory();
        int collected = 0;

        for (int slot = 0; slot < ShulkerUtil.PLAYER_STORAGE_SLOTS && collected < budget; slot++) {

            ItemStack stack = inventory.getItem(slot);

            if (ShulkerUtil.isEmpty(stack) || !rules.isCollectable(player, stack)) {
                continue;
            }

            CollectTarget target = scan.targetFor(stack, mergeOnly);

            if (target == null) {
                continue;
            }

            int moved = target.insert(stack.clone());

            if (moved <= 0) {
                continue;
            }

            if (moved >= stack.getAmount()) {
                inventory.setItem(slot, null);
            } else {

                ItemStack reduced = stack.clone();
                reduced.setAmount(stack.getAmount() - moved);
                inventory.setItem(slot, reduced);

            }

            collected += moved;

        }

        return collected;

    }

    /**
     * Итог прохода по кандидатам: сколько подобрано и уперлись ли в отсутствие места.
     */
    private record ScanOutcome(int collected, boolean noSpace) {
    }

    private ScanOutcome collectCandidates(@NotNull Player player,
                                          @NotNull CollectScan scan,
                                          @NotNull List<Item> candidates) {

        int limit = configManager.getAutoCollectMaxItemsPerTick();
        int collected = 0;
        boolean noSpace = false;

        for (Item item : candidates) {

            if (collected >= limit) {
                break;
            }

            ItemStack stack = item.getItemStack();

            if (ShulkerUtil.isEmpty(stack) || !rules.isCollectable(player, stack)) {
                continue;
            }

            CollectTarget target = scan.targetFor(stack);

            if (target == null) {

                // Для этого типа места нет - но другой тип может смерджиться
                // в свой стек, поэтому скан продолжается, а не обрывается
                noSpace = true;
                continue;

            }

            if (!callCollectEvent(player, item, target)) {
                continue;
            }

            int inserted = target.insert(stack.clone());

            if (inserted <= 0) {
                continue;
            }

            applyRemainder(item, stack, inserted);
            collected += inserted;

        }

        return new ScanOutcome(collected, noSpace);

    }

    /**
     * Убирает дроп из мира полностью или оставляет уменьшенный остаток.
     * Предмет удаляется только после успешной вставки в бокс.
     */
    private void applyRemainder(@NotNull Item item, @NotNull ItemStack stack, int inserted) {

        int rest = stack.getAmount() - inserted;

        if (rest <= 0) {
            item.remove();
            return;
        }

        ItemStack reduced = stack.clone();
        reduced.setAmount(rest);
        item.setItemStack(reduced);

    }

    private boolean callCollectEvent(@NotNull Player player,
                                     @NotNull Item item,
                                     @NotNull CollectTarget target) {

        ShulkerAutoCollectEvent event = new ShulkerAutoCollectEvent(player, item, target.shulkerItem());

        Bukkit.getPluginManager().callEvent(event);

        return !event.isCancelled();

    }

    private void notify(@NotNull Player player, int collected, boolean noSpace) {

        if (!configManager.areAutoCollectMessagesEnabled()) {
            return;
        }

        if (collected > 0) {
            messageService.send(player, MessageKey.AUTO_COLLECT,
                    Map.of("amount", String.valueOf(collected)));
            return;
        }

        if (noSpace) {
            messageService.send(player, MessageKey.AUTO_COLLECT_FULL, Map.of());
        }

    }
}
