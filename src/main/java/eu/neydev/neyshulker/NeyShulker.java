package eu.neydev.neyshulker;

import eu.neydev.neyshulker.command.CommandDispatcher;
import eu.neydev.neyshulker.command.ShulkerCommand;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.service.ConsoleService;
import eu.neydev.neyshulker.event.EventDispatcher;
import eu.neydev.neyshulker.listener.PlayerInteractListener;
import eu.neydev.neyshulker.listener.ShulkerCleanupListener;
import eu.neydev.neyshulker.listener.ShulkerGuardListener;
import eu.neydev.neyshulker.listener.ShulkerSyncListener;
import eu.neydev.neyshulker.listener.WaitListSyncListener;
import eu.neydev.neyshulker.placeholder.NeyShulkerExpansion;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class NeyShulker extends JavaPlugin {

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

        // Регистрация слушателей
        new EventDispatcher(this).registerEvents(
                new PlayerInteractListener(this),
                new ShulkerGuardListener(this),
                new ShulkerSyncListener(this),
                new ShulkerCleanupListener(this),
                new WaitListSyncListener(this)
        );

        this.commandDispatcher = new CommandDispatcher(this, consoleService);
        commandDispatcher.registerCommand(COMMAND_SHULKER, new ShulkerCommand(this));

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

    private void registerExpansion() {

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        this.expansion = new NeyShulkerExpansion(this);

        if (expansion.register()) {
            getLogger().info("PlaceholderAPI: the %neyshulker_*% expansion has been registered.");
        }

    }

    private void unregisterExpansion() {

        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }

    }
}
