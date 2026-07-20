package com.rustorio;

import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Supplier;

/**
 * Игровое поле: где лежит руда (через {@link OreMap}) и какие здания на нём стоят.
 *
 * <p>Раньше под каждый сорт здания была своя карта и своя обвязка. Теперь у зданий есть общий
 * тип {@link Building}, и мир держит их ВСЕ в ОДНОЙ карте «клетка → здание»: один цикл тика,
 * один обход для отрисовки, одна проверка «свободно». Дублирование ушло — вместе с ним ушли
 * `hasMiner`/`hasChest` и парные `forEach`.
 *
 * <p>Различие сортов осталось лишь там, где оно РЕАЛЬНО есть: правила постройки (буру нужна
 * руда, ящику — нет) — это две разные {@code place}-двери в один и тот же склад зданий.
 */
public final class World {

    private final int width;
    private final int height;

    /**
     * Клетка (упакованные координаты) → здание на ней.
     *
     * <p>{@link TreeMap}, а не обычная {@code HashMap}, — сознательно, ради ленты (урок 11):
     * {@link #tick()} обходит здания в порядке ключа, и это даёт предсказуемый порядок по {@code
     * x}. Почему это важно — см. {@link #tick()}.
     */
    private final NavigableMap<Long, Building> buildings = new TreeMap<>();

    /** Сколько всего произведено предметов с начала игры — переживает снос отдельных зданий. */
    private final ProductionStats stats = new ProductionStats();

    public World(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /** Есть ли под клеткой руда — бур ставится только на неё. */
    public boolean hasOre(int x, int y) {
        return OreMap.hasOre(x, y);
    }

    /** Свободна ли клетка — на ней не должно быть НИКАКОГО здания. */
    public boolean isFree(int x, int y) {
        return !buildings.containsKey(key(x, y));
    }

    /**
     * Поставить бур, если клетка в поле, на руде и свободна.
     *
     * <p>Правило «только на руде» живёт ЗДЕСЬ, а не в вводе: где можно строить — это про мир,
     * а не про то, какой кнопкой кликнули. Тихо ничего не делаем, если поставить нельзя.
     */
    public void placeMiner(int x, int y) {
        if (inBounds(x, y) && hasOre(x, y) && isFree(x, y)) {
            buildings.put(key(x, y), new Miner());
        }
    }

    /**
     * Поставить ящик, если клетка в поле и свободна. Руда ящику не нужна — он стоит где угодно
     * (обычно рядом с буром, чтобы тот складывал добычу именно в него).
     */
    public void placeChest(int x, int y) {
        placeIfFree(x, y, Chest::new);
    }

    /**
     * Поставить печь на свободную клетку. Руда ей под ногами не нужна — сырьё приходит от
     * соседа-бура; ставят печь обычно между буром и ящиком.
     */
    public void placeFurnace(int x, int y) {
        placeIfFree(x, y, Furnace::new);
    }

    /**
     * Поставить ленту на свободную клетку. Как ящику и печи, руда ей не нужна — везёт, что дадут.
     */
    public void placeBelt(int x, int y) {
        placeIfFree(x, y, Belt::new);
    }

    /**
     * Поставить сортировщик на свободную клетку — всегда с одним и тем же правилом
     * {@link SortRule#ORE_FORWARD}. Захочешь другое поведение для сортировщиков — меняй правило
     * ЗДЕСЬ, в одной строке; класс {@link Splitter} трогать не придётся (см. урок 12).
     */
    public void placeSplitter(int x, int y) {
        placeIfFree(x, y, () -> new Splitter(SortRule.ORE_FORWARD));
    }

    /**
     * Общее тело почти всех {@code place*}: если клетка в поле и свободна — построить то, что
     * даст {@code factory}. Четыре из пяти зданий (все, кроме бура) этим и ограничиваются —
     * различаются только тем, ЧТО именно строить (урок 13).
     */
    private void placeIfFree(int x, int y, Supplier<Building> factory) {
        if (inBounds(x, y) && isFree(x, y)) {
            buildings.put(key(x, y), factory.get());
        }
    }

    /**
     * Поставить здание ВЫБРАННОГО сорта — одна дверь на все постройки для ввода. Каждый сорт
     * ведёт к своему правилу (у бура — «на руде»), а игрок лишь говорит, что именно строит.
     */
    public void place(BuildingType type, int x, int y) {
        switch (type) {
            case MINER -> placeMiner(x, y);
            case CHEST -> placeChest(x, y);
            case FURNACE -> placeFurnace(x, y);
            case BELT -> placeBelt(x, y);
            case SPLITTER -> placeSplitter(x, y);
        }
    }

    /** Снести здание в клетке (если оно там есть). Позволяет игроку исправлять ошибки. */
    public void removeBuilding(int x, int y) {
        buildings.remove(key(x, y));
    }

    /**
     * Поставить УЖЕ ГОТОВОЕ здание в клетку — без единой проверки из {@code place*}.
     *
     * <p>Разница с {@link #place}: тот проверяет, МОЖНО ли строить (руда под ногами? клетка
     * свободна?), потому что это решение игрока, которое может быть ошибочным. Загрузка
     * восстанавливает здание, которое там УЖЕ стояло в момент сохранения, — эти правила уже были
     * проверены тогда. Раз-проверять сохранённое прошлое незачем; доверяем файлу.
     */
    void restore(int x, int y, Building building) {
        buildings.put(key(x, y), building);
    }

    /** Снести все здания и обнулить статистику — мир готов принять загруженное сохранение. */
    void clear() {
        buildings.clear();
        stats.clear();
    }

    /**
     * Один шаг мира: каждое здание проживает свой тик, зная, где оно стоит.
     *
     * <p>Обходим с КОНЦА — от больших {@code x} к меньшим ({@link NavigableMap#descendingMap()});
     * ключ упакован так, что старшие биты — это {@code x} (см. {@link #key}), поэтому порядок по
     * ключу — это порядок по {@code x}. Лента толкает предмет ВПРАВО ({@link Belt#tick}); если
     * тикать слева направо, то к моменту, когда очередь дойдёт до ленты слева, лента справа от неё
     * ещё не «ходила» в этом кадре — и предмет может проскочить сразу через несколько лент за один
     * тик. Тикая справа налево, мы всегда обрабатываем соседа СПРАВА раньше, чем до него дотянется
     * толчок слева: он либо уже освободился, либо ещё занят, но точно не сдвинется дважды за кадр.
     */
    public void tick() {
        for (Map.Entry<Long, Building> entry : buildings.descendingMap().entrySet()) {
            long k = entry.getKey();
            // Ключ карты — упакованные координаты; распаковываем обратно в (x, y) для здания.
            entry.getValue().tick(this, keyX(k), keyY(k));
        }
    }

    /**
     * Отдать предмет первому соседу (сверху/снизу/слева/справа), который его примет.
     *
     * <p>Это единственное место, через которое проходит КАЖДЫЙ произведённый предмет — и бур,
     * и печь зовут именно этот метод, отдавая добычу. Поэтому здесь же, в одной точке, считаем
     * статистику: раз предмет добрался до {@code offerToNeighbor}, значит здание его произвело,
     * независимо от того, найдётся ли сосед, готовый принять. Ни {@link Miner}, ни {@link Furnace}
     * для этого трогать не пришлось.
     *
     * @return {@code true}, если сосед принял; {@code false} — принять было некому (предмет
     *         пропадает: буру всё ещё некуда деть руду)
     */
    public boolean offerToNeighbor(int x, int y, Item item) {
        stats.record(item);
        return offer(x + 1, y, item)
                || offer(x - 1, y, item)
                || offer(x, y + 1, item)
                || offer(x, y - 1, item);
    }

    /**
     * Отдать предмет зданию в клетке, если оно способно принять.
     *
     * <p>Мир больше не перебирает сорта зданий через {@code instanceof} — он просто спрашивает у
     * здания {@link Building#accept}, а здание отвечает за себя само (ящик берёт всё, печь — только
     * руду, бур не берёт ничего). Новое принимающее здание не тронет этот метод: достаточно, чтобы
     * оно переопределило {@code accept}.
     */
    private boolean offer(int x, int y, Item item) {
        Building building = buildings.get(key(x, y));
        return building != null && building.accept(item);
    }

    /**
     * Отдать предмет ОДНОМУ конкретному соседу — адресно, не всем четырём подряд, как
     * {@link #offerToNeighbor}. Лента ({@link Belt}) везёт предмет В ОДНОМ направлении, а не
     * «куда получится», поэтому ей нужен прицельный, а не разбросанный `offer`.
     *
     * <p>И, в отличие от {@code offerToNeighbor}, этот метод НЕ пишет в {@link #stats}: лента
     * ничего не производит — она переносит то, что уже произвели и посчитали раньше (бур или
     * печь). Считать один и тот же предмет заново на каждом шаге по ленте приписало бы заводу
     * добычу, которой не было.
     */
    public boolean offerForward(int x, int y, Item item) {
        return offer(x, y, item);
    }

    /** Статистика производства — сколько всего добыто/выплавлено с начала игры (HUD её читает). */
    public ProductionStats stats() {
        return stats;
    }

    /** Обойти все здания с их координатами — нужно отрисовке. */
    public void forEachBuilding(BuildingVisitor visitor) {
        for (Map.Entry<Long, Building> entry : buildings.entrySet()) {
            long k = entry.getKey();
            visitor.visit(keyX(k), keyY(k), entry.getValue());
        }
    }

    /** Что делать с каждой клеткой-зданием при обходе. */
    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
    }

    // --- Ключ карты: две координаты, упакованные в один long ---------------------------------
    //
    // Ключом карты должен быть ОДИН объект. Хранить пару (x, y) можно было бы отдельным record'ом
    // или вложенными картами, но проще и дешевле склеить две 32-битные координаты в одно 64-битное
    // число: старшая половина — x, младшая — y. Тогда ключ — обычный long (точнее, один Long), и
    // карта работает как со всяким числом.
    //
    //   long-ключ (64 бита):   [ 32 бита x | 32 бита y ]
    //                            старшие      младшие

    /** Упаковать координаты клетки в ключ: x — в старшие 32 бита, y — в младшие. */
    private static long key(int x, int y) {
        // (long) x << 32  — двигаем x в старшую половину;  y & 0xFFFFFFFFL — берём младшие 32 бита
        // y как есть (маска гасит расширение знака, чтобы отрицательный y не «залил» биты x).
        return ((long) x << 32) | (y & 0xFFFFFFFFL);
    }

    /** Достать x из ключа: сдвигаем старшую половину вниз, в младшие биты, и обрезаем до int. */
    private static int keyX(long key) {
        return (int) (key >> 32);
    }

    /** Достать y из ключа: приведение к int само отбрасывает старшие 32 бита — остаются младшие. */
    private static int keyY(long key) {
        return (int) key;
    }
}
