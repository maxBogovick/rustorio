package com.rustorio.model;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;

/**
 * Что здание готово отдать наружу и в какую сторону.
 *
 * <p>Аналог кортежа {@code (Item, Direction)} из Rust-версии. В Java кортежей
 * нет, зато есть {@code record} — он даёт этим двум полям осмысленные имена
 * ({@code item}, {@code direction}) и неизменяемость, что читается лучше
 * безымянной пары.
 */
public record Handoff(Item item, Direction direction) {
}
