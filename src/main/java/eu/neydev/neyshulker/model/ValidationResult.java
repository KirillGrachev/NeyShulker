package eu.neydev.neyshulker.model;

import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.ValidationReason;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Результат проверки действия.
 * Позволяет сервису валидации вернуть причину запрета вместе с сообщением для игрока.
 *
 * @param reason причина запрета
 */
public record ValidationResult(@NotNull ValidationReason reason) {

    private static final ValidationResult ALLOWED = new ValidationResult(ValidationReason.NONE);

    public static @NotNull ValidationResult allowed() {
        return ALLOWED;
    }

    public static @NotNull ValidationResult denied(@NotNull ValidationReason reason) {
        return new ValidationResult(reason);
    }

    public boolean isAllowed() {
        return reason == ValidationReason.NONE;
    }

    public @Nullable MessageKey getMessageKey() {
        return reason.getMessageKey();
    }

}
