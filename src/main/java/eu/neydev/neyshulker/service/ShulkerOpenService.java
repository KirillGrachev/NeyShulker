package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.event.ShulkerOpenEvent;
import eu.neydev.neyshulker.inventory.NeyShulkerViewer;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.registry.SessionRegistry;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Сервис открытия шалкер-бокса: создание сессии, GUI и запуск автосохранения.
 */
public class ShulkerOpenService {

    private final NeyShulker plugin;
    private final SessionRegistry sessionRegistry;
    private final ShulkerContentService contentService;
    private final ShulkerTitleService titleService;
    private final ShulkerPersistenceService persistenceService;
    private final MessageService messageService;
    private final SoundService soundService;

    public ShulkerOpenService(@NotNull NeyShulker plugin,
                              @NotNull SessionRegistry sessionRegistry,
                              @NotNull ShulkerContentService contentService,
                              @NotNull ShulkerTitleService titleService,
                              @NotNull ShulkerPersistenceService persistenceService,
                              @NotNull MessageService messageService,
                              @NotNull SoundService soundService) {

        this.plugin = plugin;
        this.sessionRegistry = sessionRegistry;
        this.contentService = contentService;
        this.titleService = titleService;
        this.persistenceService = persistenceService;
        this.messageService = messageService;
        this.soundService = soundService;

    }

    /**
     * Открывает шалкер-бокс, лежащий в указанном слоте инвентаря игрока.
     *
     * @param player игрок
     * @param slot   слот с шалкер-боксом
     * @return true если GUI открыт
     */
    public boolean open(@NotNull Player player, int slot) {

        ItemStack shulker = player.getInventory().getItem(slot);

        if (!ShulkerUtil.isShulkerBox(shulker)) {
            messageService.send(player, MessageKey.OPEN_ERROR, Map.of());
            return false;
        }

        return open(player, shulker, slot);

    }

    /**
     * Открывает шалкер-бокс и регистрирует сессию.
     *
     * @param player  игрок
     * @param shulker предмет шалкер-бокса
     * @param slot    слот, в котором лежит предмет
     * @return true если GUI открыт
     */
    public boolean open(@NotNull Player player, @NotNull ItemStack shulker, int slot) {

        if (!ShulkerUtil.isShulkerBox(shulker) || sessionRegistry.hasSession(player.getUniqueId())) {
            return false;
        }

        try {

            ShulkerSession session = createSession(player, shulker, slot);

            if (session == null) {
                return false;
            }

            contentService.loadInto(shulker, session.inventory());
            player.openInventory(session.inventory());

            if (!callOpenEvent(player, session, shulker)) {
                rollback(player);
                return false;
            }

            persistenceService.scheduleAutoSave(session);
            soundService.playOpen(player);
            return true;

        } catch (RuntimeException exception) {

            plugin.getLogger().severe("Failed to open a shulker box: " + exception.getMessage());
            messageService.send(player, MessageKey.OPEN_ERROR, Map.of());
            rollback(player);
            return false;

        }

    }

    private ShulkerSession createSession(@NotNull Player player,
                                         @NotNull ItemStack shulker,
                                         int slot) {

        String title = titleService.resolve(player, shulker);

        return sessionRegistry.createSession(player, shulker, slot,
                () -> new NeyShulkerViewer(title).getInventory());

    }

    private boolean callOpenEvent(@NotNull Player player,
                                  @NotNull ShulkerSession session,
                                  @NotNull ItemStack shulker) {

        ShulkerOpenEvent event = new ShulkerOpenEvent(player, session, shulker);
        plugin.getServer().getPluginManager().callEvent(event);
        return !event.isCancelled();

    }

    private void rollback(@NotNull Player player) {

        sessionRegistry.closeSession(player.getUniqueId());
        player.closeInventory();

    }

}
