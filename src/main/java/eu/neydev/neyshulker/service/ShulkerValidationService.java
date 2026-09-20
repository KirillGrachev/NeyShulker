package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.ValidationReason;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис всех проверок плагина: открытие, вложение предметов, защита открытого шалкера.
 * Не отправляет сообщения и не меняет состояние - только принимает решение.
 */
public class ShulkerValidationService {

    private final ConfigManager configManager;
    private final PermissionService permissionService;
    private final SessionRegistry sessionRegistry;

    public ShulkerValidationService(@NotNull ConfigManager configManager,
                                    @NotNull PermissionService permissionService,
                                    @NotNull SessionRegistry sessionRegistry) {

        this.configManager = configManager;
        this.permissionService = permissionService;
        this.sessionRegistry = sessionRegistry;

    }

    /**
     * Проверяет возможность открытия шалкер-бокса из руки игрока.
     *
     * @param player       игрок
     * @param item         предмет в руке
     * @param action       действие взаимодействия
     * @param clickedBlock блок, по которому кликнули (может быть null)
     * @return результат проверки
     */
    public @NotNull ValidationResult canOpen(@NotNull Player player,
                                             @Nullable ItemStack item,
                                             @NotNull Action action,
                                             @Nullable Block clickedBlock) {

        if (!configManager.isPluginEnabled()) {
            return ValidationResult.denied(ValidationReason.PLUGIN_DISABLED);
        }

        if (!ShulkerUtil.isShulkerBox(item)) {
            return ValidationResult.denied(ValidationReason.NOT_SHULKER);
        }

        if (!permissionService.has(player, PermissionNode.USE)) {
            return ValidationResult.denied(ValidationReason.NO_PERMISSION);
        }

        if (isBlacklistedFor(player, item)) {
            return ValidationResult.denied(ValidationReason.BLACKLISTED);
        }

        if (!matchesOpenMethod(player, action, clickedBlock)) {
            return ValidationResult.denied(ValidationReason.METHOD_MISMATCH);
        }

        return ValidationResult.allowed();

    }

    /**
     * Проверяет, можно ли положить предмет в шалкер-бокс.
     *
     * @param player игрок
     * @param item   проверяемый предмет
     * @return результат проверки
     */
    public @NotNull ValidationResult canEnterShulker(@NotNull Player player,
                                                     @Nullable ItemStack item) {

        if (item == null) {
            return ValidationResult.allowed();
        }

        if (ShulkerUtil.isShulkerBox(item)) {

            ShulkerSession session = sessionRegistry.getSession(player);

            // Сам открытый шалкер-бокс нельзя никуда перекладывать
            if (session != null && item.isSimilar(session.shulkerItem())) {
                return ValidationResult.denied(ValidationReason.OPEN_SHULKER);
            }

            if (configManager.isNestedPrevented()) {
                return ValidationResult.denied(ValidationReason.NESTED_SHULKER);
            }

            return ValidationResult.allowed();

        }

        if (isBlacklistedFor(player, item)) {
            return ValidationResult.denied(ValidationReason.BLACKLISTED);
        }

        return ValidationResult.allowed();

    }

    /**
     * Проверяет, является ли предмет открытым шалкер-боксом игрока.
     * Используется для защиты от перемещения, выбрасывания и обмена руками.
     *
     * @param player игрок
     * @param item   проверяемый предмет
     * @return true если это тот самый открытый шалкер-бокс
     */
    public boolean isOpenShulker(@NotNull Player player, @Nullable ItemStack item) {

        if (!ShulkerUtil.isShulkerBox(item)) {
            return false;
        }

        ShulkerSession session = sessionRegistry.getSession(player);

        return session != null && item.isSimilar(session.shulkerItem());

    }

    /**
     * Проверяет, указывает ли слот на сам открытый шалкер-бокс.
     *
     * @param session      сессия игрока
     * @param bottomSlot   слот в нижнем инвентаре (0..35), -1 если клик не там
     * @return true если слот заблокирован
     */
    public boolean isShulkerSlot(@Nullable ShulkerSession session, int bottomSlot) {
        return session != null && bottomSlot >= 0 && bottomSlot == session.getSlot();
    }

    private boolean isBlacklistedFor(@NotNull Player player, @Nullable ItemStack item) {

        if (item == null || !configManager.isBlacklistEnabled()) {
            return false;
        }

        return configManager.isBlacklisted(item.getType())
                && !permissionService.canBypassBlacklist(player);

    }

    /**
     * Сверяет клик с настроенным способом открытия.
     * SHIFT - единственный режим, при котором шалкер можно свободно ставить:
     * крадущийся игрок не размещает блок.
     */
    private boolean matchesOpenMethod(@NotNull Player player,
                                      @NotNull Action action,
                                      @Nullable Block clickedBlock) {

        boolean sneaking = player.isSneaking();

        return switch (configManager.getOpenMethod()) {

            case SHIFT -> sneaking;

            case NO_SHIFT -> !sneaking;

            case ALWAYS -> true;

            case SMART -> sneaking || isOpenableWithoutSneak(action, clickedBlock);

        };

    }

    private boolean isOpenableWithoutSneak(@NotNull Action action, @Nullable Block clickedBlock) {

        if (action == Action.RIGHT_CLICK_AIR) {
            return true;
        }

        if (action != Action.RIGHT_CLICK_BLOCK || clickedBlock == null) {
            return false;
        }

        // Не мешаем ванильному взаимодействию: сундуки, двери, кнопки, столы и т.д.
        return !clickedBlock.getType().isInteractable();

    }
}
