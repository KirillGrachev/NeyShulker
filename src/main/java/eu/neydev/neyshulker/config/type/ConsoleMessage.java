package eu.neydev.neyshulker.config.type;

import org.jetbrains.annotations.NotNull;

/**
 * Ключи сообщений консоли.
 * Шаблоны читаются из конфигурации (messages.console.*), плейсхолдеры
 * подставляются по принципу "ключ", "значение" парами.
 */
public enum ConsoleMessage {

    UNKNOWN_SOUND("unknown_sound", true,
            "&eUnknown sound at {path}: '{value}'. Using {defaultValue}."),

    UNKNOWN_MATERIAL("unknown_material", true,
            "&eUnknown material at {path}: '{value}'. Skipped."),

    INVALID_VALUE("invalid_value", true,
            "&eInvalid value at {path}: '{value}'. Using {defaultValue}."),

    LEGACY_PATH("legacy_path", true,
            "&eLegacy config path {path}: use {replacement} instead."),

    COMMAND_MISSING("command_missing", true,
            "&eCommand {command} is missing from plugin.yml."),

    COMMAND_UNREGISTER_FAILED("command_unregister_failed", true,
            "&eFailed to unregister command {command}: {reason}.");

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
