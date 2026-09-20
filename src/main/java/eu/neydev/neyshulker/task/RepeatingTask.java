package eu.neydev.neyshulker.task;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Обертка над повторяющейся задачей Bukkit.
 * Управляет жизненным циклом: старт, остановка, безопасный рестарт после reload.
 */
public class RepeatingTask {

    private final Plugin plugin;
    private final String name;
    private final Runnable action;

    private BukkitTask task;

    public RepeatingTask(@NotNull Plugin plugin, @NotNull String name, @NotNull Runnable action) {

        this.plugin = plugin;
        this.name = name;
        this.action = action;

    }

    /**
     * Запускает задачу.
     *
     * @param delay  задержка в тиках
     * @param period период в тиках
     */
    public void start(long delay, long period) {

        stop();

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> start(delay, period));
            return;
        }

        task = Bukkit.getScheduler().runTaskTimer(plugin, action, Math.max(1L, delay), Math.max(1L, period));

    }

    /**
     * Останавливает задачу, если она запущена.
     */
    public void stop() {

        if (task != null) {
            task.cancel();
            task = null;
        }

    }

    public boolean isRunning() {
        return task != null;
    }

    public @NotNull String getName() {
        return name;
    }
}
