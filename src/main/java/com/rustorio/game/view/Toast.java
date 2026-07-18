package com.rustorio.game.view;

/**
 * Всплывающее уведомление, живущее несколько секунд («исследование завершено», «склад полон»).
 *
 * <p>В отличие от панелей и подсветок (их пересобирают каждый кадр), тост держится сам, пока
 * не истечёт его время: {@link Overlay#age} уменьшает остаток и убирает просроченные. Слой
 * render читает {@link #remaining()} и может по нему делать затухание.
 */
public final class Toast {

    private final String text;
    private float remaining;

    Toast(String text, float seconds) {
        this.text = text;
        this.remaining = seconds;
    }

    void age(float dt) {
        remaining -= dt;
    }

    boolean expired() {
        return remaining <= 0f;
    }

    public String text() {
        return text;
    }

    /** Сколько секунд тосту ещё жить (для затухания в render). */
    public float remaining() {
        return remaining;
    }
}
