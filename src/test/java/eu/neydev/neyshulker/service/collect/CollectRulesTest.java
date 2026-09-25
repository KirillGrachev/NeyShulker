package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.util.FakeItemStack;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Правила допуска: гейты игрока (онлайн, жизнь, режимы игры, права,
 * тумблер), исключение предметов и антигриф-проверки с кэшем соседей.
 */
class CollectRulesTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final PermissionService permissionService = mock(PermissionService.class);
    private final PlayerDropTracker dropTracker = new PlayerDropTracker(() -> 0L);
    private final CollectRules rules =
            new CollectRules(configManager, permissionService, dropTracker);

    private final Player player = mock(Player.class);

    private void baseGates() {

        when(player.isOnline()).thenReturn(true);
        when(player.isDead()).thenReturn(false);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(configManager.isPluginEnabled()).thenReturn(true);
        when(configManager.isAutoCollectEnabled()).thenReturn(true);
        when(configManager.isAutoCollectPermissionRequired()).thenReturn(false);

    }

    @Test
    @DisplayName("canCollectPlayer: базовые отказы")
    void canCollectPlayerBasicDenials() {

        baseGates();
        assertTrue(rules.canCollectPlayer(player, true));

        assertFalse(rules.canCollectPlayer(null, true));

        when(player.isOnline()).thenReturn(false);
        assertFalse(rules.canCollectPlayer(player, true));
        when(player.isOnline()).thenReturn(true);

        when(player.isDead()).thenReturn(true);
        assertFalse(rules.canCollectPlayer(player, true));
        when(player.isDead()).thenReturn(false);

        when(configManager.isPluginEnabled()).thenReturn(false);
        assertFalse(rules.canCollectPlayer(player, true));
        when(configManager.isPluginEnabled()).thenReturn(true);

        when(configManager.isAutoCollectEnabled()).thenReturn(false);
        assertFalse(rules.canCollectPlayer(player, true));
        when(configManager.isAutoCollectEnabled()).thenReturn(true);

        // per-player тумблер
        assertFalse(rules.canCollectPlayer(player, false));

    }

    @Test
    @DisplayName("Режимы игры: дефолт SURVIVAL+ADVENTURE, конфиг переопределяет")
    void gameModesRespectConfigWithSafeDefault() {

        baseGates();

        // Конфиг не отдает режимы (старый снапшот/мок) - дефолт
        when(configManager.getAutoCollectGameModes()).thenReturn(null);

        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertFalse(rules.canCollectPlayer(player, true), "Креатив вне дефолта");

        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        assertTrue(rules.canCollectPlayer(player, true));

        when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertFalse(rules.canCollectPlayer(player, true));

        // Сервер включил креатив списком
        when(configManager.getAutoCollectGameModes())
                .thenReturn(Set.of(GameMode.SURVIVAL, GameMode.ADVENTURE, GameMode.CREATIVE));
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(rules.canCollectPlayer(player, true));

    }

    @Test
    @DisplayName("Требуемое право проверяется только при permission.required")
    void permissionGate() {

        baseGates();
        when(configManager.isAutoCollectPermissionRequired()).thenReturn(true);
        when(permissionService.has(player, PermissionNode.AUTO_COLLECT)).thenReturn(false);

        assertFalse(rules.canCollectPlayer(player, true));

        when(permissionService.has(player, PermissionNode.AUTO_COLLECT)).thenReturn(true);
        assertTrue(rules.canCollectPlayer(player, true));

    }

    @Test
    @DisplayName("isExcluded: шалкеры, блэклист автосбора, общий блэклист и bypass")
    void exclusions() {

        baseGates();

        assertTrue(rules.isExcluded(player, new FakeItemStack(Material.WHITE_SHULKER_BOX, 1)));

        when(configManager.isAutoCollectBlacklisted(Material.SPAWNER)).thenReturn(true);
        assertTrue(rules.isExcluded(player, new FakeItemStack(Material.SPAWNER, 1)));

        when(configManager.isBlacklistEnabled()).thenReturn(true);
        when(configManager.isBlacklisted(Material.BEDROCK)).thenReturn(true);
        when(permissionService.canBypassBlacklist(player)).thenReturn(false);
        assertTrue(rules.isExcluded(player, new FakeItemStack(Material.BEDROCK, 1)));

        when(permissionService.canBypassBlacklist(player)).thenReturn(true);
        assertFalse(rules.isExcluded(player, new FakeItemStack(Material.BEDROCK, 1)));

        assertFalse(rules.isExcluded(player, new FakeItemStack(Material.DIAMOND, 1)));

    }

    @Test
    @DisplayName("Гейт полного инвентаря: выключен, пустой слот, полный инвентарь")
    void inventoryGate() {

        baseGates();

        PlayerInventory inventory = eu.neydev.neyshulker.util.TestInventories.playerInventory();
        when(player.getInventory()).thenReturn(inventory);

        // Выключен - всегда пропускаем
        when(configManager.isAutoCollectOnlyWhenInventoryFull()).thenReturn(false);
        assertFalse(rules.failsInventoryGate(player));

        // Включен, есть пустые слоты - сбор запрещен
        when(configManager.isAutoCollectOnlyWhenInventoryFull()).thenReturn(true);
        assertTrue(rules.failsInventoryGate(player));

        // Заполненное хранение - гейт открыт
        for (int slot = 0; slot < 36; slot++) {
            inventory.setItem(slot, new FakeItemStack(Material.STONE, 64));
        }
        assertFalse(rules.failsInventoryGate(player));

    }

    @Test
    @DisplayName("nearbyOthers: нулевой радиус не строит список, дальние отсекаются")
    void nearbyOthersFiltering() {

        Location at = mock(Location.class);
        Location far = mock(Location.class);
        Location near = mock(Location.class);
        World world = mock(World.class);

        Player other = mock(Player.class);
        Player distant = mock(Player.class);

        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(at);
        when(other.isOnline()).thenReturn(true);
        when(other.getLocation()).thenReturn(near);
        when(distant.isOnline()).thenReturn(true);
        when(distant.getLocation()).thenReturn(far);
        when(world.getPlayers()).thenReturn(List.of(player, other, distant));

        when(configManager.getAutoCollectRespectNearbyPlayers()).thenReturn(0.0D);
        assertTrue(rules.nearbyOthers(player).isEmpty(), "Радиус 0 выключает проверку");

        when(configManager.getAutoCollectRespectNearbyPlayers()).thenReturn(2.0D);
        when(configManager.getAutoCollectMaxDistance()).thenReturn(3.0D);
        when(near.distanceSquared(at)).thenReturn(4.0D);   // в пределах 2+3
        when(far.distanceSquared(at)).thenReturn(100.0D); // вне запаса

        List<Player> others = rules.nearbyOthers(player);

        assertEquals(1, others.size());
        assertEquals(other, others.get(0));

    }

    @Test
    @DisplayName("isContested: брошенный дроп, owner и сосед по списку")
    void contestedChecks() {

        baseGates();

        Item item = mock(Item.class);
        Location itemAt = mock(Location.class);
        Location near = mock(Location.class);

        when(item.getOwner()).thenReturn(null);
        when(item.getLocation()).thenReturn(itemAt);
        when(item.getUniqueId()).thenReturn(UUID.randomUUID());

        // Не брошен, соседей нет - не спорный
        when(configManager.isAutoCollectIgnorePlayerDropped()).thenReturn(true);
        when(configManager.getAutoCollectRespectNearbyPlayers()).thenReturn(2.0D);
        assertFalse(rules.isContested(player, item, List.of()));

        // Памятка броска
        dropTracker.mark(item);
        assertTrue(rules.isContested(player, item, List.of()));

        // Owner как подстраховка
        Item owned = mock(Item.class);
        when(owned.getOwner()).thenReturn(UUID.randomUUID());
        when(owned.getUniqueId()).thenReturn(UUID.randomUUID());
        assertTrue(rules.isContested(player, owned, List.of()));

        // Сосед у дропа
        Item fresh = mock(Item.class);
        when(fresh.getOwner()).thenReturn(null);
        when(fresh.getLocation()).thenReturn(itemAt);
        when(fresh.getUniqueId()).thenReturn(UUID.randomUUID());

        Player other = mock(Player.class);
        when(other.getLocation()).thenReturn(near);
        when(near.distanceSquared(itemAt)).thenReturn(1.0D);

        assertTrue(rules.isContested(player, fresh, List.of(other)));
        assertFalse(rules.isContested(player, fresh, List.of()));

    }

}
