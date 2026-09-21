package eu.neydev.neyshulker;

import eu.neydev.neyshulker.command.ShulkerCommand;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.service.ConsoleService;
import eu.neydev.neyshulker.event.EventDispatcher;
import eu.neydev.neyshulker.listener.PlayerInteractListener;
import eu.neydev.neyshulker.listener.ShulkerCleanupListener;
import eu.neydev.neyshulker.listener.ShulkerGuardListener;
import eu.neydev.neyshulker.listener.ShulkerSyncListener;
import eu.neydev.neyshulker.placeholder.NeyShulkerExpansion;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public final class NeyShulker extends JavaPlugin {

    private ConsoleService consoleService;
    private ConfigManager configManager;
    private ServiceContainer services;

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
                new ShulkerCleanupListener(this)
        );

        registerCommand();
        registerExpansion();

        services.getAutoCollectService().start();

        getLogger().info("NeyShulker успешно запущен!");

    }

    @Override
    public void onDisable() {

        // Содержимое открытых шалкер-боксов сохраняется до остановки задач
        services.getCloseService().closeAll();
        services.getAutoCollectService().stop();

        unregisterExpansion();

        getLogger().info("NeyShulker остановлен!");

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

    private void registerCommand() {

        PluginCommand command = getCommand("shulker");

        if (command == null) {
            getLogger().warning("Команда /shulker не найдена в plugin.yml");
            return;
        }

        ShulkerCommand executor = new ShulkerCommand(this);

        command.setExecutor(executor);
        command.setTabCompleter(executor);

    }

    private void registerExpansion() {

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            return;
        }

        this.expansion = new NeyShulkerExpansion(this);

        if (expansion.register()) {
            getLogger().info("PlaceholderAPI: расширение %neyshulker_*% зарегистрировано.");
        }

    }

    private void unregisterExpansion() {

        if (expansion != null) {
            expansion.unregister();
            expansion = null;
        }

    }
}
