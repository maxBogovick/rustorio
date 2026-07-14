package com.rustorio.model;

import com.rustorio.core.Direction;

import java.util.OptionalInt;

/**
 * Игровое поле: хранение клеток + простые операции над ними.
 *
 * <p>Мир — это плоский массив {@link Tile}; клетку {@code (x, y)} находим по
 * индексу {@code y * width + x}. Здесь НЕТ симуляции (она в
 * {@link com.rustorio.sim.Systems}) и НЕТ отрисовки — только хранение и запросы:
 * поставить, убрать, прочитать, найти соседа. Это «единый источник правды» о
 * поле.
 */
public final class World {

    private final int width;
    private final int height;
    private final Tile[] tiles;

    private World(int width, int height, Tile[] tiles) {
        this.width = width;
        this.height = height;
        this.tiles = tiles;
    }

    /**
     * Создать поле и раскидать несколько круглых залежей руды.
     * Карта детерминированная — как в Rust-версии.
     */
    public static World generate(int width, int height) {
        boolean[] ore = new boolean[width * height];
        // Круглые залежи: (центр_x, центр_y, радиус).
        int[][] patches = {{6, 5, 3}, {9, 14, 3}, {25, 6, 4}, {28, 15, 3}};
        for (int[] p : patches) {
            int cx = p[0], cy = p[1], r = p[2];
            for (int y = cy - r; y <= cy + r; y++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    int ddx = x - cx, ddy = y - cy;
                    if (ddx * ddx + ddy * ddy <= r * r && inBounds(x, y, width, height)) {
                        ore[y * width + x] = true;
                    }
                }
            }
        }

        Tile[] tiles = new Tile[width * height];
        for (int i = 0; i < tiles.length; i++) {
            tiles[i] = new Tile(ore[i]);
        }
        return new World(width, height, tiles);
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** Клетка в пределах поля? */
    public boolean inBounds(int x, int y) {
        return inBounds(x, y, width, height);
    }

    private static boolean inBounds(int x, int y, int width, int height) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private int idx(int x, int y) {
        return y * width + x;
    }

    /**
     * Прочитать клетку (для отрисовки и запросов). Вызывающий обязан заранее
     * проверить {@link #inBounds}; иначе — {@link IndexOutOfBoundsException}.
     */
    public Tile tile(int x, int y) {
        return tiles[idx(x, y)];
    }

    /** Клетка по «плоскому» индексу — нужно системам симуляции. */
    public Tile tileAt(int index) {
        return tiles[index];
    }

    /** Количество клеток (размер плоского массива). */
    public int tileCount() {
        return tiles.length;
    }

    /**
     * Поставить здание. Безопасно к координатам вне поля (просто ничего не
     * делает). Два правила защиты (перенесены из Rust-версии):
     * <ul>
     *   <li>НЕ затираем здание ДРУГОГО типа — чтобы протаскивание ленты не
     *       сносило случайно бур/печь (для смены типа сперва снеси клетку ПКМ);</li>
     *   <li>НЕ пересоздаём точно такое же (тип + направление) — иначе лента
     *       сбрасывала бы предмет каждый кадр, пока держишь ЛКМ.</li>
     * </ul>
     */
    public void place(int x, int y, Building building) {
        if (!inBounds(x, y)) {
            return;
        }
        Tile tile = tiles[idx(x, y)];
        Building existing = tile.building();
        if (existing != null) {
            if (existing.getClass() != building.getClass()) {
                return; // другой тип — не трогаем
            }
            if (existing.sameKind(building)) {
                return; // ровно такое же — не пересоздаём
            }
        }
        tile.setBuilding(building);
    }

    /** Убрать здание с клетки. Безопасно к координатам вне поля. */
    public void remove(int x, int y) {
        if (!inBounds(x, y)) {
            return;
        }
        tiles[idx(x, y)].setBuilding(null);
    }

    /**
     * Индекс соседней клетки в направлении {@code dir}, если она в пределах
     * поля. Возвращает {@link OptionalInt} — «примитивный Optional» без
     * упаковки {@code Integer}, потому что это горячий путь симуляции.
     */
    public OptionalInt neighbor(int index, Direction dir) {
        int x = index % width;
        int y = index / width;
        int nx = x + dir.dx();
        int ny = y + dir.dy();
        return inBounds(nx, ny) ? OptionalInt.of(idx(nx, ny)) : OptionalInt.empty();
    }
}
