package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundSettings;
import eu.neydev.neyshulker.config.type.InventoryCollectMode;
import eu.neydev.neyshulker.config.type.TitleMode;
import org.bukkit.Material;

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

    TitleMode getTitleMode();

    String getTitleFormat();

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

    boolean isAutoCollectMergeIntoExisting();

    InventoryCollectMode getAutoCollectInventoryMode();

    int getAutoCollectMaxItemsPerTick();

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

    SoundSettings getOpenSound();

    SoundSettings getCloseSound();

    SoundSettings getCollectSound();

    // --- Права ---

    boolean arePermissionsEnabled();

    String getPermission(PermissionNode node);

}
