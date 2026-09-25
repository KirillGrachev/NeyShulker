package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.util.FakeItemStack;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Поиск дропов: chunk-scoped скан вместо мирового, фильтры живости,
 * pickup delay, владельца и радиуса, приоритетная сортировка.
 */
class NearbyItemsFinderTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final NearbyItemsFinder finder = new NearbyItemsFinder(configManager);

    private final Player player = mock(Player.class);
    private final World world = mock(World.class);
    private final Location playerLocation = mock(Location.class);
    private final UUID playerId = UUID.randomUUID();

    private Item drop(Material material, int amount, double distanceSquared) {

        Item item = mock(Item.class);
        Location location = mock(Location.class);

        when(item.isDead()).thenReturn(false);
        when(item.isValid()).thenReturn(true);
        when(item.getPickupDelay()).thenReturn(0);
        when(item.getOwner()).thenReturn(null);
        when(item.getItemStack()).thenReturn(new FakeItemStack(material, amount));
        when(item.getLocation()).thenReturn(location);
        when(location.distanceSquared(playerLocation)).thenReturn(distanceSquared);
        return item;

    }

    private void stubWorld(Item... items) {

        when(configManager.getAutoCollectMaxDistance()).thenReturn(3.0D);
        when(configManager.isAutoCollectIgnorePickupDelay()).thenReturn(false);
        when(configManager.getAutoCollectPriorityItems()).thenReturn(List.of());
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(playerLocation);
        when(player.getUniqueId()).thenReturn(playerId);
        when(world.getNearbyEntities(any(Location.class), anyDouble(), anyDouble(), anyDouble(), any()))
                .thenReturn(List.<Entity>of((Entity[]) items));

    }

    @Test
    @DisplayName("Скан идет через getNearbyEntities с радиусом из конфига")
    void scansThroughNearbyEntities() {

        Item item = drop(Material.DIAMOND, 1, 1.0D);
        stubWorld(item);

        List<Item> found = finder.find(player);

        assertEquals(List.of(item), found);
        org.mockito.Mockito.verify(world)
                .getNearbyEntities(eq(playerLocation), eq(3.0D), eq(3.0D), eq(3.0D), any());

    }

    @Test
    @DisplayName("Мертвые, невалидные и дальние дропы отфильтрованы")
    void deadAndFarItemsFiltered() {

        Item dead = drop(Material.DIAMOND, 1, 1.0D);
        Item far = drop(Material.DIAMOND, 1, 25.0D);
        Item good = drop(Material.DIAMOND, 1, 4.0D);

        when(dead.isDead()).thenReturn(true);
        stubWorld(dead, far, good);

        List<Item> found = finder.find(player);

        assertEquals(1, found.size());
        assertSame(good, found.get(0));

    }

    @Test
    @DisplayName("Pickup delay уважается, если конфиг не велел игнорировать")
    void pickupDelayFiltered() {

        Item delayed = drop(Material.DIAMOND, 1, 1.0D);
        when(delayed.getPickupDelay()).thenReturn(10);

        stubWorld(delayed);
        assertTrue(finder.find(player).isEmpty());

        when(configManager.isAutoCollectIgnorePickupDelay()).thenReturn(true);
        assertEquals(1, finder.find(player).size());

    }

    @Test
    @DisplayName("Чужой owner отсекается, свой и отсутствующий проходят")
    void ownerFilter() {

        Item ownedByOther = drop(Material.DIAMOND, 1, 1.0D);
        Item ownedByMe = drop(Material.DIAMOND, 1, 1.0D);
        Item noOwner = drop(Material.DIAMOND, 1, 1.0D);

        when(ownedByOther.getOwner()).thenReturn(UUID.randomUUID());
        when(ownedByMe.getOwner()).thenReturn(playerId);

        stubWorld(ownedByOther, ownedByMe, noOwner);

        List<Item> found = finder.find(player);

        assertEquals(2, found.size());
        assertTrue(found.contains(ownedByMe));
        assertTrue(found.contains(noOwner));

    }

    @Test
    @DisplayName("Приоритетные материалы сортируются первыми")
    void priorityMaterialsFirst() {

        Item dirt = drop(Material.DIRT, 1, 1.0D);
        Item diamond = drop(Material.DIAMOND, 1, 1.0D);
        Item cobble = drop(Material.COBBLESTONE, 1, 1.0D);

        stubWorld(dirt, cobble, diamond);
        when(configManager.getAutoCollectPriorityItems())
                .thenReturn(List.of(Material.DIAMOND, Material.NETHERITE_INGOT));

        List<Item> found = finder.find(player);

        assertSame(diamond, found.get(0), "Приоритетный дроп первым");
        assertEquals(3, found.size());

    }

}
