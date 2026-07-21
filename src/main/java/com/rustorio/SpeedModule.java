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

    @Override
    public void tick(World world, int x, int y) {
        inner.tick(world, x, y);
        inner.tick(world, x, y); // второй вызов — и есть всё ускорение
    }

    @Override
    public boolean accept(Item item) {
        return inner.accept(item);
    }

    @Override
    public Appearance appearance() {
        return inner.appearance();
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
}
