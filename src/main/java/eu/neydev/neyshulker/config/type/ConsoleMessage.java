package eu.neydev.neyshulker.config.type;

import org.jetbrains.annotations.NotNull;

/**
 * Ключи сообщений консоли.
 * Шаблоны читаются из конфигурации (messages.console.*), плейсхолдеры
 * подставляются по принципу "ключ", "значение" парами.
 */
public enum ConsoleMessage {

    UNKNOWN_SOUND("unknown_sound", true,
            "&eНеизвестный звук в {path}: '{value}'. Использован {defaultValue}."),

    UNKNOWN_MATERIAL("unknown_material", true,
            "&eНеизвестный предмет в {path}: '{value}'. Значение пропущено."),

    INVALID_VALUE("invalid_value", true,
            "&eНекорректное значение в {path}: '{value}'. Использовано {defaultValue}.");

    private final String configKey;
    private final boolean defaultEnabled;
    private final String defaultTemplate;

    ConsoleMessage(String configKey, boolean defaultEnabled, String defaultTemplate) {
        this.configKey = configKey;
        this.defaultEnabled = defaultEnabled;
        this.defaultTemplate = defaultTemplate;
    }

    public @NotNull String getConfigKey() {
        return configKey;
    }

    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public @NotNull String getDefaultTemplate() {
        return defaultTemplate;
    }
}
