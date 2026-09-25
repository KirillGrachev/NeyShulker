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
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;

/**
 * Менеджер конфигурации NeyShulker.
 *
 * Все значения читаются один раз при загрузке и собираются в неизменяемый
 * снапшот {@link ConfigSnapshot}, который подменяется одной volatile-записью.
 * Читатели (главный поток и async-поток PlaceholderAPI) всегда видят
 * согласованный набор значений: ни составных гонок «флаг новый, список
 * старый», ни проблем видимости. Обращения из слушателей не трогают диск
 * и YAML. Некорректные значения не роняют загрузку: подставляется дефолт,
 * а в консоль уходит шаблонное предупреждение через ConsoleService.
 *
 * Источник истины один - {@code plugin.getConfig()}: отдельного парсинга
 * файла менеджер не ведет, reload использует штатный reloadConfig().
 * Поврежденный YAML не тонет в тишине: файл перечитывается явно и ошибка
 * уходит в SEVERE-лог со стектрейсом.
 */
public class ConfigManager implements NeyShulkerConfig {

    private final NeyShulker plugin;
    private final ConsoleService consoleService;
    private final List<Runnable> reloadListeners = new CopyOnWriteArrayList<>();

    private static final String PATH_ENABLED = "settings.enabled";

    private static final String PATH_OPEN_METHOD = "settings.shulker.open_method";
    private static final String PATH_TITLE = "settings.shulker.title";
    private static final String PATH_TITLE_MODE = "settings.shulker.title.mode";
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
    private static final String PATH_FULL_MESSAGE_COOLDOWN = "settings.auto_collect.full_message_cooldown";
    private static final String PATH_AUTO_COLLECT_IGNORE_DELAY = "settings.auto_collect.rules.ignore_pickup_delay";
    private static final String PATH_IGNORE_PLAYER_DROPPED = "settings.auto_collect.rules.ignore_player_dropped";
    private static final String PATH_RESPECT_NEARBY_PLAYERS = "settings.auto_collect.rules.respect_nearby_players";
    private static final String PATH_AUTO_COLLECT_PERMISSION = "settings.auto_collect.permission.required";
    private static final String PATH_AUTO_COLLECT_FILL_ORDER = "settings.auto_collect.rules.fill_order";
    private static final String PATH_AUTO_COLLECT_GAME_MODES = "settings.auto_collect.rules.gamemodes";
    private static final String PATH_AUTO_COLLECT_PRIORITY = "settings.auto_collect.priority_items";
    private static final String PATH_AUTO_COLLECT_IGNORED = "settings.auto_collect.ignored_items";
    private static final String PATH_LEGACY_AUTO_COLLECT_BLACKLIST = "settings.auto_collect.blacklist";

    private static final String PATH_MESSAGES_ENABLED = "messages.enabled";
    private static final String PATH_MESSAGE_PREFIX = "messages.prefix";
    private static final String PATH_PERMISSIONS_ENABLED = "permissions.enabled";
    private static final String PATH_PERMISSION_OP_BYPASS = "permissions.op_bypass";

    /** Минимальный интервал в тиках (период волны, save_interval). */
    private static final int MIN_INTERVAL_TICKS = 1;
    /** Минимальное количество (игроков/действий на волну, размер очереди). */
    private static final int MIN_COUNT = 1;
    private static final int MIN_COOLDOWN_SECONDS = 0;
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

    /** Режимы, в которых автосбор работает по умолчанию. */
    private static final Set<GameMode> DEFAULT_GAME_MODES =
            Collections.unmodifiableSet(EnumSet.of(GameMode.SURVIVAL, GameMode.ADVENTURE));

    /**
     * Английские дефолты сообщений, окрашенные один раз: fallback
     * {@link #getMessages} не пересобирает строки на каждый вызов.
     */
    private static final Map<MessageKey, List<String>> DEFAULT_MESSAGES;

    static {

        Map<MessageKey, List<String>> defaults = new EnumMap<>(MessageKey.class);

        for (MessageKey key : MessageKey.values()) {
            defaults.put(key, List.of(HexColorUtil.color(key.getDefaultMessage()).split("\n")));
        }

        DEFAULT_MESSAGES = Collections.unmodifiableMap(defaults);

    }

    // ------------------------------------------------------------------
    // Снапшот: все значения конфигурации одним неизменяемым объектом
    // ------------------------------------------------------------------

    private record ShulkerSection(OpenMethodType openMethod,
                                  TitleMode titleMode,
                                  String titleFormat,
                                  Map<String, String> titleNames,
                                  int saveInterval,
                                  boolean blacklistEnabled,
                                  Set<Material> blacklistedMaterials) {
    }

    private record AutoCollectSection(boolean enabled,
                                      boolean permissionRequired,
                                      double maxDistance,
                                      int wavePeriod,
                                      int wavePlayers,
                                      int waveActions,
                                      int waveQueue,
                                      int fullMessageCooldown,
                                      boolean onlyWhenInventoryFull,
                                      boolean mergeIntoExisting,
                                      CollectMode mode,
                                      boolean ignorePickupDelay,
                                      boolean ignorePlayerDropped,
                                      double respectNearbyPlayers,
                                      FillOrderType fillOrder,
                                      List<Material> priorityItems,
                                      Set<Material> blacklist,
                                      Set<GameMode> gameModes) {
    }

    private record MessagingSection(boolean enabled,
                                    String prefix,
                                    Map<MessageKey, List<String>> messages) {
    }

    private record PermissionsSection(boolean enabled,
                                      boolean opBypass,
                                      Map<PermissionNode, String> permissions) {
    }

    private record ConfigSnapshot(boolean pluginEnabled,
                                  ShulkerSection shulker,
                                  AutoCollectSection autoCollect,
                                  MessagingSection messaging,
                                  Map<SoundKey, SoundSettings> sounds,
                                  PermissionsSection permissions) {
    }

    /**
     * Текущий снапшот. Единственное изменяемое поле менеджера; подменяется
     * атомарно, поэтому составные значения никогда не читаются «наполовину».
     */
    private volatile ConfigSnapshot snapshot;

    private FileConfiguration config;

    public ConfigManager(NeyShulker plugin, ConsoleService consoleService) {

        this.plugin = plugin;
        this.consoleService = consoleService;

        plugin.saveDefaultConfig();

        this.config = plugin.getConfig();

        validateReadable();
        this.snapshot = buildSnapshot();

    }

    /**
     * Перезагружает конфигурацию и уведомляет подписанные компоненты.
     */
    public void reload() {

        plugin.reloadConfig();

        this.config = plugin.getConfig();

        validateReadable();

        ConfigSnapshot rebuilt = buildSnapshot();
        this.snapshot = rebuilt;

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

    // ------------------------------------------------------------------
    // Геттеры контракта: делегируют в текущий снапшот
    // ------------------------------------------------------------------

    @Override
    public boolean isPluginEnabled() {
        return snapshot.pluginEnabled();
    }

    @Override
    public OpenMethodType getOpenMethod() {
        return snapshot.shulker().openMethod();
    }

    @Override
    public TitleMode getTitleMode() {
        return snapshot.shulker().titleMode();
    }

    @Override
    public String getTitleFormat() {
        return snapshot.shulker().titleFormat();
    }

    @Override
    public int getSaveInterval() {
        return snapshot.shulker().saveInterval();
    }

    @Override
    public boolean isBlacklistEnabled() {
        return snapshot.shulker().blacklistEnabled();
    }

    @Override
    public Set<Material> getBlacklistedMaterials() {
        return snapshot.shulker().blacklistedMaterials();
    }

    @Override
    public boolean isBlacklisted(Material material) {

        ShulkerSection shulker = snapshot.shulker();
        return shulker.blacklistEnabled() && material != null
                && shulker.blacklistedMaterials().contains(material);

    }

    @Override
    public boolean isAutoCollectEnabled() {
        return snapshot.autoCollect().enabled();
    }

    @Override
    public boolean isAutoCollectPermissionRequired() {
        return snapshot.autoCollect().permissionRequired();
    }

    @Override
    public double getAutoCollectMaxDistance() {
        return snapshot.autoCollect().maxDistance();
    }

    @Override
    public int getWavePeriod() {
        return snapshot.autoCollect().wavePeriod();
    }

    @Override
    public int getPlayersPerWave() {
        return snapshot.autoCollect().wavePlayers();
    }

    @Override
    public int getActionsPerWave() {
        return snapshot.autoCollect().waveActions();
    }

    @Override
    public int getQueuePerPlayer() {
        return snapshot.autoCollect().waveQueue();
    }

    @Override
    public int getFullMessageCooldown() {
        return snapshot.autoCollect().fullMessageCooldown();
    }

    @Override
    public boolean isAutoCollectOnlyWhenInventoryFull() {
        return snapshot.autoCollect().onlyWhenInventoryFull();
    }

    @Override
    public boolean isAutoCollectMergeIntoExisting() {
        return snapshot.autoCollect().mergeIntoExisting();
    }

    @Override
    public CollectMode getAutoCollectMode() {
        return snapshot.autoCollect().mode();
    }

    @Override
    public boolean isAutoCollectIgnorePickupDelay() {
        return snapshot.autoCollect().ignorePickupDelay();
    }

    @Override
    public boolean isAutoCollectIgnorePlayerDropped() {
        return snapshot.autoCollect().ignorePlayerDropped();
    }

    @Override
    public double getAutoCollectRespectNearbyPlayers() {
        return snapshot.autoCollect().respectNearbyPlayers();
    }

    @Override
    public FillOrderType getAutoCollectFillOrder() {
        return snapshot.autoCollect().fillOrder();
    }

    @Override
    public Map<String, String> getTitleNames() {
        return snapshot.shulker().titleNames();
    }

    @Override
    public List<Material> getAutoCollectPriorityItems() {
        return snapshot.autoCollect().priorityItems();
    }

    @Override
    public Set<Material> getAutoCollectBlacklist() {
        return snapshot.autoCollect().blacklist();
    }

    @Override
    public boolean isAutoCollectBlacklisted(Material material) {
        return material != null && snapshot.autoCollect().blacklist().contains(material);
    }

    @Override
    public Set<GameMode> getAutoCollectGameModes() {
        return snapshot.autoCollect().gameModes();
    }

    @Override
    public boolean areMessagesEnabled() {
        return snapshot.messaging().enabled();
    }

    @Override
    public String getMessagePrefix() {
        return snapshot.messaging().prefix();
    }

    @Override
    public List<String> getMessages(MessageKey key) {
        return snapshot.messaging().messages()
                .getOrDefault(key, DEFAULT_MESSAGES.getOrDefault(key, List.of()));
    }

    @Override
    public SoundSettings getOpenSound() {
        return snapshot.sounds().get(SoundKey.OPEN);
    }

    @Override
    public SoundSettings getCloseSound() {
        return snapshot.sounds().get(SoundKey.CLOSE);
    }

    @Override
    public SoundSettings getCollectSound() {
        return snapshot.sounds().get(SoundKey.COLLECT);
    }

    @Override
    public boolean arePermissionsEnabled() {
        return snapshot.permissions().enabled();
    }

    @Override
    public boolean isPermissionOpBypass() {
        return snapshot.permissions().opBypass();
    }

    @Override
    public String getPermission(PermissionNode node) {
        return snapshot.permissions().permissions()
                .getOrDefault(node, node.getDefaultPermission());
    }

    // ------------------------------------------------------------------
    // Сборка снапшота
    // ------------------------------------------------------------------

    /**
     * Явно перечитывает файл, если результат штатной загрузки пуст:
     * Bukkit глотает InvalidConfigurationException и молча отдает пустую
     * конфигурацию. Поврежденный YAML должен кричать в консоль, а не
     * тихо работать на дефолтах.
     */
    private void validateReadable() {

        File configFile = new File(plugin.getDataFolder(), "config.yml");

        if (!configFile.exists() || !config.getKeys(false).isEmpty()) {
            return;
        }

        try {

            new YamlConfiguration().load(configFile);

        } catch (IOException | InvalidConfigurationException exception) {

            plugin.getLogger().log(Level.SEVERE,
                    "config.yml is broken and cannot be parsed - built-in defaults are used. "
                            + "Fix the file and run /shulker reload.",
                    exception);

        }

    }

    private @NotNull ConfigSnapshot buildSnapshot() {

        return new ConfigSnapshot(
                config.getBoolean(PATH_ENABLED, true),
                buildShulkerSection(),
                buildAutoCollectSection(),
                buildMessagingSection(),
                buildSounds(),
                buildPermissionsSection()
        );

    }

    private @NotNull ShulkerSection buildShulkerSection() {

        return new ShulkerSection(
                parseOpenMethod(),
                parseTitleMode(),
                color(readTitleFormat()),
                readTitleNames(),
                intOrWarn(config.getInt(PATH_SAVE_INTERVAL, 10),
                        PATH_SAVE_INTERVAL, MIN_INTERVAL_TICKS),
                readBoolean(PATH_BLOCKED_ITEMS_ENABLED, PATH_LEGACY_BLACKLIST_ENABLED, true),
                readMaterials(PATH_BLOCKED_ITEMS, PATH_LEGACY_BLACKLIST_ITEMS, DEFAULT_BLACKLIST)
        );

    }

    private @NotNull AutoCollectSection buildAutoCollectSection() {

        return new AutoCollectSection(
                config.getBoolean(PATH_AUTO_COLLECT_ENABLED, true),
                config.getBoolean(PATH_AUTO_COLLECT_PERMISSION, false),
                doubleOrWarn(config.getDouble(PATH_AUTO_COLLECT_DISTANCE, 4.5D),
                        PATH_AUTO_COLLECT_DISTANCE, MIN_DISTANCE),
                intOrWarn(config.getInt(PATH_WAVE_PERIOD, 10),
                        PATH_WAVE_PERIOD, MIN_INTERVAL_TICKS),
                intOrWarn(config.getInt(PATH_WAVE_PLAYERS, 5),
                        PATH_WAVE_PLAYERS, MIN_COUNT),
                intOrWarn(config.getInt(PATH_WAVE_ACTIONS, 16),
                        PATH_WAVE_ACTIONS, MIN_COUNT),
                intOrWarn(config.getInt(PATH_WAVE_QUEUE, 32),
                        PATH_WAVE_QUEUE, MIN_COUNT),
                intOrWarn(config.getInt(PATH_FULL_MESSAGE_COOLDOWN, 30),
                        PATH_FULL_MESSAGE_COOLDOWN, MIN_COOLDOWN_SECONDS),
                config.getBoolean(PATH_AUTO_COLLECT_ONLY_FULL, false),
                config.getBoolean(PATH_AUTO_COLLECT_MERGE, true),
                parseCollectMode(),
                config.getBoolean(PATH_AUTO_COLLECT_IGNORE_DELAY, false),
                config.getBoolean(PATH_IGNORE_PLAYER_DROPPED, true),
                doubleOrWarn(config.getDouble(PATH_RESPECT_NEARBY_PLAYERS, 4.5D),
                        PATH_RESPECT_NEARBY_PLAYERS, MIN_DISTANCE),
                parseFillOrder(),
                readMaterialList(PATH_AUTO_COLLECT_PRIORITY, DEFAULT_PRIORITY_ITEMS),
                readMaterials(PATH_AUTO_COLLECT_IGNORED,
                        PATH_LEGACY_AUTO_COLLECT_BLACKLIST, DEFAULT_AUTO_COLLECT_BLACKLIST),
                readGameModes(PATH_AUTO_COLLECT_GAME_MODES, DEFAULT_GAME_MODES)
        );

    }

    private @NotNull MessagingSection buildMessagingSection() {

        Map<MessageKey, List<String>> messages = new EnumMap<>(MessageKey.class);

        for (MessageKey key : MessageKey.values()) {

            String path = "messages." + key.getConfigKey();

            if (!config.getBoolean(path + ".enabled", key.isDefaultEnabled())) {
                messages.put(key, List.of());
                continue;
            }

            messages.put(key, dropLegacyQueueLine(key,
                    readColoredLines(path + ".text", key.getDefaultMessage())));

        }

        return new MessagingSection(
                config.getBoolean(PATH_MESSAGES_ENABLED, true),
                color(config.getString(PATH_MESSAGE_PREFIX, "")),
                Collections.unmodifiableMap(messages)
        );

    }

    private @NotNull Map<SoundKey, SoundSettings> buildSounds() {

        Map<SoundKey, SoundSettings> sounds = new EnumMap<>(SoundKey.class);

        for (SoundKey key : SoundKey.values()) {
            sounds.put(key, loadSound(key));
        }

        return Collections.unmodifiableMap(sounds);

    }

    private @NotNull PermissionsSection buildPermissionsSection() {

        Map<PermissionNode, String> permissions = new EnumMap<>(PermissionNode.class);

        for (PermissionNode node : PermissionNode.values()) {
            permissions.put(node, config.getString("permissions." + node.getConfigKey(),
                    node.getDefaultPermission()));
        }

        return new PermissionsSection(
                config.getBoolean(PATH_PERMISSIONS_ENABLED, false),
                config.getBoolean(PATH_PERMISSION_OP_BYPASS, false),
                Collections.unmodifiableMap(permissions)
        );

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
     * Читает режимы игры, в которых автосбор активен.
     * Пустой или отсутствующий список дает дефолт (SURVIVAL, ADVENTURE):
     * случайная пустая секция не должна молча выключать сбор у всех.
     *
     * @return неизменяемое множество режимов
     */
    private @NotNull Set<GameMode> readGameModes(String path, Set<GameMode> defaults) {

        List<String> names = config.getStringList(path);

        if (names.isEmpty()) {
            return defaults;
        }

        Set<GameMode> modes = EnumSet.noneOf(GameMode.class);

        for (String name : names) {

            GameMode mode = readGameMode(name, path);

            if (mode != null) {
                modes.add(mode);
            }

        }

        if (modes.isEmpty()) {

            consoleService.log(ConsoleMessage.INVALID_VALUE,
                    "path", path,
                    "value", String.join(", ", names),
                    "defaultValue", "SURVIVAL, ADVENTURE");
            return defaults;

        }

        return Collections.unmodifiableSet(modes);

    }

    private @Nullable GameMode readGameMode(@Nullable String name, @NotNull String path) {

        if (name == null || name.isBlank()) {
            return null;
        }

        String normalized = name.trim().toUpperCase(Locale.ROOT);

        for (GameMode mode : GameMode.values()) {
            if (mode.name().equals(normalized)) {
                return mode;
            }
        }

        consoleService.log(ConsoleMessage.UNKNOWN_GAME_MODE,
                "path", path,
                "value", name);
        return null;

    }

    /**
     * Читает имена безымянного шалкер-бокса по языкам клиента.
     * Ключи приводятся к нижнему регистру, значения получают цвета сразу.
     *
     * @return карту locale -> имя (пустую, если секции нет)
     */
    private @NotNull Map<String, String> readTitleNames() {

        Object raw = config.get(PATH_TITLE_NAMES);

        if (!(raw instanceof ConfigurationSection section)) {
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
     * @param path         актуальный путь
     * @param legacyPath   устаревший путь
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

        String configValue = config.get(PATH_TITLE) instanceof ConfigurationSection section
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

        if (raw instanceof ConfigurationSection section) {
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

    /**
     * Убирает наследие 2.13.0: строку очереди переноса из info_idle.
     *
     * Очередь листа ожидания - техническая деталь волн, игроку она не нужна,
     * поэтому плейсхолдер {queue} больше не подставляется. Старые конфигурации
     * продолжают загружаться: строка с плейсхолдером молча исключается из
     * сообщения, а в консоль уходит подсказка о правке файла.
     *
     * @param key   ключ сообщения
     * @param lines прочитанные строки сообщения
     * @return строки без устаревшего плейсхолдера очереди
     */
    private @NotNull List<String> dropLegacyQueueLine(@NotNull MessageKey key,
                                                      @NotNull List<String> lines) {

        if (key != MessageKey.INFO_IDLE) {
            return lines;
        }

        List<String> kept = lines.stream()
                .filter(line -> !line.contains("{queue}"))
                .toList();

        if (kept.size() == lines.size()) {
            return lines;
        }

        consoleService.log(ConsoleMessage.LEGACY_PATH,
                "path", "messages.info_idle.text",
                "replacement", "the same lines without the {queue} placeholder");
        return kept;

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

        return Collections.unmodifiableSet(materials);

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
