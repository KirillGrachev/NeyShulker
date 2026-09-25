package eu.neydev.neyshulker.task;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

/**
 * Проверка обертки повторяющейся задачи: старт, остановка, рестарт.
 */
class RepeatingTaskTest {

    @Test
    @DisplayName("start регистрирует задачу, stop отменяет")
    void startAndStop() {

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask task = mock(BukkitTask.class);
        Plugin plugin = mock(Plugin.class);

        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            RepeatingTask repeating = new RepeatingTask(plugin, () -> {
            });

            assertFalse(repeating.isRunning());
            repeating.start(20L, 20L);

            assertTrue(repeating.isRunning());
            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(20L), eq(20L));

            repeating.stop();

            assertFalse(repeating.isRunning());
            verify(task).cancel();

            // Повторная остановка безопасна
            repeating.stop();

        }

    }

    @Test
    @DisplayName("Период и задержка не падают ниже одного тика")
    void periodClampedToOneTick() {

        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        Plugin plugin = mock(Plugin.class);

        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(mock(BukkitTask.class));

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            new RepeatingTask(plugin, () -> {
            }).start(0L, -5L);

            verify(scheduler).runTaskTimer(eq(plugin), any(Runnable.class), eq(1L), eq(1L));

        }

    }


    @Test
    @DisplayName("Старт не из главного потока уезжает задачей в главный")
    void asyncStartReschedulesToMainThread() {

        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        Plugin plugin = mock(Plugin.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(false);

            RepeatingTask repeating = new RepeatingTask(plugin, () -> {
            });
            repeating.start(5L, 5L);

            // Прямой регистрации таймера нет - вместо нее задача на главный поток
            verify(scheduler, org.mockito.Mockito.never())
                    .runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong());
            verify(scheduler).runTask(eq(plugin), any(Runnable.class));
            assertFalse(repeating.isRunning());

        }

    }

    @Test
    @DisplayName("Внешняя отмена задачи честно отражается в isRunning")
    void externalCancelIsVisible() {

        org.bukkit.scheduler.BukkitScheduler scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        org.bukkit.scheduler.BukkitTask task = mock(org.bukkit.scheduler.BukkitTask.class);
        Plugin plugin = mock(Plugin.class);

        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong()))
                .thenReturn(task);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {

            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);

            RepeatingTask repeating = new RepeatingTask(plugin, () -> {
            });
            repeating.start(1L, 1L);
            assertTrue(repeating.isRunning());

            // Планировщик отменил задачу сам (например, выключение сервера)
            when(task.isCancelled()).thenReturn(true);
            assertFalse(repeating.isRunning());

        }

    }

}
