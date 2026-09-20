package eu.neydev.neyshulker.config.type;

import org.jetbrains.annotations.NotNull;

/**
 * Ключи сообщений плагина.
 * Каждый ключ хранит путь в config.yml и значение по умолчанию.
 */
public enum MessageKey {

    NO_PERMISSION("no_permission", "&cУ вас нет прав для этого действия."),
    OPEN_ERROR("open_error", "&cНе удалось открыть этот шалкер-бокс."),
    BLACKLISTED("blacklisted", "&cЭтот предмет нельзя положить в шалкер-бокс."),
    NESTED_SHULKER("nested_shulker", "&cШалкер-бокс нельзя положить в другой шалкер-бокс."),
    SELF_REMOVE("self_remove", "&cНельзя убрать открытый шалкер-бокс."),
    MOVE_BLOCKED("move_blocked", "&cОткрытый шалкер-бокс нельзя переместить."),
    DROP_BLOCKED("drop_blocked", "&cОткрытый шалкер-бокс нельзя выбросить."),
    SWAP_BLOCKED("swap_blocked", "&cОткрытый шалкер-бокс нельзя переложить."),
    NO_SPACE("no_space", "&eВ шалкер-боксе нет свободного места."),
    SAVED("saved", "&aСодержимое шалкер-бокса сохранено."),
    AUTO_COLLECT("auto_collect", "&bСобрано предметов: &f{amount}"),
    AUTO_COLLECT_FULL("auto_collect_full", "&eШалкер-бокс заполнен, сбор невозможен."),
    AUTO_COLLECT_NO_SHULKER("auto_collect_no_shulker", "&eВ инвентаре нет свободного шалкер-бокса."),
    RELOAD("reload", "&aКонфигурация перезагружена."),
    PLAYER_ONLY("player_only", "&cКоманда доступна только игрокам."),
    USAGE("usage", "&eИспользование: &f/shulker <reload|open|info|autocollect>"),
    AUTO_COLLECT_ON("auto_collect_on", "&aАвтосбор включен."),
    AUTO_COLLECT_OFF("auto_collect_off", "&eАвтосбор выключен.");

    private final String configKey;
    private final String defaultMessage;

    MessageKey(String configKey, String defaultMessage) {
        this.configKey = configKey;
        this.defaultMessage = defaultMessage;
    }

    public @NotNull String getConfigKey() {
        return configKey;
    }

    public @NotNull String getDefaultMessage() {
        return defaultMessage;
    }
}
