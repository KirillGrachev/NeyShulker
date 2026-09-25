package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Поиск дропов вокруг игрока с фильтрами pickup delay, владельца и радиуса.
 * Сортировка по приоритетным материалам: ценное уходит в бокс первым.
 *
 * Поиск идет через chunk-scoped {@code getNearbyEntities}: на мирах с
 * десятками тысяч item-сущностей (айтем-фермы, массовые сортировки) полный
 * скан мира на каждую волну стоил бы дороже всех вставок вместе взятых.
 */
public final class NearbyItemsFinder {

    private final NeyShulkerConfig config;

    public NearbyItemsFinder(@NotNull NeyShulkerConfig config) {
        this.config = config;
    }

    /**
     * Собирает кандидатов на подбор вокруг игрока.
     *
     * @param player игрок
     * @return отсортированный список дропов в радиусе
     */
    public @NotNull List<Item> find(@NotNull Player player) {

        double maxDistance = config.getAutoCollectMaxDistance();
        double squaredDistance = maxDistance * maxDistance;

        List<Item> items = new ArrayList<>();

        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(),
                maxDistance, maxDistance, maxDistance, Item.class::isInstance)) {

            if (!(entity instanceof Item item)) {
                continue;
            }

            if (item.isDead() || !item.isValid()) {
                continue;
            }

            if (!config.isAutoCollectIgnorePickupDelay() && item.getPickupDelay() > 0) {
                continue;
            }

            UUID owner = item.getOwner();

            if (owner != null && !owner.equals(player.getUniqueId())) {
                continue;
            }

            // AABB-окно getNearbyEntities шире сферы радиуса: финальная сверка по точному расстоянию
            if (item.getLocation().distanceSquared(player.getLocation()) > squaredDistance) {
                continue;
            }

            items.add(item);

        }

        sortByPriority(items);
        return items;

    }

    private void sortByPriority(@NotNull List<Item> items) {

        List<Material> priority = config.getAutoCollectPriorityItems();

        if (priority.isEmpty() || items.size() < 2) {
            return;
        }

        // EnumSet вместо List.contains в компараторе: O(1) на проверку
        Set<Material> prioritized = priority.isEmpty()
                ? EnumSet.noneOf(Material.class)
                : EnumSet.copyOf(priority);

        items.sort((first, second) -> Boolean.compare(
                prioritized.contains(second.getItemStack().getType()),
                prioritized.contains(first.getItemStack().getType())
        ));

    }

}
