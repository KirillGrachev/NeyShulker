package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.collect.PlayerDropTracker;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.ShulkerUtil;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка немедленной синхронизации листа ожидания: элементы предметов,
 * покинувших инвентарь, убираются из очереди переноса сразу, а не ждут
 * перепроверки на фазе слива.
 */
class AutoCollectWaitListSyncTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final ShulkerPersistenceService persistenceService = mock(ShulkerPersistenceService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final SoundService soundService = mock(SoundService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final PlayerDropTracker dropTracker = new PlayerDropTracker(() -> 0L);
    private final PluginManager pluginManager = mock(PluginManager.class);

    /**
     * Игрок с шалкер-боксом в хотбаре и своим миром.
     */
    private record Fixture(Player player, PlayerInventory inventory,
                           Inventory boxContents, UUID playerId) {
    }

    private Fixture fixture(Item... drops) {

        Player player = mock(Player.class);
        PlayerInventory inventory = TestInventories.playerInventory();
        Inventory boxContents = TestInventories.inventory(ShulkerUtil.SHULKER_SIZE);
        ItemStack shulker = shulker(boxContents);

        inventory.setItem(0, shulker);

        when(inventory.getContents()).thenReturn(new ItemStack[]{shulker});

        Location playerLocation = mock(Location.class);
        World world = mock(World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(drops));
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(false);

        UUID playerId = UUID.randomUUID();

        when(player.getUniqueId()).thenReturn(playerId);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(player.getItemOnCursor()).thenReturn(null);

        for (Item drop : drops) {
            Location dropLocation = mock(Location.class);

            when(dropLocation.distanceSquared(playerLocation)).thenReturn(1.0D);
            when(drop.getLocation()).thenReturn(dropLocation);
        }

        return new Fixture(player, inventory, boxContents, playerId);

    }

    private ItemStack shulker(Inventory boxContents) {

        BlockStateMeta meta = mock(BlockStateMeta.class);
        ShulkerBox box = mock(ShulkerBox.class);

        when(box.getSnapshotInventory()).thenReturn(boxContents);
        when(boxContents.getContents()).thenAnswer(answer -> {

            ItemStack[] contents = new ItemStack[ShulkerUtil.SHULKER_SIZE];

            for (int slot = 0; slot < contents.length; slot++) {
                contents[slot] = boxContents.getItem(slot);
            }

            return contents;

        });
        when(meta.getBlockState()).thenReturn(box);

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getItemMeta()).thenReturn(meta);

        return shulker;

    }

    private Item drop(Material material, int amount) {

        Item item = mock(Item.class);
        AtomicBoolean alive = new AtomicBoolean(true);

        when(item.isDead()).thenAnswer(answer -> !alive.get());
        when(item.isValid()).thenAnswer(answer -> alive.get());
        when(item.getPickupDelay()).thenReturn(0);
        when(item.getOwner()).thenReturn(null);
        when(item.getItemStack()).thenReturn(new FakeItemStack(material, amount));

        doAnswer(answer -> {
            alive.set(false);
            return null;
        }).when(item).remove();

        return item;

    }

    /**
     * Сервис с настраиваемым бюджетом: 0 - волна только детектит,
     * элементы листа ожидания остаются до следующих волн.
     */
    private AutoCollectService service(int actionsPerWave) {

        NeyShulker plugin = mock(NeyShulker.class);

        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("sync-test"));

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(configManager.isAutoCollectEnabled()).thenReturn(true);
        when(configManager.isAutoCollectPermissionRequired()).thenReturn(false);
        when(configManager.isAutoCollectOnlyWhenInventoryFull()).thenReturn(false);
        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(true);
        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.ALL);
        when(configManager.getAutoCollectMaxDistance()).thenReturn(3.0D);
        when(configManager.isAutoCollectIgnorePickupDelay()).thenReturn(false);
        when(configManager.getPlayersPerWave()).thenReturn(5);
        when(configManager.getActionsPerWave()).thenReturn(actionsPerWave);
        when(configManager.getQueuePerPlayer()).thenReturn(32);
        when(configManager.getFullMessageCooldown()).thenReturn(30);
        when(configManager.isAutoCollectBlacklisted(any())).thenReturn(false);
        when(configManager.isBlacklistEnabled()).thenReturn(false);
        when(configManager.getAutoCollectPriorityItems()).thenReturn(List.of());

        return new AutoCollectService(plugin, configManager, sessionRegistry,
                new InventoryTransferService(), persistenceService,
                messageService, soundService, permissionService, dropTracker);

    }

    private MockedStatic<Bukkit> bukkit(Fixture fixture) {

        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);

        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(fixture.player()));
        bukkit.when(() -> Bukkit.getPlayer(fixture.playerId())).thenReturn(fixture.player());

        return bukkit;

    }

    @Test
    @DisplayName("Предмет покинул слот - элемент сразу убирается из очереди")
    void syncRemovesEntryWhenItemLeftInventory() {

        Fixture fixture = fixture();

        fixture.inventory().setItem(5, new FakeItemStack(Material.DIAMOND, 3));

        AutoCollectService service = service(0);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            assertEquals(1, service.waitListSize(fixture.player()),
                    "Детекция поставила предмет в очередь");

            fixture.inventory().setItem(5, null);

            service.syncWaitList(fixture.player());

            assertEquals(0, service.waitListSize(fixture.player()),
                    "Покинувший инвентарь предмет немедленно убран из очереди переноса");
            assertNull(fixture.boxContents().getItem(0), "В бокс ничего не перенесено");

        }

    }

    @Test
    @DisplayName("Замена в слоте попадает в очередь только через новую детекцию")
    void replacementIsQueuedOnlyByFreshDetection() {

        Fixture fixture = fixture();

        fixture.inventory().setItem(5, new FakeItemStack(Material.DIAMOND, 3));

        AutoCollectService service = service(0);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            assertEquals(1, service.waitListSize(fixture.player()));

            // Предмет унесли, на его место положили другой стек того же типа
            fixture.inventory().setItem(5, null);
            service.syncWaitList(fixture.player());
            assertEquals(0, service.waitListSize(fixture.player()));

            fixture.inventory().setItem(5, new FakeItemStack(Material.DIAMOND, 8));
            service.syncWaitList(fixture.player());
            assertEquals(0, service.waitListSize(fixture.player()),
                    "Синхронизация только убирает элементы, ничего не добавляя");

            when(configManager.getActionsPerWave()).thenReturn(64);

            service.wave();

            ItemStack stored = fixture.boxContents().getItem(0);

            assertNotNull(stored, "Замена доехала в бокс через свежую детекцию");
            assertEquals(8, stored.getAmount());

        }

    }

    @Test
    @DisplayName("Валидный элемент переживает синхронизацию и переносится")
    void validEntrySurvivesSync() {

        Fixture fixture = fixture();

        fixture.inventory().setItem(5, new FakeItemStack(Material.DIAMOND, 3));

        AutoCollectService service = service(0);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            service.syncWaitList(fixture.player());

            assertEquals(1, service.waitListSize(fixture.player()),
                    "Предмет на месте - элемент остается в очереди");

            when(configManager.getActionsPerWave()).thenReturn(64);

            service.wave();

            ItemStack stored = fixture.boxContents().getItem(0);

            assertNotNull(stored);
            assertEquals(3, stored.getAmount());
            assertNull(fixture.inventory().getItem(5), "Слот игрока освобожден");

        }

    }

    @Test
    @DisplayName("Синхронизация инвентаря не трогает элементы с земли")
    void syncKeepsGroundEntries() {

        Item item = drop(Material.DIAMOND, 2);
        Fixture fixture = fixture(item);

        AutoCollectService service = service(0);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            assertEquals(1, service.waitListSize(fixture.player()));

            service.syncWaitList(fixture.player());

            assertEquals(1, service.waitListSize(fixture.player()),
                    "Дроп на земле не зависит от изменений инвентаря");

            when(configManager.getActionsPerWave()).thenReturn(64);

            service.wave();

            verify(item).remove();
            assertNotNull(fixture.boxContents().getItem(0));

        }

    }

    @Test
    @DisplayName("clearWaitList полностью очищает очередь")
    void clearWaitListEmptiesQueue() {

        Item item = drop(Material.DIAMOND, 2);
        Fixture fixture = fixture(item);

        fixture.inventory().setItem(5, new FakeItemStack(Material.EMERALD, 1));

        AutoCollectService service = service(0);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            assertEquals(2, service.waitListSize(fixture.player()));

            service.clearWaitList(fixture.player());

            assertEquals(0, service.waitListSize(fixture.player()));

        }

    }

    @Test
    @DisplayName("Предмет стал исключенным после reload - элемент убирается")
    void syncRemovesEntryBlacklistedAfterReload() {

        Fixture fixture = fixture();

        fixture.inventory().setItem(5, new FakeItemStack(Material.DIAMOND, 3));

        AutoCollectService service = service(0);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            assertEquals(1, service.waitListSize(fixture.player()));

            when(configManager.isAutoCollectBlacklisted(Material.DIAMOND)).thenReturn(true);

            service.syncWaitList(fixture.player());

            assertEquals(0, service.waitListSize(fixture.player()),
                    "Исключенный предмет не должен дожидаться переноса в очереди");

        }

    }
}
