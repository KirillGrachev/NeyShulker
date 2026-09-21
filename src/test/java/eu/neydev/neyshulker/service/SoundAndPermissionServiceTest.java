package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.PermissionNode;
import eu.neydev.neyshulker.config.type.SoundSettings;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Проверка сервисов звуков и прав.
 */
class SoundAndPermissionServiceTest {

    private final ConfigManager configManager = mock(ConfigManager.class);

    @Test
    @DisplayName("Звук играется с параметрами из конфигурации")
    void playsSoundWithConfiguredParameters() {

        Player player = mock(Player.class);
        Location location = mock(Location.class);

        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(location);
        when(configManager.getCollectSound()).thenReturn(
                new SoundSettings(Sound.ENTITY_ITEM_PICKUP, true, 0.5f, 1.2f));

        new SoundService(configManager).playCollect(player);

        verify(player).playSound(eq(location), eq(Sound.ENTITY_ITEM_PICKUP), eq(0.5f), eq(1.2f));

    }

    @Test
    @DisplayName("Выключенные звуки и оффлайн не играют")
    void silencedSoundsDoNotPlay() {

        Player player = mock(Player.class);

        when(player.isOnline()).thenReturn(true);
        when(configManager.getOpenSound()).thenReturn(
                new SoundSettings(Sound.BLOCK_SHULKER_BOX_OPEN, false, 1.0f, 1.0f));

        new SoundService(configManager).playOpen(player);
        new SoundService(configManager).playClose(null);

        verify(player, never()).playSound(org.mockito.ArgumentMatchers.any(Location.class),
                org.mockito.ArgumentMatchers.any(Sound.class),
                org.mockito.ArgumentMatchers.anyFloat(),
                org.mockito.ArgumentMatchers.anyFloat());

    }

    @Test
    @DisplayName("Выключенная система прав разрешает все")
    void disabledPermissionsAllowEverything() {

        when(configManager.arePermissionsEnabled()).thenReturn(false);

        PermissionService permissionService = new PermissionService(configManager);

        org.junit.jupiter.api.Assertions.assertTrue(permissionService.has((Player) null, PermissionNode.USE));
        org.junit.jupiter.api.Assertions.assertFalse(permissionService.canBypassBlacklist(null),
                "Выключенная система прав не снимает ограничения блэклиста");

    }

    @Test
    @DisplayName("Включенная система прав спрашивает игрока и консоль")
    void enabledPermissionsAskSender() {

        Player player = mock(Player.class);
        CommandSender console = mock(CommandSender.class);

        when(configManager.arePermissionsEnabled()).thenReturn(true);
        when(configManager.getPermission(PermissionNode.AUTO_COLLECT)).thenReturn("neyshulker.autocollect");
        when(configManager.getPermission(PermissionNode.BYPASS_BLACKLIST)).thenReturn("neyshulker.bypass.blacklist");
        when(player.hasPermission("neyshulker.autocollect")).thenReturn(true);
        when(player.hasPermission("neyshulker.bypass.blacklist")).thenReturn(false);
        when(console.hasPermission("neyshulker.autocollect")).thenReturn(true);

        PermissionService permissionService = new PermissionService(configManager);

        org.junit.jupiter.api.Assertions.assertTrue(permissionService.has(player, PermissionNode.AUTO_COLLECT));
        org.junit.jupiter.api.Assertions.assertFalse(permissionService.canBypassBlacklist(player),
                "Нет права обхода - блэклист работает");

        when(player.hasPermission("neyshulker.bypass.blacklist")).thenReturn(true);
        org.junit.jupiter.api.Assertions.assertTrue(permissionService.canBypassBlacklist(player),
                "Есть право обхода - блэклист снят");
        org.junit.jupiter.api.Assertions.assertTrue(permissionService.has(console, PermissionNode.AUTO_COLLECT));
        org.junit.jupiter.api.Assertions.assertFalse(permissionService.has((CommandSender) null, PermissionNode.AUTO_COLLECT));

    }
}
