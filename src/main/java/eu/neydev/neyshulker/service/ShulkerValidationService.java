package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.ValidationReason;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
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
     * @return результат проверки
     */
    public @NotNull ValidationResult canOpen(@NotNull Player player,
                                             @Nullable ItemStack item,
                                             @NotNull Action action) {

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

        if (!matchesOpenMethod(player, action)) {
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

            // Шалкер в шалкере не поддерживается вовсе: содержимое внутреннего
            // бокса недоступно ни игроку, ни плагину, пока жив внешний
            return ValidationResult.denied(ValidationReason.NESTED_SHULKER);

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
     * Проверяет, пытается ли игрок выбросить или обменять открытый шалкер-бокс.
     *
     * Опознавание идет по слоту сессии и типу материала, а не по isSimilar:
     * сохранения перезаписывают содержимое предмета, и сравнение меты
     * переставало узнавать собственный бокс - на этом строился дюп через Q.
     *
     * @param player игрок
     * @return true если в руке игрока лежит открытый шалкер-бокс
     */
    public boolean isHeldOpenShulker(@NotNull Player player) {

        ShulkerSession session = sessionRegistry.getSession(player);

        if (session == null) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();

        if (inventory.getHeldItemSlot() != session.getSlot()) {
            return false;
        }

        return ShulkerUtil.isShulkerBox(inventory.getItem(session.getSlot()));

    }

    /**
     * Проверяет, является ли выброшенный предмет самим открытым боксом.
     *
     * Сравнение идет с живым стеком слота сессии, а не со слепком открытия:
     * автосохранения перезаписывают мету, и слепок переставал узнавать бокс.
     * Ваниль не может выбросить бокс мимо кликовых проверок (слот заблокирован
     * на уровне InventoryClickEvent), поэтому сюда доходят только выбросы
     * чужими путями - командами и сторонними плагинами.
     *
     * @param player игрок
     * @param item   выброшенный предмет
     * @return true если это тот самый открытый шалкер-бокс
     */
    public boolean isDroppedOpenShulker(@NotNull Player player, @Nullable ItemStack item) {

        if (!ShulkerUtil.isShulkerBox(item)) {
            return false;
        }

        ShulkerSession session = sessionRegistry.getSession(player);

        if (session == null) {
            return false;
        }

        ItemStack inSlot = player.getInventory().getItem(session.getSlot());
        return ShulkerUtil.isShulkerBox(inSlot) && item.isSimilar(inSlot);

    }

    /**
     * Проверяет, заденет ли обмен руками (F) открытый шалкер-бокс.
     * Обмен всегда затрагивает вторую руку, поэтому сессия во второй руке
     * блокируется целиком, а сессия в основной - только когда бокс в ней.
     *
     * @param player игрок
     * @return true если обмен нужно отменить
     */
    public boolean isSwapTouchingOpenShulker(@NotNull Player player) {

        ShulkerSession session = sessionRegistry.getSession(player);

        if (session == null) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();

        if (session.getSlot() == ShulkerUtil.OFF_HAND_SLOT) {
            return ShulkerUtil.isShulkerBox(inventory.getItem(ShulkerUtil.OFF_HAND_SLOT));
        }

        return isHeldOpenShulker(player);

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
     *
     * AIR и SMART держат клики по блокам ванильными, поэтому установка
     * шалкера работает всегда; SHIFT требует явный модификатор.
     */
    private boolean matchesOpenMethod(@NotNull Player player, @NotNull Action action) {

        boolean sneaking = player.isSneaking();
        boolean rightClickAir = action == Action.RIGHT_CLICK_AIR;

        return switch (configManager.getOpenMethod()) {

            // По воздуху нет ванильного взаимодействия - открытие ничего не крадет
            case AIR -> rightClickAir;

            case SHIFT -> sneaking;
            case NO_SHIFT -> !sneaking;
            case ALWAYS -> true;

            // Shift - явный модификатор "нужен GUI", воздух - быстрый путь
            case SMART -> sneaking || rightClickAir;
        };

    }

}
