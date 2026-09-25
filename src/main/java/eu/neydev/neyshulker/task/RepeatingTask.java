package eu.neydev.neyshulker.task;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * Обертка над повторяющейся задачей Bukkit.
 * Управляет жизненным циклом: старт, остановка, безопасный рестарт после reload.
 *
 * Поток: start/stop допускаются из любого потока (поле задачи volatile,
 * отмена BukkitTask потокобезопасна), но сама задача планировщика всегда
 * выполняется в главном потоке.
 */
public class RepeatingTask {

    private final Plugin plugin;
    private final Runnable action;

    private volatile BukkitTask task;

    public RepeatingTask(@NotNull Plugin plugin, @NotNull Runnable action) {

        this.plugin = plugin;
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

        task = Bukkit.getScheduler().runTaskTimer(plugin, action,
                Math.max(1L, delay), Math.max(1L, period));

    }

    /**
     * Останавливает задачу, если она запущена.
     */
    public void stop() {

        BukkitTask current = task;
        task = null;

        if (current != null) {
            current.cancel();
        }

    }

    /**
     * Запущена ли задача прямо сейчас: после отмены планировщиком
     * (например, при выключении сервера) флаг честно сбрасывается.
     */
    public boolean isRunning() {

        BukkitTask current = task;
        return current != null && !current.isCancelled();

    }

}
