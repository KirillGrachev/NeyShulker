package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.TitleMode;
import eu.neydev.neyshulker.util.ShulkerUtil;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Map;

/**
 * Сервис заголовка GUI шалкер-бокса.
 *
 * Имя безымянного бокса берется по языку клиента (Player#getLocale):
 * поставленный шалкер vanilla показывает именно так, поэтому меню открытого
 * бокса совпадает с контейнером на языке игрока. Нужные языки перечисляются
 * в конфигурации (settings.shulker.title.names), отсутствующий язык падает
 * в ключ default, а затем в английское имя материала.
 */
public class ShulkerTitleService {

    private static final String DEFAULT_LOCALE_KEY = "default";
    private static final String NAME_PLACEHOLDER = "{shulker_name}";
    private static final String MATERIAL_PLACEHOLDER = "{shulker_material}";

    private final ConfigManager configManager;

    public ShulkerTitleService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Собирает заголовок для предмета по настроенному режиму.
     *
     * ORIGINAL - имя самого шалкера (display name предмета или имя по языку
     * клиента), CUSTOM - шаблон конфигурации с плейсхолдерами {shulker_name}
     * (имя по языку клиента) и {shulker_material} (английское имя материала).
     * Пустой шаблон в CUSTOM-режиме деградирует в имя шалкера, чтобы GUI
     * никогда не оставался без заголовка.
     *
     * @param viewer  игрок, которому открывается GUI (источник локали)
     * @param shulker предмет шалкер-бокса
     * @return готовый заголовок
     */
    public @NotNull String resolve(@Nullable Player viewer, @NotNull ItemStack shulker) {

        String name = resolveName(viewer, shulker);

        if (configManager.getTitleMode() == TitleMode.ORIGINAL) {
            return name;
        }

        String format = configManager.getTitleFormat();

        if (format == null || format.isBlank()) {
            return name;
        }

        return format
                .replace(NAME_PLACEHOLDER, name)
                .replace(MATERIAL_PLACEHOLDER, ShulkerUtil.prettifyMaterial(shulker.getType()));

    }

    /**
     * Имя шалкера для заголовка: display name предмета, а без него -
     * имя по языку клиента из конфигурации.
     *
     * @param viewer  игрок, которому открывается GUI
     * @param shulker предмет шалкер-бокса
     * @return имя для подстановки в шаблон
     */
    private @NotNull String resolveName(@Nullable Player viewer, @NotNull ItemStack shulker) {

        String displayName = readDisplayName(shulker);

        if (displayName != null) {
            return displayName;
        }

        String localeName = localeName(viewer);

        if (localeName != null) {
            return localeName;
        }

        return ShulkerUtil.prettifyMaterial(shulker.getType());

    }

    /**
     * Читает display name предмета.
     *
     * @param shulker предмет шалкер-бокса
     * @return имя предмета или null, если его нет
     */
    private @Nullable String readDisplayName(@NotNull ItemStack shulker) {

        org.bukkit.inventory.meta.ItemMeta itemMeta = shulker.getItemMeta();

        if (itemMeta != null && itemMeta.hasDisplayName()) {
            return itemMeta.getDisplayName();
        }

        return null;

    }

    /**
     * Ищет имя безымянного бокса под язык клиента.
     *
     * @param viewer игрок, которому открывается GUI
     * @return имя из конфигурации или null, если язык не накрыт
     */
    private @Nullable String localeName(@Nullable Player viewer) {

        Map<String, String> names = configManager.getTitleNames();

        if (names.isEmpty()) {
            return null;
        }

        String locale = viewer == null ? null : viewer.getLocale();

        if (locale != null && !locale.isBlank()) {

            String localized = names.get(locale.toLowerCase(Locale.ROOT));

            if (localized != null) {
                return localized;
            }

        }

        return names.get(DEFAULT_LOCALE_KEY);

    }
}
