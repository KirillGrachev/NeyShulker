package eu.neydev.neyshulker.service;

import eu.neydev.neyshulker.config.ConfigManager;
import eu.neydev.neyshulker.config.type.MessageKey;
import eu.neydev.neyshulker.util.HexColorUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Сервис сообщений: префикс, HEX-цвета, плейсхолдеры и поддержка многострочных текстов.
 */
public class MessageService {

    private final ConfigManager configManager;

    public MessageService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Отправляет сообщение без плейсхолдеров.
     *
     * @param player получатель
     * @param key    ключ сообщения
     */
    public void send(@Nullable Player player, @NotNull MessageKey key) {
        send(player, key, Map.of());
    }

    /**
     * Отправляет сообщение без плейсхолдеров любому отправителю (игрок или консоль).
     *
     * @param sender получатель
     * @param key    ключ сообщения
     */
    public void send(@Nullable CommandSender sender, @NotNull MessageKey key) {
        send(sender, key, Map.of());
    }

    /**
     * Отправляет сообщение с подстановкой плейсхолдеров.
     *
     * @param player       получатель
     * @param key          ключ сообщения
     * @param placeholders карта плейсхолдеров вида {amount} -> 5
     */
    public void send(@Nullable Player player,
                     @NotNull MessageKey key,
                     @NotNull Map<String, String> placeholders) {

        if (player == null || !player.isOnline() || !configManager.areMessagesEnabled()) {
            return;
        }

        for (String line : build(key, placeholders)) {
            player.sendMessage(line);
        }

    }

    /**
     * Отправляет сообщение любому отправителю (игрок или консоль).
     *
     * @param sender       получатель
     * @param key          ключ сообщения
     * @param placeholders карта плейсхолдеров
     */
    public void send(@Nullable CommandSender sender,
                     @NotNull MessageKey key,
                     @NotNull Map<String, String> placeholders) {

        if (sender == null || !configManager.areMessagesEnabled()) {
            return;
        }

        for (String line : build(key, placeholders)) {
            sender.sendMessage(line);
        }

    }

    /**
     * Отправляет произвольную строку с префиксом и HEX-цветами.
     * Используется командами и служебной информацией.
     *
     * @param player получатель
     * @param text   текст сообщения
     */
    public void sendRaw(@Nullable Player player, @NotNull String text) {

        if (player == null || !player.isOnline() || !configManager.areMessagesEnabled()) {
            return;
        }

        player.sendMessage(applyPlaceholders(HexColorUtil.color(text),
                Map.of("prefix", configManager.getMessagePrefix())));

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

        List<String> lines = configManager.getMessages(key);

        if (lines.isEmpty()) {
            return List.of();
        }

        Map<String, String> all = new java.util.HashMap<>(placeholders);
        all.putIfAbsent("prefix", configManager.getMessagePrefix());

        return lines.stream()
                .map(line -> applyPlaceholders(line, all))
                .toList();

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
