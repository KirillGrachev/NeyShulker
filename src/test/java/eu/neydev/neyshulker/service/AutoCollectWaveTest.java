package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.MessageKey;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка волновой модели автосбора: ротация игроков по ломтикам,
 * глобальный бюджет погружений и потолок листа ожидания.
 */
class AutoCollectWaveTest {

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
    private record Fixture(Player player, World world, Inventory boxContents, UUID playerId) {
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

        return new Fixture(player, world, boxContents, playerId);

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

    /**
     * Дроп, который после remove() честно становится мертвым:
     * устаревшие элементы листа ожидания должны отбрасываться.
     */
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

    private AutoCollectService service(int playersPerWave, int actionsPerWave, int queuePerPlayer) {

        NeyShulker plugin = mock(NeyShulker.class);

        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("wave-test"));

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(configManager.isAutoCollectEnabled()).thenReturn(true);
        when(configManager.isAutoCollectPermissionRequired()).thenReturn(false);
        when(configManager.isAutoCollectOnlyWhenInventoryFull()).thenReturn(false);
        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(true);
        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.ALL);
        when(configManager.getAutoCollectMaxDistance()).thenReturn(3.0D);
        when(configManager.isAutoCollectIgnorePickupDelay()).thenReturn(false);
        when(configManager.getPlayersPerWave()).thenReturn(playersPerWave);
        when(configManager.getActionsPerWave()).thenReturn(actionsPerWave);
        when(configManager.getQueuePerPlayer()).thenReturn(queuePerPlayer);
        when(configManager.getFullMessageCooldown()).thenReturn(30);
        when(configManager.isAutoCollectBlacklisted(any())).thenReturn(false);
        when(configManager.isBlacklistEnabled()).thenReturn(false);
        when(configManager.getAutoCollectPriorityItems()).thenReturn(List.of());

        return new AutoCollectService(plugin, configManager, sessionRegistry,
                new InventoryTransferService(), persistenceService,
                messageService, soundService, permissionService, dropTracker);

    }

    private MockedStatic<Bukkit> bukkit(Fixture... fixtures) {

        MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);

        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::getOnlinePlayers)
                .thenAnswer(answer -> List.copyOf(java.util.Arrays.stream(fixtures)
                        .map(Fixture::player).toList()));

        for (Fixture fixture : fixtures) {
            bukkit.when(() -> Bukkit.getPlayer(fixture.playerId())).thenReturn(fixture.player());
        }

        return bukkit;

    }

    @Test
    @DisplayName("Волна детектит только ломтик игроков, ротация охватывает всех")
    void waveDetectsSliceAndRotates() {

        Item dropA = drop(Material.DIAMOND, 1);
        Item dropB = drop(Material.DIAMOND, 1);

        Fixture first = fixture(dropA);
        Fixture second = fixture(dropB);

        AutoCollectService service = service(1, 64, 32);

        try (MockedStatic<Bukkit> ignored = bukkit(first, second)) {

            service.wave();

            verify(first.world()).getEntitiesByClass(Item.class);
            verify(second.world(), never()).getEntitiesByClass(Item.class);
            verify(dropA).remove();
            verify(dropB, never()).remove();

            service.wave();

            verify(second.world()).getEntitiesByClass(Item.class);
            verify(dropB).remove();

        }

    }

    @Test
    @DisplayName("Бюджет действий на волну дозирует погружение")
    void drainRespectsActionsBudget() {

        Item first = drop(Material.DIAMOND, 3);
        Item second = drop(Material.DIAMOND, 2);

        Fixture fixture = fixture(first, second);

        AutoCollectService service = service(5, 3, 32);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            verify(first).remove();
            verify(second, never()).remove();

            ItemStack stored = fixture.boxContents().getItem(0);

            assertNotNull(stored, "Первый дроп должен успеть погрузиться в рамках бюджета");
            assertEquals(3, stored.getAmount());

            service.wave();

            verify(second).remove();

            ItemStack merged = fixture.boxContents().getItem(0);

            assertNotNull(merged);
            assertEquals(5, merged.getAmount(), "Остаток доезжает на следующей волне");

        }

    }

    @Test
    @DisplayName("Лист ожидания ограничен queue_per_player, лишнее не теряется")
    void queueCapacityLimitsWaitList() {

        Item first = drop(Material.DIAMOND, 3);
        Item second = drop(Material.EMERALD, 2);

        Fixture fixture = fixture(first, second);

        AutoCollectService service = service(5, 64, 1);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();

            verify(first).remove();
            verify(second, never()).remove();
            assertNull(fixture.boxContents().getItem(1), "Переполненный лист не грузит второй дроп");

            service.wave();

            verify(second).remove();
            assertNotNull(fixture.boxContents().getItem(1), "На следующей волне второй дроп обнаружен заново");

        }

    }

    @Test
    @DisplayName("Полный бокс под массовым дропом: сообщение о полноте не чаще кулдауна")
    void fullBoxNoticeRespectsCooldown() {

        Item drop = drop(Material.DIAMOND, 1);
        Fixture fixture = fixture(drop);

        for (int slot = 0; slot < ShulkerUtil.SHULKER_SIZE; slot++) {
            fixture.boxContents().setItem(slot, new FakeItemStack(Material.DIRT, 64));
        }

        AutoCollectService service = service(5, 64, 32);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();
            service.wave();
            service.wave();

            verify(drop, never()).remove();
            verify(messageService, times(1))
                    .send(fixture.player(), MessageKey.AUTO_COLLECT_FULL, Map.of());

        }

    }

    @Test
    @DisplayName("MATCHING: незнакомый тип при свободном месте отклоняется молча")
    void matchingRejectionStaysSilent() {

        Item drop = drop(Material.DIAMOND, 1);
        Fixture fixture = fixture(drop);

        fixture.boxContents().setItem(0, new FakeItemStack(Material.DIRT, 64));

        AutoCollectService service = service(5, 64, 32);

        // После хелпера: тот ставит дефолтный ALL, а последний stub выигрывает
        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.MATCHING);

        try (MockedStatic<Bukkit> ignored = bukkit(fixture)) {

            service.wave();
            service.wave();

            verify(drop, never()).remove();
            verify(messageService, never())
                    .send(eq(fixture.player()), eq(MessageKey.AUTO_COLLECT_FULL), anyMap());

        }

    }
}
