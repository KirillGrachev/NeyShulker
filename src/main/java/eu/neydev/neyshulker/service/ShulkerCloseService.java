package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.event.ShulkerCloseEvent;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис закрытия шалкер-бокса.
 * Порядок операций фиксирован: сначала сохраняем содержимое в предмет,
 * затем снимаем сессию и только после этого уведомляем остальных.
 * Событие закрытия получает фактический предмет после сохранения,
 * а не слепок открытия.
 */
public class ShulkerCloseService {

    private final NeyShulker plugin;
    private final SessionRegistry sessionRegistry;
    private final ShulkerPersistenceService persistenceService;
    private final SoundService soundService;

    public ShulkerCloseService(@NotNull NeyShulker plugin,
                               @NotNull SessionRegistry sessionRegistry,
                               @NotNull ShulkerPersistenceService persistenceService,
                               @NotNull SoundService soundService) {

        this.plugin = plugin;
        this.sessionRegistry = sessionRegistry;
        this.persistenceService = persistenceService;
        this.soundService = soundService;

    }

    /**
     * Закрывает сессию игрока, если она существует.
     *
     * @param player игрок
     * @return true если сессия была закрыта
     */
    public boolean close(@Nullable Player player) {

        if (player == null) {
            return false;
        }

        return close(player, sessionRegistry.getSession(player));

    }

    /**
     * Закрывает конкретную сессию.
     *
     * @param player  игрок
     * @param session закрываемая сессия
     * @return true если сессия была закрыта
     */
    public boolean close(@Nullable Player player, @Nullable ShulkerSession session) {

        if (player == null || session == null) {
            return false;
        }

        // Повторное закрытие (InventoryCloseEvent + PlayerQuitEvent) не должно ничего ломать
        if (sessionRegistry.getSession(player.getUniqueId()) != session) {
            return false;
        }

        persistenceService.cancelAutoSave(session);
        persistenceService.persist(session, false);

        ItemStack saved = persistenceService.finalizeSession(session);

        sessionRegistry.closeSession(player.getUniqueId());

        callCloseEvent(player, session, saved);
        soundService.playClose(player);
        return true;

    }

    /**
     * Закрывает все открытые сессии.
     * Используется при выключении плагина.
     */
    public void closeAll() {

        for (ShulkerSession session : sessionRegistry.getSessions()) {

            Player player = session.getPlayer();

            if (player != null && player.isOnline()) {
                player.closeInventory();
            }

            // Если слушатель закрытия не сработал - закрываем принудительно
            if (sessionRegistry.getSession(session.playerId()) == session) {
                persistenceService.persist(session, false);
                sessionRegistry.closeSession(session.playerId());
            }

        }

        persistenceService.cancelAllAutoSaves();
        sessionRegistry.clear();

        plugin.getLogger().info("Open shulker boxes have been saved and closed.");

    }

    private void callCloseEvent(@NotNull Player player,
                                @NotNull ShulkerSession session,
                                @NotNull ItemStack saved) {

        plugin.getServer().getPluginManager().callEvent(
                new ShulkerCloseEvent(player, saved, session.getSlot()));

    }

}
