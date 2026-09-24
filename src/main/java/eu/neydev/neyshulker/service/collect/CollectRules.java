package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Правила допуска автосбора: гейты игрока и допустимость предметов.
 * Вынесены из оркестратора, чтобы политики читались отдельно от механики.
 */
public final class CollectRules {

    private final ConfigManager configManager;
    private final PermissionService permissionService;
    private final PlayerDropTracker dropTracker;

    public CollectRules(@NotNull ConfigManager configManager,
                        @NotNull PermissionService permissionService,
                        @NotNull PlayerDropTracker dropTracker) {

        this.configManager = configManager;
        this.permissionService = permissionService;
        this.dropTracker = dropTracker;

    }

    /**
     * Проверяет: считается ли дроп спорным, то есть чужим.
     *
     * Спорный предмет не всасывается: выброшенные игроком вещи трогать нельзя
     * (передача или намеренный сброс), а лут, у которого стоит другой игрок,
     * считается его добычей (спавн, чужая ферма).
     *
     * @param collector собирающий игрок
     * @param item      дроп на земле
     * @return true, если дроп надо пропустить
     */
    public boolean isContested(@NotNull Player collector, @NotNull Item item) {

        if (configManager.isAutoCollectIgnorePlayerDropped() && isPlayerThrown(item)) {
            return true;
        }

        double radius = configManager.getAutoCollectRespectNearbyPlayers();

        if (radius <= 0D) {
            return false;
        }

        Location at = item.getLocation();

        for (Player other : collector.getWorld().getPlayers()) {

            if (other == collector || !other.isOnline()) {
                continue;
            }

            if (other.getLocation().distanceSquared(at) <= radius * radius) {
                return true;
            }
        }

        return false;
    }

    /**
     * Проверяет: выброшен ли предмет игроком (памятка или ванильный флаг владельца).
     */
    private boolean isPlayerThrown(@NotNull Item item) {

        if (dropTracker.isPlayerDropped(item)) {
            return true;
        }

        UUID owner = item.getOwner();
        return owner != null;
    }

    /**
     * Может ли игрок участвовать в автосборе в этом состоянии.
     *
     * @param player        игрок
     * @param enabledForPlayer per-player тумблер игрока
     * @return true если сбор для игрока активен
     */
    public boolean canCollectPlayer(@Nullable Player player, boolean enabledForPlayer) {

        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        if (!configManager.isPluginEnabled() || !configManager.isAutoCollectEnabled()) {
            return false;
        }

        if (player.getGameMode() == GameMode.SPECTATOR || player.getGameMode() == GameMode.CREATIVE) {
            return false;
        }

        if (!enabledForPlayer) {
            return false;
        }

        return !configManager.isAutoCollectPermissionRequired()
                || permissionService.has(player, PermissionNode.AUTO_COLLECT);

    }

    /**
     * Исключен ли предмет из сбора.
     *
     * @param player игрок (для проверки bypass-права)
     * @param stack  предмет дропа
     * @return true если предмет собирать нельзя
     */
    public boolean isExcluded(@NotNull Player player, @NotNull ItemStack stack) {

        Material material = stack.getType();

        // Шалкер в шалкере не поддерживается: авто-сбор тоже не трогает боксы
        if (ShulkerUtil.isShulkerBox(stack)) {
            return true;
        }

        if (configManager.isAutoCollectBlacklisted(material)) {
            return true;
        }

        return configManager.isBlacklistEnabled()
                && configManager.isBlacklisted(material)
                && !permissionService.canBypassBlacklist(player);

    }

    /**
     * Гейт полного инвентаря.
     *
     * @param player игрок
     * @return true если инвентарь еще не полон и сбор запрещен гейтом
     */
    public boolean failsInventoryGate(@NotNull Player player) {

        if (!configManager.isAutoCollectOnlyWhenInventoryFull()) {
            return false;
        }

        for (ItemStack item : player.getInventory().getStorageContents()) {

            if (ShulkerUtil.isEmpty(item)) {
                return true;
            }

        }

        return false;

    }
}
