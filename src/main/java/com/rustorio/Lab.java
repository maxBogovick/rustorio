package com.rustorio;

/**
 * Лаборатория: тратит готовые предметы на очки исследований вместо того, чтобы отдавать их
 * дальше соседу.
 *
 * <p>Внутри устроена как {@link Furnace} — буфер и таймер, — но выход у неё не {@link Item}, а
 * очко в {@link Research} ({@link World#research()}): она не встаёт в цепочку «сосед → сосед»,
 * а завершает её. Принимает все пять «готовых» предметов верхнего уровня — {@link Item#GEAR},
 * {@link Item#MECHANISM}, {@link Item#ENGINE}, {@link Item#CHASSIS} и {@link Item#ALLOY_GEAR} —
 * сама цепочка лаборатории безразлична, и более сложный/дорогой предмет НЕ даёт бонусных очков
 * (сознательное упрощение: отдельная цена за предмет потребовала бы очереди вместо простого
 * счётчика буфера — лишний риск ради второстепенной детали).
 */
public final class Lab implements Building {

    /** Сколько предметов лаборатория готова держать в очереди. */
    private static final int BUFFER_MAX = 5;
    /** За сколько тиков один предмет превращается в одно очко исследований. */
    private static final int RESEARCH_TIME = 10;

    /** Сколько предметов сейчас ждёт переработки. */
    private int buffer;
    /**
     * Отсчёт до готовности текущего очка — тот же счётчик, что у {@link Furnace} ({@link
     * ProcessTimer}), вместо своего же {@code cooldown}, который раньше копировал буфер+таймер
     * печи почти дословно.
     */
    private final ProcessTimer timer = new ProcessTimer(RESEARCH_TIME);

    /** Принимает шестерёнки, механизмы, моторы, шасси и шестерни из сплава — любая готовая продукция кормит исследования. */
    @Override
    public boolean accept(World world, Item item) {
        boolean known = item == Item.GEAR || item == Item.MECHANISM || item == Item.ENGINE
                || item == Item.CHASSIS || item == Item.ALLOY_GEAR;
        if (!known || buffer >= BUFFER_MAX) {
            return false;
        }
        buffer++;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (buffer == 0) {
            return;                          // перерабатывать нечего
        }
        if (!timer.tick(effectiveTime(world))) {
            return;                          // ещё считаем
        }
        buffer--;
        world.research().addPoints(1);       // выход — не соседу, а в дерево исследований
    }

    /**
     * Время на одно очко с учётом технологии {@link Tech#FAST_LAB} — открыта, значит лаборатория
     * тратит предмет вдвое быстрее. Эффект накапливается сам на себя: чем быстрее лаборатория,
     * тем быстрее приходят следующие очки и следующие технологии.
     */
    private static int effectiveTime(World world) {
        return world.research().fasterIfUnlocked(Tech.FAST_LAB, RESEARCH_TIME);
    }

    @Override
    public Appearance appearance() {
        return buffer > 0 ? Appearance.of(Sprite.LAB, buffer) : Appearance.of(Sprite.LAB);
    }

    @Override
    public BuildingType type() {
        return BuildingType.LAB;
    }

    /** Состояние для сохранения: буфер и таймер — вход не типизирован жёстко, помнить нечего. */
    @Override
    public String save() {
        return buffer + " " + timer.cooldown();
    }

    /** Воссоздать лабораторию из сохранённого состояния. */
    static Lab load(String data) {
        String[] fields = data.split(" ");
        Lab lab = new Lab();
        lab.buffer = Integer.parseInt(fields[0]);
        lab.timer.restore(Integer.parseInt(fields[1]));
        return lab;
    }
}
