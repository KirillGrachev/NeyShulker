package eu.neydev.neyshulker;

import eu.neydev.neyshulker.command.CommandDispatcher;
import eu.neydev.neyshulker.command.ShulkerCommand;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.listener.PlayerDropListener;
import eu.neydev.neyshulker.listener.PlayerInteractListener;
import eu.neydev.neyshulker.listener.ShulkerCleanupListener;
import eu.neydev.neyshulker.listener.ShulkerGuardListener;
import eu.neydev.neyshulker.listener.ShulkerSyncListener;
import eu.neydev.neyshulker.listener.WaitListSyncListener;
import eu.neydev.neyshulker.placeholder.NeyShulkerExpansion;
import eu.neydev.neyshulker.service.ConsoleService;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

/**
 * Точка входа плагина.
 *
 * Класс не final осознанно: интеграционные тесты (MockBukkit) грузят плагин
 * через прокси-подкласс, а наследование JavaPlugin в продакшене никто не
 * использует - подклассы создаются только тестовой средой.
 */
public class NeyShulker extends JavaPlugin {

    private static final String COMMAND_SHULKER = "shulker";

    private ConsoleService consoleService;
    private ConfigManager configManager;
    private ServiceContainer services;
    private @Nullable CommandDispatcher commandDispatcher;

    private @Nullable NeyShulkerExpansion expansion;

    @Override
    public void onEnable() {

        this.consoleService = new ConsoleService(this);
        this.configManager = new ConfigManager(this, consoleService);
        this.services = new ServiceContainer(this, configManager, consoleService);

        registerListeners();

        this.commandDispatcher = new CommandDispatcher(this, consoleService);
        commandDispatcher.registerCommand(COMMAND_SHULKER, new ShulkerCommand(
                configManager,
                services.getPermissionService(),
                services.getMessageService(),
                services.getOpenService(),
                services.getValidationService(),
                services.getAutoCollectService(),
                services.getSessionRegistry()
        ));

        registerExpansion();
        services.getAutoCollectService().start();
        getLogger().info("NeyShulker started successfully.");

    }

    @Override
    public void onDisable() {

        // Содержимое открытых шалкер-боксов сохраняется до остановки задач
        services.getCloseService().closeAll();
        services.getAutoCollectService().stop();

        unregisterExpansion();

        if (commandDispatcher != null) {
            commandDispatcher.unregisterCommand(COMMAND_SHULKER);
        }

        getLogger().info("NeyShulker stopped.");

    }

    public ConsoleService getConsoleService() {
        return consoleService;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public ServiceContainer getServices() {
        return services;
    }

    /**
     * Регистрация слушателей с явными зависимостями: каждый получает
     * нужные сервисы из контейнера, а не тянет их из плагина сам.
     */
    private void registerListeners() {

        Listener[] listeners = {
                new PlayerInteractListener(this,
                        configManager,
                        services.getValidationService(),
                        services.getOpenService(),
                        services.getMessageService(),
                        services.getSessionRegistry()),
                new ShulkerGuardListener(
                        services.getSessionRegistry(),
                        services.getValidationService(),
                        services.getMessageService()),
                new ShulkerSyncListener(
                        services.getSessionRegistry(),
                        services.getTransferService()),
                new ShulkerCleanupListener(
                        services.getSessionRegistry(),
                        services.getCloseService(),
                        services.getAutoCollectService()),
                new WaitListSyncListener(services.getAutoCollectService()),
                new PlayerDropListener(services.getDropTracker())
        };

        for (Listener listener : listeners) {
            getServer().getPluginManager().registerEvents(listener, this);
        }

    }

    private void registerExpansion() {

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        this.expansion = new NeyShulkerExpansion(this,
                configManager,
                services.getSessionRegistry(),
                services.getAutoCollectService());

        if (expansion.register()) {
            getLogger().info("PlaceholderAPI: the %neyshulker_*% expansion has been registered.");
        }

    }

    private void unregisterExpansion() {

        if (expansion != null) {
            expansion.shutdown();
            expansion.unregister();
            expansion = null;
        }

    }

}
