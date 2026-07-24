package com.rustorio;

import java.util.ArrayList;
import java.util.List;
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

    /** Очки исследований и открытые технологии — переживает снос отдельных зданий, как и стата. */
    private final Research research = new Research();

    /**
     * Кто хочет узнавать о каждом произведённом предмете. Статистика подписана всегда — но список
     * не завязан на неё по имени: другой слушатель встанет рядом без единой правки этого класса
     * (урок 14).
     */
    private final List<ProductionListener> productionListeners = new ArrayList<>();

    public World(int width, int height) {
        this.width = width;
        this.height = height;
        // Ссылка на метод — тот же приём, что SortRule.ORE_FORWARD в уроке 12: подписка без
        // отдельного класса-обёртки. ProductionStats ничего не знает про то, что она «слушатель».
        productionListeners.add(stats::record);
    }

    /**
     * Подписать независимого слушателя на событие «предмет произведён» — в дополнение к
     * статистике, без замены её. Мир не запоминает, ЧТО это за слушатель: только то, что у него
     * есть {@link ProductionListener#onProduced}.
     */
    public void addProductionListener(ProductionListener listener) {
        productionListeners.add(listener);
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
     *
     * @return {@code true}, если бур реально поставлен (урок 15: команда должна знать, было ли
     *         что запоминать для отмены)
     */
    public boolean placeMiner(int x, int y) {
        if (inBounds(x, y) && hasOre(x, y) && isFree(x, y)) {
            buildings.put(key(x, y), new Miner());
            return true;
        }
        return false;
    }

    /**
     * Поставить ящик, если клетка в поле и свободна. Руда ящику не нужна — он стоит где угодно
     * (обычно рядом с буром, чтобы тот складывал добычу именно в него).
     */
    public boolean placeChest(int x, int y) {
        return placeIfFree(x, y, Chest::new);
    }

    /**
     * Поставить печь, отдающую готовое в {@code direction}, на свободную клетку. Руда ей под
     * ногами не нужна — сырьё приходит от соседа-бура; ставят печь обычно между буром и ящиком.
     * Принимает сырьё с ЛЮБОЙ стороны, а вот отдаёт — только в эту, чтобы не столкнуть готовый
     * продукт назад на собственный вход (см. {@link Furnace}).
     */
    public boolean placeFurnace(int x, int y, Direction direction) {
        return placeIfFree(x, y, () -> new Furnace(BuildingType.FURNACE, direction));
    }

    /**
     * Поставить пресс на свободную клетку — та же {@link Furnace}, что и печь, только другой
     * роли ({@link BuildingType#PRESS}). Какой именно рецепт (железный или бронзовый) она возьмёт
     * — решится позже, по первому предмету, который ей принесут (см. {@link Furnace#accept}).
     */
    public boolean placePress(int x, int y, Direction direction) {
        return placeIfFree(x, y, () -> new Furnace(BuildingType.PRESS, direction));
    }

    /**
     * Поставить ленту, повёрнутую в {@code direction}, на свободную клетку. Как ящику и печи,
     * руда ей не нужна — везёт, что дадут, только теперь ещё и КУДА игрок скажет (урок 19).
     *
     * <p>В отличие от прочих {@code place*}, не сводится к {@link #placeIfFree}: новый тайл нужно
     * ещё пристроить к {@link BeltSegment} соседей той же {@link Direction} — приставить головой/
     * хвостом к существующему участку, склеить два участка, если новый тайл встал МЕЖДУ ними, или
     * завести собственный сегмент из одной клетки, если соседей-лент нет вовсе (см.
     * {@link #attachToSegment}).
     */
    public boolean placeBelt(int x, int y, Direction direction) {
        if (!inBounds(x, y) || !isFree(x, y)) {
            return false;
        }
        Belt belt = new Belt(direction);
        buildings.put(key(x, y), belt);
        attachToSegment(belt, x, y, direction);
        return true;
    }

    /**
     * Найти сегменты-соседей нового ленточного тайла (позади и впереди по {@code direction}) и
     * пристроить его к ним — см. {@link #placeBelt} и {@link #restore}.
     *
     * <p>Порядок вызовов НЕ важен (в частности, для {@link SaveGame#load}, который восстанавливает
     * тайлы в произвольном порядке ключа карты): какой бы тайл ни появился вторым, именно ЕГО
     * вызов и обнаружит уже стоящего соседа и сольёт сегменты — результат одинаков независимо от
     * того, кто из двух восстановился первым.
     */
    private void attachToSegment(Belt belt, int x, int y, Direction direction) {
        Belt behind = beltNeighbor(x - direction.dx(), y - direction.dy(), direction);
        Belt ahead = beltNeighbor(x + direction.dx(), y + direction.dy(), direction);

        if (behind != null) {
            behind.segment().addHead(belt);
            if (ahead != null && ahead.segment() != behind.segment()) {
                behind.segment().mergeHead(ahead.segment()); // новый тайл встал МЕЖДУ двух сегментов
            }
        } else if (ahead != null) {
            ahead.segment().addTail(belt);
        } else {
            new BeltSegment(direction).addHead(belt); // соседей-лент нет — сегмент из одной клетки
        }
    }

    /**
     * Сосед в клетке {@code (x, y)}, если это лента ТОГО ЖЕ направления, — иначе {@code null}.
     * {@link Building#unwrap} обязателен: апгрейженная лента лежит в карте как {@link SpeedModule},
     * а не {@link Belt} (см. его javadoc).
     */
    private Belt beltNeighbor(int x, int y, Direction direction) {
        Building neighbor = buildings.get(key(x, y));
        if (neighbor == null) {
            return null;
        }
        return (Building.unwrap(neighbor) instanceof Belt belt && belt.direction() == direction)
                ? belt
                : null;
    }

    /**
     * Поставить вход подземной ленты, повёрнутый в {@code direction}, — берёт предмет от соседа
     * позади и ищет свою пару (выход) впереди по направлению (см. {@link UndergroundBelt}).
     */
    public boolean placeUndergroundIn(int x, int y, Direction direction) {
        return placeIfFree(x, y, () -> new UndergroundBelt(UndergroundBelt.Kind.IN, direction));
    }

    /** Поставить выход подземной ленты — принимает предмет только от своей пары-входа. */
    public boolean placeUndergroundOut(int x, int y, Direction direction) {
        return placeIfFree(x, y, () -> new UndergroundBelt(UndergroundBelt.Kind.OUT, direction));
    }

    /**
     * Поставить сортировщик, повёрнутый в {@code direction}, на свободную клетку — всегда с одним
     * и тем же правилом {@link SortRule#ORE_FORWARD}. Захочешь другое поведение для сортировщиков
     * — меняй правило ЗДЕСЬ, в одной строке; класс {@link Splitter} трогать не придётся (см. урок
     * 12). Направление, в отличие от правила, СЛУШАЕТ игрока — как у ленты, печи и туннеля.
     */
    public boolean placeSplitter(int x, int y, Direction direction) {
        return placeIfFree(x, y, () -> new Splitter(SortRule.ORE_FORWARD, direction));
    }

    /** Поставить лабораторию на свободную клетку — кормит {@link #research()} готовыми деталями. */
    public boolean placeLab(int x, int y) {
        return placeIfFree(x, y, Lab::new);
    }

    /**
     * Общее тело почти всех {@code place*}: если клетка в поле и свободна — построить то, что
     * даст {@code factory}. Четыре из пяти зданий (все, кроме бура) этим и ограничиваются —
     * различаются только тем, ЧТО именно строить (урок 13).
     *
     * @return {@code true}, если здание реально поставлено
     */
    private boolean placeIfFree(int x, int y, Supplier<Building> factory) {
        if (inBounds(x, y) && isFree(x, y)) {
            buildings.put(key(x, y), factory.get());
            return true;
        }
        return false;
    }

    /**
     * Поставить здание ВЫБРАННОГО сорта лицом в {@code direction} — одна дверь на все постройки
     * для ввода. Каждый сорт ведёт к своему правилу (у бура — «на руде»), а игрок лишь говорит,
     * что именно строит; направление важно ленте, печи/прессу, туннелю и сортировщику — бур,
     * ящик и лаборатория его игнорируют (принимают/отдают одинаково со всех сторон).
     *
     * @return {@code true}, если здание реально поставлено (клавиатура/мышь этого не проверяют —
     *         это забота мира)
     */
    public boolean place(BuildingType type, int x, int y, Direction direction) {
        return switch (type) {
            case MINER -> placeMiner(x, y);
            case CHEST -> placeChest(x, y);
            case FURNACE -> placeFurnace(x, y, direction);
            case BELT -> placeBelt(x, y, direction);
            case SPLITTER -> placeSplitter(x, y, direction);
            case PRESS -> placePress(x, y, direction);
            case UNDERGROUND_IN -> placeUndergroundIn(x, y, direction);
            case UNDERGROUND_OUT -> placeUndergroundOut(x, y, direction);
            case LAB -> placeLab(x, y);
        };
    }

    /** То же самое с направлением по умолчанию — удобно для зданий, которым оно безразлично. */
    public boolean place(BuildingType type, int x, int y) {
        return place(type, x, y, Direction.RIGHT);
    }

    /**
     * Снести здание в клетке и вернуть его — или {@code null}, если клетка была пуста.
     *
     * <p>Раньше метод ничего не возвращал: снёс и снёс. Теперь возвращает ИМЕННО СНЕСЁННОЕ
     * здание — не новое такое же, а тот же объект, с тем же внутренним состоянием (сколько было
     * руды в печи, что лежало в ящике). Это и нужно отмене (урок 15, {@link RemoveAction}):
     * вернуть на место можно только то, что реально стояло, а не его копию с нуля.
     */
    public Building removeBuilding(int x, int y) {
        Building removed = buildings.remove(key(x, y));
        if (removed != null && Building.unwrap(removed) instanceof Belt belt) {
            belt.segment().remove(belt); // сжимает/режет BeltSegment соседей вокруг дыры
        }
        return removed;
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
        if (Building.unwrap(building) instanceof Belt belt) {
            attachToSegment(belt, x, y, belt.direction());
        }
    }

    /** Снести все здания и обнулить статистику/исследования — мир готов принять сохранение. */
    void clear() {
        buildings.clear();
        stats.clear();
        research.clear();
    }

    /** Восстановить состояние исследований из сохранённой строки — см. {@link Research#restore}. */
    void restoreResearch(String data) {
        research.restore(data);
    }

    /**
     * Один шаг мира: каждое здание проживает свой тик, зная, где оно стоит.
     *
     * <p>Раньше (урок 11) всё тикало «с конца» — от больших координат к меньшим — ради ленты,
     * которая толкает предмет только ВПРАВО: так сосед справа всегда успевал сходить раньше, чем
     * до него дотягивался толчок слева, и предмет не проскакивал сразу через несколько лент за
     * один тик. Урок 19 дал ленте направление НА ВЫБОР — и внезапно оказалось, что для ленты
     * ВЛЕВО или ВВЕРХ тот же самый порядок стал НЕПРАВИЛЬНЫМ: он защищает соседа, толкающего к
     * бОльшим координатам, а не к меньшим (проверено — цепочка лент влево телепортировала предмет
     * через всю себя за один тик, а не по клетке за раз).
     *
     * <p>Единого порядка на все случаи не существует: два прохода. Сначала — все, кому подходит
     * обход «с конца» ({@link Building#prefersDescendingTick()} вернул {@code true}: и здания без
     * направления, и лента вправо/вниз). Потом — оставшиеся, в ОБРАТНОМ порядке (лента влево/
     * вверх). Каждое здание само говорит, какой обход ему нужен, — мир не разбирает, лента это
     * или нет (никакого {@code instanceof}, та же дисциплина, что и в уроке 6).
     */
    public void tick() {
        for (Map.Entry<Long, Building> entry : buildings.descendingMap().entrySet()) {
            if (entry.getValue().prefersDescendingTick()) {
                tickEntry(entry);
            }
        }
        for (Map.Entry<Long, Building> entry : buildings.entrySet()) {
            if (!entry.getValue().prefersDescendingTick()) {
                tickEntry(entry);
            }
        }
    }

    private void tickEntry(Map.Entry<Long, Building> entry) {
        long k = entry.getKey();
        // Ключ карты — упакованные координаты; распаковываем обратно в (x, y) для здания.
        entry.getValue().tick(this, keyX(k), keyY(k));
    }

    /** Сообщить всем {@link ProductionListener}, что предмет произведён, — один раз на одну порцию. */
    public void notifyProduced(Item item) {
        for (ProductionListener listener : productionListeners) {
            listener.onProduced(item);
        }
    }

    /**
     * Попытаться отдать предмет соседу (сверху/снизу/слева/справа) — БЕЗ уведомления слушателей.
     *
     * <p>И {@link Miner}, и {@link Furnace} держат готовый предмет у себя, пока сосед не
     * освободится (как лента держит груз), и пробуют отдать его КАЖДЫЙ тик, пока не получится.
     * Поэтому «произведено» ({@link #notifyProduced}, один раз — в момент готовности) и
     * «доставлено» (этот метод, сколько угодно повторных попыток) — два РАЗНЫХ события. Если бы
     * каждая попытка доставки считалась в {@link ProductionStats} заново, статистика приписывала
     * бы заводу продукцию, которой не было, — та же ловушка с двойным счётом, что уже решали для
     * {@link #offerForward} (урок 11).
     */
    public boolean tryDeliverToNeighbor(int x, int y, Item item) {
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
        return building != null && building.accept(this, item);
    }

    /**
     * Отдать предмет ОДНОМУ конкретному соседу — адресно, не всем четырём подряд, как
     * {@link #tryDeliverToNeighbor}. Лента ({@link Belt}) везёт предмет В ОДНОМ направлении, а не
     * «куда получится», поэтому ей нужен прицельный, а не разбросанный `offer`.
     *
     * <p>Как и {@code tryDeliverToNeighbor}, этот метод НЕ пишет в {@link #stats}: лента
     * ничего не производит — она переносит то, что уже произвели и посчитали раньше (бур или
     * печь). Считать один и тот же предмет заново на каждом шаге по ленте приписало бы заводу
     * добычу, которой не было.
     */
    public boolean offerForward(int x, int y, Item item) {
        return offer(x, y, item);
    }

    /**
     * Заглянуть в клетку, НЕ предлагая ей ничего и ничего не потребляя, — в отличие от
     * {@link #offer}. Нужно {@link UndergroundBelt}: вход ищет свою пару впереди по направлению
     * и должен ОПОЗНАТЬ её (сорт, направление, свободна ли), прежде чем решить, перекладывать
     * груз или нет, — а не просто спросить «примешь?», как это делает обычная передача соседу.
     */
    Building peek(int x, int y) {
        return buildings.get(key(x, y));
    }

    /** Статистика производства — сколько всего добыто/выплавлено с начала игры (HUD её читает). */
    public ProductionStats stats() {
        return stats;
    }

    /** Дерево исследований — очки и открытые технологии (HUD их читает, {@link Lab} пополняет). */
    public Research research() {
        return research;
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
