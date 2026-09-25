package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.type.ConsoleMessage;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Консольный сервис: шаблоны зашиты в код, предупреждение нельзя
 * выключить или переписать из конфигурации, плейсхолдеры подставляются
 * парами, цвета транслируются один раз при загрузке класса.
 */
class ConsoleServiceTest {

    private final List<LogRecord> records = new ArrayList<>();

    private ConsoleService service() {

        JavaPlugin plugin = mock(JavaPlugin.class);
        Logger logger = Logger.getLogger("console-test-" + System.nanoTime());

        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {

            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });

        when(plugin.getLogger()).thenReturn(logger);
        return new ConsoleService(plugin);

    }

    @Test
    @DisplayName("Шаблон из кода печатается с подстановкой плейсхолдеров")
    void logsCodeTemplateWithPlaceholders() {

        service().log(ConsoleMessage.INVALID_VALUE,
                "path", "a.b", "value", "x", "defaultValue", "y");

        assertEquals(1, records.size());
        assertEquals(Level.WARNING, records.get(0).getLevel());
        assertTrue(records.get(0).getMessage().contains("Invalid value at a.b: 'x'. Using y."),
                "Плейсхолдеры подставлены: " + records.get(0).getMessage());

    }

    @Test
    @DisplayName("Все ключи логируются: отключить диагностику нельзя")
    void everyMessageIsAlwaysLogged() {

        ConsoleService service = service();

        for (ConsoleMessage message : ConsoleMessage.values()) {
            service.log(message, "path", "p", "value", "v",
                    "defaultValue", "d", "replacement", "r",
                    "command", "c", "reason", "why");
        }

        assertEquals(ConsoleMessage.values().length, records.size(),
                "Каждый ключ дал ровно одно предупреждение - флагов enabled больше нет");

        for (LogRecord record : records) {
            assertEquals(Level.WARNING, record.getLevel());
            assertTrue(record.getMessage().startsWith("§e"),
                    "&e транслирован в legacy-код один раз при загрузке: " + record.getMessage());
        }

    }

    @Test
    @DisplayName("Нечетное число аргументов и лишние пары безопасны")
    void oddPlaceholderArgumentsAreSafe() {

        service().log(ConsoleMessage.LEGACY_PATH, "path", "old.path", "replacement");

        assertEquals(1, records.size());
        assertTrue(records.get(0).getMessage().contains("Legacy config path old.path"));
        assertTrue(records.get(0).getMessage().contains("{replacement}"),
                "Незакрытая пара остается как есть, ничего не падает");

    }

    @Test
    @DisplayName("Повторные вызовы не мутируют шаблоны")
    void templatesAreImmutable() {

        ConsoleService service = service();

        service.log(ConsoleMessage.UNKNOWN_MATERIAL, "path", "one", "value", "TWO");
        service.log(ConsoleMessage.UNKNOWN_MATERIAL, "path", "three", "value", "FOUR");

        assertEquals(2, records.size());
        assertTrue(records.get(1).getMessage().contains("three"));
        assertTrue(records.get(1).getMessage().contains("FOUR"));
        assertTrue(!records.get(1).getMessage().contains("TWO"),
                "Подстановки предыдущего вызова не протекают в следующий");

    }

}
