package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundKey;
import eu.neydev.neyshulker.config.type.SoundSettings;
import eu.neydev.neyshulker.config.type.InventoryCollectMode;
import eu.neydev.neyshulker.config.type.TitleMode;
import eu.neydev.neyshulker.service.ConsoleService;
import eu.neydev.neyshulker.util.HexColorUtil;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Менеджер конфигурации NeyShulker.
 *
 * Все значения читаются один раз при загрузке и кэшируются, обращения
 * из слушателей не трогают диск и YAML. Некорректные значения не роняют
 * загрузку: подставляется дефолт, а в консоль уходит шаблонное
 * предупреждение через ConsoleService.
 */
public class ConfigManager implements NeyShulkerConfig {

    private final NeyShulker plugin;
    private final ConsoleService consoleService;
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    private FileConfiguration config;

    // --- Общие пути ---

    private static final String PATH_ENABLED = "settings.enabled";

    // --- Шалкер-бокс ---

    private static final String PATH_OPEN_METHOD = "settings.shulker.open_method";
    private static final String PATH_TITLE = "settings.shulker.title";
    private static final String PATH_TITLE_MODE = "settings.shulker.title.mode";
    private static final String PATH_TITLE_FORMAT = "settings.shulker.title.format";
    private static final String PATH_SAVE_INTERVAL = "settings.shulker.save_interval";
    private static final String PATH_BLACKLIST_ENABLED = "settings.shulker.blacklist.enabled";
    private static final String PATH_BLACKLIST_ITEMS = "settings.shulker.blacklist.items";

    // --- Автосбор ---

    private static final String PATH_AUTO_COLLECT_ENABLED = "settings.auto_collect.enabled";
    private static final String PATH_AUTO_COLLECT_INTERVAL = "settings.auto_collect.scan.interval";
    private static final String PATH_AUTO_COLLECT_DISTANCE = "settings.auto_collect.scan.distance";
    private static final String PATH_AUTO_COLLECT_MAX_ITEMS = "settings.auto_collect.scan.limit";
    private static final String PATH_AUTO_COLLECT_ONLY_FULL = "settings.auto_collect.rules.only_full_inventory";
    private static final String PATH_AUTO_COLLECT_MERGE = "settings.auto_collect.rules.merge_into_existing";
    private static final String PATH_AUTO_COLLECT_INV_MODE = "settings.auto_collect.inventory.mode";
    private static final String PATH_AUTO_COLLECT_IGNORE_DELAY = "settings.auto_collect.rules.ignore_pickup_delay";
    private static final String PATH_AUTO_COLLECT_PERMISSION = "settings.auto_collect.permission.required";
    private static final String PATH_AUTO_COLLECT_MESSAGES = "settings.auto_collect.feedback.messages";
    private static final String PATH_AUTO_COLLECT_PRIORITY = "settings.auto_collect.priority_items";
    private static final String PATH_AUTO_COLLECT_BLACKLIST = "settings.auto_collect.blacklist";

    // --- Сообщения, звуки, права ---

    private static final String PATH_MESSAGES_ENABLED = "messages.enabled";
    private static final String PATH_MESSAGE_PREFIX = "messages.prefix";
    private static final String PATH_PERMISSIONS_ENABLED = "permissions.enabled";

    // --- Ограничения значений ---

    private static final int MIN_INTERVAL_TICKS = 1;
    private static final double MIN_DISTANCE = 0.0D;

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
    private TitleMode titleMode;
    private String titleFormat;
    private int saveInterval;
    private boolean blacklistEnabled;
    private Set<Material> blacklistedMaterials;

    private boolean autoCollectEnabled;
    private boolean autoCollectPermissionRequired;
    private int autoCollectInterval;
    private double autoCollectMaxDistance;
    private boolean autoCollectOnlyWhenInventoryFull;
    private boolean autoCollectMergeIntoExisting;
    private InventoryCollectMode autoCollectInventoryMode;
    private int autoCollectMaxItemsPerTick;
    private boolean autoCollectIgnorePickupDelay;
    private boolean autoCollectMessages;
    private List<Material> autoCollectPriorityItems;
    private Set<Material> autoCollectBlacklist;

    private boolean messagesEnabled;
    private String messagePrefix;
    private final Map<MessageKey, List<String>> messages = new EnumMap<>(MessageKey.class);

    private final Map<SoundKey, SoundSettings> sounds = new EnumMap<>(SoundKey.class);

    private boolean permissionsEnabled;
    private final Map<PermissionNode, String> permissions = new EnumMap<>(PermissionNode.class);

    public ConfigManager(NeyShulker plugin, ConsoleService consoleService) {

        this.plugin = plugin;
        this.consoleService = consoleService;

        saveDefaultConfig();

        loadConfig();
        cacheConfigValues();

    }

    /**
     * Перезагружает конфигурацию и уведомляет подписанные компоненты.
     */
    public void reload() {

        plugin.reloadConfig();

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
    public TitleMode getTitleMode() {
        return titleMode;
    }

    @Override
    public String getTitleFormat() {
        return titleFormat;
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
    public boolean isAutoCollectMergeIntoExisting() {
        return autoCollectMergeIntoExisting;
    }

    @Override
    public InventoryCollectMode getAutoCollectInventoryMode() {
        return autoCollectInventoryMode;
    }

    @Override
    public int getAutoCollectMaxItemsPerTick() {
        return autoCollectMaxItemsPerTick;
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

        List<String> cached = messages.get(key);

        if (cached != null) {
            return cached;
        }

        // Дефолт ключа может содержать переносы: каждое полотно режется на строки
        return List.of(HexColorUtil.color(key.getDefaultMessage()).split("\n"));

    }

    @Override
    public SoundSettings getOpenSound() {
        return sounds.get(SoundKey.OPEN);
    }

    @Override
    public SoundSettings getCloseSound() {
        return sounds.get(SoundKey.CLOSE);
    }

    @Override
    public SoundSettings getCollectSound() {
        return sounds.get(SoundKey.COLLECT);
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
        openMethod = parseOpenMethod();
        titleMode = parseTitleMode();
        titleFormat = color(readTitleFormat());
        saveInterval = intOrWarn(config.getInt(PATH_SAVE_INTERVAL, 10),
                PATH_SAVE_INTERVAL, MIN_INTERVAL_TICKS);
        blacklistEnabled = config.getBoolean(PATH_BLACKLIST_ENABLED, true);
        blacklistedMaterials = readMaterials(PATH_BLACKLIST_ITEMS, DEFAULT_BLACKLIST);

    }

    private void cacheAutoCollectValues() {

        autoCollectEnabled = config.getBoolean(PATH_AUTO_COLLECT_ENABLED, true);
        autoCollectPermissionRequired = config.getBoolean(PATH_AUTO_COLLECT_PERMISSION, false);
        autoCollectInterval = intOrWarn(config.getInt(PATH_AUTO_COLLECT_INTERVAL, 20),
                PATH_AUTO_COLLECT_INTERVAL, MIN_INTERVAL_TICKS);
        autoCollectMaxDistance = doubleOrWarn(config.getDouble(PATH_AUTO_COLLECT_DISTANCE, 4.5D),
                PATH_AUTO_COLLECT_DISTANCE, MIN_DISTANCE);
        autoCollectOnlyWhenInventoryFull = config.getBoolean(PATH_AUTO_COLLECT_ONLY_FULL, false);
        autoCollectMergeIntoExisting = config.getBoolean(PATH_AUTO_COLLECT_MERGE, true);
        autoCollectInventoryMode = parseInventoryCollectMode();
        autoCollectMaxItemsPerTick = intOrWarn(config.getInt(PATH_AUTO_COLLECT_MAX_ITEMS, 8),
                PATH_AUTO_COLLECT_MAX_ITEMS, MIN_INTERVAL_TICKS);
        autoCollectIgnorePickupDelay = config.getBoolean(PATH_AUTO_COLLECT_IGNORE_DELAY, false);
        autoCollectMessages = config.getBoolean(PATH_AUTO_COLLECT_MESSAGES, false);
        autoCollectBlacklist = readMaterials(PATH_AUTO_COLLECT_BLACKLIST, DEFAULT_AUTO_COLLECT_BLACKLIST);
        autoCollectPriorityItems = readMaterialList(PATH_AUTO_COLLECT_PRIORITY, DEFAULT_PRIORITY_ITEMS);

    }

    private void cacheMessages() {

        messagesEnabled = config.getBoolean(PATH_MESSAGES_ENABLED, true);
        messagePrefix = color(config.getString(PATH_MESSAGE_PREFIX, ""));
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

        sounds.clear();

        for (SoundKey key : SoundKey.values()) {
            sounds.put(key, loadSound(key));
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

    // --- Чтение значений с валидацией ---

    /**
     * Читает режим досортировки инвентаря; при некорректном значении - MATCHING.
     *
     * @return режим источника "инвентарь"
     */
    private @NotNull InventoryCollectMode parseInventoryCollectMode() {

        String configValue = config.getString(PATH_AUTO_COLLECT_INV_MODE,
                InventoryCollectMode.MATCHING.name());

        InventoryCollectMode parsed = InventoryCollectMode.fromString(configValue, null);

        if (parsed != null) {
            return parsed;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", PATH_AUTO_COLLECT_INV_MODE,
                "value", String.valueOf(configValue),
                "defaultValue", InventoryCollectMode.MATCHING.name());

        return InventoryCollectMode.MATCHING;

    }

    /**
     * Читает режим заголовка.
     *
     * Поддерживаются обе формы конфигурации: секция title с mode/format
     * и legacy-строка title: "..." (равносильна CUSTOM с этим шаблоном).
     *
     * @return режим заголовка GUI
     */
    private @NotNull TitleMode parseTitleMode() {

        String configValue = config.get(PATH_TITLE) instanceof org.bukkit.configuration.ConfigurationSection section
                ? section.getString("mode", TitleMode.CUSTOM.name())
                : TitleMode.CUSTOM.name();

        TitleMode parsed = TitleMode.fromString(configValue, null);

        if (parsed != null) {
            return parsed;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", PATH_TITLE_MODE,
                "value", String.valueOf(configValue),
                "defaultValue", TitleMode.CUSTOM.name());

        return TitleMode.CUSTOM;

    }

    /**
     * Читает шаблон заголовка из секции или legacy-строки.
     *
     * @return сырой шаблон с плейсхолдером {shulker_name}
     */
    private @NotNull String readTitleFormat() {

        Object raw = config.get(PATH_TITLE);

        if (raw instanceof org.bukkit.configuration.ConfigurationSection section) {
            return section.getString("format", "{shulker_name}");
        }

        if (raw instanceof String legacy) {
            return legacy;
        }

        return "{shulker_name}";

    }

    /**
     * Читает способ открытия; при некорректном значении возвращается AIR.
     *
     * @return способ открытия шалкер-бокса
     */
    private @NotNull OpenMethodType parseOpenMethod() {

        String configValue = config.getString(PATH_OPEN_METHOD, OpenMethodType.AIR.name());
        OpenMethodType parsed = OpenMethodType.fromString(configValue, null);

        if (parsed != null) {
            return parsed;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", PATH_OPEN_METHOD,
                "value", String.valueOf(configValue),
                "defaultValue", OpenMethodType.AIR.name());

        return OpenMethodType.AIR;

    }

    /**
     * Читает звук ключа; неизвестное имя подменяется дефолтом с предупреждением.
     *
     * @param key ключ звука
     * @return готовые настройки звука
     */
    private @NotNull SoundSettings loadSound(@NotNull SoundKey key) {

        String path = "sounds." + key.getConfigKey();

        return SoundSettings.of(
                config.getString(path + ".sound"),
                key.getDefaultSound(),
                config.getBoolean(path + ".enabled", true),
                (float) config.getDouble(path + ".volume", key.getDefaultVolume()),
                (float) config.getDouble(path + ".pitch", key.getDefaultPitch()),
                () -> consoleService.log(ConsoleMessage.UNKNOWN_SOUND,
                        "path", path + ".sound",
                        "value", String.valueOf(config.getString(path + ".sound")),
                        "defaultValue", key.getDefaultSound().name())
        );

    }

    private int intOrWarn(int value, @NotNull String path, int minimum) {

        if (value >= minimum) {
            return value;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", path,
                "value", String.valueOf(value),
                "defaultValue", String.valueOf(minimum));

        return minimum;

    }

    private double doubleOrWarn(double value, @NotNull String path, double minimum) {

        if (value >= minimum) {
            return value;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", path,
                "value", String.valueOf(value),
                "defaultValue", String.valueOf(minimum));

        return minimum;

    }

    private @NotNull List<String> readColoredLines(String path, String defaultValue) {

        Object raw = config.get(path);

        if (raw == null) {
            return List.of(color(defaultValue));
        }

        if (raw instanceof List<?> list) {

            List<String> lines = new ArrayList<>(list.size());

            for (Object element : list) {

                if (element != null) {
                    lines.add(color(String.valueOf(element)));
                }

            }

            return Collections.unmodifiableList(lines);

        }

        return List.of(color(String.valueOf(raw)));

    }

    private @NotNull Set<Material> readMaterials(String path, Set<Material> defaults) {

        List<String> names = config.getStringList(path);

        if (names.isEmpty()) {
            return EnumSet.copyOf(defaults);
        }

        Set<Material> materials = EnumSet.noneOf(Material.class);

        for (String name : names) {

            Material material = readMaterial(name, path);

            if (material != null) {
                materials.add(material);
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

            Material material = readMaterial(name, path);

            if (material != null && !materials.contains(material)) {
                materials.add(material);
            }

        }

        return Collections.unmodifiableList(materials);

    }

    private @Nullable Material readMaterial(@Nullable String name, @NotNull String path) {

        if (name == null || name.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(name.trim());

        if (material == null) {

            consoleService.log(ConsoleMessage.UNKNOWN_MATERIAL,
                    "path", path,
                    "value", name);

        }

        return material;

    }

    private @NotNull String color(@Nullable String text) {
        return HexColorUtil.color(text);
    }
}
