package eu.neydev.neyshulker;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.service.ConsoleService;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.service.collect.CollectRules;
import eu.neydev.neyshulker.service.collect.NearbyItemsFinder;
import eu.neydev.neyshulker.service.collect.PlayerDropTracker;
import eu.neydev.neyshulker.service.InventoryTransferService;
import eu.neydev.neyshulker.service.MessageService;
import eu.neydev.neyshulker.service.PermissionService;
import eu.neydev.neyshulker.service.ShulkerCloseService;
import eu.neydev.neyshulker.service.ShulkerContentService;
import eu.neydev.neyshulker.service.ShulkerOpenService;
import eu.neydev.neyshulker.service.ShulkerPersistenceService;
import eu.neydev.neyshulker.service.ShulkerTitleService;
import eu.neydev.neyshulker.service.ShulkerTransferService;
import eu.neydev.neyshulker.service.ShulkerValidationService;
import eu.neydev.neyshulker.service.SoundService;

/**
 * Контейнер компонентов плагина.
 * Порядок создания зафиксирован: каждый сервис получает уже готовые
 * зависимости. Контейнер собирает граф полностью - включая механику
 * автосбора (CollectRules, NearbyItemsFinder) и подписку автосбора
 * на reload конфигурации, - чтобы ни один компонент не создавал
 * зависимости себе сам и не подписывался из собственного конструктора.
 */
public final class ServiceContainer {

    private final NeyShulker plugin;
    private final ConfigManager configManager;
    private final ConsoleService consoleService;

    private final SessionRegistry sessionRegistry;
    private final MessageService messageService;
    private final SoundService soundService;
    private final PermissionService permissionService;
    private final ShulkerContentService contentService;
    private final ShulkerTitleService titleService;
    private final InventoryTransferService inventoryTransferService;
    private final ShulkerValidationService validationService;
    private final ShulkerPersistenceService persistenceService;
    private final ShulkerOpenService openService;
    private final ShulkerCloseService closeService;
    private final ShulkerTransferService transferService;
    private final PlayerDropTracker dropTracker;
    private final CollectRules collectRules;
    private final NearbyItemsFinder nearbyItemsFinder;
    private final AutoCollectService autoCollectService;

    public ServiceContainer(NeyShulker plugin, ConfigManager configManager,
                            ConsoleService consoleService) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.consoleService = consoleService;

        // Базовый слой
        this.sessionRegistry = new SessionRegistry();
        this.messageService = new MessageService(configManager);
        this.soundService = new SoundService(configManager);
        this.permissionService = new PermissionService(configManager);

        // Слой работы с данными
        this.contentService = new ShulkerContentService();
        this.titleService = new ShulkerTitleService(configManager);
        this.inventoryTransferService = new InventoryTransferService();
        this.validationService = new ShulkerValidationService(configManager,
                permissionService, sessionRegistry);

        // Слой жизненного цикла шалкер-бокса
        this.persistenceService = new ShulkerPersistenceService(plugin, configManager,
                sessionRegistry, contentService, messageService);
        this.openService = new ShulkerOpenService(plugin, sessionRegistry, contentService,
                titleService, persistenceService, messageService, soundService);
        this.closeService = new ShulkerCloseService(plugin, sessionRegistry,
                persistenceService, soundService);
        this.transferService = new ShulkerTransferService(sessionRegistry, persistenceService);

        // Механика и фоновые задачи автосбора
        this.dropTracker = new PlayerDropTracker(System::currentTimeMillis);
        this.collectRules = new CollectRules(configManager, permissionService, dropTracker);
        this.nearbyItemsFinder = new NearbyItemsFinder(configManager);
        this.autoCollectService = new AutoCollectService(plugin, configManager, sessionRegistry,
                inventoryTransferService, persistenceService, messageService, soundService,
                collectRules, nearbyItemsFinder, dropTracker);

        // Подписка на reload конфигурации - здесь, а не в конструкторе сервиса:
        // сервис не должен публиковать this до завершения собственного создания
        configManager.onReload(autoCollectService::restart);

    }

    public NeyShulker getPlugin() {
        return plugin;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public SessionRegistry getSessionRegistry() {
        return sessionRegistry;
    }

    public MessageService getMessageService() {
        return messageService;
    }

    public SoundService getSoundService() {
        return soundService;
    }

    public PermissionService getPermissionService() {
        return permissionService;
    }

    public ShulkerContentService getContentService() {
        return contentService;
    }

    public ShulkerTitleService getTitleService() {
        return titleService;
    }

    public InventoryTransferService getInventoryTransferService() {
        return inventoryTransferService;
    }

    public ShulkerValidationService getValidationService() {
        return validationService;
    }

    public ShulkerPersistenceService getPersistenceService() {
        return persistenceService;
    }

    public ShulkerOpenService getOpenService() {
        return openService;
    }

    public ShulkerCloseService getCloseService() {
        return closeService;
    }

    public ShulkerTransferService getTransferService() {
        return transferService;
    }

    public AutoCollectService getAutoCollectService() {
        return autoCollectService;
    }

    public PlayerDropTracker getDropTracker() {
        return dropTracker;
    }

    public CollectRules getCollectRules() {
        return collectRules;
    }

    public NearbyItemsFinder getNearbyItemsFinder() {
        return nearbyItemsFinder;
    }

}
