package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.event.ShulkerAutoCollectEvent;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.task.RepeatingTask;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис автосбора: подбирает предметы вокруг игрока и складывает их в шалкер-бокс.
 *
 * Что исправлено относительно наивной реализации:
 * 1. Шалкер-боксы не засасываются в шалкер-бокс (поведение настраивается, по умолчанию запрещено).
 * 2. Открытая сессия обрабатывается напрямую, без записи в слот под открытым GUI.
 * 3. Предмет сначала копируется в шалкер, содержимое фиксируется, и только потом
 *    предмет удаляется из мира - при любой ошибке ничего не теряется и не дублируется.
 * 4. Учитываются pickup delay, владелец предмета, игровой режим и лимит предметов за тик.
 * 5. Целевой шалкер выбирается по числу свободных слотов и наличию приоритетных предметов
 *    (старая версия сравнивала материал шалкера с материалом руды, то есть не работала вовсе).
 */
public class AutoCollectService {

    private final NeyShulker plugin;
    private final ConfigManager configManager;
    private final SessionRegistry sessionRegistry;
    private final InventoryTransferService transferService;
    private final ShulkerPersistenceService persistenceService;
    private final MessageService messageService;
    private final SoundService soundService;
    private final PermissionService permissionService;

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
        this.permissionService = permissionService;

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

    // --- Логика сбора ---

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

        if (!canCollect(player)) {
            return;
        }

        if (configManager.isAutoCollectOnlyWhenInventoryFull() && !isInventoryFull(player)) {
            return;
        }

        // Предмет на курсоре - игрок активно работает с инвентарем, не мешаем
        if (!ShulkerUtil.isEmpty(player.getItemOnCursor())) {
            return;
        }

        ShulkerSession session = sessionRegistry.getSession(player);
        List<Item> candidates = findNearbyItems(player);

        if (candidates.isEmpty()) {
            return;
        }

        int limit = configManager.getAutoCollectMaxItemsPerTick();
        int collected = 0;
        boolean noSpace = false;

        for (Item item : candidates) {

            if (collected >= limit) {
                break;
            }

            CollectTarget target = resolveTarget(player, session);

            if (target == null) {
                noSpace = true;
                break;
            }

            int inserted = collectItem(player, item, target);

            if (inserted <= 0) {
                continue;
            }

            collected += inserted;
            soundService.playCollect(player);

        }

        notify(player, collected, noSpace);

    }

    private int collectItem(@NotNull Player player,
                            @NotNull Item item,
                            @NotNull CollectTarget target) {

        if (!item.isValid() || item.isDead()) {
            return 0;
        }

        ItemStack stack = item.getItemStack();

        if (ShulkerUtil.isEmpty(stack) || !isCollectable(player, stack)) {
            return 0;
        }

        if (!callCollectEvent(player, item, target)) {
            return 0;
        }

        ItemStack copy = stack.clone();
        int inserted = target.insert(copy);

        if (inserted <= 0) {
            return 0;
        }

        // Содержимое шалкера фиксируется до удаления предмета из мира
        target.flush();

        int rest = copy.getAmount() - inserted;

        if (rest <= 0) {
            item.remove();
        } else {
            copy.setAmount(rest);
            item.setItemStack(copy);
        }

        return inserted;

    }

    private @Nullable CollectTarget resolveTarget(@NotNull Player player, @Nullable ShulkerSession session) {

        if (session != null) {
            return new SessionTarget(session, transferService);
        }

        int slot = findTargetSlot(player);

        if (slot < 0) {
            return null;
        }

        return new ItemTarget(player, slot, transferService);

    }

    /**
     * Ищет слот шалкер-бокса, в который стоит складывать предметы.
     * Сначала проверяются боксы, уже содержащие приоритетные предметы,
     * затем выбирается самый свободный.
     */
    private int findTargetSlot(@NotNull Player player) {

        ItemStack[] contents = player.getInventory().getContents();

        int bestSlot = -1;
        int bestScore = 0;

        for (int i = 0; i < contents.length; i++) {

            ItemStack candidate = contents[i];

            if (!ShulkerUtil.isShulkerBox(candidate)) {
                continue;
            }

            int freeSlots = ShulkerUtil.countFreeSlots(candidate);

            if (freeSlots <= 0) {
                continue;
            }

            int score = freeSlots + (containsPriorityItem(candidate) ? 1000 : 0);

            if (score > bestScore) {
                bestScore = score;
                bestSlot = i;
            }

        }

        return bestSlot;

    }

    private boolean containsPriorityItem(@NotNull ItemStack shulker) {

        List<Material> priority = configManager.getAutoCollectPriorityItems();

        if (priority.isEmpty()) {
            return false;
        }

        ItemStack[] contents = ShulkerUtil.readContents(shulker);

        if (contents == null) {
            return false;
        }

        for (ItemStack item : contents) {

            if (item != null && priority.contains(item.getType())) {
                return true;
            }

        }

        return false;

    }

    private boolean isCollectable(@NotNull Player player, @NotNull ItemStack stack) {

        Material material = stack.getType();

        if (ShulkerUtil.isShulkerBox(stack) && !configManager.isAutoCollectShulkerBoxesEnabled()) {
            return false;
        }

        if (configManager.isAutoCollectBlacklisted(material)) {
            return false;
        }

        if (configManager.isBlacklistEnabled() && configManager.isBlacklisted(material)
                && !permissionService.canBypassBlacklist(player)) {
            return false;
        }

        return true;

    }

    private boolean callCollectEvent(@NotNull Player player,
                                     @NotNull Item item,
                                     @NotNull CollectTarget target) {

        ShulkerAutoCollectEvent event = new ShulkerAutoCollectEvent(player, item, target.shulkerItem());

        Bukkit.getPluginManager().callEvent(event);

        return !event.isCancelled();

    }

    private @NotNull List<Item> findNearbyItems(@NotNull Player player) {

        double maxDistance = configManager.getAutoCollectMaxDistance();
        double squaredDistance = maxDistance * maxDistance;

        List<Item> items = new ArrayList<>();

        for (Item item : player.getWorld().getEntitiesByClass(Item.class)) {

            if (item == null || item.isDead() || !item.isValid()) {
                continue;
            }

            if (!configManager.isAutoCollectIgnorePickupDelay() && item.getPickupDelay() > 0) {
                continue;
            }

            UUID owner = item.getOwner();

            if (owner != null && !owner.equals(player.getUniqueId())) {
                continue;
            }

            if (item.getLocation().distanceSquared(player.getLocation()) > squaredDistance) {
                continue;
            }

            items.add(item);

        }

        sortByPriority(items);

        return items;

    }

    private void sortByPriority(@NotNull List<Item> items) {

        List<Material> priority = configManager.getAutoCollectPriorityItems();

        if (priority.isEmpty() || items.size() < 2) {
            return;
        }

        items.sort((first, second) -> Boolean.compare(
                priority.contains(second.getItemStack().getType()),
                priority.contains(first.getItemStack().getType())
        ));

    }

    private boolean canCollect(@Nullable Player player) {

        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        if (!configManager.isPluginEnabled() || !configManager.isAutoCollectEnabled()) {
            return false;
        }

        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }

        if (!isEnabledFor(player)) {
            return false;
        }

        return !configManager.isAutoCollectPermissionRequired()
                || permissionService.has(player, PermissionNode.AUTO_COLLECT);

    }

    private boolean isInventoryFull(@NotNull Player player) {

        for (ItemStack item : player.getInventory().getStorageContents()) {

            if (ShulkerUtil.isEmpty(item)) {
                return false;
            }

        }

        return true;

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

    // --- Цели сбора ---

    /**
     * Цель автосбора: открытый GUI или шалкер-бокс, лежащий в инвентаре.
     */
    sealed interface CollectTarget permits SessionTarget, ItemTarget {

        @NotNull ItemStack shulkerItem();

        int insert(@NotNull ItemStack item);

        void flush();

    }

    /**
     * Открытая сессия: содержимое пишется в GUI, сохранение уходит в сервис персистентности.
     */
    private record SessionTarget(@NotNull ShulkerSession session,
                                 @NotNull InventoryTransferService transferService) implements CollectTarget {

        @Override
        public @NotNull ItemStack shulkerItem() {
            return session.shulkerItem();
        }

        @Override
        public int insert(@NotNull ItemStack item) {
            return transferService.insert(session.getPlayer(), session.inventory(), item);
        }

        @Override
        public void flush() {
            session.markModified();
        }
    }

    /**
     * Шалкер-бокс в инвентаре: содержимое пишется в метаданные предмета,
     * затем предмет возвращается в тот же слот после проверки.
     */
    private record ItemTarget(@NotNull Player player,
                              int slot,
                              @NotNull InventoryTransferService transferService) implements CollectTarget {

        @Override
        public @NotNull ItemStack shulkerItem() {

            ItemStack shulker = player.getInventory().getItem(slot);

            return shulker == null ? new ItemStack(Material.AIR) : shulker;

        }

        @Override
        public int insert(@NotNull ItemStack item) {

            ItemStack shulker = player.getInventory().getItem(slot);
            ItemStack[] contents = ShulkerUtil.readContents(shulker);

            if (contents == null) {
                return 0;
            }

            int inserted = transferService.insertInto(contents, item);

            if (inserted <= 0) {
                return 0;
            }

            // Кэш содержимого обновляется сразу, чтобы следующий предмет видел актуальное состояние
            ItemStack saved = ShulkerUtil.writeContents(shulker, contents);

            if (saved == null) {
                return 0;
            }

            writeBack(saved);

            return inserted;

        }

        /**
         * Содержимое записывается сразу в insert(), дополнительная фиксация не нужна.
         */
        @Override
        public void flush() {

        }

        private void writeBack(@NotNull ItemStack saved) {

            ItemStack current = player.getInventory().getItem(slot);

            if (!ShulkerUtil.isShulkerBox(current)) {
                return;
            }

            player.getInventory().setItem(slot, saved);

            if (player.isOnline()) {
                player.updateInventory();
            }

        }
    }
}
