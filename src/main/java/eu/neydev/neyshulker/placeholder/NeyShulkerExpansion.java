package eu.neydev.neyshulker.placeholder;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Расширение PlaceholderAPI: %neyshulker_*%.
 *
 * Доступные плейсхолдеры:
 * open, name, slot, free_slots, used_slots, total_slots, items,
 * autocollect, autocollect_permitted, version
 *
 * Потоковая модель: PlaceholderAPI вызывает onRequest и из чужих потоков
 * (TAB-плагины, скорборды), а читать живой CraftInventory из async нельзя.
 * Поэтому статистика сессий кэшируется: раз в секунду задача главного потока
 * обновляет кэш, а любые запросы (включая async) отдают готовые значения,
 * не трогая инвентари.
 */
public class NeyShulkerExpansion extends PlaceholderExpansion {

    /** Период обновления кэша статистики сессий. */
    private static final long REFRESH_PERIOD_TICKS = 20L;

    private final NeyShulker plugin;
    private final NeyShulkerConfig config;
    private final SessionRegistry sessionRegistry;
    private final AutoCollectService autoCollectService;

    private final Map<UUID, SessionStats> statsCache = new ConcurrentHashMap<>();
    private @Nullable BukkitTask refreshTask;

    public NeyShulkerExpansion(@NotNull NeyShulker plugin,
                               @NotNull NeyShulkerConfig config,
                               @NotNull SessionRegistry sessionRegistry,
                               @NotNull AutoCollectService autoCollectService) {

        this.plugin = plugin;
        this.config = config;
        this.sessionRegistry = sessionRegistry;
        this.autoCollectService = autoCollectService;

        // В тестах плагин выключен и планировщика нет - задача не планируется
        if (plugin.isEnabled()) {
            this.refreshTask = Bukkit.getScheduler().runTaskTimer(plugin,
                    this::refreshCache, REFRESH_PERIOD_TICKS, REFRESH_PERIOD_TICKS);
        }

    }

    /**
     * Останавливает фоновое обновление кэша (вызов из onDisable плагина).
     */
    public void shutdown() {

        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }

        statsCache.clear();

    }

    @Override
    public @NotNull String getIdentifier() {
        return "neyshulker";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Ney";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {

        String key = params.toLowerCase(Locale.ROOT);

        if (key.equals("version")) {
            return getVersion();
        }

        if (key.equals("autocollect_permitted")) {
            return String.valueOf(config.isAutoCollectEnabled());
        }

        if (!(player instanceof Player online)) {
            return "";
        }

        if (key.equals("autocollect")) {
            return String.valueOf(autoCollectService.isRunning()
                    && autoCollectService.isEnabledFor(online));
        }

        SessionStats stats = statsFor(online);

        return switch (key) {
            case "open" -> String.valueOf(stats != null);
            case "name" -> stats == null ? "" : stats.name();
            case "slot" -> stats == null ? "" : String.valueOf(stats.slot());
            case "free_slots" -> stats == null ? "" : String.valueOf(stats.freeSlots());
            case "used_slots" -> stats == null ? ""
                    : String.valueOf(ShulkerUtil.SHULKER_SIZE - stats.freeSlots());
            case "total_slots" -> String.valueOf(ShulkerUtil.SHULKER_SIZE);
            case "items" -> stats == null ? "" : String.valueOf(stats.items());
            default -> null;
        };

    }

    /**
     * Статистика игрока: в главном потоке перечитывается из живой сессии,
     * в чужом потоке отдается кэш (безопасно и без обращения к инвентарю).
     */
    private @Nullable SessionStats statsFor(@NotNull Player player) {

        if (Bukkit.isPrimaryThread()) {

            SessionStats fresh = computeStats(player);
            UUID playerId = player.getUniqueId();

            if (fresh == null) {
                statsCache.remove(playerId);
            } else {
                statsCache.put(playerId, fresh);
            }

            return fresh;

        }

        return statsCache.get(player.getUniqueId());

    }

    private void refreshCache() {

        for (Player player : Bukkit.getOnlinePlayers()) {

            SessionStats fresh = computeStats(player);

            if (fresh == null) {
                statsCache.remove(player.getUniqueId());
            } else {
                statsCache.put(player.getUniqueId(), fresh);
            }

        }

    }

    private @Nullable SessionStats computeStats(@NotNull Player player) {

        ShulkerSession session = sessionRegistry.getSession(player);

        if (session == null) {
            return null;
        }

        return new SessionStats(
                session.getShulkerName(),
                session.getSlot(),
                ShulkerUtil.countFreeSlots(session.inventory()),
                ShulkerUtil.countItems(session.inventory())
        );

    }

    /**
     * Кэшированная статистика открытой сессии.
     *
     * @param name      имя бокса
     * @param slot      слот с боксом
     * @param freeSlots свободных слотов в живом GUI
     * @param items     суммарное число предметов в живом GUI
     */
    private record SessionStats(String name, int slot, int freeSlots, int items) {
    }

}
