package eu.neydev.neyshulker.config.type;

import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Настройки одного звука плагина.
 *
 * @param sound   сам звук (при неизвестном имени подставляется defaultSound)
 * @param enabled играет ли звук
 * @param volume  громкость
 * @param pitch   высота
 */
public record SoundSettings(@NotNull Sound sound,
                            boolean enabled,
                            float volume,
                            float pitch) {

    /**
     * Собирает настройки звука из значений конфигурации.
     * Неизвестное имя звука не роняет загрузку: подставляется дефолт,
     * а вызывается колбэк предупреждения (консольный сервис).
     *
     * @param name          имя звука из конфигурации
     * @param defaultSound  звук по умолчанию
     * @param enabled       включен ли звук
     * @param volume        громкость
     * @param pitch         высота
     * @param onUnknownName колбэк предупреждения о неизвестном имени
     * @return готовые настройки
     */
    public static @NotNull SoundSettings of(@Nullable String name,
                                            @NotNull Sound defaultSound,
                                            boolean enabled,
                                            float volume,
                                            float pitch,
                                            @NotNull Runnable onUnknownName) {

        Sound sound = defaultSound;

        if (name != null && !name.isBlank()) {
            try {
                sound = Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                onUnknownName.run();
            }
        }

        return new SoundSettings(sound, enabled, volume, pitch);

    }
}
