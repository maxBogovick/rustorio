package com.rustorio;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Состояние дерева исследований: сколько очков накоплено и какие {@link Tech} уже открыты.
 *
 * <p>Разблокировка АВТОМАТИЧЕСКАЯ, без экрана выбора: очки приносит {@link Lab}, и как только их
 * хватает на следующую по порядку (не открытую) технологию — она открывается сама, {@link
 * #addPoints} проверяет это после каждого пополнения. Это сознательное упрощение: настоящее
 * дерево с выбором «что открывать первым» — отдельный интерфейс, не тема этой игры (см. принцип
 * «просто и предсказуемо» в GDD эталона).
 */
public final class Research {

    private int points;
    private final Set<Tech> unlocked = EnumSet.noneOf(Tech.class);

    /** Сколько очков накоплено с начала игры. */
    public int points() {
        return points;
    }

    /** Уже открытые технологии — HUD показывает их игроку. */
    public Set<Tech> unlocked() {
        return EnumSet.copyOf(unlocked);
    }

    /** Открыта ли технология {@code tech} — читают {@link Miner} и {@link Furnace} в своём тике. */
    public boolean isUnlocked(Tech tech) {
        return unlocked.contains(tech);
    }

    /** Добавить очки (лаборатория зовёт это раз в готовую порцию) и разблокировать, что доступно. */
    public void addPoints(int amount) {
        points += amount;
        for (Tech tech : Tech.values()) {
            if (points >= tech.cost()) {
                unlocked.add(tech);
            }
        }
    }

    /** Сбросить в начальное состояние — мир готов принять загруженное сохранение. */
    void clear() {
        points = 0;
        unlocked.clear();
    }

    /** Состояние для сохранения: очки и открытые технологии через запятую (или {@code "-"}). */
    String save() {
        String names = unlocked.isEmpty()
                ? "-"
                : unlocked.stream().map(Enum::name).collect(Collectors.joining(","));
        return points + " " + names;
    }

    /** Восстановить состояние из сохранённой строки. */
    void restore(String data) {
        String[] parts = data.split(" ", 2);
        clear();
        points = Integer.parseInt(parts[0]);
        if (!parts[1].equals("-")) {
            for (String name : parts[1].split(",")) {
                unlocked.add(Tech.valueOf(name));
            }
        }
    }
}
