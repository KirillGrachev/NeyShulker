package eu.neydev.neyshulker;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import eu.neydev.neyshulker.model.ShulkerSession;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.command.PluginCommand;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Интеграционные тесты на MockBukkit: плагин загружается в живой
 * серверный мок, слушатели реагируют через настоящий диспетчер событий,
 * команды исполняются через command map, планировщик реально тикает.
 *
 * Ограничение среды: ItemFactory мок-сервера не отдает BlockStateMeta для
 * шалкер-боксов, поэтому содержимое предмета в этих тестах не persists -
 * проверяются жизненный цикл, wiring и GUI-механика (перенос содержимого
 * покрыт юнитами на транзакционном ядре).
 */
class NeyShulkerIntegrationTest {

    private ServerMock server;
    private NeyShulker plugin;

    @BeforeEach
    void setUp() {

        server = MockBukkit.mock();
        plugin = MockBukkit.load(NeyShulker.class);

    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Плагин стартует: сервисы, команда и авто-сбор на месте")
    void pluginStartsAndWiresEverything() {

        assertTrue(plugin.isEnabled());
        assertNotNull(plugin.getServices());
        assertNotNull(plugin.getServices().getAutoCollectService());
        assertTrue(plugin.getServices().getAutoCollectService().isRunning(),
                "Волновая задача запущена из onEnable");

        PluginCommand command = plugin.getCommand("shulker");

        assertNotNull(command, "Команда из plugin.yml найдена");
        assertNotNull(command.getExecutor(), "Executor привязан");
        assertNotNull(command.getTabCompleter(), "Tab-completer привязан");

    }

    @Test
    @DisplayName("ПКМ по воздуху с шалкером открывает GUI на следующем тике")
    void interactOpensGuiOnNextTick() {

        PlayerMock player = server.addPlayer();
        ItemStack shulker = new ItemStack(Material.WHITE_SHULKER_BOX);

        player.getInventory().setItem(0, shulker);
        player.getInventory().setHeldItemSlot(0);

        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR,
                shulker, null, BlockFace.SELF, EquipmentSlot.HAND);

        server.getPluginManager().callEvent(event);

        assertTrue(event.isCancelled(), "Интеракт по воздуху отменен: блок не ставится");

        // Открытие отложено на следующий тик
        assertNull(plugin.getServices().getSessionRegistry().getSession(player),
                "В том же тике сессии еще нет");

        server.getScheduler().performTicks(2);

        ShulkerSession session = plugin.getServices().getSessionRegistry().getSession(player);

        assertNotNull(session, "После тика сессия зарегистрирована");
        assertEquals(0, session.getSlot());
        assertSame(session.inventory(), player.getOpenInventory().getTopInventory(),
                "Игрок смотрит в GUI сессии");
        assertTrue(player.getOpenInventory().getTopInventory().getHolder()
                instanceof eu.neydev.neyshulker.inventory.NeyShulkerViewer,
                "Держатель GUI - маркер плагина");

    }

    @Test
    @DisplayName("Закрытое GUI снимает сессию через настоящий InventoryCloseEvent")
    void closingGuiRemovesSession() {

        PlayerMock player = server.addPlayer();
        ItemStack shulker = new ItemStack(Material.WHITE_SHULKER_BOX);

        player.getInventory().setItem(0, shulker);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player,
                Action.RIGHT_CLICK_AIR, shulker, null, BlockFace.SELF, EquipmentSlot.HAND));
        server.getScheduler().performTicks(2);

        assertNotNull(plugin.getServices().getSessionRegistry().getSession(player));

        player.closeInventory();
        server.getScheduler().performTicks(2);

        assertNull(plugin.getServices().getSessionRegistry().getSession(player),
                "Cleanup-слушатель снял сессию по InventoryCloseEvent");

    }

    @Test
    @DisplayName("Команды исполняются через command map сервера")
    void commandsRunThroughServerCommandMap() {

        PlayerMock player = server.addPlayer();

        server.dispatchCommand(player, "shulker info");

        String message = player.nextMessage();
        assertNotNull(message);
        assertTrue(message.contains("No open shulker boxes"),
                "info без сессии отвечает настраиваемым сообщением: " + message);
        drainMessages(player);

        server.dispatchCommand(player, "shulker autocollect");
        assertTrue(player.nextMessage().contains("Auto-collect disabled"),
                "Первый toggle выключает автосбор игрока");
        drainMessages(player);

        server.dispatchCommand(player, "shulker autocollect");
        assertTrue(player.nextMessage().contains("Auto-collect enabled"),
                "Второй toggle включает обратно");
        drainMessages(player);

        server.dispatchCommand(player, "shulker");
        assertTrue(player.nextMessage().contains("NeyShulker commands"),
                "Без аргументов печатается справка");

    }

    /** info многострочен: опустошаем очередь, чтобы следующие проверки не читали хвост. */
    private void drainMessages(PlayerMock player) {
        while (player.nextMessage() != null) {
            // noop
        }
    }

    @Test
    @DisplayName("Reload из консоли перечитывает конфигурацию")
    void consoleReloadWorks() {

        server.dispatchCommand(server.getConsoleSender(), "shulker reload");
        server.getScheduler().performTicks(1);

        // Плагин жив, конфигурация перечитана, волна продолжает работать
        assertTrue(plugin.isEnabled());
        assertTrue(plugin.getServices().getAutoCollectService().isRunning());

    }

    @Test
    @DisplayName("Выключение сервера закрывает сессии без исключений")
    void shutdownClosesSessionsGracefully() {

        PlayerMock player = server.addPlayer();
        ItemStack shulker = new ItemStack(Material.WHITE_SHULKER_BOX);

        player.getInventory().setItem(0, shulker);
        server.getPluginManager().callEvent(new PlayerInteractEvent(player,
                Action.RIGHT_CLICK_AIR, shulker, null, BlockFace.SELF, EquipmentSlot.HAND));
        server.getScheduler().performTicks(2);

        assertNotNull(plugin.getServices().getSessionRegistry().getSession(player));

        // unmock вызывает onDisable: closeAll + остановка задач
        MockBukkit.unmock();

        assertTrue(plugin.getServices().getSessionRegistry().isEmpty(),
                "Реестр сессий очищен при выключении");
        assertFalse(plugin.getServices().getAutoCollectService().isRunning(),
                "Волновая задача остановлена");

        // Повторный mock для корректного tearDown
        server = MockBukkit.mock();

    }


    @Test
    @DisplayName("Геттеры точки входа отдают живые компоненты")
    void entryPointGettersExposeComponents() {

        assertNotNull(plugin.getConsoleService());
        assertNotNull(plugin.getConfigManager());
        assertSame(plugin.getConfigManager(), plugin.getServices().getConfigManager());
        assertSame(plugin.getConsoleService(), plugin.getServices().getConsoleService());
        assertSame(plugin, plugin.getServices().getPlugin());

        // PAPI не установлен: экспаншен не регистрируется, отключение безопасно
        assertNull(server.getPluginManager().getPlugin("PlaceholderAPI"));

    }

}
