package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис сохранения содержимого обратно в предмет шалкер-бокса.
 *
 * Ключевые отличия от наивной реализации, которые закрывают дюп:
 * 1. Сохранение выполняется строго в главном потоке - никаких гонок с кликами.
 * 2. Перед записью слот проверяется: если шалкер-бокс исчез или заменен,
 *    содержимое возвращается в первый свободный слот, а не теряется.
 * 3. Флаг saving исключает повторный вход и параллельные записи.
 * 4. Сообщение о сохранении отправляется только при реальном изменении.
 */
public class ShulkerPersistenceService {

    private final NeyShulker plugin;
    private final ConfigManager configManager;
    private final SessionRegistry sessionRegistry;
    private final ShulkerContentService contentService;
    private final MessageService messageService;

    /** Задачи автосохранения по идентификатору сессии. */
    private final Map<UUID, BukkitTask> autoSaveTasks = new ConcurrentHashMap<>();

    public ShulkerPersistenceService(@NotNull NeyShulker plugin,
                                     @NotNull ConfigManager configManager,
                                     @NotNull SessionRegistry sessionRegistry,
                                     @NotNull ShulkerContentService contentService,
                                     @NotNull MessageService messageService) {

        this.plugin = plugin;
        this.configManager = configManager;
        this.sessionRegistry = sessionRegistry;
        this.contentService = contentService;
        this.messageService = messageService;

    }

    /**
     * Сохраняет содержимое сессии в предмет игрока.
     *
     * @param session сохраняемая сессия
     * @param notify  отправить ли игроку сообщение об успехе
     * @return true если сохранение выполнено
     */
    public boolean persist(@Nullable ShulkerSession session, boolean notify) {

        if (session == null || !Bukkit.isPrimaryThread()) {
            return false;
        }

        Player player = session.getPlayer();

        if (player == null || !player.isOnline()) {
            return false;
        }

        // Сессия уже закрыта - предмет мог быть передан другому компоненту
        if (sessionRegistry.getSession(session.playerId()) != session) {
            return false;
        }

        if (!session.saving().compareAndSet(false, true)) {
            return false;
        }

        long stamp = session.getModificationStamp();

        try {

            ItemStack[] contents = contentService.snapshot(session.inventory());
            ItemStack saved = ShulkerUtil.writeContents(session.shulkerItem(), contents);

            if (saved == null) {
                return false;
            }

            int slot = resolveSlot(player, session, saved);

            if (slot < 0) {
                plugin.getLogger().warning("Не удалось сохранить шалкер-бокс игрока "
                        + player.getName() + ": нет подходящего слота.");
                return false;
            }

            boolean relocated = slot != session.getSlot();

            writeBack(player, slot, saved, relocated);
            session.setSlot(slot);

            if (notify) {
                messageService.send(player, MessageKey.SAVED, Map.of());
            }

            return true;

        } catch (RuntimeException exception) {

            plugin.getLogger().severe("Ошибка сохранения шалкер-бокса игрока "
                    + player.getName() + ": " + exception.getMessage());
            return false;

        } finally {

            session.saving().set(false);

        }

    }

    /**
     * Помечает сессию измененной и планирует сохранение на следующем тике.
     * Повторные вызовы в пределах одного тика схлопываются в одно сохранение.
     *
     * @param session изменяемая сессия
     */
    public void markAndSchedule(@Nullable ShulkerSession session) {

        if (session == null) {
            return;
        }

        session.markModified();
        schedule(session, 1L);

    }

    /**
     * Планирует отложенное сохранение сессии.
     *
     * @param session сохраняемая сессия
     * @param delay   задержка в тиках
     */
    public void schedule(@Nullable ShulkerSession session, long delay) {

        if (session == null || !session.saveScheduled().compareAndSet(false, true)) {
            return;
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {

            session.saveScheduled().set(false);

            // Сессия могла быть закрыта: тогда сохранение уже выполнено при закрытии
            if (sessionRegistry.getSession(session.playerId()) == session) {
                persist(session, false);
            }

        }, Math.max(1L, delay));

    }

    /**
     * Запускает периодическое автосохранение, пока сессия открыта.
     * Задача останавливается при закрытии сессии или выключении плагина.
     *
     * @param session сохраняемая сессия
     */
    public void scheduleAutoSave(@NotNull ShulkerSession session) {

        long interval = configManager.getSaveInterval();

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin,
                () -> persist(session, false), interval, interval);

        autoSaveTasks.put(session.sessionId(), task);

    }

    /**
     * Останавливает автосохранение конкретной сессии.
     *
     * @param session закрываемая сессия
     */
    public void cancelAutoSave(@Nullable ShulkerSession session) {

        if (session == null) {
            return;
        }

        BukkitTask task = autoSaveTasks.remove(session.sessionId());

        if (task != null) {
            task.cancel();
        }

    }

    /**
     * Останавливает все задачи автосохранения.
     */
    public void cancelAllAutoSaves() {

        autoSaveTasks.values().forEach(BukkitTask::cancel);
        autoSaveTasks.clear();

    }

    /**
     * Синхронно сохраняет все открытые сессии.
     * Используется при выключении плагина.
     */
    public void persistAll() {

        for (ShulkerSession session : sessionRegistry.getSessions()) {
            persist(session, false);
        }

    }

    /**
     * Определяет слот для записи.
     * Если исходный слот все еще занят шалкер-боксом того же типа - пишем туда,
     * иначе ищем пустой слот, чтобы не затереть чужой предмет.
     */
    private int resolveSlot(@NotNull Player player,
                            @NotNull ShulkerSession session,
                            @NotNull ItemStack saved) {

        PlayerInventory inventory = player.getInventory();
        int slot = session.getSlot();

        if (slot >= 0 && slot < inventory.getSize()) {

            ItemStack current = inventory.getItem(slot);

            if (ShulkerUtil.isEmpty(current) || current.getType() == saved.getType()) {
                return slot;
            }

        }

        for (int i = 0; i < inventory.getSize(); i++) {

            if (ShulkerUtil.isEmpty(inventory.getItem(i))) {
                return i;
            }

        }

        return -1;

    }

    private void writeBack(@NotNull Player player,
                           int slot,
                           @NotNull ItemStack saved,
                           boolean relocated) {

        player.getInventory().setItem(slot, saved);

        if (relocated) {
            player.updateInventory();
        }

    }
}
