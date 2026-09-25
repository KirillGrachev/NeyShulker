package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.ValidationReason;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.model.ValidationResult;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.SessionTagger;
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
 *
 * Идентификация открытого бокса идет по метке сессии в PersistentDataContainer
 * предмета (см. {@link SessionTagger}): сравнение меты со слепком открытия
 * переставало работать после первого же автосохранения, а сравнение материала
 * не отличало бокс-близнец. Legacy-проверки по слоту остаются только для
 * немеченых предметов (тестовые заглушки без ItemMeta).
 */
public class ShulkerValidationService {

    private final NeyShulkerConfig config;
    private final PermissionService permissionService;
    private final SessionRegistry sessionRegistry;

    public ShulkerValidationService(@NotNull NeyShulkerConfig config,
                                    @NotNull PermissionService permissionService,
                                    @NotNull SessionRegistry sessionRegistry) {

        this.config = config;
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

        ValidationResult common = canOpenCommon(player, item);

        if (!common.isAllowed()) {
            return common;
        }

        if (!matchesOpenMethod(player, action)) {
            return ValidationResult.denied(ValidationReason.METHOD_MISMATCH);
        }

        return ValidationResult.allowed();

    }

    /**
     * Проверяет открытие шалкер-бокса командой: мастер-выключатель, права
     * и черный список, но без проверки способа открытия (команда - явное
     * намерение, open_method к ней неприменим).
     *
     * @param player игрок
     * @param item   предмет в руке
     * @return результат проверки
     */
    public @NotNull ValidationResult canOpenViaCommand(@NotNull Player player,
                                                       @Nullable ItemStack item) {
        return canOpenCommon(player, item);
    }

    private @NotNull ValidationResult canOpenCommon(@NotNull Player player,
                                                    @Nullable ItemStack item) {

        if (!config.isPluginEnabled()) {
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

        return ValidationResult.allowed();

    }

    /**
     * Проверяет, можно ли положить предмет в шалкер-бокс.
     *
     * @param player       игрок
     * @param item         проверяемый предмет
     * @return результат проверки
     */
    public @NotNull ValidationResult canEnterShulker(@NotNull Player player,
                                                     @Nullable ItemStack item) {

        if (item == null) {
            return ValidationResult.allowed();
        }

        if (ShulkerUtil.isShulkerBox(item)) {

            // Сам открытый шалкер-бокс нельзя никуда перекладывать
            if (isOpenShulker(player, item)) {
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

        if (session == null) {
            return false;
        }

        // Метка сессии не устаревает: автосохранения переносят ее вместе с метой
        if (SessionTagger.isSession(item, session.sessionId())) {
            return true;
        }

        // Legacy-ветка для немеченых предметов (слепок сессии без ItemMeta)
        return SessionTagger.sessionIdOf(session.shulkerItem()) == null
                && item.isSimilar(session.shulkerItem());

    }

    /**
     * Проверяет, пытается ли игрок выбросить или обменять открытый шалкер-бокс.
     *
     * Опознавание идет по слоту сессии и типу материала: сохранения
     * перезаписывают содержимое предмета, и сравнение меты переставало
     * узнавать собственный бокс - на этом строился дюп через Q.
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
     * Основной критерий - метка сессии на самом выброшенном предмете:
     * она не зависит от слота и переживает любые перемещения бокса.
     * Legacy-ветка (немеченые предметы) сверяется с живым стеком слота
     * сессии, а не со слепком открытия.
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

        if (SessionTagger.isSession(item, session.sessionId())) {
            return true;
        }

        if (SessionTagger.sessionIdOf(session.shulkerItem()) != null) {
            // Сессия меченая, а на выброшенном предмете метки нет - это не наш бокс
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

        if (item == null || !config.isBlacklistEnabled()) {
            return false;
        }

        return config.isBlacklisted(item.getType())
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

        return switch (config.getOpenMethod()) {

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
