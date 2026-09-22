package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис синхронизации содержимого открытого шалкер-бокса.
 *
 * Переносы разрешенных предметов оставлены ванильными (клиент предсказывает
 * их без задержек, быстрые shift-клики не теряются); роль сервиса -
 * отметить изменение и отложить сохранение на следующий тик.
 */
public class ShulkerTransferService {

    private final SessionRegistry sessionRegistry;
    private final ShulkerPersistenceService persistenceService;

    public ShulkerTransferService(@NotNull SessionRegistry sessionRegistry,
                                  @NotNull ShulkerPersistenceService persistenceService) {

        this.sessionRegistry = sessionRegistry;
        this.persistenceService = persistenceService;

    }

    /**
     * Фиксирует обычное изменение содержимого (клик, drag, ванильный shift).
     * Содержимое меняет ванильный обработчик, поэтому достаточно
     * отложить сохранение на следующий тик.
     *
     * @param session изменяемая сессия
     */
    public void markChanged(@Nullable ShulkerSession session) {

        if (!isActive(session)) {
            return;
        }

        persistenceService.scheduleSave(session);

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
