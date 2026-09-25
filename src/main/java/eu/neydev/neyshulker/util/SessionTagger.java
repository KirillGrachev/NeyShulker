package eu.neydev.neyshulker.util;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.UUID;

/**
 * Метка открытой сессии в PersistentDataContainer предмета.
 *
 * Идентичность бокса больше не держится на паре «слот + материал»: при
 * открытии предмет помечается UUID сессии, и все проверки (запись
 * содержимого, охрана открытого бокса, событие выброса) сверяют метку.
 * Это закрывает семейство краевых сценариев с боксами-близнецами:
 * содержимое не может быть записано в чужой предмет того же материала,
 * а украденный и возвращенный бокс опознается по метке, а не по слоту.
 *
 * Все методы null-безопасны и не изменяют переданные предметы:
 * tag/strip возвращают новые экземпляры. Предмет без ItemMeta
 * (тестовые заглушки) считается немеченым - для него действуют
 * legacy-проверки по слоту и материалу.
 */
public final class SessionTagger {

    /**
     * Ключ метки. Namespace фиксирован строкой, чтобы утилиту можно было
     * использовать без экземпляра плагина (в том числе в unit-тестах).
     */
    private static final NamespacedKey SESSION_KEY = new NamespacedKey("neyshulker", "session");

    private SessionTagger() {
    }

    /**
     * Читает UUID сессии из метки предмета.
     *
     * @param item проверяемый предмет
     * @return идентификатор сессии или null, если метки нет
     */
    public static @Nullable UUID sessionIdOf(@Nullable ItemStack item) {

        if (item == null) {
            return null;
        }

        ItemMeta meta = metaOf(item);

        if (meta == null || meta.getPersistentDataContainer() == null) {
            return null;
        }

        String raw;

        try {
            raw = meta.getPersistentDataContainer().get(SESSION_KEY, PersistentDataType.STRING);
        } catch (RuntimeException exception) {
            return null;
        }

        if (raw == null || raw.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(raw.trim().toLowerCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            return null;
        }

    }

    /**
     * Проверяет: помечен ли предмет конкретной сессией.
     *
     * @param item      проверяемый предмет
     * @param sessionId идентификатор сессии
     * @return true если метка предмета совпадает с сессией
     */
    public static boolean isSession(@Nullable ItemStack item, @NotNull UUID sessionId) {
        return sessionId.equals(sessionIdOf(item));
    }

    /**
     * Возвращает копию предмета с меткой сессии.
     *
     * @param item      исходный предмет (не изменяется)
     * @param sessionId идентификатор сессии
     * @return меченая копия или null, если метку поставить некуда
     */
    public static @Nullable ItemStack tag(@Nullable ItemStack item, @NotNull UUID sessionId) {

        if (item == null) {
            return null;
        }

        ItemStack copy = item.clone();

        if (copy == null) {
            return null;
        }

        ItemMeta meta = metaOf(copy);

        if (meta == null || meta.getPersistentDataContainer() == null) {
            return copy;
        }

        meta.getPersistentDataContainer()
                .set(SESSION_KEY, PersistentDataType.STRING, sessionId.toString());
        copy.setItemMeta(meta);
        return copy;

    }

    /**
     * Возвращает копию предмета без метки сессии (закрытие GUI).
     *
     * @param item исходный предмет (не изменяется)
     * @return копия без метки или тот же предмет, если метки не было
     */
    public static @Nullable ItemStack strip(@Nullable ItemStack item) {

        if (item == null || sessionIdOf(item) == null) {
            return item;
        }

        ItemStack copy = item.clone();

        if (copy == null) {
            return null;
        }

        ItemMeta meta = metaOf(copy);

        if (meta == null || meta.getPersistentDataContainer() == null) {
            return copy;
        }

        meta.getPersistentDataContainer().remove(SESSION_KEY);
        copy.setItemMeta(meta);
        return copy;

    }

    private static @Nullable ItemMeta metaOf(@NotNull ItemStack item) {

        try {
            return item.getItemMeta();
        } catch (RuntimeException exception) {
            return null;
        }

    }

}
