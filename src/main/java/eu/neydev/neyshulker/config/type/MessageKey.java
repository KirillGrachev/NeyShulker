package eu.neydev.neyshulker.config.type;

import org.jetbrains.annotations.NotNull;

/**
 * Ключи сообщений плагина.
 * Каждый ключ хранит путь в config.yml и значение по умолчанию.
 */
public enum MessageKey {

    NO_PERMISSION("no_permission", "{prefix}&cУ вас нет прав для этого действия."),
    OPEN_ERROR("open_error", "{prefix}&cНе удалось открыть этот шалкер-бокс."),
    NO_SHULKER_IN_HAND("no_shulker_in_hand", "{prefix}&cВозьмите шалкер-бокс в руку."),
    BLACKLISTED("blacklisted", "{prefix}&cЭтот предмет нельзя положить в шалкер-бокс."),
    NESTED_SHULKER("nested_shulker", "{prefix}&cШалкер-бокс нельзя положить в другой шалкер-бокс."),
    SELF_REMOVE("self_remove", "{prefix}&cНельзя убрать открытый шалкер-бокс."),
    MOVE_BLOCKED("move_blocked", "{prefix}&cОткрытый шалкер-бокс нельзя переместить."),
    DROP_BLOCKED("drop_blocked", "{prefix}&cОткрытый шалкер-бокс нельзя выбросить."),
    SWAP_BLOCKED("swap_blocked", "{prefix}&cОткрытый шалкер-бокс нельзя переложить."),
    NO_SPACE("no_space", "{prefix}&eВ шалкер-боксе нет свободного места."),
    SAVED("saved", "{prefix}&aСодержимое шалкер-бокса сохранено."),
    AUTO_COLLECT("auto_collect", "{prefix}&bСобрано предметов: &f{amount}"),
    AUTO_COLLECT_FULL("auto_collect_full", "{prefix}&eШалкер-бокс заполнен, сбор невозможен."),
    AUTO_COLLECT_NO_SHULKER("auto_collect_no_shulker", "{prefix}&eВ инвентаре нет свободного шалкер-бокса."),
    RELOAD("reload", "{prefix}&aКонфигурация перезагружена."),
    PLAYER_ONLY("player_only", "{prefix}&cКоманда доступна только игрокам."),
    USAGE("usage", "{prefix}&#8ce0ffКоманды NeyShulker:\n"
            + "&f/shulker reload &8- &7перезагрузить конфигурацию\n"
            + "&f/shulker open &8- &7открыть шалкер-бокс в руке\n"
            + "&f/shulker info &8- &7состояние сессии и автосбора\n"
            + "&f/shulker autocollect &8- &7переключить автосбор себе"),
    AUTO_COLLECT_ON("auto_collect_on", "{prefix}&aАвтосбор включен."),
    AUTO_COLLECT_OFF("auto_collect_off", "{prefix}&eАвтосбор выключен.");

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
