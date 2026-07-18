package com.rustorio.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Шина событий производства: связывает издателей (машины) и подписчиков ({@link
 * ProductionObserver}). «Субъект» в шаблоне Observer.
 *
 * <p>Издатель зовёт {@link #publish}, ничего не зная о подписчиках; подписчик регистрируется
 * через {@link #subscribe}, ничего не зная об издателях. Порядок рассылки — порядок подписки,
 * то есть детерминированный; сама рассылка синхронная, в том же тике.
 *
 * <p>Шина живёт в {@code core} и не зависит ни от чего, кроме {@link Item}: её должны видеть и
 * издатели ({@code model}), и подписчики ({@code game}), и «коробка тика» {@link TickContext}.
 */
public final class ProductionBus {

    private final List<ProductionObserver> observers = new ArrayList<>();

    /** Подписать наблюдателя. Регистрируется один раз, обычно при создании игры. */
    public void subscribe(ProductionObserver observer) {
        observers.add(observer);
    }

    /** Разослать событие всем подписчикам. Если их нет — вызов ничего не делает. */
    public void publish(Item item, int amount) {
        for (ProductionObserver observer : observers) {
            observer.onProduced(item, amount);
        }
    }
}
