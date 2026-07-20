package com.rustorio;

/**
 * Бур: стоит на клетке с рудой и раз в несколько тиков добывает порцию руды, отдавая её в
 * соседний ящик.
 *
 * <p>Теперь бур — это {@link Building}: мир держит его в общей карте зданий и каждый тик зовёт
 * {@link #tick(World, int, int)}. Раньше {@code tick()} возвращал руду, а разбирался с ней мир;
 * теперь бур сам знает своё поведение — «добыл и отдал соседу», — а мир лишь предоставляет
 * маршрутизацию до соседей ({@link World#offerToNeighbor}).
 */
public final class Miner implements Building {

    /** За сколько тиков готовится одна порция руды. */
    private static final int MINE_TIME = 3;

    /** Сколько тиков осталось до следующей порции. */
    private int cooldown = MINE_TIME;

    @Override
    public void tick(World world, int x, int y) {
        if (--cooldown > 0) {
            return;                 // ещё копаем
        }
        cooldown = MINE_TIME;       // завод на следующую порцию
        world.offerToNeighbor(x, y, Item.IRON_ORE); // отдать добычу соседу-ящику (если он есть)
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.MINER); // всегда один спрайт, без числа
    }

    @Override
    public BuildingType type() {
        return BuildingType.MINER;
    }

    /** Состояние для сохранения: сколько тиков осталось до готовности порции. */
    @Override
    public String save() {
        return Integer.toString(cooldown);
    }

    /** Воссоздать бур из сохранённого состояния — в отличие от {@code new Miner()}, не «с нуля». */
    static Miner load(String data) {
        Miner miner = new Miner();
        miner.cooldown = Integer.parseInt(data);
        return miner;
    }
}
