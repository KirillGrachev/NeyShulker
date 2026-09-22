package eu.neydev.neyshulker.command;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.service.ConsoleService;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandMap;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;

/**
 * Регистрация команд плагина в одном месте.
 */
public class CommandDispatcher {

    private final NeyShulker plugin;
    private final ConsoleService consoleService;

    public CommandDispatcher(@NotNull NeyShulker plugin, @NotNull ConsoleService consoleService) {
        this.plugin = plugin;
        this.consoleService = consoleService;
    }

    /**
     * Регистрирует обработчики команд.
     *
     * @param command  название команды из plugin.yml
     * @param executor обработчик команды и автодополнения
     */
    public void registerCommand(@NotNull String command, @NotNull TabExecutor executor) {

        PluginCommand pluginCommand = plugin.getCommand(command);

        if (pluginCommand == null) {

            consoleService.log(ConsoleMessage.COMMAND_MISSING, "command", command);
            return;

        }

        pluginCommand.setExecutor(executor);
        pluginCommand.setTabCompleter(executor);

    }

    /**
     * Вырегиструет команду и её алиасы из command map сервера.
     * <p>
     * Используется при отключении плагина: команда не должна оставаться
     * «зомби» и бросать CommandException у выключенного плагина.
     *
     * @param command название команды из plugin.yml
     */
    public void unregisterCommand(@NotNull String command) {

        PluginCommand pluginCommand = plugin.getCommand(command);

        if (pluginCommand == null) {
            return;
        }

        try {

            Method getCommandMap = Bukkit.getServer().getClass().getMethod("getCommandMap");
            CommandMap commandMap = (CommandMap) getCommandMap.invoke(Bukkit.getServer());

            pluginCommand.unregister(commandMap);
            syncCommands();

        } catch (ReflectiveOperationException | RuntimeException exception) {

            consoleService.log(ConsoleMessage.COMMAND_UNREGISTER_FAILED,
                    "command", command,
                    "reason", String.valueOf(exception.getMessage()));

        }
    }

    /**
     * Просит сервер пересобрать дерево команд (Paper/Brigadier),
     * чтобы вырегистрованная команда исчезла и из автодополнения.
     */
    private void syncCommands() {

        try {

            Method syncCommands = Bukkit.getServer().getClass().getMethod("syncCommands");
            syncCommands.invoke(Bukkit.getServer());

        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // сервер без syncCommands: команда исчезнет после рестарта
        }
    }
}
