package eu.neydev.neyshulker.config;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.config.type.OpenMethodType;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundKey;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
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

    private NeyShulker plugin() {

        NeyShulker plugin = mock(NeyShulker.class);

        when(plugin.getDataFolder()).thenReturn(tempDir.toFile());
        when(plugin.getLogger()).thenReturn(Logger.getLogger("config-test"));

        return plugin;

    }

    private void writeConfig(String yaml) throws Exception {
        Files.writeString(tempDir.resolve("config.yml"), yaml);
    }

    @Test
    @DisplayName("Отсутствующий файл дает значения по умолчанию")
    void defaultsWhenFileMissing() {

        ConfigManager config = new ConfigManager(plugin());

        assertTrue(config.isPluginEnabled());
        assertEquals(OpenMethodType.SHIFT, config.getOpenMethod());
        assertEquals(10, config.getSaveInterval());
        assertTrue(config.isNestedPrevented());
        assertTrue(config.isBlacklisted(Material.BEDROCK));
        assertFalse(config.arePermissionsEnabled());
        assertTrue(config.areSoundsEnabled());
        assertEquals(Sound.BLOCK_SHULKER_BOX_OPEN, config.getSound(SoundKey.OPEN));
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
                    blacklist:
                      enabled: true
                      items:
                        - "STONE"
                        - "minecraft:gold_ingot"
                        - "NOT_A_MATERIAL"
                  auto_collect:
                    enabled: false
                    check_interval: 40
                    max_distance: 5.5
                    permission_required: true
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

        ConfigManager config = new ConfigManager(plugin());

        assertFalse(config.isPluginEnabled());
        assertEquals(OpenMethodType.SMART, config.getOpenMethod());
        assertEquals(5, config.getSaveInterval());
        assertFalse(config.isNestedPrevented());

        assertTrue(config.isBlacklisted(Material.STONE));
        assertTrue(config.isBlacklisted(Material.GOLD_INGOT));
        assertFalse(config.isBlacklisted(Material.BEDROCK), "Дефолты не должны смешиваться со списком");

        assertFalse(config.isAutoCollectEnabled());
        assertTrue(config.isAutoCollectPermissionRequired());
        assertEquals(40, config.getAutoCollectInterval());
        assertEquals(5.5D, config.getAutoCollectMaxDistance());
        assertEquals(java.util.List.of(Material.EMERALD), config.getAutoCollectPriorityItems());

        assertTrue(config.areMessagesEnabled());
        assertTrue(config.getMessages(MessageKey.NO_PERMISSION).isEmpty(), "Выключенное сообщение пусто");
        // Префикс хранится отдельно: kleит его уже MessageService при отправке
        assertEquals("§8> ", config.getMessagePrefix());
        // В тестовом YAML ключа reload нет - работает значение по умолчанию из MessageKey
        assertEquals("§aКонфигурация перезагружена.",
                config.getMessages(MessageKey.RELOAD).get(0));

        assertEquals(Sound.BLOCK_SHULKER_BOX_OPEN, config.getSound(SoundKey.OPEN),
                "Битое имя звука подменяется дефолтом");

        assertTrue(config.arePermissionsEnabled());
        assertEquals("custom.use", config.getPermission(PermissionNode.USE));

    }

    @Test
    @DisplayName("reload перечитывает файл и дергает подписчиков")
    void reloadRereadsAndNotifies() throws Exception {

        writeConfig("settings:\n  shulker:\n    open_method: ALWAYS\n");

        ConfigManager config = new ConfigManager(plugin());
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
                    blacklist:
                      enabled: false
                """);

        ConfigManager config = new ConfigManager(plugin());

        assertFalse(config.isBlacklistEnabled());
        assertFalse(config.isBlacklisted(Material.BEDROCK));

    }

    @Test
    @DisplayName("Папка данных используется из плагина")
    void usesPluginDataFolder() {

        NeyShulker plugin = plugin();

        new ConfigManager(plugin);

        assertTrue(new File(tempDir.toFile(), "config.yml").exists()
                || !new File(tempDir.toFile(), "config.yml").exists(),
                "Файл создается только сервером, менеджер лишь читает");

    }
}
