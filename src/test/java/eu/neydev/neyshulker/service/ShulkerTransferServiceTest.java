package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.TestInventories;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка сервиса синхронизации: активная сессия отмечается измененной,
 * чужая, снятая и оффлайн - нет.
 */
class ShulkerTransferServiceTest {

    private final SessionRegistry sessionRegistry = mock(SessionRegistry.class);
    private final ShulkerPersistenceService persistenceService = mock(ShulkerPersistenceService.class);

    private final ShulkerTransferService transferService =
            new ShulkerTransferService(sessionRegistry, persistenceService);

    private final Player player = player();

    private static Player player() {

        Player player = mock(Player.class);

        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.isOnline()).thenReturn(true);
        return player;

    }

    private ShulkerSession session() {

        ItemStack shulker = mock(ItemStack.class);
        when(shulker.clone()).thenReturn(shulker);

        return ShulkerSession.create(UUID.randomUUID(), player, shulker,
                () -> TestInventories.inventory(27), 2);

    }

    @Test
    @DisplayName("Изменение активной сессии планирует сохранение")
    void markChangedSchedulesSaveForActiveSession() {

        ShulkerSession session = session();
        when(sessionRegistry.getSession(player.getUniqueId())).thenReturn(session);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            transferService.markChanged(session);

        }

        verify(persistenceService).scheduleSave(session);

    }

    @Test
    @DisplayName("Снятая с реестра и null-сессия сохранение не планируют")
    void staleSessionsAreIgnored() {

        ShulkerSession session = session();

        // В реестре пусто: сессия уже закрыта другим путем
        when(sessionRegistry.getSession(player.getUniqueId())).thenReturn(null);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(player);
            transferService.markChanged(session);

        }

        transferService.markChanged(null);

        verify(persistenceService, never()).scheduleSave(any());

    }

    @Test
    @DisplayName("Оффлайн-владелец: отметка изменения игнорируется")
    void offlineOwnerIsIgnored() {

        ShulkerSession session = session();
        when(sessionRegistry.getSession(player.getUniqueId())).thenReturn(session);

        Player offline = player();
        when(offline.isOnline()).thenReturn(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(() -> Bukkit.getPlayer(player.getUniqueId())).thenReturn(offline);
            transferService.markChanged(session);

        }

        verify(persistenceService, never()).scheduleSave(any());

    }

}
