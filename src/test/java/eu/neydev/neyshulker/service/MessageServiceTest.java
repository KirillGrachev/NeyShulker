package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка сервиса сообщений: префикс, плейсхолдеры, многострочность, выключатели.
 */
class MessageServiceTest {

    private final ConfigManager configManager = mock(ConfigManager.class);
    private final MessageService messageService = new MessageService(configManager);

    @Test
    @DisplayName("Сообщение собирается с префиксом и плейсхолдерами")
    void buildsMessageWithPrefixAndPlaceholders() {

        when(configManager.areMessagesEnabled()).thenReturn(true);
        when(configManager.getMessagePrefix()).thenReturn("§8» §7");
        when(configManager.getMessages(MessageKey.AUTO_COLLECT))
                .thenReturn(List.of("{prefix}Collected: §f{amount}", "without prefix", ""));

        List<String> lines = messageService.build(MessageKey.AUTO_COLLECT,
                Map.of("amount", "5"));

        // Префикс появляется только там, где его явно попросили
        assertEquals(List.of("§8» §7Collected: §f5", "without prefix", ""), lines);

    }

    @Test
    @DisplayName("Отправка игроку доходит до sendMessage построчно")
    void sendsToPlayerLineByLine() {

        Player player = mock(Player.class);

        when(player.isOnline()).thenReturn(true);
        when(configManager.areMessagesEnabled()).thenReturn(true);
        when(configManager.getMessagePrefix()).thenReturn("");
        when(configManager.getMessages(MessageKey.RELOAD)).thenReturn(List.of("one", "{prefix}two"));

        messageService.send(player, MessageKey.RELOAD);

        verify(player).sendMessage("one");
        verify(player).sendMessage("two");

    }

    @Test
    @DisplayName("Выключенные сообщения не отправляются")
    void disabledMessagesAreNotSent() {

        Player player = mock(Player.class);

        when(configManager.areMessagesEnabled()).thenReturn(false);

        messageService.send(player, MessageKey.RELOAD);
        messageService.sendRaw(player, "text");

        // guard сперва проверяет онлайн-статус, поэтому сверяем именно отправку
        verify(player, never()).sendMessage(anyString());

    }

    @Test
    @DisplayName("Оффлайн-игрок и null безопасны")
    void offlineAndNullPlayersAreSafe() {

        Player offline = mock(Player.class);

        when(offline.isOnline()).thenReturn(false);
        when(configManager.areMessagesEnabled()).thenReturn(true);
        when(configManager.getMessages(MessageKey.RELOAD)).thenReturn(List.of("x"));

        messageService.send(offline, MessageKey.RELOAD);
        messageService.send((Player) null, MessageKey.RELOAD);

        verify(offline, never()).sendMessage(org.mockito.ArgumentMatchers.anyString());

    }

    @Test
    @DisplayName("Консоль получает то же сообщение")
    void sendsToConsole() {

        CommandSender console = mock(CommandSender.class);

        when(configManager.areMessagesEnabled()).thenReturn(true);
        when(configManager.getMessagePrefix()).thenReturn("P ");
        when(configManager.getMessages(MessageKey.USAGE)).thenReturn(List.of("{prefix}usage"));

        messageService.send(console, MessageKey.USAGE);

        verify(console).sendMessage("P usage");

    }

    @Test
    @DisplayName("sendRaw применяет HEX-цвета и префикс")
    void sendRawAppliesColors() {

        Player player = mock(Player.class);

        when(player.isOnline()).thenReturn(true);
        when(configManager.areMessagesEnabled()).thenReturn(true);
        // Префикс приходит из ConfigManager уже окрашенным и только через {prefix}
        when(configManager.getMessagePrefix()).thenReturn("§7> ");

        messageService.sendRaw(player, "{prefix}&cError");
        messageService.sendRaw(player, "&aClean");

        verify(player).sendMessage("§7> §cError");
        verify(player).sendMessage("§aClean");

    }
}
