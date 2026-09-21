package eu.neydev.neyshulker.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Базовое событие NeyShulker.
 * Все собственные события плагина наследуются от него.
 *
 * Важны два контракта Bukkit, которые здесь соблюдены явно:
 * 1. Событие синхронное: конструктор не передает флаг async, иначе вызов
 *    из главного потока падает с "may only be triggered asynchronously".
 * 2. HandlerList НЕ наследуется и не является общим: каждый конкретный класс
 *    события держит свой статический HandlerList. Общий список приводил бы
 *    к тому, что слушатель одного события получал бы все события семейства.
 */
public abstract class NeyShulkerEvent extends Event {

    protected NeyShulkerEvent() {
        super();
    }

    @Override
    public abstract @NotNull HandlerList getHandlers();
}
