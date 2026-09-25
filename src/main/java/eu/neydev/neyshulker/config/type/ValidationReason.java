package eu.neydev.neyshulker.config.type;

/**
 * Причина запрета действия с шалкер-боксом.
 * NONE означает, что действие разрешено.
 */
public enum ValidationReason {

    NONE(null),
    PLUGIN_DISABLED(null),
    NOT_SHULKER(null),
    NO_PERMISSION(MessageKey.NO_PERMISSION),
    BLACKLISTED(MessageKey.BLACKLISTED),
    NESTED_SHULKER(MessageKey.NESTED_SHULKER),
    OPEN_SHULKER(MessageKey.SELF_REMOVE),
    METHOD_MISMATCH(null);

    private final MessageKey messageKey;

    ValidationReason(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /**
     * @return ключ сообщения для игрока или null, если причина не озвучивается
     */
    public MessageKey getMessageKey() {
        return messageKey;
    }

}
