package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.NeyShulkerConfig;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.util.HexColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Сервис сообщений: префикс, HEX-цвета, плейсхолдеры и поддержка многострочных текстов.
 *
 * Единственная точка входа по получателю - {@link CommandSender}: Player
 * является CommandSender, поэтому отдельные перегрузки не нужны (пара
 * Player/CommandSender-перегрузок делала вызов send(null, key)
 * неоднозначным на уровне компиляции).
 */
public class MessageService {

    private final NeyShulkerConfig config;

    public MessageService(@NotNull NeyShulkerConfig config) {
        this.config = config;
    }

    /**
     * Отправляет сообщение без плейсхолдеров.
     *
     * @param sender получатель (игрок или консоль)
     * @param key    ключ сообщения
     */
    public void send(@Nullable CommandSender sender, @NotNull MessageKey key) {
        send(sender, key, Map.of());
    }

    /**
     * Отправляет сообщение с подстановкой плейсхолдеров.
     *
     * @param sender       получатель (игрок или консоль)
     * @param key          ключ сообщения
     * @param placeholders карта плейсхолдеров вида {amount} -> 5
     */
    public void send(@Nullable CommandSender sender,
                     @NotNull MessageKey key,
                     @NotNull Map<String, String> placeholders) {

        if (!isDeliverable(sender)) {
            return;
        }

        for (String line : build(key, placeholders)) {
            sender.sendMessage(line);
        }

    }

    /**
     * Отправляет произвольную строку с HEX-цветами.
     * Префикс подставляется только через явный плейсхолдер {prefix} -
     * та же семантика, что у {@link #build}.
     *
     * @param sender получатель (игрок или консоль)
     * @param text   текст сообщения
     */
    public void sendRaw(@Nullable CommandSender sender, @NotNull String text) {

        if (!isDeliverable(sender)) {
            return;
        }

        sender.sendMessage(applyPlaceholders(HexColorUtil.color(text),
                Map.of("prefix", config.getMessagePrefix())));

    }

    /**
     * Собирает готовые строки сообщения с плейсхолдерами.
     *
     * Префикс плагина НЕ пришивается автоматически: он появляется только там,
     * где автор сообщения явно написал {prefix}. Так многострочные сообщения
     * не превращаются в простыню из повторяющихся префиксов.
     *
     * @param key          ключ сообщения
     * @param placeholders карта плейсхолдеров
     * @return список строк для отправки (пустой, если сообщение выключено)
     */
    public @NotNull List<String> build(@NotNull MessageKey key,
                                       @NotNull Map<String, String> placeholders) {

        List<String> lines = config.getMessages(key);

        if (lines.isEmpty()) {
            return List.of();
        }

        Map<String, String> all = new HashMap<>(placeholders);
        all.putIfAbsent("prefix", config.getMessagePrefix());

        return lines.stream()
                .map(line -> applyPlaceholders(line, all))
                .toList();

    }

    private boolean isDeliverable(@Nullable CommandSender sender) {

        if (sender == null || !config.areMessagesEnabled()) {
            return false;
        }

        // Оффлайн-игрок сообщения не получит: отправка в мертвое соединение бессмысленна
        return !(sender instanceof Player player) || player.isOnline();

    }

    private @NotNull String applyPlaceholders(@NotNull String line,
                                              @NotNull Map<String, String> placeholders) {

        String result = line;

        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return result;

    }

}
