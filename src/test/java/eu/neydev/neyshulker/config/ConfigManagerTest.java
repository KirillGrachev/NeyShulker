package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.service.ConsoleService;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.FillOrderType;
import eu.neydev.neyshulker.config.type.TitleMode;
import eu.neydev.neyshulker.config.type.PermissionNode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Проверка менеджера конфигурации на настоящем YAML-файле:
 * значения по умолчанию, парсинг, предупреждения и перезагрузка.
 */
class ConfigManagerTest {

    @TempDir
    Path tempDir;

    private final List<LogRecord> consoleRecords = new ArrayList<>();

    private NeyShulker plugin() {

        NeyShulker plugin = mock(NeyShulker.class);

        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(countingLogger());
        when(plugin.getConfig()).thenReturn(new YamlConfiguration());

        return plugin;

    }

    private Logger countingLogger() {

        Logger logger = Logger.getLogger("config-test-" + System.nanoTime());

        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {

            @Override
            public void publish(LogRecord record) {
                consoleRecords.add(record);
            }

            @Override
            public void flush() {

            }

            @Override
            public void close() {

            }
        });

        return logger;

    }

    private ConfigManager configManager(NeyShulker plugin) {
        return new ConfigManager(plugin, new ConsoleService(plugin));
    }

    private void writeConfig(String yaml) throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), yaml);
    }

    @Test
    @DisplayName("Отсутствующий файл дает значения по умолчанию")
    void defaultsWhenFileMissing() {

        ConfigManager config = configManager(plugin());

        assertTrue(config.isPluginEnabled());
        assertEquals(OpenMethodType.AIR, config.getOpenMethod());
        assertEquals(10, config.getSaveInterval());
        assertTrue(config.isBlacklisted(Material.BEDROCK));
        assertFalse(config.arePermissionsEnabled());
        assertTrue(config.getOpenSound().enabled());
        assertEquals(Sound.BLOCK_SHULKER_BOX_OPEN, config.getOpenSound().sound());
        assertFalse(config.getMessages(MessageKey.RELOAD).isEmpty());
        assertTrue(config.getAutoCollectPriorityItems().contains(Material.DIAMOND));
        assertEquals("neyshulker.autocollect", config.getPermission(PermissionNode.AUTO_COLLECT));

    }

    @Test
    @DisplayName("Значения из файла читаются и кэшируются")
    void readsValuesFromFile() throws Exception {

        writeConfig("""
                settings:
                  enabled: false
                  shulker:
                    open_method: SMART
                    save_interval: 5
                    prevent_nested: false
                    blocked_items:
                      enabled: true
                      items:
                        - "STONE"
                        - "minecraft:gold_ingot"
                        - "NOT_A_MATERIAL"
                  auto_collect:
                    enabled: false
                    scan:
                      distance: 5.5
                    waves:
                      period: 40
                      players_per_wave: 3
                      actions_per_wave: 7
                      queue_per_player: 9
                    full_message_cooldown: 12
                    permission:
                      required: true
                    priority_items:
                      - "EMERALD"
                      - "BROKEN_NAME"
                messages:
                  prefix: "&8> "
                  no_permission:
                    enabled: false
                sounds:
                  open:
                    sound: NOT_A_SOUND
                permissions:
                  enabled: true
                  use: "custom.use"
                """);

        ConfigManager config = configManager(plugin());

        assertFalse(config.isPluginEnabled());
        assertEquals(OpenMethodType.SMART, config.getOpenMethod());
        assertEquals(5, config.getSaveInterval());

        assertTrue(config.isBlacklisted(Material.STONE));
        assertTrue(config.isBlacklisted(Material.GOLD_INGOT));
        assertFalse(config.isBlacklisted(Material.BEDROCK), "Дефолты не должны смешиваться со списком");

        assertFalse(config.isAutoCollectEnabled());
        assertTrue(config.isAutoCollectPermissionRequired());
        assertEquals(40, config.getWavePeriod());
        assertEquals(3, config.getPlayersPerWave());
        assertEquals(7, config.getActionsPerWave());
        assertEquals(9, config.getQueuePerPlayer());
        assertEquals(12, config.getFullMessageCooldown());
        assertEquals(5.5D, config.getAutoCollectMaxDistance());
        assertEquals(java.util.List.of(Material.EMERALD), config.getAutoCollectPriorityItems());

        assertTrue(config.areMessagesEnabled());
        assertTrue(config.getMessages(MessageKey.NO_PERMISSION).isEmpty(), "Выключенное сообщение пусто");
        // Префикс хранится отдельно: подставляет его уже MessageService при отправке
        assertEquals("§8> ", config.getMessagePrefix());
        // В тестовом YAML ключа reload нет - работает значение по умолчанию из MessageKey
        // ConfigManager отдает шаблон как есть: {prefix} подставит MessageService
        assertTrue(config.getMessages(MessageKey.RELOAD).get(0)
                .contains("Configuration reloaded."), "Дефолты сообщений на английском");
        assertTrue(config.getMessages(MessageKey.AUTO_COLLECT).isEmpty(),
                "Служебное сообщение автосбора выключено по умолчанию");

        assertEquals(Sound.BLOCK_SHULKER_BOX_OPEN, config.getOpenSound().sound(),
                "Битое имя звука подменяется дефолтом");

        assertTrue(config.arePermissionsEnabled());
        assertEquals("custom.use", config.getPermission(PermissionNode.USE));

    }

    @Test
    @DisplayName("legacy-строка info_idle с {queue} исключается из сообщения")
    void legacyQueueLineDroppedFromInfoIdle() throws Exception {

        writeConfig("""
                messages:
                  info_idle:
                    enabled: true
                    text:
                      - "{prefix}&7No open shulker boxes."
                      - "&7Auto-collect: {state}"
                      - "&7Transfer queue: &f{queue}"
                """);

        ConfigManager config = configManager(plugin());

        List<String> lines = config.getMessages(MessageKey.INFO_IDLE);

        assertEquals(2, lines.size(), "Очередь - техническая деталь волн, в чате ее больше нет");
        assertTrue(lines.stream().noneMatch(line -> line.contains("{queue}")));

    }

    @Test
    @DisplayName("reload перечитывает файл и дергает подписчиков")
    void reloadRereadsAndNotifies() throws Exception {

        writeConfig("settings:\n  shulker:\n    open_method: ALWAYS\n");

        ConfigManager config = configManager(plugin());
        AtomicInteger calls = new AtomicInteger();

        config.onReload(calls::incrementAndGet);

        assertEquals(OpenMethodType.ALWAYS, config.getOpenMethod());

        writeConfig("settings:\n  shulker:\n    open_method: NO_SHIFT\n");
        config.reload();

        assertEquals(OpenMethodType.NO_SHIFT, config.getOpenMethod());
        assertEquals(1, calls.get());

    }

    @Test
    @DisplayName("Черный список выключается целиком")
    void blacklistCanBeDisabled() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    blocked_items:
                      enabled: false
                """);

        ConfigManager config = configManager(plugin());

        assertFalse(config.isBlacklistEnabled());
        assertFalse(config.isBlacklisted(Material.BEDROCK));

    }

    @Test
    @DisplayName("Title: секция с режимом ORIGINAL")
    void titleSectionOriginalMode() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    title:
                      mode: ORIGINAL
                      format: "unused {shulker_name}"
                """);

        ConfigManager config = configManager(plugin());

        assertEquals(TitleMode.ORIGINAL, config.getTitleMode());
        assertEquals("unused {shulker_name}", config.getTitleFormat());

    }

    @Test
    @DisplayName("Title: legacy-строка равносильна CUSTOM")
    void titleLegacyStringIsCustom() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    title: " &#ff00ff{shulker_name} "
                """);

        ConfigManager config = configManager(plugin());

        assertEquals(TitleMode.CUSTOM, config.getTitleMode());
        assertEquals(" §x§f§f§0§0§f§f{shulker_name} ", config.getTitleFormat());

    }

    @Test
    @DisplayName("Title: битый режим дает CUSTOM и предупреждение")
    void titleInvalidModeFallsBack() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    title:
                      mode: RAINBOW
                """);

        ConfigManager config = configManager(plugin());

        assertEquals(TitleMode.CUSTOM, config.getTitleMode());
        assertEquals(1, consoleRecords.size());

    }

    @Test
    @DisplayName("Битые значения дают дефолты и шаблоные предупреждения")
    void invalidValuesFallBackWithConsoleWarnings() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    open_method: TELEPORT
                    save_interval: 0
                    blocked_items:
                      items:
                        - "NOT_A_MATERIAL"
                  auto_collect:
                    scan:
                      distance: -5.0
                sounds:
                  open:
                    sound: NOT_A_SOUND
                """);

        ConfigManager config = configManager(plugin());

        assertEquals(OpenMethodType.AIR, config.getOpenMethod());
        assertEquals(1, config.getSaveInterval());
        assertEquals(0.0D, config.getAutoCollectMaxDistance());
        assertEquals(Sound.BLOCK_SHULKER_BOX_OPEN, config.getOpenSound().sound());
        assertTrue(config.getBlacklistedMaterials().isEmpty());

        assertEquals(5, consoleRecords.size(),
                "Каждое битое значение дает ровно одно предупреждение");

    }

    @Test
    @DisplayName("Legacy-пути черных списков продолжают работать с предупреждением")
    void legacyBlacklistPathsStillRead() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    blacklist:
                      enabled: true
                      items:
                        - "STONE"
                  auto_collect:
                    blacklist:
                      - "BARRIER"
                """);

        ConfigManager config = configManager(plugin());

        assertTrue(config.isBlacklisted(Material.STONE));
        assertTrue(config.isAutoCollectBlacklisted(Material.BARRIER));
        assertEquals(3, consoleRecords.size(),
                "По предупреждению на каждый прочитанный legacy-путь");

    }

    @Test
    @DisplayName("fill_order парсится, битое значение падает в BALANCED")
    void fillOrderParsing() throws Exception {

        writeConfig("""
                settings:
                  auto_collect:
                    rules:
                      fill_order: COMPACT
                """);

        assertEquals(FillOrderType.COMPACT, configManager(plugin()).getAutoCollectFillOrder());

        writeConfig("""
                settings:
                  auto_collect:
                    rules:
                      fill_order: RANDOM
                """);

        assertEquals(FillOrderType.BALANCED, configManager(plugin()).getAutoCollectFillOrder());
        assertEquals(1, consoleRecords.size());

        assertEquals(FillOrderType.BALANCED, configManager(plugin()).getAutoCollectFillOrder(),
                "Без файла конфигурации действует стратегия по умолчанию");

    }

    @Test
    @DisplayName("Имена заголовка читаются по ключам языков")
    void titleNamesReadPerLocale() throws Exception {

        writeConfig("""
                settings:
                  shulker:
                    title:
                      names:
                        default: "Shulker Box"
                        RU_RU: "Шалкеровый ящик"
                """);

        ConfigManager config = configManager(plugin());

        assertEquals("Shulker Box", config.getTitleNames().get("default"));
        assertEquals("Шалкеровый ящик", config.getTitleNames().get("ru_ru"),
                "Ключи языков приводятся к нижнему регистру");

    }
}
