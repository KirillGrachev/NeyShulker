package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundSettings;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.TitleMode;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

/**
 * Контракт конфигурации NeyShulker.
 * Все остальные компоненты зависят от интерфейса, а не от реализации.
 */
public interface NeyShulkerConfig {


    boolean isPluginEnabled();


    OpenMethodType getOpenMethod();

    TitleMode getTitleMode();

    String getTitleFormat();

    int getSaveInterval();

    boolean isBlacklistEnabled();

    Set<Material> getBlacklistedMaterials();

    boolean isBlacklisted(Material material);


    boolean isAutoCollectEnabled();

    boolean isAutoCollectPermissionRequired();

    double getAutoCollectMaxDistance();

    int getWavePeriod();

    int getPlayersPerWave();

    int getActionsPerWave();

    int getQueuePerPlayer();

    boolean isAutoCollectOnlyWhenInventoryFull();

    boolean isAutoCollectMergeIntoExisting();

    CollectMode getAutoCollectMode();

    boolean isAutoCollectIgnorePickupDelay();

    boolean areAutoCollectMessagesEnabled();

    List<Material> getAutoCollectPriorityItems();

    Set<Material> getAutoCollectBlacklist();

    boolean isAutoCollectBlacklisted(Material material);


    boolean areMessagesEnabled();

    String getMessagePrefix();

    List<String> getMessages(MessageKey key);


    SoundSettings getOpenSound();

    SoundSettings getCloseSound();

    SoundSettings getCollectSound();


    boolean arePermissionsEnabled();

    boolean isPermissionOpBypass();

    String getPermission(PermissionNode node);

}
