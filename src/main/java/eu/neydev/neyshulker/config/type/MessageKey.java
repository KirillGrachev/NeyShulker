package eu.neydev.neyshulker.config.type;

import org.jetbrains.annotations.NotNull;

/**
 * Ключи сообщений плагина.
 * Каждый ключ хранит путь в config.yml, дефолтный текст на английском
 * и признак включенности по умолчанию (для служебных сообщений - выключен).
 */
public enum MessageKey {

    NO_PERMISSION("no_permission", true,
            "{prefix}&#ff6b6bYou do not have permission to do this."),

    OPEN_ERROR("open_error", true,
            "{prefix}&#ff6b6bFailed to open this shulker box."),

    NO_SHULKER_IN_HAND("no_shulker_in_hand", true,
            "{prefix}&#ff6b6bHold a shulker box in your hand first."),

    BLACKLISTED("blacklisted", true,
            "{prefix}&#ff6b6bThis item cannot be placed into a shulker box."),

    NESTED_SHULKER("nested_shulker", true,
            "{prefix}&#ff6b6bA shulker box cannot be placed into another shulker box."),

    SELF_REMOVE("self_remove", true,
            "{prefix}&#ff6b6bYou cannot take the opened shulker box away."),

    MOVE_BLOCKED("move_blocked", true,
            "{prefix}&#ff6b6bThe opened shulker box cannot be moved."),

    DROP_BLOCKED("drop_blocked", true,
            "{prefix}&#ff6b6bThe opened shulker box cannot be dropped."),

    SWAP_BLOCKED("swap_blocked", true,
            "{prefix}&#ff6b6bThe opened shulker box cannot be swapped."),

    NO_SPACE("no_space", true,
            "{prefix}&#ffe066The shulker box is full."),

    SAVED("saved", false,
            "{prefix}&#9dff8cThe contents of the shulker box have been saved."),

    AUTO_COLLECT("auto_collect", false,
            "{prefix}&#8ce0ffCollected items: &f{amount}"),

    AUTO_COLLECT_FULL("auto_collect_full", true,
            "{prefix}&#ffe066The shulker box is full, cannot collect items."),

    AUTO_COLLECT_NO_SHULKER("auto_collect_no_shulker", false,
            "{prefix}&#ffe066No free shulker box found in your inventory."),

    RELOAD("reload", true,
            "{prefix}&#9dff8cConfiguration reloaded."),

    PLAYER_ONLY("player_only", true,
            "{prefix}&#ff6b6bThis command is available to players only."),

    USAGE("usage", true, "{prefix}&#8ce0ffNeyShulker commands:\n"
            + "&f/shulker reload &8- &7reload the configuration\n"
            + "&f/shulker open &8- &7open the shulker box in your hand\n"
            + "&f/shulker info &8- &7current session and auto-collect state\n"
            + "&f/shulker autocollect &8- &7toggle auto-collect for yourself"),

    AUTO_COLLECT_ON("auto_collect_on", true,
            "{prefix}&#9dff8cAuto-collect enabled."),

    AUTO_COLLECT_OFF("auto_collect_off", true,
            "{prefix}&#ffe066Auto-collect disabled."),

    INFO_IDLE("info_idle", true, "{prefix}&7No open shulker boxes.\n"
            + "&7Auto-collect: {state}\n"
            + "&7Transfer queue: &f{queue}"),

    INFO_SESSION("info_session", true, "{prefix}&dShulker: &f{name}\n"
            + "&dSlot: &f{slot}\n"
            + "&dFree slots: &f{free}&7/&f{size}\n"
            + "&dItems inside: &f{items}\n"
            + "&dOpen for: &f{seconds}&7 s."),

    STATE_ON("state_on", true, "&aenabled"),

    STATE_OFF("state_off", true, "&cdisabled");

    private final String configKey;
    private final boolean defaultEnabled;
    private final String defaultMessage;

    MessageKey(String configKey, boolean defaultEnabled, String defaultMessage) {
        this.configKey = configKey;
        this.defaultEnabled = defaultEnabled;
        this.defaultMessage = defaultMessage;
    }

    public @NotNull String getConfigKey() {
        return configKey;
    }

    /**
     * Включено ли сообщение, если его секции нет в конфигурации.
     */
    public boolean isDefaultEnabled() {
        return defaultEnabled;
    }

    public @NotNull String getDefaultMessage() {
        return defaultMessage;
    }
}
