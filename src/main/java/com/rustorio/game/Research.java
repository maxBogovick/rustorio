package com.rustorio.game;

import com.rustorio.core.Balance;
import com.rustorio.core.Effect;
import com.rustorio.core.Tech;
import com.rustorio.model.Lab;
import com.rustorio.model.Technology;
import com.rustorio.model.World;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Прогресс исследований: сколько очков накоплено и что уже открыто.
 *
 * <p><b>Почему это живёт в {@code game}, а не в {@code model}.</b> Мир (модель) знает про
 * здания и предметы — это «физика». А «сколько у игрока очков и что он открыл» — это
 * состояние ИГРЫ, а не мира. Разделение то же, что между «на поле стоит печь» и «игрок
 * выбрал инструмент».
 *
 * <p><b>Про сбор очков.</b> Лаборатории копят очки у себя, а {@link #collect} каждый тик их
 * ЗАБИРАЕТ (см. {@code Lab#drainPoints}). Забирает, а не подсматривает: иначе одни и те же
 * очки начислялись бы столько раз, сколько раз мы заглянули, — то есть каждый тик.
 */
public final class Research {

    private final Balance balance;
    private final Set<Tech> unlocked = EnumSet.noneOf(Tech.class);
    private int points;

    public Research(Balance balance) {
        this.balance = balance;
    }

    /** Забрать очки, накопленные всеми лабораториями мира. Зовётся раз в тик. */
    public void collect(World world) {
        world.forEachBuilding((x, y, building) -> {
            if (building instanceof Lab lab) {
                points += lab.drainPoints();
            }
        });
    }

    /**
     * Восстановить прогресс из сохранения: очки и набор открытых технологий.
     *
     * <p><b>Важно: эффекты технологий здесь НЕ применяются повторно.</b> Баланс
     * восстанавливается отдельно, из своего снимка. Если бы мы тут прогнали {@code
     * effect.applyTo(balance)} за каждую открытую технологию, а баланс при этом уже был
     * загружен, апгрейды применились бы ДВАЖДЫ — лента поехала бы вчетверо быстрее вместо
     * вдвое. Снимок хранит РЕЗУЛЬТАТ, а не переигрывает историю.
     */
    public void restore(int points, Collection<Tech> unlockedTechs) {
        this.points = points;
        unlocked.clear();
        unlocked.addAll(unlockedTechs);
    }

    public int points() {
        return points;
    }

    public Set<Tech> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public boolean isUnlocked(Tech tech) {
        return unlocked.contains(tech);
    }

    /**
     * Можно ли открыть технологию прямо сейчас: не открыта, предпосылки готовы, очков хватает.
     */
    public boolean canResearch(Tech tech) {
        Technology technology = Technology.of(tech);
        return !unlocked.contains(tech)
                && technology.isAvailableWith(unlocked)
                && points >= technology.cost();
    }

    /** Что игрок может открыть прямо сейчас (для интерфейса). */
    public List<Technology> available() {
        return Technology.all().stream()
                .filter(t -> canResearch(t.id()))
                .toList();
    }

    /**
     * Открыть технологию: списать очки и применить её эффекты.
     *
     * <p>Проверка {@link #canResearch} стоит ВНУТРИ, а не «пусть вызывающий проверит сам»:
     * иначе однажды кто-то забудет проверить, и очки уйдут в минус — или технология откроется
     * дважды, а её эффект применится дважды (лента поедет вчетверо быстрее вместо вдвое).
     *
     * @return {@code true}, если открыли; {@code false}, если было нельзя
     */
    public boolean research(Tech tech) {
        if (!canResearch(tech)) {
            return false;
        }
        Technology technology = Technology.of(tech);
        points -= technology.cost();
        unlocked.add(tech);
        for (Effect effect : technology.effects()) {
            effect.applyTo(balance);
        }
        return true;
    }
}
