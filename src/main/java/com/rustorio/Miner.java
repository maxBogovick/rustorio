package com.rustorio;

/**
 * Бур: стоит на клетке с рудой и раз в несколько тиков добывает порцию руды, отдавая её в
 * соседний ящик.
 *
 * <p>Теперь бур — это {@link Building}: мир держит его в общей карте зданий и каждый тик зовёт
 * {@link #tick(World, int, int)}. Раньше {@code tick()} возвращал руду, а разбирался с ней мир;
 * теперь бур сам знает своё поведение — «добыл и отдал соседу», — а мир лишь предоставляет
 * маршрутизацию до соседей ({@link World#tryDeliverToNeighbor}).
 *
 * <p>Какую именно руду добывать, бур не хранит и не решает — спрашивает {@link OreMap#oreAt} по
 * СВОИМ координатам каждый раз заново: руда под клеткой не меняется, значит хранить её отдельным
 * полем незачем (та же логика, что развела {@link Recipe} от печи).
 *
 * <p>Добытую руду бур ДЕРЖИТ у себя, пока её не заберёт сосед, — как груз на ленте ({@link Belt}).
 * Раньше бур копал не глядя на соседа: если отдать было некому, руда просто пропадала, а бур тут
 * же начинал копать следующую порцию — счётчик «добыто» рос бесконечно, а видимого толку не было
 * ни капли. Теперь, пока в руках есть порция, новая добыча НЕ начинается: бур ждёт, как и всё
 * остальное на конвейере.
 */
public final class Miner implements Building {

    /** За сколько тиков готовится одна порция руды. */
    private static final int MINE_TIME = 3;

    /** Сколько тиков осталось до следующей порции. */
    private int cooldown = MINE_TIME;

    /** Добытая, но ещё не переданная соседу руда — или {@code null}, если руки свободны. */
    private Item held;

    @Override
    public void tick(World world, int x, int y) {
        if (held == null) {
            if (--cooldown > 0) {
                return;                      // ещё копаем
            }
            cooldown = effectiveTime(world); // завод на следующую порцию
            held = OreMap.oreAt(x, y);
            world.notifyProduced(held);      // засчитать добычу РОВНО ОДИН РАЗ — в момент добычи
        }
        if (world.tryDeliverToNeighbor(x, y, held)) {
            held = null;                     // сосед забрал — руки свободны, можно копать дальше
        }
        // сосед занят или его нет — руда ждёт в руках, следующая порция не начинается
    }

    /**
     * Время добычи одной порции с учётом технологии {@link Tech#FAST_MINING} — открыта, значит
     * бур вдвое быстрее.
     */
    private static int effectiveTime(World world) {
        return world.research().isUnlocked(Tech.FAST_MINING) ? Math.max(1, MINE_TIME / 2) : MINE_TIME;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.MINER); // всегда один спрайт, без числа
    }

    /** Руда в руках — то, что рисуется поверх бура как груз, пока сосед не забрал. */
    @Override
    public Item heldItem() {
        return held;
    }

    @Override
    public BuildingType type() {
        return BuildingType.MINER;
    }

    /** Состояние для сохранения: таймер и руда в руках (или {@code "-"}, если руки свободны). */
    @Override
    public String save() {
        return cooldown + " " + (held == null ? "-" : held.name());
    }

    /** Воссоздать бур из сохранённого состояния — в отличие от {@code new Miner()}, не «с нуля». */
    static Miner load(String data) {
        String[] parts = data.split(" ", 2);
        Miner miner = new Miner();
        miner.cooldown = Integer.parseInt(parts[0]);
        if (!parts[1].equals("-")) {
            miner.held = Item.valueOf(parts[1]);
        }
        return miner;
    }
}
