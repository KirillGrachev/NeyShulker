package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.SoundSettings;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис звуков плагина. Все звуки читаются из конфигурации.
 */
public class SoundService {

    private final NeyShulkerConfig config;

    public SoundService(@NotNull NeyShulkerConfig config) {
        this.config = config;
    }

    /**
     * Проигрывает звук игроку.
     *
     * @param player   получатель
     * @param settings настройки звука из конфигурации
     */
    public void play(@Nullable Player player, @NotNull SoundSettings settings) {

        if (player == null || !player.isOnline() || !settings.enabled()) {
            return;
        }

        player.playSound(player.getLocation(),
                settings.sound(),
                settings.volume(),
                settings.pitch()
        );

    }

    public void playOpen(@Nullable Player player) {
        play(player, config.getOpenSound());
    }

    public void playClose(@Nullable Player player) {
        play(player, config.getCloseSound());
    }

    public void playCollect(@Nullable Player player) {
        play(player, config.getCollectSound());
    }

}
