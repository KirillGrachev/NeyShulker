package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис прав доступа.
 * Если система прав выключена в конфигурации - разрешено все.
 */
public class PermissionService {

    private final ConfigManager configManager;

    public PermissionService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Проверяет наличие права у игрока.
     *
     * @param player проверяемый игрок
     * @param node   узел права
     * @return true если действие разрешено
     */
    public boolean has(@Nullable Player player, @NotNull PermissionNode node) {

        if (!configManager.arePermissionsEnabled()) {
            return true;
        }

        return player != null && player.hasPermission(configManager.getPermission(node));

    }

    /**
     * Проверяет наличие права у любого отправителя (игрок или консоль).
     *
     * @param sender проверяемый отправитель
     * @param node   узел права
     * @return true если действие разрешено
     */
    public boolean has(@Nullable CommandSender sender, @NotNull PermissionNode node) {

        if (!configManager.arePermissionsEnabled()) {
            return true;
        }

        return sender != null && sender.hasPermission(configManager.getPermission(node));

    }

    /**
     * Проверяет право на обход черного списка предметов.
     *
     * Bypass-узлы инверсны функциональным: выключенная система прав означает
     * "фичи доступны всем", но никак не "ограничения сняты со всех".
     * Поэтому обход требует включенную систему прав И наличие права.
     *
     * @param player проверяемый игрок
     * @return true если игрок игнорирует черный список
     */
    public boolean canBypassBlacklist(@Nullable Player player) {

        if (!configManager.arePermissionsEnabled()) {
            return false;
        }

        return player != null
                && player.hasPermission(configManager.getPermission(PermissionNode.BYPASS_BLACKLIST));

    }
}
