package eu.neydev.neyshulker;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.service.ConsoleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Дымовой тест сборки графа зависимостей: контейнер создается целиком,
 * все компоненты связаны и доступны через геттеры. Падение здесь означает,
 * что onEnable не переживет запуск сервера.
 */
class ServiceContainerSmokeTest {

    @Test
    @DisplayName("Контейнер собирает весь граф без исключений")
    void containerBuildsFullGraph() {

        NeyShulker plugin = mock(NeyShulker.class);
        ConfigManager configManager = mock(ConfigManager.class);
        ConsoleService consoleService = mock(ConsoleService.class);

        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getLogger("container-smoke"));

        ServiceContainer container = new ServiceContainer(plugin, configManager, consoleService);

        assertSame(plugin, container.getPlugin());
        assertSame(configManager, container.getConfigManager());
        assertSame(consoleService, container.getConsoleService());

        assertNotNull(container.getSessionRegistry());
        assertNotNull(container.getMessageService());
        assertNotNull(container.getSoundService());
        assertNotNull(container.getPermissionService());
        assertNotNull(container.getContentService());
        assertNotNull(container.getTitleService());
        assertNotNull(container.getInventoryTransferService());
        assertNotNull(container.getValidationService());
        assertNotNull(container.getPersistenceService());
        assertNotNull(container.getOpenService());
        assertNotNull(container.getCloseService());
        assertNotNull(container.getTransferService());
        assertNotNull(container.getDropTracker());
        assertNotNull(container.getCollectRules());
        assertNotNull(container.getNearbyItemsFinder());
        assertNotNull(container.getAutoCollectService());

    }

}
