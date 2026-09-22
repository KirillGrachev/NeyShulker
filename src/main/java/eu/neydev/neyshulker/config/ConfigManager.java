package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.ConsoleMessage;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundKey;
import eu.neydev.neyshulker.config.type.SoundSettings;
import eu.neydev.neyshulker.config.type.CollectMode;
import eu.neydev.neyshulker.config.type.FillOrderType;
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
import java.util.HashMap;
import java.util.Locale;
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


    private static final String PATH_ENABLED = "settings.enabled";


    private static final String PATH_OPEN_METHOD = "settings.shulker.open_method";
    private static final String PATH_TITLE = "settings.shulker.title";
    private static final String PATH_TITLE_MODE = "settings.shulker.title.mode";
    private static final String PATH_TITLE_FORMAT = "settings.shulker.title.format";
    private static final String PATH_SAVE_INTERVAL = "settings.shulker.save_interval";
    private static final String PATH_TITLE_NAMES = "settings.shulker.title.names";
    private static final String PATH_BLOCKED_ITEMS_ENABLED = "settings.shulker.blocked_items.enabled";
    private static final String PATH_BLOCKED_ITEMS = "settings.shulker.blocked_items.items";
    private static final String PATH_LEGACY_BLACKLIST_ENABLED = "settings.shulker.blacklist.enabled";
    private static final String PATH_LEGACY_BLACKLIST_ITEMS = "settings.shulker.blacklist.items";


    private static final String PATH_AUTO_COLLECT_ENABLED = "settings.auto_collect.enabled";
    private static final String PATH_AUTO_COLLECT_DISTANCE = "settings.auto_collect.scan.distance";
    private static final String PATH_WAVE_PERIOD = "settings.auto_collect.waves.period";
    private static final String PATH_WAVE_PLAYERS = "settings.auto_collect.waves.players_per_wave";
    private static final String PATH_WAVE_ACTIONS = "settings.auto_collect.waves.actions_per_wave";
    private static final String PATH_WAVE_QUEUE = "settings.auto_collect.waves.queue_per_player";
    private static final String PATH_AUTO_COLLECT_ONLY_FULL = "settings.auto_collect.rules.only_full_inventory";
    private static final String PATH_AUTO_COLLECT_MERGE = "settings.auto_collect.rules.merge_into_existing";
    private static final String PATH_AUTO_COLLECT_MODE = "settings.auto_collect.mode";
    private static final String PATH_AUTO_COLLECT_IGNORE_DELAY = "settings.auto_collect.rules.ignore_pickup_delay";
    private static final String PATH_AUTO_COLLECT_PERMISSION = "settings.auto_collect.permission.required";
    private static final String PATH_AUTO_COLLECT_FILL_ORDER = "settings.auto_collect.rules.fill_order";
    private static final String PATH_AUTO_COLLECT_PRIORITY = "settings.auto_collect.priority_items";
    private static final String PATH_AUTO_COLLECT_IGNORED = "settings.auto_collect.ignored_items";
    private static final String PATH_LEGACY_AUTO_COLLECT_BLACKLIST = "settings.auto_collect.blacklist";


    private static final String PATH_MESSAGES_ENABLED = "messages.enabled";
    private static final String PATH_MESSAGE_PREFIX = "messages.prefix";
    private static final String PATH_PERMISSIONS_ENABLED = "permissions.enabled";
    private static final String PATH_PERMISSION_OP_BYPASS = "permissions.op_bypass";


    private static final int MIN_INTERVAL_TICKS = 1;
    private static final double MIN_DISTANCE = 0.0D;


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


    private boolean pluginEnabled;

    private OpenMethodType openMethod;
    private TitleMode titleMode;
    private String titleFormat;
    private Map<String, String> titleNames;
    private int saveInterval;
    private boolean blacklistEnabled;
    private Set<Material> blacklistedMaterials;

    private boolean autoCollectEnabled;
    private boolean autoCollectPermissionRequired;
    private double autoCollectMaxDistance;
    private int wavePeriod;
    private int wavePlayers;
    private int waveActions;
    private int waveQueue;
    private boolean autoCollectOnlyWhenInventoryFull;
    private boolean autoCollectMergeIntoExisting;
    private CollectMode autoCollectMode;
    private boolean autoCollectIgnorePickupDelay;
    private FillOrderType autoCollectFillOrder;
    private List<Material> autoCollectPriorityItems;
    private Set<Material> autoCollectBlacklist;

    private boolean messagesEnabled;
    private String messagePrefix;
    private final Map<MessageKey, List<String>> messages = new EnumMap<>(MessageKey.class);

    private final Map<SoundKey, SoundSettings> sounds = new EnumMap<>(SoundKey.class);

    private boolean permissionsEnabled;
    private boolean permissionOpBypass;
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
    public double getAutoCollectMaxDistance() {
        return autoCollectMaxDistance;
    }

    @Override
    public int getWavePeriod() {
        return wavePeriod;
    }

    @Override
    public int getPlayersPerWave() {
        return wavePlayers;
    }

    @Override
    public int getActionsPerWave() {
        return waveActions;
    }

    @Override
    public int getQueuePerPlayer() {
        return waveQueue;
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
    public CollectMode getAutoCollectMode() {
        return autoCollectMode;
    }

    @Override
    public boolean isAutoCollectIgnorePickupDelay() {
        return autoCollectIgnorePickupDelay;
    }

    @Override
    public FillOrderType getAutoCollectFillOrder() {
        return autoCollectFillOrder;
    }

    @Override
    public Map<String, String> getTitleNames() {
        return titleNames;
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
    public boolean isPermissionOpBypass() {
        return permissionOpBypass;
    }

    @Override
    public String getPermission(PermissionNode node) {
        return permissions.getOrDefault(node, node.getDefaultPermission());
    }


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
        titleNames = readTitleNames();
        blacklistEnabled = readBoolean(PATH_BLOCKED_ITEMS_ENABLED, PATH_LEGACY_BLACKLIST_ENABLED, true);
        blacklistedMaterials = readMaterials(PATH_BLOCKED_ITEMS, PATH_LEGACY_BLACKLIST_ITEMS, DEFAULT_BLACKLIST);

    }

    private void cacheAutoCollectValues() {

        autoCollectEnabled = config.getBoolean(PATH_AUTO_COLLECT_ENABLED, true);
        autoCollectPermissionRequired = config.getBoolean(PATH_AUTO_COLLECT_PERMISSION, false);
        autoCollectMaxDistance = doubleOrWarn(config.getDouble(PATH_AUTO_COLLECT_DISTANCE, 4.5D),
                PATH_AUTO_COLLECT_DISTANCE, MIN_DISTANCE);
        wavePeriod = intOrWarn(config.getInt(PATH_WAVE_PERIOD, 10),
                PATH_WAVE_PERIOD, MIN_INTERVAL_TICKS);
        wavePlayers = intOrWarn(config.getInt(PATH_WAVE_PLAYERS, 5),
                PATH_WAVE_PLAYERS, MIN_INTERVAL_TICKS);
        waveActions = intOrWarn(config.getInt(PATH_WAVE_ACTIONS, 16),
                PATH_WAVE_ACTIONS, MIN_INTERVAL_TICKS);
        waveQueue = intOrWarn(config.getInt(PATH_WAVE_QUEUE, 32),
                PATH_WAVE_QUEUE, MIN_INTERVAL_TICKS);
        autoCollectOnlyWhenInventoryFull = config.getBoolean(PATH_AUTO_COLLECT_ONLY_FULL, false);
        autoCollectMergeIntoExisting = config.getBoolean(PATH_AUTO_COLLECT_MERGE, true);
        autoCollectMode = parseCollectMode();
        autoCollectIgnorePickupDelay = config.getBoolean(PATH_AUTO_COLLECT_IGNORE_DELAY, false);
        autoCollectFillOrder = parseFillOrder();
        autoCollectBlacklist = readMaterials(PATH_AUTO_COLLECT_IGNORED,
                PATH_LEGACY_AUTO_COLLECT_BLACKLIST, DEFAULT_AUTO_COLLECT_BLACKLIST);
        autoCollectPriorityItems = readMaterialList(PATH_AUTO_COLLECT_PRIORITY, DEFAULT_PRIORITY_ITEMS);

    }

    private void cacheMessages() {

        messagesEnabled = config.getBoolean(PATH_MESSAGES_ENABLED, true);
        messagePrefix = color(config.getString(PATH_MESSAGE_PREFIX, ""));
        messages.clear();

        for (MessageKey key : MessageKey.values()) {

            String path = "messages." + key.getConfigKey();

            if (!config.getBoolean(path + ".enabled", key.isDefaultEnabled())) {
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
        permissionOpBypass = config.getBoolean(PATH_PERMISSION_OP_BYPASS, false);
        permissions.clear();

        for (PermissionNode node : PermissionNode.values()) {
            permissions.put(node, config.getString("permissions." + node.getConfigKey(),
                    node.getDefaultPermission()));
        }

    }


    /**
     * Читает глобальный режим автосбора; при некорректном значении - ALL.
     *
     * @return режим обоих источников сбора
     */
    private @NotNull CollectMode parseCollectMode() {

        String configValue = config.getString(PATH_AUTO_COLLECT_MODE, CollectMode.ALL.name());
        CollectMode parsed = CollectMode.fromString(configValue, null);

        if (parsed != null) {
            return parsed;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", PATH_AUTO_COLLECT_MODE,
                "value", configValue,
                "defaultValue", CollectMode.ALL.name());

        return CollectMode.ALL;

    }

    /**
     * Читает стратегию выбора бокса на нижнем ярусе подбора цели;
     * при некорректном значении - BALANCED.
     *
     * @return стратегию распределения дропа по боксам
     */
    private @NotNull FillOrderType parseFillOrder() {

        String configValue = config.getString(PATH_AUTO_COLLECT_FILL_ORDER, FillOrderType.BALANCED.name());
        FillOrderType parsed = FillOrderType.fromString(configValue, null);

        if (parsed != null) {
            return parsed;
        }

        consoleService.log(ConsoleMessage.INVALID_VALUE,
                "path", PATH_AUTO_COLLECT_FILL_ORDER,
                "value", configValue,
                "defaultValue", FillOrderType.BALANCED.name());

        return FillOrderType.BALANCED;

    }

    /**
     * Читает имена безымянного шалкер-бокса по языкам клиента.
     * Ключи приводятся к нижнему регистру, значения получают цвета сразу.
     *
     * @return карту locale -> имя (пустую, если секции нет)
     */
    private @NotNull Map<String, String> readTitleNames() {

        Object raw = config.get(PATH_TITLE_NAMES);

        if (!(raw instanceof org.bukkit.configuration.ConfigurationSection section)) {
            return Map.of();
        }

        Map<String, String> names = new HashMap<>();

        for (String key : section.getKeys(false)) {

            String name = section.getString(key);

            if (name != null && !name.isBlank()) {
                names.put(key.toLowerCase(Locale.ROOT), color(name));
            }

        }

        return Collections.unmodifiableMap(names);

    }

    /**
     * Читает булев флаг с legacy-путем как запасным: старые конфигурации
     * продолжают работать, а в консоль уходит предупреждение о переименовании.
     *
     * @param path        актуальный путь
     * @param legacyPath  устаревший путь
     * @param defaultValue значение по умолчанию
     * @return значение флага
     */
    private boolean readBoolean(@NotNull String path, @NotNull String legacyPath, boolean defaultValue) {

        if (config.isSet(path)) {
            return config.getBoolean(path, defaultValue);
        }

        if (config.isSet(legacyPath)) {

            consoleService.log(ConsoleMessage.LEGACY_PATH,
                    "path", legacyPath,
                    "replacement", path);

            return config.getBoolean(legacyPath, defaultValue);
        }

        return defaultValue;

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
                "value", configValue,
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
                "value", configValue,
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

    private @NotNull Set<Material> readMaterials(String path,
                                                  String legacyPath,
                                                  Set<Material> defaults) {

        List<String> names = config.getStringList(path);
        String sourcePath = path;

        if (names.isEmpty() && config.isSet(legacyPath)) {

            names = config.getStringList(legacyPath);
            sourcePath = legacyPath;

            consoleService.log(ConsoleMessage.LEGACY_PATH,
                    "path", legacyPath,
                    "replacement", path);

        }

        if (names.isEmpty()) {
            return EnumSet.copyOf(defaults);
        }

        Set<Material> materials = EnumSet.noneOf(Material.class);

        for (String name : names) {

            Material material = readMaterial(name, sourcePath);

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
