package eu.neydev.neyshulker.service.collect;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Правила допуска автосбора: гейты игрока и допустимость предметов.
 * Вынесены из оркестратора, чтобы политики читались отдельно от механики.
 */
public final class CollectRules {

    private final ConfigManager configManager;
    private final PermissionService permissionService;

    public CollectRules(@NotNull ConfigManager configManager,
                        @NotNull PermissionService permissionService) {

        this.configManager = configManager;
        this.permissionService = permissionService;

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
     * Допустим ли предмет для сбора в шалкер.
     *
     * @param player игрок (для проверки bypass-права)
     * @param stack  предмет дропа
     * @return true если предмет можно положить
     */
    public boolean isCollectable(@NotNull Player player, @NotNull ItemStack stack) {

        Material material = stack.getType();

        // Шалкер в шалкере не поддерживается: авто-сбор тоже не трогает боксы
        if (ShulkerUtil.isShulkerBox(stack)) {
            return false;
        }

        if (configManager.isAutoCollectBlacklisted(material)) {
            return false;
        }

        if (configManager.isBlacklistEnabled() && configManager.isBlacklisted(material)
                && !permissionService.canBypassBlacklist(player)) {
            return false;
        }

        return true;

    }

    /**
     * Гейт полного инвентаря.
     *
     * @param player игрок
     * @return true если сбор разрешен при текущей заполненности
     */
    public boolean passesInventoryGate(@NotNull Player player) {

        if (!configManager.isAutoCollectOnlyWhenInventoryFull()) {
            return true;
        }

        for (ItemStack item : player.getInventory().getStorageContents()) {

            if (ShulkerUtil.isEmpty(item)) {
                return false;
            }

        }

        return true;

    }
}
