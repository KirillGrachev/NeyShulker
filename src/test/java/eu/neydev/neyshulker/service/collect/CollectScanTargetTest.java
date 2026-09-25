package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.FillOrderType;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.service.ShulkerPersistenceService;
import eu.neydev.neyshulker.util.FakeItemStack;
import eu.neydev.neyshulker.util.ShulkerUtil;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка детерминированного выбора бокса: ярусы предпочтения,
 * стратегии fill_order и ничьи по порядку слотов.
 */
class CollectScanTargetTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final InventoryTransferService transferService = mock(InventoryTransferService.class);
    private final ShulkerPersistenceService persistenceService = mock(ShulkerPersistenceService.class);

    private CollectScan scan(ItemStack... boxesBySlot) {

        Player player = mock(Player.class);
        PlayerInventory inventory = TestInventories.playerInventory();

        ItemStack[] contents = new ItemStack[41];

        for (ItemStack box : boxesBySlot) {
            contents[slotOf(box)] = box;
        }

        when(inventory.getContents()).thenReturn(contents);
        when(player.getInventory()).thenReturn(inventory);
        return new CollectScan(player, null, configManager, transferService, persistenceService);

    }

    /**
     * Дефолтные политики скана: режим ALL, merge включен, стратегия BALANCED.
     */
    private void defaultRules() {

        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.ALL);
        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(true);
        when(configManager.getAutoCollectFillOrder()).thenReturn(FillOrderType.BALANCED);

    }

    /**
     * Номер слота зашиваем в amount предмета-обертки, чтобы не плодить пар.
     */
    private int slotOf(ItemStack box) {
        return box.getAmount();
    }

    private ItemStack box(int slot, ItemStack... contents) {

        Inventory boxInventory = TestInventories.inventory(ShulkerUtil.SHULKER_SIZE);

        for (int i = 0; i < contents.length; i++) {
            boxInventory.setItem(i, contents[i]);
        }

        BlockStateMeta meta = mock(BlockStateMeta.class);
        ShulkerBox box = mock(ShulkerBox.class);

        when(box.getSnapshotInventory()).thenReturn(boxInventory);
        when(boxInventory.getContents()).thenAnswer(answer -> {

            ItemStack[] snapshot = new ItemStack[ShulkerUtil.SHULKER_SIZE];

            for (int i = 0; i < snapshot.length; i++) {
                snapshot[i] = boxInventory.getItem(i);
            }

            return snapshot;

        });
        when(meta.getBlockState()).thenReturn(box);

        // amount несет номер слота игрока, тип - материал бокса
        ItemStack shulker = mock(ItemStack.class);

        when(shulker.getType()).thenReturn(Material.WHITE_SHULKER_BOX);
        when(shulker.getAmount()).thenReturn(slot);
        when(shulker.clone()).thenReturn(shulker);
        when(shulker.getItemMeta()).thenReturn(meta);
        return shulker;

    }

    private int targetSlot(CollectScan scan, Material material) {

        CollectTarget target = scan.targetFor(new FakeItemStack(material, 1));
        return target == null ? -1 : ((BoxCollectTarget) target).getSlot();

    }

    @Test
    @DisplayName("Ярус 1: дроп вливается в стек с наибольшим остатком места")
    void mergeTierPrefersStackWithMostRoom() {

        defaultRules();

        CollectScan scan = scan(
                box(0, new FakeItemStack(Material.COBBLESTONE, 41)),
                box(1, new FakeItemStack(Material.COBBLESTONE, 10)));

        assertEquals(1, targetSlot(scan, Material.COBBLESTONE),
                "В стеке 10/64 места больше, чем в 41/64");

    }

    @Test
    @DisplayName("Ярус 2: тип стягивается в бокс, где его уже больше")
    void typeTierConsolidatesIntoFullerBox() {

        defaultRules();

        CollectScan scan = scan(
                box(0, new FakeItemStack(Material.COBBLESTONE, 64),
                        new FakeItemStack(Material.COBBLESTONE, 64)),
                box(1, new FakeItemStack(Material.COBBLESTONE, 64)));

        assertEquals(0, targetSlot(scan, Material.COBBLESTONE),
                "Полных стеков типа больше в боксе 0 - дроп идет туда же");

    }

    @Test
    @DisplayName("Ярус 3: стратегия fill_order ранжирует свободные боксы")
    void freeTierFollowsFillOrder() {

        defaultRules();

        // Бокс 0 почти пустой, бокс 5 забит камнем почти целиком
        CollectScan scan = scan(box(0, new FakeItemStack(Material.DIRT, 1)), dense(5, 25));

        assertEquals(0, targetSlot(scan, Material.GOLD_INGOT),
                "BALANCED: больше свободных слотов в боксе 0");

        when(configManager.getAutoCollectFillOrder()).thenReturn(FillOrderType.COMPACT);
        assertEquals(5, targetSlot(scan, Material.GOLD_INGOT),
                "COMPACT: меньше свободных слотов в боксе 5");

        when(configManager.getAutoCollectFillOrder()).thenReturn(FillOrderType.INVENTORY);
        assertEquals(0, targetSlot(scan, Material.GOLD_INGOT),
                "INVENTORY: побеждает меньший номер слота");

    }

    @Test
    @DisplayName("Ничья внутри яруса решается порядком слотов")
    void tiesBreakBySlotOrder() {

        defaultRules();
        CollectScan scan = scan(box(3), box(7));

        assertEquals(3, targetSlot(scan, Material.GOLD_INGOT),
                "Оба бокса пустые: побеждает меньший слот");

    }

    @Test
    @DisplayName("merge_into_existing=false отключает верхний ярус")
    void mergeTierCanBeDisabled() {

        defaultRules();
        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(false);

        CollectScan scan = scan(box(0, partials(27)), box(1));

        assertEquals(1, targetSlot(scan, Material.COBBLESTONE),
                "Бокс 0 без свободных слотов не участвует, дроп уходит в свободный бокс 1");

        when(configManager.isAutoCollectMergeIntoExisting()).thenReturn(true);

        assertEquals(0, targetSlot(scan, Material.COBBLESTONE),
                "С включенным merge частичный стек поглощает дроп");

    }

    @Test
    @DisplayName("MATCHING не пускает незнакомый тип в пустые боксы")
    void matchingGateBlocksUnknownType() {

        defaultRules();
        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.MATCHING);

        CollectScan scan = scan(box(0, new FakeItemStack(Material.DIRT, 64)));

        assertEquals(0, targetSlot(scan, Material.DIRT));
        assertEquals(-1, targetSlot(scan, Material.GOLD_INGOT));
        assertNull(scan.targetFor(new FakeItemStack(Material.GOLD_INGOT, 1)));

    }

    @Test
    @DisplayName("hasSpaceFor: место - свободный слот или частичный стек того же типа")
    void hasSpaceForSeesPhysicalSpace() {

        defaultRules();
        CollectScan full = scan(box(0, partials(27)));

        assertFalse(full.hasSpaceFor(new FakeItemStack(Material.DIAMOND, 1)),
                "Свободных слотов нет, мерджиться алмазу некуда");
        assertTrue(full.hasSpaceFor(new FakeItemStack(Material.COBBLESTONE, 1)),
                "Частичный стек того же типа поглощает дроп и без свободного слота");

        CollectScan matching = scan(box(0, new FakeItemStack(Material.DIRT, 64)));
        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.MATCHING);

        assertNull(matching.targetFor(new FakeItemStack(Material.GOLD_INGOT, 1)));
        assertTrue(matching.hasSpaceFor(new FakeItemStack(Material.GOLD_INGOT, 1)),
                "Отказ по гейту режима - не нехватка места");

    }

    @Test
    @DisplayName("MATCHING: незнакомый сессии тип уходит на ярусы боксов")
    void matchingSessionFallsThroughToBoxes() {

        defaultRules();
        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.MATCHING);

        Inventory session = TestInventories.inventory(ShulkerUtil.SHULKER_SIZE);
        session.setItem(0, new FakeItemStack(Material.DIRT, 64));

        CollectScan scan = scanWithSession(session,
                box(0, new FakeItemStack(Material.COBBLESTONE, 1)));

        assertEquals(0, targetSlot(scan, Material.COBBLESTONE),
                "Сессия не хранит булыжник - цель выбирают ярусы боксов");
        assertFalse(scan.targetFor(new FakeItemStack(Material.DIRT, 1)) instanceof BoxCollectTarget,
                "Тип есть в сессии: открытое GUI приоритетнее боксов");

        when(configManager.getAutoCollectMode()).thenReturn(CollectMode.ALL);

        assertFalse(scan.targetFor(new FakeItemStack(Material.COBBLESTONE, 1)) instanceof BoxCollectTarget,
                "В ALL сессия принимает любой тип раньше боксов");

    }

    @Test
    @DisplayName("hasSpaceFor с открытым GUI: полная сессия места не обещает")
    void sessionSpaceGate() {

        defaultRules();
        Inventory session = TestInventories.inventory(ShulkerUtil.SHULKER_SIZE);

        for (int i = 0; i < ShulkerUtil.SHULKER_SIZE; i++) {
            session.setItem(i, new FakeItemStack(Material.DIRT, 64));
        }

        CollectScan scan = scanWithSession(session);

        assertFalse(scan.hasSpaceFor(new FakeItemStack(Material.DIAMOND, 1)),
                "Полная сессия: ни свободного слота, ни частичного стека");

        session.setItem(5, null);

        assertTrue(scan.hasSpaceFor(new FakeItemStack(Material.DIAMOND, 1)),
                "Появился свободный слот - место есть");

    }

    /**
     * Скан с открытым GUI: сессия строится поверх тех же боксов инвентаря.
     */
    private CollectScan scanWithSession(Inventory sessionInventory, ItemStack... boxesBySlot) {

        Player player = mock(Player.class);
        PlayerInventory inventory = TestInventories.playerInventory();

        ItemStack[] contents = new ItemStack[41];

        for (ItemStack box : boxesBySlot) {
            contents[slotOf(box)] = box;
        }

        when(inventory.getContents()).thenReturn(contents);
        when(player.getInventory()).thenReturn(inventory);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());

        ShulkerSession session = ShulkerSession.create(UUID.randomUUID(), player,
                new FakeItemStack(Material.WHITE_SHULKER_BOX, 1), () -> sessionInventory, 0);
        return new CollectScan(player, session, configManager, transferService, persistenceService);

    }

    private ItemStack dense(int slot, int stoneStacks) {

        ItemStack[] contents = new ItemStack[stoneStacks + 1];
        contents[0] = new FakeItemStack(Material.DIRT, 1);

        for (int i = 1; i <= stoneStacks; i++) {
            contents[i] = new FakeItemStack(Material.STONE, 64);
        }

        return box(slot, contents);

    }

    private ItemStack[] partials(int count) {

        ItemStack[] contents = new ItemStack[count];

        for (int i = 0; i < count; i++) {
            contents[i] = new FakeItemStack(Material.COBBLESTONE, 32);
        }

        return contents;

    }

}
