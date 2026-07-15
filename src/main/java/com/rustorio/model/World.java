package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Игровое поле: хранение клеток + простые операции над ними.
 *
 * <p><b>Внутри — чанки, снаружи — координаты.</b> Мир состоит из кусков 32×32
 * ({@link Chunk}), которые создаются по мере надобности. Но наружу торчат только координаты:
 * {@code tile(x, y)}, {@code place}, {@code remove}, {@code forEachBuilding}. Симуляция,
 * отрисовка и ввод НЕ ЗНАЮТ, как мир хранит клетки, — и именно поэтому переход с плоского
 * массива на чанки не потребовал в них ни строчки правок.
 *
 * <p>Это и есть смысл слова «фасад»: за ним можно менять фундамент, пока форма двери
 * прежняя. Проверка того, что фасад держит, — старые тесты {@code WorldTest}, которые после
 * замены устройства мира прошли, не изменившись.
 *
 * <p>Здесь НЕТ симуляции (она в {@code sim}) и НЕТ отрисовки — только хранение и запросы.
 */
public final class World {

    private final int width;
    private final int height;

    /**
     * Куски мира: «ключ куска → кусок». Кусок появляется, только когда в него заглянули.
     *
     * <p>Порядок обхода этой карты произволен, поэтому обходить её напрямую НЕЛЬЗЯ:
     * симуляция обязана быть воспроизводимой. Обход идёт по координатной сетке кусков —
     * см. {@link #forEachBuilding}.
     */
    private final Map<Long, Chunk> chunks = new HashMap<>();

    /** Транспортные линии мира: их сборку и разборку ведёт только она. */
    private final BeltNetwork belts = new BeltNetwork(this);

    /**
     * Номер текущего тика — им клетки метятся как «уже занятые» (штамп версии, см. Tile).
     *
     * <p><b>Почему счётчик живёт ЗДЕСЬ, а не в симуляции.</b> Сначала он был полем
     * {@code Simulation} — и это оказалось ловушкой: новая симуляция над тем же миром
     * начинала счёт заново, с единицы, и натыкалась на штампы, оставшиеся от прошлой. Клетки
     * выглядели «уже занятыми», передачи молча не происходили. Вывод общий: <b>счётчик и
     * штампы, которые им метятся, обязаны жить в одном месте.</b> Штампы — на клетках, значит
     * и счётчик — в мире.
     */
    private int tick;

    private World(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Создать поле. Руда больше не «раскладывается» — она задана функцией от координат
     * ({@link OreMap}), потому что клетки теперь создаются по мере надобности.
     */
    public static World generate(int width, int height) {
        return new World(width, height);
    }

    /** Линии лент (читает симуляция и отрисовка). */
    public BeltNetwork belts() {
        return belts;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Клетка в пределах поля? */
    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    /**
     * Прочитать клетку. Вызывающий обязан заранее проверить {@link #inBounds}.
     *
     * <p>Кусок, в котором лежит клетка, создаётся здесь же, если его ещё нет.
     */
    public Tile tile(int x, int y) {
        return chunk(x >> Chunk.SHIFT, y >> Chunk.SHIFT)
                .tile(x & Chunk.MASK, y & Chunk.MASK);
    }

    private Chunk chunk(int chunkX, int chunkY) {
        return chunks.computeIfAbsent(key(chunkX, chunkY), _ -> new Chunk(chunkX, chunkY));
    }

    /** Упаковать координаты куска в один {@code long} — это и есть ключ карты. */
    private static long key(int chunkX, int chunkY) {
        return ((long) chunkX << 32) | (chunkY & 0xFFFFFFFFL);
    }

    /**
     * Обойти все здания мира.
     *
     * <p><b>Почему обход, а не «дай клетку номер N».</b> Раньше симуляция ходила по миру
     * плоским индексом ({@code tileAt(347)}). Такой индекс существует, только пока мир — один
     * массив фиксированного размера; в мире из кусков никакого «номера 347» нет. Поэтому
     * наружу торчит обход, а КАК он устроен внутри — личное дело мира.
     *
     * <p><b>Обход идёт по СЕТКЕ кусков, а не по карте.</b> Порядок обхода {@code HashMap}
     * произволен и может меняться между запусками — а симуляция обязана быть воспроизводимой.
     * Поэтому мы идём по координатам кусков, а карту только спрашиваем.
     *
     * <p>Пустые куски пропускаются целиком: на пустом мире обход почти бесплатен.
     */
    public void forEachBuilding(BuildingVisitor visitor) {
        int chunksX = (width + Chunk.SIZE - 1) >> Chunk.SHIFT;
        int chunksY = (height + Chunk.SIZE - 1) >> Chunk.SHIFT;
        for (int cy = 0; cy < chunksY; cy++) {
            for (int cx = 0; cx < chunksX; cx++) {
                Chunk chunk = chunks.get(key(cx, cy));
                if (chunk == null) {
                    continue; // в этот кусок ещё никто не заглядывал — зданий там нет
                }
                for (int ly = 0; ly < Chunk.SIZE; ly++) {
                    for (int lx = 0; lx < Chunk.SIZE; lx++) {
                        Building building = chunk.tile(lx, ly).building();
                        if (building != null) {
                            visitor.visit((cx << Chunk.SHIFT) + lx, (cy << Chunk.SHIFT) + ly,
                                    building);
                        }
                    }
                }
            }
        }
    }

    /** Сколько кусков реально создано (для тестов: пустой мир не должен их плодить). */
    int chunkCount() {
        return chunks.size();
    }

    /** Что делать с каждым зданием при обходе мира. */
    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
    }

    /**
     * Поставить здание. Безопасно к координатам вне поля. Два правила защиты:
     * <ul>
     *   <li>НЕ затираем здание ДРУГОГО типа — чтобы протаскивание ленты не сносило бур;</li>
     *   <li>НЕ пересоздаём точно такое же (тип + направление) — иначе лента сбрасывала бы
     *       предмет каждый кадр, пока держишь ЛКМ.</li>
     * </ul>
     */
    public void place(int x, int y, Building building) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tile(x, y);
        Building existing = tile.building();
        if (existing != null) {
            if (existing.getClass() != building.getClass()) {
                return; // другой тип — не трогаем
            }
            if (existing.sameKind(building)) {
                return; // ровно такое же — не пересоздаём
            }
            // Тот же тип, другое направление: старую ленту сперва честно вынимаем из её
            // линии (иначе линия осталась бы ссылаться на снесённую клетку).
            if (existing instanceof Belt oldBelt) {
                belts.onBeltRemoved(new Cell(x, y), oldBelt);
            }
        }
        tile.setBuilding(building);
        // Здание не знает своих координат, поэтому то, что зависит от МЕСТА, сообщает
        // ему мир — один раз, при постройке (см. javadoc Miner).
        if (building instanceof Miner miner) {
            miner.setOnOre(tile.hasOre());
        }
        if (building instanceof Belt newBelt) {
            belts.onBeltPlaced(new Cell(x, y), newBelt);
        }
    }

    /** Убрать здание с клетки. Безопасно к координатам вне поля. */
    public void remove(int x, int y) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tile(x, y);
        // Снимаем ленту с линии ДО того, как убрать её с клетки: разрезу нужна и сама
        // лента (её место в линии), и её соседи на своих местах.
        if (tile.building() instanceof Belt belt) {
            belts.onBeltRemoved(new Cell(x, y), belt);
        }
        tile.setBuilding(null);
    }

    /**
     * Снять ВСЕ здания с поля — подготовка к загрузке сохранения поверх текущей игры.
     *
     * <p>Снос идёт через {@link #remove}, а не «обнулить массив»: только так ленты честно
     * выходят из своих транспортных линий, и {@link BeltNetwork} остаётся пуст и
     * непротиворечив. Счётчик тиков НЕ сбрасываем: он монотонный, и продолжение отсчёта с
     * большого числа безопаснее обнуления (старые «штампы» на клетках не совпадут с новым
     * тиком и не притворятся свежими).
     */
    public void clear() {
        List<Cell> occupied = new ArrayList<>();
        forEachBuilding((x, y, building) -> occupied.add(new Cell(x, y)));
        for (Cell cell : occupied) {
            remove(cell.x(), cell.y());
        }
    }

    /**
     * Вернуть на ленту предмет из сохранения (B2). Зовётся ПОСЛЕ того, как все ленты
     * расставлены и линии собраны, иначе клетке не на что ссылаться.
     *
     * @param withinSlot позиция внутри клетки (0..{@link Config#SLOTS_PER_TILE}-1)
     */
    public void restoreBeltItem(int x, int y, int withinSlot, Item item) {
        if (!inBounds(x, y)) {
            return;
        }
        if (!(tile(x, y).building() instanceof Belt belt)) {
            throw new IllegalStateException("нет ленты под предмет на (" + x + "," + y + ")");
        }
        BeltSegment segment = belt.segment();
        if (segment == null) {
            throw new IllegalStateException("лента на (" + x + "," + y + ") не в линии");
        }
        segment.insert(item, belt.indexInSegment() * Config.SLOTS_PER_TILE + withinSlot);
    }

    /** Начать новый тик: все прошлые «застолблённые» клетки автоматически освобождаются. */
    public void beginTick() {
        tick++;
    }

    /**
     * Застолбить соседнюю клетку на этот тик: «в неё уже кладут предмет».
     *
     * @return {@code true}, если клетка досталась вам; {@code false}, если сосед успел
     *         раньше или клетки за краем поля не существует
     */
    public boolean claimNeighbor(int x, int y, Direction dir) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) && tile(nx, ny).claim(tick);
    }

    /**
     * Здание на соседней клетке в направлении {@code dir}, если оно там есть.
     *
     * <p>Возвращает {@code null}, а не {@code Optional}: это горячий путь симуляции, и
     * {@code Optional} создавал бы объект на каждое здание на каждом тике.
     */
    public @Nullable Building neighborBuilding(int x, int y, Direction dir) {
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) ? tile(nx, ny).building() : null;
    }
}
