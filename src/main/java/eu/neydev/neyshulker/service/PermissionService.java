package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.PermissionNode;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис прав доступа.
 * Если система прав выключена в конфигурации - разрешено все.
 */
public class PermissionService {

    private final NeyShulkerConfig config;

    public PermissionService(@NotNull NeyShulkerConfig config) {
        this.config = config;
    }

    /**
     * Проверяет наличие права у игрока.
     *
     * @param player проверяемый игрок
     * @param node   узел права
     * @return true если действие разрешено
     */
    public boolean has(@Nullable Player player, @NotNull PermissionNode node) {
        return has((CommandSender) player, node);
    }

    /**
     * Проверяет наличие права у любого отправителя (игрок или консоль).
     *
     * @param sender проверяемый отправитель
     * @param node   узел права
     * @return true если действие разрешено
     */
    public boolean has(@Nullable CommandSender sender, @NotNull PermissionNode node) {

        if (!config.arePermissionsEnabled()) {
            return true;
        }

        if (sender == null) {
            return false;
        }

        if (isOpBypass(sender)) {
            return true;
        }

        return sender.hasPermission(config.getPermission(node));

    }

    /**
     * Попадает ли отправитель под op-bypass: OP-игроки и консоль
     * игнорируют проверки прав при включенном permissions.op_bypass.
     *
     * @param sender отправитель
     * @return true если проверки прав для него не действуют
     */
    private boolean isOpBypass(@Nullable CommandSender sender) {

        return config.isPermissionOpBypass()
                && sender != null
                && (sender.isOp() || sender instanceof ConsoleCommandSender);

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

        if (!config.arePermissionsEnabled()) {
            return false;
        }

        if (isOpBypass(player)) {
            return true;
        }

        return player != null
                && player.hasPermission(config.getPermission(PermissionNode.BYPASS_BLACKLIST));

    }

}
