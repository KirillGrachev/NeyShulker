package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.util.HexColorUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис сообщений консоли.
 *
 * Шаблоны берутся из конфигурации (messages.console.*), поэтому владелец
 * сервера может переформулировать предупреждения без пересборки.
 * Плейсхолдеры {key} подставляются парами аргументов: "key", "value".
 */
public class ConsoleService {

    private static final String PATH_CONSOLE = "messages.console.";
    private final JavaPlugin plugin;

    public ConsoleService(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Печатает предупреждение в консоль по шаблону сообщения.
     *
     * @param message    ключ сообщения
     * @param placeholders пары "ключ", "значение"
     */
    public void log(@NotNull ConsoleMessage message, @NotNull String... placeholders) {

        org.bukkit.configuration.ConfigurationSection section =
                plugin.getConfig().getConfigurationSection(PATH_CONSOLE + message.getConfigKey());

        boolean enabled = section != null
                ? section.getBoolean("enabled", message.isDefaultEnabled())
                : message.isDefaultEnabled();

        if (!enabled) {
            return;
        }

        String rawTemplate = section != null
                ? section.getString("text", message.getDefaultTemplate())
                : plugin.getConfig().getString(PATH_CONSOLE + message.getConfigKey(),
                        message.getDefaultTemplate());

        for (String line : readLines(section, rawTemplate)) {
            plugin.getLogger().warning(HexColorUtil.color(applyPlaceholders(line, placeholders)));
        }

    }

    /**
     * Читает текст сообщения в общем формате: список строк или одиночная
     * строка (включая block-scalar). Пустые строки сохраняются как разделители.
     */
    private @NotNull java.util.List<String> readLines(@Nullable org.bukkit.configuration.ConfigurationSection section,
                                                      @NotNull String fallback) {

        if (section != null && section.isList("text")) {
            return section.getStringList("text");
        }

        return java.util.List.of(fallback.split("\n"));

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
