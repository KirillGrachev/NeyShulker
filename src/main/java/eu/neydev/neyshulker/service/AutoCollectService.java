package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.event.ShulkerAutoCollectEvent;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.collect.CollectEntry;
import eu.neydev.neyshulker.service.collect.CollectRules;
import eu.neydev.neyshulker.service.collect.CollectScan;
import eu.neydev.neyshulker.service.collect.CollectTarget;
import eu.neydev.neyshulker.service.collect.NearbyItemsFinder;
import eu.neydev.neyshulker.service.collect.PlayerDropTracker;
import eu.neydev.neyshulker.service.collect.PlayerWaitList;
import eu.neydev.neyshulker.task.RepeatingTask;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Оркестратор автосбора: жизненный цикл задачи, per-player тумблер
 * и волновая обработка игроков.
 *
 * Нагрузка на сервер постоянна и не зависит от онлайна: детекция обрабатывает
 * за волну только ломтик игроков (ротация по кругу), обнаруженные источники
 * попадают в лист ожидания игрока, а погружение дозируется глобальным
 * бюджетом действий на волну. Тяжелые операции (построение приемников,
 * вставка, запись меты) выполняются только на фазе слива и только для
 * игроков с непустыми листами ожидания.
 *
 * Механика вынесена в пакет service.collect: CollectRules (политики допуска),
 * NearbyItemsFinder (поиск дропов), CollectScan (рабочее содержимое боксов
 * и цели), CollectTarget (приемники), CollectEntry и PlayerWaitList
 * (лист ожидания). Сам оркестратор остается тонким.
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
    private final PlayerDropTracker dropTracker;
    private final NearbyItemsFinder nearbyItemsFinder;
    private final RepeatingTask collectTask;

    private final Set<UUID> disabledPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, PlayerWaitList> waitLists = new ConcurrentHashMap<>();
    private final Map<UUID, Long> fullNotices = new ConcurrentHashMap<>();

    private int rotationIndex;

    public AutoCollectService(@NotNull NeyShulker plugin,
                              @NotNull ConfigManager configManager,
                              @NotNull SessionRegistry sessionRegistry,
                              @NotNull InventoryTransferService transferService,
                              @NotNull ShulkerPersistenceService persistenceService,
                              @NotNull MessageService messageService,
                              @NotNull SoundService soundService,
                              @NotNull PermissionService permissionService,
                              @NotNull PlayerDropTracker dropTracker) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.sessionRegistry = sessionRegistry;
        this.transferService = transferService;
        this.persistenceService = persistenceService;
        this.messageService = messageService;
        this.soundService = soundService;
        this.dropTracker = dropTracker;

        this.rules = new CollectRules(configManager, permissionService, dropTracker);
        this.nearbyItemsFinder = new NearbyItemsFinder(configManager);
        this.collectTask = new RepeatingTask(plugin, "auto-collect", this::wave);

        configManager.onReload(this::restart);

    }

    /**
     * Запускает автосбор, если он включен в конфигурации.
     */
    public void start() {

        if (!configManager.isPluginEnabled() || !configManager.isAutoCollectEnabled()) {
            plugin.getLogger().info("Auto-collect is disabled in the configuration.");
            return;
        }

        collectTask.start(20L, configManager.getWavePeriod());

        plugin.getLogger().info("Auto-collect started (waves every "
                + configManager.getWavePeriod() + " ticks, "
                + configManager.getPlayersPerWave() + " players per wave).");

    }

    /**
     * Останавливает автосбор и очищает листы ожидания.
     */
    public void stop() {

        collectTask.stop();
        waitLists.clear();
        fullNotices.clear();
        dropTracker.clear();

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
        waitLists.remove(playerId);

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
        waitLists.remove(player.getUniqueId());
        fullNotices.remove(player.getUniqueId());

    }

    public boolean isRunning() {
        return collectTask.isRunning();
    }

    /**
     * Одна волна: детекция ломтика игроков, затем дозированный слив
     * накопленных листов ожидания.
     *
     * Вызывается задачей по расписанию; публична для проверяемых тестов.
     */
    public void wave() {

        dropTracker.purge();

        detectWave();
        drainWave();

    }

    /**
     * Детекция ломтика игроков текущей волны.
     *
     * Ротация по кругу: каждый игрок попадает в детекцию раз в
     * ceil(online / players_per_wave) волн, а стоимость волны не растет
     * вместе с онлайном.
     */
    private void detectWave() {

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());

        if (online.isEmpty()) {
            return;
        }

        int total = online.size();
        int count = Math.min(configManager.getPlayersPerWave(), total);

        for (int i = 0; i < count; i++) {
            detectPlayer(online.get((rotationIndex + i) % total));
        }

        rotationIndex = (rotationIndex + count) % total;

    }

    /**
     * Обнаруживает источники игрока и складывает их в лист ожидания.
     *
     * Легкая фаза: без построения приемников, без вставок и без записи меты,
     * только ворота допуска, поиск дропов в радиусе и типы в инвентаре.
     *
     * @param player игрок
     */
    private void detectPlayer(@Nullable Player player) {

        if (!rules.canCollectPlayer(player, player != null && isEnabledFor(player))) {
            return;
        }

        if (rules.failsInventoryGate(player)) {
            return;
        }

        // Предмет на курсоре - игрок активно работает с инвентарем, не мешаем
        if (!ShulkerUtil.isEmpty(player.getItemOnCursor())) {
            return;
        }

        // Нет ни одного бокса-приемника - нечего и сканировать сущности мира
        if (!hasReceptacles(player)) {
            return;
        }

        PlayerWaitList waitList = waitLists.computeIfAbsent(player.getUniqueId(),
                key -> new PlayerWaitList(configManager.getQueuePerPlayer()));

        for (Item item : nearbyItemsFinder.find(player)) {

            if (rules.isContested(player, item)) {
                continue;
            }

            if (!waitList.offer(CollectEntry.ground(item))) {
                break;
            }

        }

        PlayerInventory inventory = player.getInventory();

        for (int slot = 0; slot < ShulkerUtil.PLAYER_STORAGE_SLOTS; slot++) {

            ItemStack stack = inventory.getItem(slot);

            if (ShulkerUtil.isEmpty(stack) || rules.isExcluded(player, stack)) {
                continue;
            }

            if (!waitList.offer(CollectEntry.inventory(slot, stack.getType()))) {
                break;
            }

        }

    }

    /**
     * Дешевая проверка приемников: открытая сессия или шалкер-боксы
     * в содержимом инвентаря (включая вторую руку). Зеркалит то, что
     * позже построит CollectScan, но без чтения содержимого боксов.
     */
    private boolean hasReceptacles(@NotNull Player player) {

        if (sessionRegistry.getSession(player) != null) {
            return true;
        }

        PlayerInventory inventory = player.getInventory();

        if (ShulkerUtil.isShulkerBox(inventory.getItem(ShulkerUtil.OFF_HAND_SLOT))) {
            return true;
        }

        for (ItemStack stack : inventory.getContents()) {
            if (ShulkerUtil.isShulkerBox(stack)) {
                return true;
            }
        }

        return false;

    }

    /**
     * Сливает листы ожидания в рамках глобального бюджета действий волны.
     */
    private void drainWave() {

        int budget = configManager.getActionsPerWave();

        for (UUID playerId : List.copyOf(waitLists.keySet())) {

            if (budget <= 0) {
                return;
            }

            Player player = Bukkit.getPlayer(playerId);

            if (player == null) {

                waitLists.remove(playerId);
                fullNotices.remove(playerId);
                continue;

            }

            budget -= drainPlayer(player, budget);

        }

    }

    /**
     * Погружает элементы листа ожидания игрока, не превышая бюджет действий.
     *
     * @param player игрок
     * @param budget доступное число погружений
     * @return сколько предметов перемещено
     */
    private int drainPlayer(@NotNull Player player, int budget) {

        PlayerWaitList waitList = waitLists.get(player.getUniqueId());

        if (waitList == null || waitList.isEmpty() || budget <= 0) {
            return 0;
        }

        if (!rules.canCollectPlayer(player, isEnabledFor(player))) {
            return 0;
        }

        if (rules.failsInventoryGate(player)) {
            return 0;
        }

        if (!ShulkerUtil.isEmpty(player.getItemOnCursor())) {
            return 0;
        }

        ShulkerSession session = sessionRegistry.getSession(player);
        CollectScan scan = new CollectScan(player, session, configManager,
                transferService, persistenceService);

        if (!scan.hasTargets()) {
            waitLists.remove(player.getUniqueId());
            return 0;
        }

        PlayerInventory inventory = player.getInventory();
        int collected = 0;
        boolean noSpace = false;

        while (collected < budget && !waitList.isEmpty()) {

            CollectEntry entry = waitList.poll();

            if (entry == null) {
                break;
            }

            DrainStep step = entry.isGround()
                    ? drainGroundEntry(player, scan, waitList, entry)
                    : drainInventoryEntry(player, inventory, scan, entry);

            collected += step.moved();
            noSpace = noSpace || step.noSpace();

            // Головной элемент вернули в лист (pickup delay) - ждем следующую волну
            if (step.blocked()) {
                break;
            }

        }

        if (collected > 0) {
            scan.flush();
            soundService.playCollect(player);
        }

        notify(player, collected, noSpace);

        if (waitList.isEmpty()) {
            waitLists.remove(player.getUniqueId());
        }

        return collected;

    }

    /**
     * Погружает один дроп с земли.
     *
     * Сущность могла исчезнуть или измениться между детекцией и сливом,
     * поэтому состояние перечитывается, а предмет удаляется из мира
     * только после успешной вставки.
     */
    private DrainStep drainGroundEntry(@NotNull Player player,
                                       @NotNull CollectScan scan,
                                       @NotNull PlayerWaitList waitList,
                                       @NotNull CollectEntry entry) {

        Item item = entry.item();

        if (item == null || item.isDead() || !item.isValid()) {
            return DrainStep.none();
        }

        if (!configManager.isAutoCollectIgnorePickupDelay() && item.getPickupDelay() > 0) {
            waitList.offerFirst(entry);
            return DrainStep.delayed();
        }

        // Спорный дроп (брошен игроком или у него стоит другой игрок) не всасываем
        if (rules.isContested(player, item)) {
            return DrainStep.none();
        }

        ItemStack stack = item.getItemStack();

        if (ShulkerUtil.isEmpty(stack) || rules.isExcluded(player, stack)) {
            return DrainStep.none();
        }

        CollectTarget target = scan.targetFor(stack);

        if (target == null) {

            // Отказ по гейту режима (MATCHING не знает тип) остается тихим,
            // как любой другой игнор; кончившееся место - повод для сообщения
            // о полном боксе. Слив продолжается: другой тип может смерджиться
            // в свой стек, поэтому обрывать его нельзя
            return scan.hasSpaceFor(stack) ? DrainStep.none() : DrainStep.full();
        }

        if (!callCollectEvent(player, item, target)) {
            return DrainStep.none();
        }

        int inserted = target.insert(stack.clone());

        if (inserted <= 0) {
            return DrainStep.full();
        }

        applyRemainder(item, stack, inserted);
        return DrainStep.moved(inserted);

    }

    /**
     * Погружает один предмет из области хранения инвентаря.
     *
     * Слот между детекцией и сливом мог измениться, поэтому элемент
     * дополнительно сверяется с текущим состоянием слота.
     */
    private DrainStep drainInventoryEntry(@NotNull Player player,
                                          @NotNull PlayerInventory inventory,
                                          @NotNull CollectScan scan,
                                          @NotNull CollectEntry entry) {

        ItemStack stack = inventory.getItem(entry.slot());

        if (inventoryEntryStale(player, stack, entry)) {
            return DrainStep.none();
        }

        CollectTarget target = scan.targetFor(stack);

        if (target == null) {
            return DrainStep.none();
        }

        int moved = target.insert(stack.clone());

        if (moved <= 0) {
            return DrainStep.none();
        }

        if (moved >= stack.getAmount()) {
            inventory.setItem(entry.slot(), null);
        } else {

            ItemStack reduced = stack.clone();
            reduced.setAmount(stack.getAmount() - moved);
            inventory.setItem(entry.slot(), reduced);

        }

        return DrainStep.moved(moved);

    }

    /**
     * Покинул ли предмет элемента свое место в инвентаре: слот пуст,
     * тип предмета не совпадает с записанным при детекции или предмет
     * стал исключенным (блэклист, шалкер-бокс).
     *
     * Общий критерий для фазы слива и для немедленной синхронизации
     * листа ожидания по событиям инвентаря.
     */
    private boolean inventoryEntryStale(@NotNull Player player,
                                        @Nullable ItemStack stack,
                                        @NotNull CollectEntry entry) {
        return ShulkerUtil.isEmpty(stack)
                || stack.getType() != entry.material()
                || rules.isExcluded(player, stack);
    }

    /**
     * Немедленно сверяет лист ожидания с текущим инвентарем игрока.
     *
     * Вызывается слушателем WaitListSyncListener, когда инвентарь изменился
     * (клик, drag, выброс, установка блока, поедание, поломка, обмен рук):
     * элемент, чей предмет покинул свое место, убирается из очереди сразу
     * и не может быть перенесен случайно между волнами. Элементы с земли
     * не трогаются - состояние дропа перечитывается на фазе слива.
     *
     * @param player игрок
     */
    public void syncWaitList(@NotNull Player player) {

        PlayerWaitList waitList = waitLists.get(player.getUniqueId());

        if (waitList == null) {
            return;
        }

        PlayerInventory inventory = player.getInventory();

        waitList.removeIf(entry -> !entry.isGround()
                && inventoryEntryStale(player, inventory.getItem(entry.slot()), entry));

        if (waitList.isEmpty()) {
            waitLists.remove(player.getUniqueId());
        }

    }

    /**
     * Полностью очищает лист ожидания игрока.
     *
     * Точка входа для событий, после которых очередь целиком теряет смысл:
     * смерть (инвентарь выпал на землю и будет заново обнаружен как дроп).
     *
     * @param player игрок
     */
    public void clearWaitList(@NotNull Player player) {
        waitLists.remove(player.getUniqueId());
    }

    /**
     * Размер листа ожидания игрока.
     *
     * Публичен для проверяемых тестов: очередь - техническая деталь волн,
     * игроку она не показывается и в чат не выводится.
     *
     * @param player игрок
     * @return число элементов в очереди переноса
     */
    public int waitListSize(@NotNull Player player) {

        PlayerWaitList waitList = waitLists.get(player.getUniqueId());
        return waitList == null ? 0 : waitList.size();

    }

    /**
     * Шаг слива одного элемента листа ожидания.
     *
     * @param moved   сколько предметов перемещено
     * @param noSpace физически нет места ни в одном приемнике скана
     * @param blocked элемент вернули в голову листа, слив игрока отложен
     */
    private record DrainStep(int moved, boolean noSpace, boolean blocked) {

        static DrainStep none() {
            return new DrainStep(0, false, false);
        }

        static DrainStep full() {
            return new DrainStep(0, true, false);
        }

        static DrainStep delayed() {
            return new DrainStep(0, false, true);
        }

        static DrainStep moved(int moved) {
            return new DrainStep(moved, false, false);
        }

    }

    /**
     * Полный немедленный проход для одного игрока: детекция всех источников
     * и слив без ограничения бюджета. Точка входа для тестов и разовых
     * вызовов; в бою работа идет волнами через {@link #wave()}.
     *
     * @param player игрок
     */
    public void collectAround(@Nullable Player player) {

        detectPlayer(player);

        if (player != null) {
            drainPlayer(player, Integer.MAX_VALUE);
        }

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

        // Выключенное сообщение дает пустой список строк: MessageService сам
        // промолчит, отдельный тумблер автосбору больше не нужен
        if (collected > 0) {

            messageService.send(player, MessageKey.AUTO_COLLECT,
                    Map.of("amount", String.valueOf(collected)));
            return;

        }

        if (!noSpace) {
            return;
        }

        // Кулдаун на игрока: полный бокс под массовым дропом (взрыв крипера)
        // остается одной строкой в чате, а не сообщением на каждую волну
        long now = System.currentTimeMillis();
        long cooldownMillis = configManager.getFullMessageCooldown() * 1000L;
        Long lastNotice = fullNotices.get(player.getUniqueId());

        if (lastNotice != null && now - lastNotice < cooldownMillis) {
            return;
        }

        fullNotices.put(player.getUniqueId(), now);
        messageService.send(player, MessageKey.AUTO_COLLECT_FULL, Map.of());

    }

}
