package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Правила допуска автосбора: гейты игрока и допустимость предметов.
 * Вынесены из оркестратора, чтобы политики читались отдельно от механики.
 */
public final class CollectRules {

    /** Дефолт, если конфигурация не отдает режимы (старые моки/снапшоты). */
    private static final Set<GameMode> DEFAULT_GAME_MODES =
            Collections.unmodifiableSet(EnumSet.of(GameMode.SURVIVAL, GameMode.ADVENTURE));

    private final NeyShulkerConfig config;
    private final PermissionService permissionService;
    private final PlayerDropTracker dropTracker;

    public CollectRules(@NotNull NeyShulkerConfig config,
                        @NotNull PermissionService permissionService,
                        @NotNull PlayerDropTracker dropTracker) {

        this.config = config;
        this.permissionService = permissionService;
        this.dropTracker = dropTracker;

    }

    /**
     * Соседи игрока, которые могут претендовать на дроп вокруг него.
     *
     * Легкий аналог кэша на фазу: список строится один раз на игрока
     * (детекция или слив), а проверка каждого предмета идет по короткому
     * списку вместо полного перебора игроков мира на каждый дроп.
     * Радиус - сумма дистанции сбора и радиуса уважения: предмет в пределах
     * дистанции сбора не может быть ближе к дальнему игроку, чем этот запас.
     */
    public @NotNull List<Player> nearbyOthers(@NotNull Player collector) {

        double radius = config.getAutoCollectRespectNearbyPlayers();

        if (radius <= 0D) {
            return List.of();
        }

        double reach = config.getAutoCollectMaxDistance() + radius;
        double squaredReach = reach * reach;

        Location at = collector.getLocation();
        List<Player> candidates = new ArrayList<>();

        for (Player other : collector.getWorld().getPlayers()) {

            if (other == collector || !other.isOnline()) {
                continue;
            }

            if (other.getLocation().distanceSquared(at) <= squaredReach) {
                candidates.add(other);
            }

        }

        return candidates;

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
        return isContested(collector, item, nearbyOthers(collector));
    }

    /**
     * Проверяет спорность дропа по заранее построенному списку соседей.
     *
     * @param collector собирающий игрок
     * @param item      дроп на земле
     * @param others    кандидаты-соседи из {@link #nearbyOthers}
     * @return true, если дроп надо пропустить
     */
    public boolean isContested(@NotNull Player collector,
                               @NotNull Item item,
                               @NotNull List<Player> others) {

        if (config.isAutoCollectIgnorePlayerDropped() && isPlayerThrown(item)) {
            return true;
        }

        double radius = config.getAutoCollectRespectNearbyPlayers();

        if (radius <= 0D || others.isEmpty()) {
            return false;
        }

        Location at = item.getLocation();

        for (Player other : others) {
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
     * @param player           игрок
     * @param enabledForPlayer per-player тумблер игрока
     * @return true если сбор для игрока активен
     */
    public boolean canCollectPlayer(@Nullable Player player, boolean enabledForPlayer) {

        if (player == null || !player.isOnline() || player.isDead()) {
            return false;
        }

        if (!config.isPluginEnabled() || !config.isAutoCollectEnabled()) {
            return false;
        }

        if (!allowedGameModes().contains(player.getGameMode())) {
            return false;
        }

        if (!enabledForPlayer) {
            return false;
        }

        return !config.isAutoCollectPermissionRequired()
                || permissionService.has(player, PermissionNode.AUTO_COLLECT);

    }

    private @NotNull Set<GameMode> allowedGameModes() {

        Set<GameMode> modes = config.getAutoCollectGameModes();
        return modes == null || modes.isEmpty() ? DEFAULT_GAME_MODES : modes;

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

        if (config.isAutoCollectBlacklisted(material)) {
            return true;
        }

        return config.isBlacklistEnabled()
                && config.isBlacklisted(material)
                && !permissionService.canBypassBlacklist(player);

    }

    /**
     * Гейт полного инвентаря.
     *
     * @param player игрок
     * @return true если инвентарь еще не полон и сбор запрещен гейтом
     */
    public boolean failsInventoryGate(@NotNull Player player) {

        if (!config.isAutoCollectOnlyWhenInventoryFull()) {
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
