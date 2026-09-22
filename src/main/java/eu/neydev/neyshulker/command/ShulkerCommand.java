package eu.neydev.neyshulker.command;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.service.ShulkerOpenService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Команда /shulker: перезагрузка конфигурации, открытие шалкер-бокса,
 * информация о текущей сессии и переключение автосбора.
 */
public class ShulkerCommand implements TabExecutor {

    private final NeyShulker plugin;
    private final PermissionService permissionService;
    private final MessageService messageService;
    private final ShulkerOpenService openService;
    private final AutoCollectService autoCollectService;

    public ShulkerCommand(@NotNull NeyShulker plugin) {

        this.plugin = plugin;
        this.permissionService = plugin.getServices().getPermissionService();
        this.messageService = plugin.getServices().getMessageService();
        this.openService = plugin.getServices().getOpenService();
        this.autoCollectService = plugin.getServices().getAutoCollectService();

    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             String @NotNull [] args) {

        if (args.length == 0) {
            messageService.send(sender, MessageKey.USAGE, Map.of());
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {

            case "reload" -> handleReload(sender);

            case "open" -> handleOpen(sender);

            case "info" -> handleInfo(sender);

            case "autocollect" -> handleAutoCollect(sender);

            default -> messageService.send(sender, MessageKey.USAGE, Map.of());

        }

        return true;

    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String alias,
                                                String @NotNull [] args) {

        if (args.length == 1) {
            return filter(List.of("reload", "open", "info", "autocollect"), args[0]);
        }

        return List.of();

    }


    private void handleReload(@NotNull CommandSender sender) {

        if (!permissionService.has(sender, PermissionNode.RELOAD)) {
            messageService.send(sender, MessageKey.NO_PERMISSION, Map.of());
            return;
        }

        plugin.getConfigManager().reload();

        messageService.send(sender, MessageKey.RELOAD, Map.of());

    }

    private void handleOpen(@NotNull CommandSender sender) {

        if (!(sender instanceof Player player)) {
            messageService.send(sender, MessageKey.PLAYER_ONLY, Map.of());
            return;
        }

        if (!permissionService.has(player, PermissionNode.USE)) {
            messageService.send(player, MessageKey.NO_PERMISSION);
            return;
        }

        int slot = player.getInventory().getHeldItemSlot();
        ItemStack item = player.getInventory().getItem(slot);

        // Вторая рука - полноценный источник открытия, как и в интеракте
        if (!ShulkerUtil.isShulkerBox(item)) {

            slot = ShulkerUtil.OFF_HAND_SLOT;
            item = player.getInventory().getItem(slot);

        }

        if (!ShulkerUtil.isShulkerBox(item)) {
            messageService.send(player, MessageKey.NO_SHULKER_IN_HAND);
            return;
        }

        if (!openService.open(player, item, slot)) {
            messageService.send(player, MessageKey.OPEN_ERROR);
        }

    }

    private void handleInfo(@NotNull CommandSender sender) {

        if (!(sender instanceof Player player)) {
            messageService.send(sender, MessageKey.PLAYER_ONLY, Map.of());
            return;
        }

        ShulkerSession session = plugin.getServices().getSessionRegistry().getSession(player);

        if (session == null) {

            boolean autoCollectActive = plugin.getConfigManager().isAutoCollectEnabled()
                    && autoCollectService.isEnabledFor(player);

            messageService.send(player, MessageKey.INFO_IDLE, Map.of(
                    "state", state(autoCollectActive),
                    "queue", String.valueOf(autoCollectService.waitListSize(player))));

            return;

        }

        ItemStack shulker = session.shulkerItem();
        int freeSlots = ShulkerUtil.countFreeSlots(shulker);

        messageService.send(player, MessageKey.INFO_SESSION, Map.of(
                "name", session.getShulkerName(),
                "slot", String.valueOf(session.getSlot()),
                "free", String.valueOf(freeSlots),
                "size", String.valueOf(ShulkerUtil.SHULKER_SIZE),
                "items", String.valueOf(ShulkerUtil.countItems(shulker)),
                "seconds", String.valueOf((System.currentTimeMillis() - session.openedAt()) / 1000L)));

    }

    private void handleAutoCollect(@NotNull CommandSender sender) {

        if (!(sender instanceof Player player)) {
            messageService.send(sender, MessageKey.PLAYER_ONLY, Map.of());
            return;
        }

        if (!permissionService.has(player, PermissionNode.AUTO_COLLECT)) {
            messageService.send(player, MessageKey.NO_PERMISSION);
            return;
        }

        boolean enabled = autoCollectService.toggle(player);

        messageService.send(player, enabled ? MessageKey.AUTO_COLLECT_ON : MessageKey.AUTO_COLLECT_OFF);

    }

    private @NotNull List<String> filter(@NotNull List<String> source, @NotNull String token) {

        String prefix = token.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String value : source) {

            if (value.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(value);
            }

        }

        return result;

    }

    /**
     * Готовое слово-статус для плейсхолдера {state}: строки сообщения
     * state_on / state_off собираются через MessageService, чтобы оставались
     * настраиваемыми из конфигурации.
     *
     * @param enabled состояние автосбора
     * @return текст статуса для подстановки
     */
    private @NotNull String state(boolean enabled) {
        return String.join(" ", messageService.build(
                enabled ? MessageKey.STATE_ON : MessageKey.STATE_OFF, Map.of()));
    }
}
