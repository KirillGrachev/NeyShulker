package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.collect.PlayerDropTracker;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.ShulkerUtil;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка автосбора: вакуум допустимых дропов в шалкер-бокс,
 * гейты конфигурации и пер-player переключатель.
 */
class AutoCollectServiceTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final ShulkerPersistenceService persistenceService = mock(ShulkerPersistenceService.class);
    private final MessageService messageService = mock(MessageService.class);
    private final SoundService soundService = mock(SoundService.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final PlayerDropTracker dropTracker = new PlayerDropTracker(() -> 0L);

    private final Player player = mock(Player.class);
    private final PlayerInventory playerInventory = TestInventories.playerInventory();
    private final Inventory boxContents = TestInventories.inventory(27);

    private final ItemStack shulker = shulker();
    private final Item drop = drop();

    @Test
    @DisplayName("Брошенный игроком предмет авто-сбор не всасывает")
    void thrownDropIsNotCollected() {

        when(configManager.isAutoCollectIgnorePlayerDropped()).thenReturn(true);
        dropTracker.mark(drop);

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        service.collectAround(player);
        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Дроп с флагом владельца авто-сбор пропускает")
    void ownedDropIsNotCollected() {

        when(configManager.isAutoCollectIgnorePlayerDropped()).thenReturn(true);
        when(drop.getOwner()).thenReturn(java.util.UUID.randomUUID());

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        service.collectAround(player);
        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("У дропа стоит другой игрок - лут не всасывается")
    void dropNearOtherPlayerIsNotCollected() {

        when(configManager.getAutoCollectRespectNearbyPlayers()).thenReturn(4.5D);
        Location at = drop.getLocation();
        Player other = mock(Player.class);

        when(other.isOnline()).thenReturn(true);
        when(other.getLocation()).thenReturn(at);

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        when(player.getWorld().getPlayers()).thenReturn(List.of(player, other));
        service.collectAround(player);
        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Другой игрок далеко от дропа - лут всасывается")
    void distantPlayerDoesNotBlockCollect() {

        when(configManager.getAutoCollectRespectNearbyPlayers()).thenReturn(4.5D);
        Location far = mock(Location.class);
        when(far.distanceSquared(drop.getLocation())).thenReturn(100.0D);
        Player other = mock(Player.class);

        when(other.isOnline()).thenReturn(true);
        when(other.getLocation()).thenReturn(far);

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        when(player.getWorld().getPlayers()).thenReturn(List.of(player, other));
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        verify(drop).remove();
        assertEquals(3, boxContents.getItem(0).getAmount());

    }

    @Test
    @DisplayName("Единственный игрок рядом - сам собирающий: лут всасывается")
    void soloCollectorStillCollects() {

        when(configManager.getAutoCollectRespectNearbyPlayers()).thenReturn(4.5D);

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        when(player.getWorld().getPlayers()).thenReturn(List.of(player));
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        verify(drop).remove();
        assertEquals(3, boxContents.getItem(0).getAmount());

    }

    private ItemStack shulker() {

        BlockStateMeta meta = mock(BlockStateMeta.class);
        ShulkerBox box = mock(ShulkerBox.class);

        when(box.getSnapshotInventory()).thenReturn(boxContents);
        when(boxContents.getContents()).thenReturn(new ItemStack[ShulkerUtil.SHULKER_SIZE]);
        when(meta.getBlockState()).thenReturn(box);

        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getItemMeta()).thenReturn(meta);
        return shulker;

    }

    private Item drop() {

        Location dropLocation = mock(Location.class);
        Location playerLocation = mock(Location.class);

        when(dropLocation.distanceSquared(playerLocation)).thenReturn(1.0D);
        Item drop = mock(Item.class);

        when(drop.isDead()).thenReturn(false);
        when(drop.isValid()).thenReturn(true);
        when(drop.getPickupDelay()).thenReturn(0);
        when(drop.getOwner()).thenReturn(null);
        when(drop.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(drop.getLocation()).thenReturn(dropLocation);
        when(drop.getItemStack()).thenReturn(new FakeItemStack(Material.DIAMOND, 3));

        when(player.getLocation()).thenReturn(playerLocation);
        return drop;

    }

    private AutoCollectService service() {

        NeyShulker plugin = mock(NeyShulker.class);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("autocollect-test"));

        when(configManager.isPluginEnabled()).thenReturn(true);
        when(configManager.isAutoCollectEnabled()).thenReturn(true);
        when(configManager.isAutoCollectPermissionRequired()).thenReturn(false);
        when(configManager.isAutoCollectOnlyWhenInventoryFull()).thenReturn(false);
        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(true);
        when(configManager.getAutoCollectMode())
                .thenReturn(eu.neydev.neyshulker.config.type.CollectMode.ALL);
        when(configManager.getAutoCollectMaxDistance()).thenReturn(3.0D);
        when(configManager.isAutoCollectIgnorePickupDelay()).thenReturn(false);
        when(configManager.getQueuePerPlayer()).thenReturn(64);
        when(configManager.getFullMessageCooldown()).thenReturn(30);
        when(configManager.isAutoCollectBlacklisted(any())).thenReturn(false);
        when(configManager.isBlacklistEnabled()).thenReturn(false);
        when(configManager.getAutoCollectPriorityItems()).thenReturn(List.of());

        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(false);
        when(player.getUniqueId()).thenReturn(java.util.UUID.randomUUID());
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(playerInventory);
        when(player.getItemOnCursor()).thenReturn(null);

        org.bukkit.World world = mock(org.bukkit.World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(drop));
        when(player.getWorld()).thenReturn(world);

        return new AutoCollectService(plugin, configManager, sessionRegistry,
                new InventoryTransferService(), persistenceService,
                messageService, soundService, permissionService, dropTracker);

    }

    @Test
    @DisplayName("Дроп в радиусе уезжает в шалкер из инвентаря")
    void collectsDropIntoStorageShulker() {

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);

        when(server.getPluginManager()).thenReturn(pluginManager);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service().collectAround(player);

        }

        verify(drop).remove();
        verify(soundService).playCollect(player);

        ItemStack stored = boxContents.getItem(0);

        assertNotNull(stored, "Предмет должен оказаться в содержимом бокса");
        assertEquals(3, stored.getAmount());

    }

    @Test
    @DisplayName("Шалкер во второй руке - полноценная цель сбора")
    void offHandShulkerIsCollectTarget() {

        when(playerInventory.getContents()).thenReturn(new ItemStack[0]);
        when(playerInventory.getItem(ShulkerUtil.OFF_HAND_SLOT)).thenReturn(shulker);

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);

        when(server.getPluginManager()).thenReturn(pluginManager);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service().collectAround(player);

        }

        verify(drop).remove();
        verify(playerInventory).setItem(eq(ShulkerUtil.OFF_HAND_SLOT), any());

    }

    private Item secondDrop() {

        Item second = mock(Item.class);
        Location dropLocation = mock(Location.class);

        when(dropLocation.distanceSquared(player.getLocation())).thenReturn(2.0D);
        when(second.isDead()).thenReturn(false);
        when(second.isValid()).thenReturn(true);
        when(second.getPickupDelay()).thenReturn(0);
        when(second.getOwner()).thenReturn(null);
        when(second.getLocation()).thenReturn(dropLocation);
        when(second.getItemStack()).thenReturn(new FakeItemStack(Material.DIAMOND, 2));
        return second;

    }

    @Test
    @DisplayName("Полный бокс с частичным стеком того же типа дозибирает дроп")
    void mergeIntoFullBoxWithMatchingType() {

        ItemStack[] full = new ItemStack[ShulkerUtil.SHULKER_SIZE];
        full[0] = new FakeItemStack(Material.DIAMOND, 60);

        for (int i = 1; i < full.length; i++) {
            full[i] = new FakeItemStack(Material.STONE, 64);
        }

        when(boxContents.getContents()).thenReturn(full);
        when(playerInventory.getItem(0)).thenReturn(shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();

        when(configManager.getAutoCollectMode())
                .thenReturn(eu.neydev.neyshulker.config.type.CollectMode.MATCHING);

        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        verify(drop).remove();
        verify(playerInventory).setItem(eq(0), any());
        assertEquals(63, boxContents.getItem(0).getAmount(),
                "Дроп смерджен в частичный стек полного бокса");

    }

    @Test
    @DisplayName("Без merge-режима полный бокс не цель")
    void mergeDisabledSkipsFullBox() {

        ItemStack[] full = new ItemStack[ShulkerUtil.SHULKER_SIZE];
        full[0] = new FakeItemStack(Material.DIAMOND, 60);

        for (int i = 1; i < full.length; i++) {
            full[i] = new FakeItemStack(Material.STONE, 64);
        }

        when(boxContents.getContents()).thenReturn(full);
        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(false);
        service.collectAround(player);
        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Все дропы забираются за один скан, resync один раз")
    void allDropsCollectedInSingleScan() {

        Item second = secondDrop();

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();

        // Мир с двумя дропами stub-ится после сервиса: service() ставит свой мир
        org.bukkit.World world = mock(org.bukkit.World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(drop, second));
        when(player.getWorld()).thenReturn(world);

        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        verify(drop).remove();
        verify(second).remove();
        verify(player, org.mockito.Mockito.times(1)).updateInventory();
        verify(soundService, org.mockito.Mockito.times(1)).playCollect(player);

    }

    @Test
    @DisplayName("Пер-player переключатель останавливает сбор")
    void perPlayerToggleStopsCollection() {

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        AutoCollectService service = service();
        service.toggle(player);
        service.collectAround(player);
        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Гейт полного инвентаря следует конфигурации")
    void fullInventoryGateRespectsConfig() {

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});
        when(playerInventory.getStorageContents()).thenReturn(new ItemStack[36]);

        AutoCollectService service = service();

        // Пере-стаб после конструктора: service() сам ставит дефолт false
        when(configManager.isAutoCollectOnlyWhenInventoryFull()).thenReturn(true);

        service.collectAround(player);
        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Свежий дроп с pickup delay не трогается")
    void freshDropWithPickupDelayIsIgnored() {

        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});
        when(drop.getPickupDelay()).thenReturn(10);

        Server server = mock(Server.class);
        PluginManager pluginManager = mock(PluginManager.class);

        when(server.getPluginManager()).thenReturn(pluginManager);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service().collectAround(player);

        }

        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Дроп шалкер-бокса не собирается никогда")
    void shulkerDropsAreNeverCollected() {

        when(drop.getItemStack()).thenReturn(new FakeItemStack(Material.WHITE_SHULKER_BOX, 1));
        playerInventory.setItem(0, shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service().collectAround(player);

        }

        verify(drop, never()).remove();

    }

    @Test
    @DisplayName("Нет места для первого типа - второй все равно собирается")
    void noSpaceForOneTypeDoesNotBlockOthers() {

        // Бокс: полные стеки булыжника и частичный стек земли
        ItemStack[] full = new ItemStack[ShulkerUtil.SHULKER_SIZE];

        for (int i = 0; i < full.length; i++) {
            full[i] = new FakeItemStack(Material.COBBLESTONE, 64);
        }

        full[0] = new FakeItemStack(Material.DIRT, 60);

        when(boxContents.getContents()).thenReturn(full);
        when(playerInventory.getItem(0)).thenReturn(shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        // Дроп: булыжник (места нет) и земля (мерджится)
        when(drop.getItemStack()).thenReturn(new FakeItemStack(Material.COBBLESTONE, 4));

        Item dirt = secondDrop();
        when(dirt.getItemStack()).thenReturn(new FakeItemStack(Material.DIRT, 3));
        AutoCollectService service = service();

        // Мир stub-ится после сервиса: service() ставит свой мир по умолчанию
        org.bukkit.World world = mock(org.bukkit.World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(drop, dirt));
        when(player.getWorld()).thenReturn(world);

        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        verify(drop, never()).remove();
        verify(dirt).remove();
        assertEquals(63, boxContents.getItem(0).getAmount());

    }

    @Test
    @DisplayName("MATCHING: предмет из инвентаря досортировывается в свой стек")
    void inventoryMatchingSortsKnownType() {

        ItemStack[] boxStacks = new ItemStack[ShulkerUtil.SHULKER_SIZE];
        boxStacks[0] = new FakeItemStack(Material.COBBLESTONE, 60);

        for (int i = 1; i < boxStacks.length; i++) {
            boxStacks[i] = new FakeItemStack(Material.STONE, 64);
        }

        when(boxContents.getContents()).thenReturn(boxStacks);
        when(playerInventory.getItem(0)).thenReturn(shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        playerInventory.setItem(5, new FakeItemStack(Material.COBBLESTONE, 4));
        playerInventory.setItem(6, new FakeItemStack(Material.DIAMOND, 2));

        AutoCollectService service = service();
        org.bukkit.World world = mock(org.bukkit.World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of());
        when(player.getWorld()).thenReturn(world);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        assertEquals(null, playerInventory.getItem(5), "Кобла ушла в бокс");
        assertEquals(64, boxContents.getItem(0).getAmount());
        assertNotNull(playerInventory.getItem(6), "Новый тип в MATCHING не тронут");

    }

    @Test
    @DisplayName("MATCHING: полные стеки типа не блокируют сбор при свободных слотах")
    void matchingFullStacksDoNotBlockWhenFreeSlotsExist() {

        ItemStack[] boxStacks = new ItemStack[ShulkerUtil.SHULKER_SIZE];

        for (int i = 0; i < boxStacks.length; i++) {
            boxStacks[i] = new FakeItemStack(Material.COBBLESTONE, 64);
        }

        boxStacks[5] = null;
        boxStacks[6] = null;

        when(boxContents.getContents()).thenReturn(boxStacks);
        when(playerInventory.getItem(0)).thenReturn(shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        playerInventory.setItem(9, new FakeItemStack(Material.COBBLESTONE, 4));
        AutoCollectService service = service();

        when(configManager.getAutoCollectMode())
                .thenReturn(eu.neydev.neyshulker.config.type.CollectMode.MATCHING);

        org.bukkit.World world = mock(org.bukkit.World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of());
        when(player.getWorld()).thenReturn(world);

        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        assertEquals(null, playerInventory.getItem(9),
                "Тип уже в боксе - свободные слоты принимают его даже при полных стеках");
        assertEquals(4, boxContents.getItem(5).getAmount());

    }

    @Test
    @DisplayName("ALL: любой допустимый предмет из инвентаря уходит в бокс")
    void inventoryAllMovesAnyEligible() {

        when(boxContents.getContents()).thenReturn(new ItemStack[ShulkerUtil.SHULKER_SIZE]);
        when(playerInventory.getItem(0)).thenReturn(shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        playerInventory.setItem(6, new FakeItemStack(Material.DIAMOND, 2));
        AutoCollectService service = service();
        org.bukkit.World world = mock(org.bukkit.World.class);

        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of());
        when(player.getWorld()).thenReturn(world);

        when(configManager.getAutoCollectMode())
                .thenReturn(eu.neydev.neyshulker.config.type.CollectMode.ALL);

        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        assertEquals(null, playerInventory.getItem(6));
        assertEquals(2, boxContents.getItem(0).getAmount());

    }

    @Test
    @DisplayName("MATCHING: неизвестный тип не собирается ни с земли, ни из инвентаря")
    void matchingIgnoresUnknownTypesEverywhere() {

        when(boxContents.getContents()).thenReturn(new ItemStack[ShulkerUtil.SHULKER_SIZE]);
        when(playerInventory.getItem(0)).thenReturn(shulker);
        when(playerInventory.getContents()).thenReturn(new ItemStack[]{shulker});

        playerInventory.setItem(6, new FakeItemStack(Material.DIAMOND, 2));
        AutoCollectService service = service();

        when(configManager.getAutoCollectMode())
                .thenReturn(eu.neydev.neyshulker.config.type.CollectMode.MATCHING);

        org.bukkit.World world = mock(org.bukkit.World.class);
        when(world.getEntitiesByClass(Item.class)).thenReturn(List.of(drop));
        when(player.getWorld()).thenReturn(world);
        PluginManager pluginManager = mock(PluginManager.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            service.collectAround(player);

        }

        verify(drop, never()).remove();
        assertNotNull(playerInventory.getItem(6));

    }

    @Test
    @DisplayName("Без шалкера в инвентаре сбор не происходит")
    void noShulkerNoCollection() {

        when(playerInventory.getContents()).thenReturn(new ItemStack[0]);
        service().collectAround(player);
        verify(drop, never()).remove();

    }

}
