package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.SessionTagger;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Сервис сохранения содержимого открытого шалкер-бокса обратно в предмет.
 *
 * Ключевые решения текущей реализации, которые закрывают дюп:
 * 1. Сохранение выполняется строго в главном потоке - никаких гонок с кликами.
 * 2. Целевой слот ищется по метке сессии в PersistentDataContainer предмета:
 *    содержимое не может быть записано в бокс-близнец того же материала,
 *    а при исчезновении помеченного бокса сессия открепляется.
 * 3. Флаг saving исключает повторный вход и параллельные записи.
 */
public class ShulkerPersistenceService {

    private final NeyShulker plugin;
    private final NeyShulkerConfig config;
    private final SessionRegistry sessionRegistry;
    private final ShulkerContentService contentService;
    private final MessageService messageService;

    /** Задачи автосохранения по идентификатору сессии. */
    private final Map<UUID, BukkitTask> autoSaveTasks = new ConcurrentHashMap<>();

    public ShulkerPersistenceService(@NotNull NeyShulker plugin,
                                     @NotNull NeyShulkerConfig config,
                                     @NotNull SessionRegistry sessionRegistry,
                                     @NotNull ShulkerContentService contentService,
                                     @NotNull MessageService messageService) {

        this.plugin = plugin;
        this.config = config;
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

        // Открепленная сессия молчалива: состояние уже обработано и закрыто
        if (session.isDetached()) {
            return false;
        }

        if (!session.saving().compareAndSet(false, true)) {
            return false;
        }

        try {

            ItemStack[] contents = contentService.snapshot(session.inventory());
            ItemStack saved = ShulkerUtil.writeContents(session.shulkerItem(), contents);

            if (saved == null) {
                return false;
            }

            PlayerInventory inventory = player.getInventory();
            int slot = resolveSlot(inventory, session, session.getSlot(), saved.getType());

            if (slot < 0) {
                detach(player, session);
                return false;
            }

            if (slot != session.getSlot()) {
                // Помеченный бокс переехал внешним вмешательством: следуем
                // за меткой, а не воссоздаем вторую копию в осиротевшем слоте
                session.setSlot(slot);
            }

            writeBack(player, slot, saved);

            if (notify) {
                messageService.send(player, MessageKey.SAVED);
            }

            return true;

        } catch (RuntimeException exception) {

            plugin.getLogger().log(Level.SEVERE,
                    "Failed to save the shulker box of player " + player.getName(), exception);
            return false;

        } finally {
            session.saving().set(false);
        }

    }

    /**
     * Финализирует сессию при закрытии: снимает служебную метку с живого
     * предмета и возвращает его слепок для {@code ShulkerCloseEvent}.
     *
     * @param session закрываемая сессия (сохранение уже выполнено)
     * @return фактический предмет после сохранения или слепок открытия,
     *         если живой предмет недоступен
     */
    public @NotNull ItemStack finalizeSession(@NotNull ShulkerSession session) {

        Player player = session.getPlayer();

        if (player != null && player.isOnline() && Bukkit.isPrimaryThread()) {

            PlayerInventory inventory = player.getInventory();
            ItemStack live = inventory.getItem(session.getSlot());

            if (SessionTagger.isSession(live, session.sessionId())) {

                ItemStack stripped = SessionTagger.strip(live);

                if (stripped != null) {
                    inventory.setItem(session.getSlot(), stripped);
                    return stripped.clone();
                }

            }

        }

        return session.shulkerItem().clone();

    }

    /**
     * Планирует сохранение сессии на следующий тик.
     * Повторные вызовы в пределах одного тика схлопываются в одно сохранение.
     *
     * @param session сохраняемая сессия
     */
    public void scheduleSave(@Nullable ShulkerSession session) {

        if (session == null) {
            return;
        }

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

        cancelAutoSave(session);

        long interval = config.getSaveInterval();

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
     * Открепляет сессию, у которой бокс исчез из слота.
     * Выполняется ровно один раз; консольных уведомлений нет по решению
     * владельца плагина.
     */
    private void detach(@NotNull Player player, @NotNull ShulkerSession session) {

        if (!session.markDetached()) {
            return;
        }

        cancelAutoSave(session);

        // Слушатель закрытия снимет сессию и раздаст закрывающие события
        player.closeInventory();

    }

    /**
     * Определяет слот для записи.
     *
     * Если предмет сессии помечен (штатный путь), запись идет только
     * в помеченный предмет: исходный слот, затем поиск метки по инвентарю.
     * Бокс-близнец того же материала целью не считается - запись в него
     * оставила бы украденный оригинал с содержимым последнего автосейва.
     *
     * Для немеченых предметов (тестовые заглушки без ItemMeta) действует
     * legacy-правило: исходный слот с материалом бокса или пустой исходный
     * слот и бокс того же типа в инвентаре.
     *
     * @param inventory инвентарь игрока
     * @param session   сохраняемая сессия
     * @param slot      слот сессии
     * @param savedType материал сохраняемого бокса
     * @return слот для записи или -1, если писать некуда
     */
    static int resolveSlot(@NotNull PlayerInventory inventory,
                           @NotNull ShulkerSession session,
                           int slot,
                           @NotNull Material savedType) {

        if (slot < 0 || slot >= inventory.getSize()) {
            return -1;
        }

        UUID sessionId = session.sessionId();
        boolean tagged = SessionTagger.isSession(session.shulkerItem(), sessionId);

        if (tagged) {

            if (SessionTagger.isSession(inventory.getItem(slot), sessionId)) {
                return slot;
            }

            for (int i = 0; i < inventory.getSize(); i++) {
                if (SessionTagger.isSession(inventory.getItem(i), sessionId)) {
                    return i;
                }
            }

            return -1;

        }

        ItemStack current = inventory.getItem(slot);

        if (current != null && current.getType() == savedType) {
            return slot;
        }

        if (!ShulkerUtil.isEmpty(current)) {
            return -1;
        }

        for (int i = 0; i < inventory.getSize(); i++) {

            ItemStack candidate = inventory.getItem(i);

            if (candidate != null && candidate.getType() == savedType) {
                return i;
            }

        }

        return -1;

    }

    private void writeBack(@NotNull Player player,
                           int slot,
                           @NotNull ItemStack saved) {
        player.getInventory().setItem(slot, saved);
    }

}
