package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Сервис заголовка GUI шалкер-бокса.
 */
public class ShulkerTitleService {

    private final ConfigManager configManager;

    public ShulkerTitleService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Собирает заголовок для предмета.
     * Поддерживаемый плейсхолдер: {shulker_name}.
     *
     * @param shulker предмет шалкер-бокса
     * @return готовый заголовок с примененными цветами
     */
    public @NotNull String resolve(@NotNull ItemStack shulker) {

        String format = configManager.getShulkerTitle();

        if (format == null || format.isBlank()) {
            return ShulkerUtil.getShulkerName(shulker);
        }

        return format.replace("{shulker_name}", ShulkerUtil.getShulkerName(shulker));

    }
}
