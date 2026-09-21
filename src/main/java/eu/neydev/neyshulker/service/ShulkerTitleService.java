package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.TitleMode;
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
     * Собирает заголовок для предмета по настроенному режиму.
     *
     * ORIGINAL - имя самого шалкера (display name предмета или имя материала),
     * CUSTOM - шаблон конфигурации с плейсхолдером {shulker_name}.
     * Пустой шаблон в CUSTOM-режиме деградирует в имя шалкера, чтобы GUI
     * никогда не оставался без заголовка.
     *
     * @param shulker предмет шалкер-бокса
     * @return готовый заголовок
     */
    public @NotNull String resolve(@NotNull ItemStack shulker) {

        String name = ShulkerUtil.getShulkerName(shulker);

        if (configManager.getTitleMode() == TitleMode.ORIGINAL) {
            return name;
        }

        String format = configManager.getTitleFormat();

        if (format == null || format.isBlank()) {
            return name;
        }

        return format.replace("{shulker_name}", name);

    }
}
