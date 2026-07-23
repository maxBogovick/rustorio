package com.rustorio;

/**
 * Правило сортировки: куда направить предмет — вперёд ({@code true}) или вниз ({@code false}).
 *
 * <p>Отдельный тип от {@link Splitter} — намеренно. Правило может меняться независимо от
 * здания: другой сортировщик на карте мог бы захотеть сортировать иначе, и заводить под каждое
 * правило новый класс здания (в закрытой {@code sealed}-иерархии {@link Building} это вообще
 * невозможно без правки {@code permits}) — явный перебор. Вместо этого правило — просто значение,
 * которое сортировщику дают при постройке.
 */
@FunctionalInterface
public interface SortRule {

    /** Куда положить {@code item}: {@code true} — вперёд, {@code false} — вниз. */
    boolean forward(Item item);

    /** Правило по умолчанию: руда едет вперёд, всё остальное (пластины) — вниз. */
    SortRule ORE_FORWARD = item -> item == Item.IRON_ORE || item == Item.BRONZE_ORE;
}
