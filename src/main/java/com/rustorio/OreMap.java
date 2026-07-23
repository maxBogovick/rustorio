package com.rustorio;

/**
 * Где в мире лежит руда и КАКАЯ — функция от координат клетки, а не массив.
 *
 * <p>Детерминированная: одна и та же клетка всегда даёт один и тот же ответ, в каком бы
 * порядке её ни спрашивали. Залежи — те же 12 круглых пятен, что были в исходной игре
 * (первые четыре — стартовая область в левом верхнем углу карты); восемь из них — железо,
 * остальные четыре — бронза (вторая цепочка предметов, см. {@link Item}).
 *
 * <p>Это первый кусочек «мира», возвращённый в логику: графике он нужен, чтобы нарисовать
 * карту с рудными областями. Бур ({@link Miner}) спрашивает {@link #oreAt} напрямую, вместо
 * того чтобы носить сорт руды в собственном состоянии, — руда под клеткой не меняется, значит
 * и хранить её у бура незачем.
 */
public final class OreMap {

    /** Одна круглая залежь: центр, радиус и сорт руды в ней. */
    private record Patch(int cx, int cy, int radius, Item ore) {
        boolean contains(int x, int y) {
            int dx = x - cx;
            int dy = y - cy;
            return dx * dx + dy * dy <= radius * radius;
        }
    }

    private static final Patch[] PATCHES = {
            new Patch(6, 5, 3, Item.IRON_ORE), new Patch(9, 14, 3, Item.IRON_ORE),
            new Patch(25, 6, 4, Item.IRON_ORE), new Patch(28, 15, 3, Item.IRON_ORE),
            new Patch(52, 10, 4, Item.IRON_ORE), new Patch(74, 20, 3, Item.IRON_ORE),
            new Patch(45, 34, 4, Item.IRON_ORE), new Patch(14, 44, 3, Item.IRON_ORE),
            new Patch(60, 52, 4, Item.BRONZE_ORE), new Patch(84, 42, 3, Item.BRONZE_ORE),
            new Patch(33, 56, 3, Item.BRONZE_ORE), new Patch(88, 8, 3, Item.BRONZE_ORE),
    };

    private OreMap() {
    }

    /** Есть ли под клеткой {@code (x, y)} залежь руды — любого сорта. */
    public static boolean hasOre(int x, int y) {
        return oreAt(x, y) != null;
    }

    /** Какая руда лежит под клеткой {@code (x, y)}, или {@code null}, если руды нет. */
    public static Item oreAt(int x, int y) {
        for (Patch patch : PATCHES) {
            if (patch.contains(x, y)) {
                return patch.ore();
            }
        }
        return null;
    }
}
