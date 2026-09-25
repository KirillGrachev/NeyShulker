package eu.neydev.neyshulker.event;

import eu.neydev.neyshulker.NeyShulker;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

/**
 * Единая точка регистрации слушателей плагина.
 */
public class EventDispatcher {

    private final NeyShulker plugin;

    public EventDispatcher(NeyShulker plugin) {
        this.plugin = plugin;
    }

    /**
     * Регистрирует слушатели в PluginManager.
     *
     * @param listeners регистрируемые слушатели
     */
    public void registerEvents(Listener @NotNull ... listeners) {
        for (Listener listener : listeners) {
            Bukkit.getPluginManager().registerEvents(listener, plugin);
        }
    }

}
