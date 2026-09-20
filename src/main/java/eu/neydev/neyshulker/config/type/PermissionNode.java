package eu.neydev.neyshulker.config.type;

import org.jetbrains.annotations.NotNull;

/**
 * Узлы прав плагина.
 * Значения читаются из конфигурации, поэтому их можно переопределить.
 */
public enum PermissionNode {

    USE("use", "neyshulker.use"),
    AUTO_COLLECT("auto_collect", "neyshulker.autocollect"),
    BYPASS_BLACKLIST("bypass_blacklist", "neyshulker.bypass.blacklist"),
    RELOAD("reload", "neyshulker.reload");

    private final String configKey;
    private final String defaultPermission;

    PermissionNode(String configKey, String defaultPermission) {
        this.configKey = configKey;
        this.defaultPermission = defaultPermission;
    }

    public @NotNull String getConfigKey() {
        return configKey;
    }

    public @NotNull String getDefaultPermission() {
        return defaultPermission;
    }
}
