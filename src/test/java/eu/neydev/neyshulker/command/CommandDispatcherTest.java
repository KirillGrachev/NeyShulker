package eu.neydev.neyshulker.command;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.service.ConsoleService;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabExecutor;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Регистрация и вырегистрация команд: wiring обработчиков, реакция на
 * отсутствие команды в plugin.yml и fallback, когда сервер не отдает
 * command map (юнит-тест без живого сервера).
 */
class CommandDispatcherTest {

    private final NeyShulker plugin = mock(NeyShulker.class);
    private final ConsoleService consoleService = mock(ConsoleService.class);
    private final CommandDispatcher dispatcher = new CommandDispatcher(plugin, consoleService);

    @Test
    void registerCommandWiresExecutorAndTabCompleter() {

        PluginCommand command = mock(PluginCommand.class);
        TabExecutor executor = mock(TabExecutor.class);

        when(plugin.getCommand("shulker")).thenReturn(command);
        dispatcher.registerCommand("shulker", executor);

        verify(command).setExecutor(executor);
        verify(command).setTabCompleter(executor);
        verifyNoInteractions(consoleService);

    }

    @Test
    void registerCommandLogsMissingCommand() {

        TabExecutor executor = mock(TabExecutor.class);
        when(plugin.getCommand("shulker")).thenReturn(null);
        dispatcher.registerCommand("shulker", executor);
        verify(consoleService).log(ConsoleMessage.COMMAND_MISSING, "command", "shulker");

    }

    @Test
    void unregisterCommandSilentlySkipsMissingCommand() {

        when(plugin.getCommand("shulker")).thenReturn(null);
        dispatcher.unregisterCommand("shulker");
        verifyNoInteractions(consoleService);

    }

    @Test
    void unregisterCommandLogsFailureWhenCommandMapUnavailable() {

        PluginCommand command = mock(PluginCommand.class);
        when(plugin.getCommand("shulker")).thenReturn(command);
        dispatcher.unregisterCommand("shulker");

        verify(consoleService).log(eq(ConsoleMessage.COMMAND_UNREGISTER_FAILED),
                eq("command"), eq("shulker"),
                eq("reason"), anyString());
        verify(command, never()).unregister(any());

    }

}
