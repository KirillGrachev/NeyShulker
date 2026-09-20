package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.SoundKey;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Сервис звуков плагина. Все звуки читаются из конфигурации.
 */
public class SoundService {

    private final ConfigManager configManager;

    public SoundService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Проигрывает звук игроку.
     *
     * @param player получатель
     * @param key    ключ звука
     */
    public void play(@Nullable Player player, SoundKey key) {

        if (player == null || !player.isOnline() || !configManager.areSoundsEnabled()) {
            return;
        }

        player.playSound(player.getLocation(),
                configManager.getSound(key),
                configManager.getSoundVolume(key),
                configManager.getSoundPitch(key)
        );

    }

    public void playOpen(@Nullable Player player) {
        play(player, SoundKey.OPEN);
    }

    public void playClose(@Nullable Player player) {
        play(player, SoundKey.CLOSE);
    }

    public void playCollect(@Nullable Player player) {
        play(player, SoundKey.COLLECT);
    }
}
