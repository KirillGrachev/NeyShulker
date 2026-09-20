package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundKey;
import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.List;
import java.util.Set;

/**
 * Контракт конфигурации NeyShulker.
 * Все остальные компоненты зависят от интерфейса, а не от реализации.
 */
public interface NeyShulkerConfig {

    // --- Общие настройки ---

    boolean isPluginEnabled();

    // --- Шалкер-бокс ---

    OpenMethodType getOpenMethod();

    String getShulkerTitle();

    boolean isNestedPrevented();

    int getSaveInterval();

    boolean isBlacklistEnabled();

    Set<Material> getBlacklistedMaterials();

    boolean isBlacklisted(Material material);

    // --- Автосбор ---

    boolean isAutoCollectEnabled();

    boolean isAutoCollectPermissionRequired();

    int getAutoCollectInterval();

    double getAutoCollectMaxDistance();

    boolean isAutoCollectOnlyWhenInventoryFull();

    int getAutoCollectMaxItemsPerTick();

    boolean isAutoCollectShulkerBoxesEnabled();

    boolean isAutoCollectIgnorePickupDelay();

    boolean areAutoCollectMessagesEnabled();

    List<Material> getAutoCollectPriorityItems();

    Set<Material> getAutoCollectBlacklist();

    boolean isAutoCollectBlacklisted(Material material);

    // --- Сообщения ---

    boolean areMessagesEnabled();

    String getMessagePrefix();

    List<String> getMessages(MessageKey key);

    // --- Звуки ---

    boolean areSoundsEnabled();

    Sound getSound(SoundKey key);

    float getSoundVolume(SoundKey key);

    float getSoundPitch(SoundKey key);

    // --- Права ---

    boolean arePermissionsEnabled();

    String getPermission(PermissionNode node);

}
