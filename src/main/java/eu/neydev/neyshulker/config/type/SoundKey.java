package eu.neydev.neyshulker.config.type;

import org.bukkit.Sound;
import org.jetbrains.annotations.NotNull;

/**
 * Ключи звуков плагина.
 */
public enum SoundKey {

    OPEN("open", Sound.BLOCK_SHULKER_BOX_OPEN, 1.0f, 1.0f),
    CLOSE("close", Sound.BLOCK_SHULKER_BOX_CLOSE, 1.0f, 1.0f),
    COLLECT("collect", Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);

    private final String configKey;
    private final Sound defaultSound;
    private final float defaultVolume;
    private final float defaultPitch;

    SoundKey(String configKey, Sound defaultSound, float defaultVolume, float defaultPitch) {

        this.configKey = configKey;
        this.defaultSound = defaultSound;
        this.defaultVolume = defaultVolume;
        this.defaultPitch = defaultPitch;

    }

    public @NotNull String getConfigKey() {
        return configKey;
    }

    public @NotNull Sound getDefaultSound() {
        return defaultSound;
    }

    public float getDefaultVolume() {
        return defaultVolume;
    }

    public float getDefaultPitch() {
        return defaultPitch;
    }

}
