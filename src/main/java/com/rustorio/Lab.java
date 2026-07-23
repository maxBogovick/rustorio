package com.rustorio;

/**
 * Лаборатория: тратит готовые предметы на очки исследований вместо того, чтобы отдавать их
 * дальше соседу.
 *
 * <p>Внутри устроена как {@link Furnace} — буфер и таймер, — но выход у неё не {@link Item}, а
 * очко в {@link Research} ({@link World#research()}): она не встаёт в цепочку «сосед → сосед»,
 * а завершает её. Принимает все три «готовых» предмета верхнего уровня — {@link Item#GEAR},
 * {@link Item#MECHANISM} и {@link Item#ENGINE} — сама цепочка лаборатории безразлична, и мотор
 * НЕ даёт бонусных очков (сознательное упрощение: отдельная цена за предмет потребовала бы
 * очереди вместо простого счётчика буфера — лишний риск ради второстепенной детали).
 */
public final class Lab implements Building {

    /** Сколько предметов лаборатория готова держать в очереди. */
    private static final int BUFFER_MAX = 5;
    /** За сколько тиков один предмет превращается в одно очко исследований. */
    private static final int RESEARCH_TIME = 10;

    /** Сколько предметов сейчас ждёт переработки. */
    private int buffer;
    /** Сколько тиков осталось до готовности текущего очка. */
    private int cooldown = RESEARCH_TIME;

    /** Принимает шестерёнки, механизмы и моторы — любая «готовая» продукция кормит исследования. */
    @Override
    public boolean accept(World world, Item item) {
        boolean known = item == Item.GEAR || item == Item.MECHANISM || item == Item.ENGINE;
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
        if (--cooldown > 0) {
            return;                          // ещё считаем
        }
        cooldown = effectiveTime(world);
        buffer--;
        world.research().addPoints(1);       // выход — не соседу, а в дерево исследований
    }

    /**
     * Время на одно очко с учётом технологии {@link Tech#FAST_LAB} — открыта, значит лаборатория
     * тратит предмет вдвое быстрее. Эффект накапливается сам на себя: чем быстрее лаборатория,
     * тем быстрее приходят следующие очки и следующие технологии.
     */
    private static int effectiveTime(World world) {
        return world.research().isUnlocked(Tech.FAST_LAB) ? Math.max(1, RESEARCH_TIME / 2) : RESEARCH_TIME;
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
        return buffer + " " + cooldown;
    }

    /** Воссоздать лабораторию из сохранённого состояния. */
    static Lab load(String data) {
        String[] fields = data.split(" ");
        Lab lab = new Lab();
        lab.buffer = Integer.parseInt(fields[0]);
        lab.cooldown = Integer.parseInt(fields[1]);
        return lab;
    }
}
