package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.SoundKey;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * Сервис переноса предметов между шалкер-боксом и инвентарем игрока.
 *
 * Shift-клик обрабатывается вручную: ванильное перемещение отменяется,
 * а предмет переносится одной транзакцией с немедленным сохранением.
 * Это исключает ситуацию, когда клиент и сервер расходятся в количестве предметов.
 */
public class ShulkerTransferService {

    private final ConfigManager configManager;
    private final SessionRegistry sessionRegistry;
    private final InventoryTransferService transferService;
    private final ShulkerPersistenceService persistenceService;
    private final MessageService messageService;
    private final SoundService soundService;

    public ShulkerTransferService(@NotNull ConfigManager configManager,
                                  @NotNull SessionRegistry sessionRegistry,
                                  @NotNull InventoryTransferService transferService,
                                  @NotNull ShulkerPersistenceService persistenceService,
                                  @NotNull MessageService messageService,
                                  @NotNull SoundService soundService) {

        this.configManager = configManager;
        this.sessionRegistry = sessionRegistry;
        this.transferService = transferService;
        this.persistenceService = persistenceService;
        this.messageService = messageService;
        this.soundService = soundService;

    }

    /**
     * Переносит стек из нижнего инвентаря игрока в шалкер-бокс.
     *
     * @param session    сессия открытого шалкера
     * @param bottom     нижний инвентарь представления
     * @param sourceSlot слот игрока
     * @return true если хотя бы один предмет перемещен
     */
    public boolean shiftIntoShulker(@Nullable ShulkerSession session,
                                    @NotNull Inventory bottom,
                                    int sourceSlot) {

        if (!isActive(session)) {
            return false;
        }

        int moved = transferService.moveFirst(session.getPlayer(),
                bottom, sourceSlot, session.inventory(), Integer.MAX_VALUE);

        if (moved <= 0) {
            messageService.send(session.getPlayer(), MessageKey.NO_SPACE, Map.of());
            return false;
        }

        afterTransfer(session);

        return true;

    }

    /**
     * Переносит стек из шалкер-бокса в инвентарь игрока.
     *
     * @param session    сессия открытого шалкера
     * @param bottom     нижний инвентарь представления
     * @param sourceSlot слот шалкер-бокса
     * @return true если хотя бы один предмет перемещен
     */
    public boolean shiftFromShulker(@Nullable ShulkerSession session,
                                    @NotNull Inventory bottom,
                                    int sourceSlot) {

        if (!isActive(session)) {
            return false;
        }

        int moved = transferService.moveFirst(session.getPlayer(),
                session.inventory(), sourceSlot, bottom, Integer.MAX_VALUE);

        if (moved <= 0) {
            messageService.send(session.getPlayer(), MessageKey.NO_SPACE, Map.of());
            return false;
        }

        afterTransfer(session);

        return true;

    }

    /**
     * Фиксирует обычное изменение содержимого (клик, drag, перенос курсором).
     * Содержимое меняется ванильным обработчиком, поэтому достаточно
     * отложить сохранение на следующий тик.
     *
     * @param session изменяемая сессия
     */
    public void markChanged(@Nullable ShulkerSession session) {

        if (!isActive(session)) {
            return;
        }

        persistenceService.markAndSchedule(session);

    }

    /**
     * Фиксирует перенос, выполненный плагином: немедленное сохранение плюс звук.
     *
     * @param session изменяемая сессия
     */
    public void afterTransfer(@Nullable ShulkerSession session) {

        if (!isActive(session)) {
            return;
        }

        session.markModified();
        persistenceService.persist(session, false);
        soundService.play(session.getPlayer(), SoundKey.COLLECT);

    }

    private boolean isActive(@Nullable ShulkerSession session) {

        if (session == null) {
            return false;
        }

        Player player = session.getPlayer();

        return player != null && player.isOnline()
                && sessionRegistry.getSession(player.getUniqueId()) == session;

    }
}
