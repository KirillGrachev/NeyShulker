package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundKey;
import eu.neydev.neyshulker.util.HexColorUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер конфигурации NeyShulker.
 * Значения читаются один раз при загрузке и кэшируются,
 * поэтому обращения из слушателей не трогают диск и YAML.
 */
public class ConfigManager implements NeyShulkerConfig {

    private final NeyShulker plugin;
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    private FileConfiguration config;

    // --- Общие пути ---

    private static final String PATH_ENABLED = "settings.enabled";

    // --- Шалкер-бокс ---

    private static final String PATH_OPEN_METHOD = "settings.shulker.open_method";
    private static final String PATH_TITLE = "settings.shulker.title";
    private static final String PATH_PREVENT_NESTED = "settings.shulker.prevent_nested";
    private static final String PATH_SAVE_INTERVAL = "settings.shulker.save_interval";
    private static final String PATH_BLACKLIST_ENABLED = "settings.shulker.blacklist.enabled";
    private static final String PATH_BLACKLIST_ITEMS = "settings.shulker.blacklist.items";

    // --- Автосбор ---

    private static final String PATH_AUTO_COLLECT_ENABLED = "settings.auto_collect.enabled";
    private static final String PATH_AUTO_COLLECT_PERMISSION = "settings.auto_collect.permission_required";
    private static final String PATH_AUTO_COLLECT_INTERVAL = "settings.auto_collect.check_interval";
    private static final String PATH_AUTO_COLLECT_DISTANCE = "settings.auto_collect.max_distance";
    private static final String PATH_AUTO_COLLECT_ONLY_FULL = "settings.auto_collect.only_when_inventory_full";
    private static final String PATH_AUTO_COLLECT_MAX_ITEMS = "settings.auto_collect.max_items_per_tick";
    private static final String PATH_AUTO_COLLECT_SHULKERS = "settings.auto_collect.collect_shulker_boxes";
    private static final String PATH_AUTO_COLLECT_IGNORE_DELAY = "settings.auto_collect.ignore_pickup_delay";
    private static final String PATH_AUTO_COLLECT_MESSAGES = "settings.auto_collect.messages";
    private static final String PATH_AUTO_COLLECT_PRIORITY = "settings.auto_collect.priority_items";
    private static final String PATH_AUTO_COLLECT_BLACKLIST = "settings.auto_collect.blacklist";

    // --- Сообщения, звуки, права ---

    private static final String PATH_MESSAGES_ENABLED = "messages.enabled";
    private static final String PATH_MESSAGE_PREFIX = "messages.prefix";
    private static final String PATH_SOUNDS_ENABLED = "sounds.enabled";
    private static final String PATH_PERMISSIONS_ENABLED = "permissions.enabled";

    // --- Значения по умолчанию ---

    private static final Set<Material> DEFAULT_BLACKLIST = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.COMMAND_BLOCK,
            Material.CHAIN_COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
            Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.SPAWNER,
            Material.END_PORTAL_FRAME
    );

    private static final Set<Material> DEFAULT_AUTO_COLLECT_BLACKLIST = EnumSet.of(
            Material.BEDROCK, Material.BARRIER, Material.SPAWNER,
            Material.COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW
    );

    private static final List<Material> DEFAULT_PRIORITY_ITEMS = List.of(
            Material.NETHERITE_INGOT, Material.NETHERITE_SCRAP, Material.ANCIENT_DEBRIS,
            Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT
    );

    // --- Кэш ---

    private boolean pluginEnabled;

    private OpenMethodType openMethod;
    private String shulkerTitle;
    private boolean nestedPrevented;
    private int saveInterval;
    private boolean blacklistEnabled;
    private Set<Material> blacklistedMaterials;

    private boolean autoCollectEnabled;
    private boolean autoCollectPermissionRequired;
    private int autoCollectInterval;
    private double autoCollectMaxDistance;
    private boolean autoCollectOnlyWhenInventoryFull;
    private int autoCollectMaxItemsPerTick;
    private boolean autoCollectShulkerBoxes;
    private boolean autoCollectIgnorePickupDelay;
    private boolean autoCollectMessages;
    private List<Material> autoCollectPriorityItems;
    private Set<Material> autoCollectBlacklist;

    private boolean messagesEnabled;
    private String messagePrefix;
    private final Map<MessageKey, List<String>> messages = new EnumMap<>(MessageKey.class);

    private boolean soundsEnabled;
    private final Map<SoundKey, Sound> sounds = new EnumMap<>(SoundKey.class);
    private final Map<SoundKey, Float> soundVolumes = new EnumMap<>(SoundKey.class);
    private final Map<SoundKey, Float> soundPitches = new EnumMap<>(SoundKey.class);

    private boolean permissionsEnabled;
    private final Map<PermissionNode, String> permissions = new EnumMap<>(PermissionNode.class);

    public ConfigManager(NeyShulker plugin) {

        this.plugin = plugin;

        saveDefaultConfig();

        loadConfig();
        cacheConfigValues();

    }

    /**
     * Перезагружает конфигурацию и уведомляет подписанные компоненты.
     */
    public void reload() {

        loadConfig();
        cacheConfigValues();

        reloadListeners.forEach(Runnable::run);

    }

    /**
     * Подписывает компонент на перезагрузку конфигурации.
     *
     * @param listener действие, выполняемое после reload
     */
    public void onReload(Runnable listener) {
        reloadListeners.add(listener);
    }

    @Override
    public boolean isPluginEnabled() {
        return pluginEnabled;
    }

    @Override
    public OpenMethodType getOpenMethod() {
        return openMethod;
    }

    @Override
    public String getShulkerTitle() {
        return shulkerTitle;
    }

    @Override
    public boolean isNestedPrevented() {
        return nestedPrevented;
    }

    @Override
    public int getSaveInterval() {
        return saveInterval;
    }

    @Override
    public boolean isBlacklistEnabled() {
        return blacklistEnabled;
    }

    @Override
    public Set<Material> getBlacklistedMaterials() {
        return blacklistedMaterials;
    }

    @Override
    public boolean isBlacklisted(Material material) {
        return blacklistEnabled && material != null && blacklistedMaterials.contains(material);
    }

    @Override
    public boolean isAutoCollectEnabled() {
        return autoCollectEnabled;
    }

    @Override
    public boolean isAutoCollectPermissionRequired() {
        return autoCollectPermissionRequired;
    }

    @Override
    public int getAutoCollectInterval() {
        return autoCollectInterval;
    }

    @Override
    public double getAutoCollectMaxDistance() {
        return autoCollectMaxDistance;
    }

    @Override
    public boolean isAutoCollectOnlyWhenInventoryFull() {
        return autoCollectOnlyWhenInventoryFull;
    }

    @Override
    public int getAutoCollectMaxItemsPerTick() {
        return autoCollectMaxItemsPerTick;
    }

    @Override
    public boolean isAutoCollectShulkerBoxesEnabled() {
        return autoCollectShulkerBoxes;
    }

    @Override
    public boolean isAutoCollectIgnorePickupDelay() {
        return autoCollectIgnorePickupDelay;
    }

    @Override
    public boolean areAutoCollectMessagesEnabled() {
        return autoCollectMessages;
    }

    @Override
    public List<Material> getAutoCollectPriorityItems() {
        return autoCollectPriorityItems;
    }

    @Override
    public Set<Material> getAutoCollectBlacklist() {
        return autoCollectBlacklist;
    }

    @Override
    public boolean isAutoCollectBlacklisted(Material material) {
        return material != null && autoCollectBlacklist.contains(material);
    }

    @Override
    public boolean areMessagesEnabled() {
        return messagesEnabled;
    }

    @Override
    public String getMessagePrefix() {
        return messagePrefix;
    }

    @Override
    public List<String> getMessages(MessageKey key) {
        return messages.getOrDefault(key, List.of(HexColorUtil.color(key.getDefaultMessage())));
    }

    @Override
    public boolean areSoundsEnabled() {
        return soundsEnabled;
    }

    @Override
    public Sound getSound(SoundKey key) {
        return sounds.getOrDefault(key, key.getDefaultSound());
    }

    @Override
    public float getSoundVolume(SoundKey key) {
        return soundVolumes.getOrDefault(key, key.getDefaultVolume());
    }

    @Override
    public float getSoundPitch(SoundKey key) {
        return soundPitches.getOrDefault(key, key.getDefaultPitch());
    }

    @Override
    public boolean arePermissionsEnabled() {
        return permissionsEnabled;
    }

    @Override
    public String getPermission(PermissionNode node) {
        return permissions.getOrDefault(node, node.getDefaultPermission());
    }

    // --- Загрузка ---

    private void saveDefaultConfig() {
        plugin.saveDefaultConfig();
    }

    private void loadConfig() {
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    private void cacheConfigValues() {

        cacheShulkerValues();
        cacheAutoCollectValues();
        cacheMessages();
        cacheSounds();
        cachePermissions();

    }

    private void cacheShulkerValues() {

        pluginEnabled = config.getBoolean(PATH_ENABLED, true);
        openMethod = OpenMethodType.fromString(config.getString(PATH_OPEN_METHOD), OpenMethodType.SHIFT);
        shulkerTitle = HexColorUtil.color(config.getString(PATH_TITLE, "{shulker_name}"));
        nestedPrevented = config.getBoolean(PATH_PREVENT_NESTED, true);
        saveInterval = Math.max(1, config.getInt(PATH_SAVE_INTERVAL, 10));
        blacklistEnabled = config.getBoolean(PATH_BLACKLIST_ENABLED, true);
        blacklistedMaterials = readMaterials(PATH_BLACKLIST_ITEMS, DEFAULT_BLACKLIST);

    }

    private void cacheAutoCollectValues() {

        autoCollectEnabled = config.getBoolean(PATH_AUTO_COLLECT_ENABLED, true);
        autoCollectPermissionRequired = config.getBoolean(PATH_AUTO_COLLECT_PERMISSION, false);
        autoCollectInterval = Math.max(1, config.getInt(PATH_AUTO_COLLECT_INTERVAL, 20));
        autoCollectMaxDistance = Math.max(0.0D, config.getDouble(PATH_AUTO_COLLECT_DISTANCE, 3.0D));
        autoCollectOnlyWhenInventoryFull = config.getBoolean(PATH_AUTO_COLLECT_ONLY_FULL, true);
        autoCollectMaxItemsPerTick = Math.max(1, config.getInt(PATH_AUTO_COLLECT_MAX_ITEMS, 8));
        autoCollectShulkerBoxes = config.getBoolean(PATH_AUTO_COLLECT_SHULKERS, false);
        autoCollectIgnorePickupDelay = config.getBoolean(PATH_AUTO_COLLECT_IGNORE_DELAY, false);
        autoCollectMessages = config.getBoolean(PATH_AUTO_COLLECT_MESSAGES, false);
        autoCollectBlacklist = readMaterials(PATH_AUTO_COLLECT_BLACKLIST, DEFAULT_AUTO_COLLECT_BLACKLIST);
        autoCollectPriorityItems = readMaterialList(PATH_AUTO_COLLECT_PRIORITY, DEFAULT_PRIORITY_ITEMS);

    }

    private void cacheMessages() {

        messagesEnabled = config.getBoolean(PATH_MESSAGES_ENABLED, true);
        messagePrefix = HexColorUtil.color(config.getString(PATH_MESSAGE_PREFIX, ""));
        messages.clear();

        for (MessageKey key : MessageKey.values()) {

            String path = "messages." + key.getConfigKey();

            if (!config.getBoolean(path + ".enabled", true)) {
                messages.put(key, List.of());
                continue;
            }

            messages.put(key, readColoredLines(path + ".text", key.getDefaultMessage()));

        }

    }

    private void cacheSounds() {

        soundsEnabled = config.getBoolean(PATH_SOUNDS_ENABLED, true);

        sounds.clear();
        soundVolumes.clear();
        soundPitches.clear();

        if (!soundsEnabled) {
            return;
        }

        for (SoundKey key : SoundKey.values()) {

            String path = "sounds." + key.getConfigKey();

            sounds.put(key, readSound(path + ".sound", key.getDefaultSound()));
            soundVolumes.put(key, (float) config.getDouble(path + ".volume", key.getDefaultVolume()));
            soundPitches.put(key, (float) config.getDouble(path + ".pitch", key.getDefaultPitch()));

        }

    }

    private void cachePermissions() {

        permissionsEnabled = config.getBoolean(PATH_PERMISSIONS_ENABLED, false);
        permissions.clear();

        for (PermissionNode node : PermissionNode.values()) {
            permissions.put(node, config.getString("permissions." + node.getConfigKey(),
                    node.getDefaultPermission()));
        }

    }

    // --- Чтение значений ---

    private @NotNull List<String> readColoredLines(String path, String defaultValue) {

        Object raw = config.get(path);

        if (raw == null) {
            return List.of(HexColorUtil.color(defaultValue));
        }

        if (raw instanceof List<?> list) {

            List<String> lines = new ArrayList<>(list.size());

            for (Object element : list) {

                if (element != null) {
                    lines.add(HexColorUtil.color(String.valueOf(element)));
                }

            }

            return Collections.unmodifiableList(lines);

        }

        return List.of(HexColorUtil.color(String.valueOf(raw)));

    }

    private @NotNull Set<Material> readMaterials(String path, Set<Material> defaults) {

        List<String> names = config.getStringList(path);

        if (names.isEmpty()) {
            return EnumSet.copyOf(defaults);
        }

        Set<Material> materials = EnumSet.noneOf(Material.class);

        for (String name : names) {

            Material material = readMaterial(name);

            if (material != null) {
                materials.add(material);
            } else {
                warnUnknownMaterial(path, name);
            }

        }

        return materials;

    }

    private @NotNull List<Material> readMaterialList(String path, List<Material> defaults) {

        List<String> names = config.getStringList(path);

        if (names.isEmpty()) {
            return List.copyOf(defaults);
        }

        List<Material> materials = new ArrayList<>(names.size());

        for (String name : names) {

            Material material = readMaterial(name);

            if (material != null && !materials.contains(material)) {
                materials.add(material);
            } else if (material == null) {
                warnUnknownMaterial(path, name);
            }

        }

        return Collections.unmodifiableList(materials);

    }

    private Material readMaterial(String name) {

        if (name == null || name.isBlank()) {
            return null;
        }

        // isItem() не проверяем: реестр материалов поднимается только на сервере,
        // а "лишний" блок-материал в списке безвреден - предметы им не совпадут
        return Material.matchMaterial(name.trim());

    }

    private @NotNull Sound readSound(String path, Sound defaultSound) {

        String name = config.getString(path, defaultSound.name());

        if (name == null || name.isBlank()) {
            return defaultSound;
        }

        try {
            return Sound.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Неизвестный звук в " + path + ": '" + name
                    + "'. Использован " + defaultSound.name() + ".");
            return defaultSound;
        }

    }

    private void warnUnknownMaterial(String path, String name) {
        plugin.getLogger().warning("Неизвестный предмет в " + path + ": '" + name + "'. Значение пропущено.");
    }
}
