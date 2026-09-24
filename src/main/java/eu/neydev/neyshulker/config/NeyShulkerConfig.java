package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundSettings;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.FillOrderType;
import eu.neydev.neyshulker.config.type.TitleMode;
import org.bukkit.Material;

import java.util.List;
import java.util.Map;
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

    /**
     * Названия безымянного шалкер-бокса по языкам клиента (ключи - локали
     * в нижнем регистре вида "ru_ru", ключ "default" - запасной вариант).
     * Значения раскрашиваются еще при загрузке.
     */
    Map<String, String> getTitleNames();

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

    /**
     * Пауза в секундах между повторными сообщениями о полном боксе для одного
     * игрока: массовый дроп не превращает чат в простыню уведомлений.
     */
    int getFullMessageCooldown();

    boolean isAutoCollectOnlyWhenInventoryFull();

    boolean isAutoCollectMergeIntoExisting();

    CollectMode getAutoCollectMode();

    boolean isAutoCollectIgnorePickupDelay();

    /**
     * @return true - авто-сбор не трогает предметы, выброшенные игроками
     */
    boolean isAutoCollectIgnorePlayerDropped();

    /**
     * @return радиус в блоках: дроп, у которого стоит другой игрок, не собирается (0 выключает)
     */
    double getAutoCollectRespectNearbyPlayers();

    FillOrderType getAutoCollectFillOrder();

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
