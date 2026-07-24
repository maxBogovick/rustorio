package com.rustorio;

/**
 * Модуль скорости: ускоряет ОДНО конкретное здание вдвое, ОБОРАЧИВАЯ его, а не изменяя.
 *
 * <p>Апгрейды технологий (если бы они у нас были) ускоряют ВСЕ печи разом — это другая ось
 * прогрессии. Этот модуль — про другое: вставить его в ОДНУ конкретную печь, чтобы быстрее
 * работала только она, соседняя — нет. И чтобы модули складывались: печь в двух модулях быстрее,
 * чем в одном (см. {@link #tick}, оборачивание в оборачивание).
 *
 * <p>Хитрость в том, КАК устроено ускорение: {@code SpeedModule} не знает, что внутри — печь,
 * пресс или что угодно другое, и знать не должен. Он просто зовёт {@code inner.tick} ДВАЖДЫ за
 * один настоящий тик мира. Здание внутри честно не подозревает, что его поторопили.
 */
public final class SpeedModule implements Building {

    private final Building inner;

    public SpeedModule(Building inner) {
        this.inner = inner;
    }

    /** Что обёрнуто — нужно {@link Building#unwrap}, чтобы найти настоящее здание под слоями. */
    Building inner() {
        return inner;
    }

    @Override
    public void tick(World world, int x, int y) {
        inner.tick(world, x, y);
        inner.tick(world, x, y); // второй вызов — и есть всё ускорение
    }

    @Override
    public boolean accept(World world, Item item) {
        return inner.accept(world, item);
    }

    @Override
    public Appearance appearance() {
        return inner.appearance();
    }

    @Override
    public Item heldItem() {
        return inner.heldItem();
    }

    /** Один слой этой обёртки плюс всё, что уже было обёрнуто внутри — см. {@link Building#speedLevel}. */
    @Override
    public int speedLevel() {
        return 1 + inner.speedLevel();
    }

    @Override
    public BuildingType type() {
        return inner.type();
    }

    @Override
    public String save() {
        return inner.save();
    }

    @Override
    public boolean prefersDescendingTick() {
        // Обязательное делегирование: у prefersDescendingTick есть default, компилятор НЕ заставит
        // его переопределить — но забудь эту строку, и апгрейженная лента влево/вверх (урок 19)
        // снова начнёт телепортировать предмет через всю цепочку за один тик, потому что обёртка
        // молча вернула бы true вместо настоящего ответа ленты внутри.
        return inner.prefersDescendingTick();
    }

    @Override
    public Direction outputDirection() {
        // Тот же класс забывчивости, что и у prefersDescendingTick выше: у outputDirection тоже
        // есть default (null), и без этой строки апгрейженная лента/печь/туннель молча теряла бы
        // стрелку направления на экране — здание работало бы верно, а рисовалось бы как «без
        // направления».
        return inner.outputDirection();
    }

    @Override
    public Direction secondaryOutputDirection() {
        // Тот же приём, что и у outputDirection выше, — на этот раз ради апгрейженного сплиттера.
        return inner.secondaryOutputDirection();
    }
}
