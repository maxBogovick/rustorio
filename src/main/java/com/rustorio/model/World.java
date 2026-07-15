package com.rustorio.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Игровое поле: хранение клеток + простые операции над ними.
 *
 * <p><b>Внутри — чанки, снаружи — координаты.</b> Мир состоит из кусков 32×32
 * ({@link Chunk}), которые создаются по мере надобности. Но наружу торчат только
 * координаты: {@code tile(x, y)}, {@code inBounds(x, y)}. Отрисовка и ввод НЕ ЗНАЮТ,
 * как мир хранит клетки, — поэтому устройство хранения можно менять, не трогая их.
 *
 * <p>Это и есть смысл слова «фасад»: за ним можно менять фундамент, пока форма
 * двери прежняя.
 *
 * <p>Здесь НЕТ отрисовки и НЕТ ввода — только хранение и запросы. По ходу курса
 * мир научится ставить и сносить здания ({@code place}/{@code remove}) и обходить
 * их для симуляции.
 */
public final class World {

    private final int width;
    private final int height;

    /**
     * Куски мира: «ключ куска → кусок». Кусок появляется, только когда в него заглянули.
     *
     * <p>Порядок обхода этой карты произволен, поэтому обходить её напрямую НЕЛЬЗЯ:
     * симуляция обязана быть воспроизводимой. Когда появится обход зданий, он пойдёт
     * по координатной сетке кусков, а карту будет только спрашивать.
     */
    private final Map<Long, Chunk> chunks = new HashMap<>();

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
}
