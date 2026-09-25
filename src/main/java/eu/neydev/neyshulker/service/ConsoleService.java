package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.util.HexColorUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис сообщений консоли.
 *
 * Шаблоны зашиты в код ({@link ConsoleMessage}) и готовятся один раз
 * при загрузке класса: окрашены, разрезаны на строки, неизменяемы.
 * Конфигурация не читается вовсе - диагностику нельзя выключить или
 * переписать из config.yml (см. javadoc {@link ConsoleMessage}).
 * Плейсхолдеры {key} подставляются парами аргументов: "ключ", "значение".
 */
public class ConsoleService {

    /** Окрашенные шаблоны: цвет считается один раз, а не на каждое предупреждение. */
    private static final Map<ConsoleMessage, List<String>> TEMPLATES;

    static {

        Map<ConsoleMessage, List<String>> templates = new EnumMap<>(ConsoleMessage.class);

        for (ConsoleMessage message : ConsoleMessage.values()) {
            templates.put(message,
                    List.of(HexColorUtil.color(message.getDefaultTemplate()).split("\n")));
        }

        TEMPLATES = Map.copyOf(templates);

    }

    private final JavaPlugin plugin;

    public ConsoleService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Печатает предупреждение в консоль по шаблону сообщения.
     *
     * @param message      ключ сообщения
     * @param placeholders пары "ключ", "значение"
     */
    public void log(@NotNull ConsoleMessage message, @NotNull String... placeholders) {

        for (String line : TEMPLATES.get(message)) {
            plugin.getLogger().warning(applyPlaceholders(line, placeholders));
        }

    }

    private @NotNull String applyPlaceholders(@NotNull String template,
                                              @NotNull String... placeholders) {

        String result = template;

        for (int i = 0; i + 1 < placeholders.length; i += 2) {
            result = result.replace("{" + placeholders[i] + "}", placeholders[i + 1]);
        }

        return result;

    }

}
