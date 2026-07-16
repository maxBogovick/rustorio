package com.rustorio.model;

import com.rustorio.core.Direction;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Игровое поле: хранение клеток + операции над ними.
 *
 * <p><b>Внутри — чанки, снаружи — координаты.</b> Мир состоит из кусков 32×32
 * ({@link Chunk}), которые создаются по мере надобности. Но наружу торчат только
 * координаты: {@code tile(x, y)}, {@code place}, {@code remove}, {@code forEachBuilding}.
 * Симуляция, отрисовка и ввод НЕ ЗНАЮТ, как мир хранит клетки, — поэтому устройство
 * хранения можно менять, не трогая их.
 *
 * <p>Это и есть смысл слова «фасад»: за ним можно менять фундамент, пока форма
 * двери прежняя.
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
     * симуляция обязана быть воспроизводимой. Обход зданий идёт по координатной сетке
     * кусков — см. {@link #forEachBuilding}.
     */
    private final Map<Long, Chunk> chunks = new HashMap<>();

    /** Транспортные линии мира: их сборку и разборку ведёт только она. */
    private final BeltNetwork belts = new BeltNetwork(this);

    /**
     * Номер текущего тика — им клетки метятся как «уже занятые» (штамп версии, см. Tile).
     * Счётчик живёт ЗДЕСЬ, вместе со штампами на клетках: новая симуляция над тем же
     * миром не должна начинать счёт заново и натыкаться на старые штампы.
     */
    private int tick;

    private World(int width, int height) {
        this.width = width;
        this.height = height;
    }

    /**
     * Создать поле. Руда не «раскладывается» при генерации — она задана функцией от
     * координат ({@link OreMap}), потому что клетки создаются по мере надобности.
     */
    public static World generate(int width, int height) {
        return new World(width, height);
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

    /** Сколько кусков реально создано (для тестов: пустой мир не должен их плодить). */
    int chunkCount() {
        return chunks.size();
    }

    /** Транспортные линии мира: сборка, разборка, движение предметов по ним. */
    public BeltNetwork belts() {
        return belts;
    }

    /**
     * Поставить здание. Безопасно к координатам вне поля. Два правила защиты:
     * <ul>
     *   <li>НЕ затираем здание ДРУГОГО типа — чтобы протаскивание линии не сносило
     *       соседей;</li>
     *   <li>НЕ пересоздаём точно такое же (тип + направление) — иначе здание теряло
     *       бы своё состояние каждый кадр, пока держишь ЛКМ.</li>
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
        // Снимаем ленту с линии ДО того, как убрать её с клетки: линии нужна и сама
        // лента (её место в линии), пока клетка ещё на месте.
        if (tile.building() instanceof Belt belt) {
            belts.onBeltRemoved(new Cell(x, y), belt);
        }
        tile.setBuilding(null);
    }

    /**
     * Обойти все здания мира и дать каждое {@code visitor}. Порядок — по координатной
     * сетке кусков (не по {@code chunks.values()}: порядок карты произволен, а симуляция
     * обязана быть воспроизводимой).
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

    /**
     * «Что сделать с каждым зданием» — один метод, поэтому можно передать лямбдой.
     * {@code @FunctionalInterface} — обещание компилятору: здесь всегда РОВНО один
     * абстрактный метод; попытка добавить второй сломает сборку, а не лямбды по всему коду.
     */
    @FunctionalInterface
    public interface BuildingVisitor {
        void visit(int x, int y, Building building);
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
