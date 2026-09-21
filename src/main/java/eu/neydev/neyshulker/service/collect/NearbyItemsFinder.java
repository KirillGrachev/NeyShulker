package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Поиск дропов вокруг игрока с фильтрами pickup delay, владельца и радиуса.
 * Сортировка по приоритетным материалам: ценное уходит в бокс первым.
 */
public final class NearbyItemsFinder {

    private final ConfigManager configManager;

    public NearbyItemsFinder(@NotNull ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Собирает кандидатов на подбор вокруг игрока.
     *
     * @param player игрок
     * @return отсортированный список дропов в радиусе
     */
    public @NotNull List<Item> find(@NotNull Player player) {

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
}
