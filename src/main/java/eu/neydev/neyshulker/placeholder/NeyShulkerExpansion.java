package eu.neydev.neyshulker.placeholder;

import eu.neydev.neyshulker.NeyShulker;
import eu.neydev.neyshulker.model.ShulkerSession;
import eu.neydev.neyshulker.service.AutoCollectService;
import eu.neydev.neyshulker.util.ShulkerUtil;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Расширение PlaceholderAPI: %neyshulker_*%.
 *
 * Доступные плейсхолдеры:
 * open, name, slot, free_slots, used_slots, total_slots, items,
 * autocollect, autocollect_permitted, version
 */
public class NeyShulkerExpansion extends PlaceholderExpansion {

    private final NeyShulker plugin;

    public NeyShulkerExpansion(NeyShulker plugin) {
        this.plugin = plugin;
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
            return String.valueOf(plugin.getConfigManager().isAutoCollectEnabled());
        }

        if (!(player instanceof Player online)) {
            return "";
        }

        AutoCollectService autoCollectService = plugin.getServices().getAutoCollectService();

        if (key.equals("autocollect")) {
            return String.valueOf(autoCollectService.isRunning()
                    && autoCollectService.isEnabledFor(online));
        }

        ShulkerSession session = plugin.getServices().getSessionRegistry().getSession(online);

        return switch (key) {
            case "open" -> String.valueOf(session != null);
            case "name" -> session == null ? "" : session.getShulkerName();
            case "slot" -> session == null ? "" : String.valueOf(session.getSlot());

            case "free_slots" -> session == null ? ""
                    : String.valueOf(countFreeSlots(session));

            case "used_slots" -> session == null ? ""
                    : String.valueOf(ShulkerUtil.SHULKER_SIZE - countFreeSlots(session));

            case "total_slots" -> String.valueOf(ShulkerUtil.SHULKER_SIZE);

            case "items" -> session == null ? ""
                    : String.valueOf(countItems(session));

            default -> null;
        };

    }

    /**
     * Считает свободные слоты по живому содержимому GUI,
     * а не по слепку предмета на момент открытия.
     */
    private int countFreeSlots(@NotNull ShulkerSession session) {

        int free = 0;

        for (int i = 0; i < session.inventory().getSize(); i++) {
            if (ShulkerUtil.isEmpty(session.inventory().getItem(i))) {
                free++;
            }
        }

        return free;

    }

    private int countItems(@NotNull ShulkerSession session) {

        int amount = 0;

        for (int i = 0; i < session.inventory().getSize(); i++) {

            ItemStack item = session.inventory().getItem(i);

            if (!ShulkerUtil.isEmpty(item)) {
                amount += item.getAmount();
            }

        }

        return amount;

    }

}
