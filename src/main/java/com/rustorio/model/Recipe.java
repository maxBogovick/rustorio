package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;

import java.util.List;
import java.util.Optional;

/**
 * Рецепт превращения одного предмета в другой конкретной машиной.
 *
 * <p>{@code record} — идеальный носитель «чистых данных»: неизменяемый,
 * с автоматическими {@code equals/hashCode/toString}, без единой строки
 * шаблонного кода (Java 16+). Это ровно то, чем был {@code struct Recipe}
 * в Rust.
 *
 * <p>Ключевая идея расширяемости: <b>500 рецептов = 500 строк ДАННЫХ</b> в
 * {@link #ALL}, ноль нового кода и ноль новых {@code switch}. Машина ищет
 * рецепт только среди своих ({@code machine}), иначе печь могла бы «случайно»
 * принять чужое сырьё.
 */
public record Recipe(Tool machine, Item input, Item output, float time) {

    /** Таблица рецептов игры. Единственное место, где они перечислены. */
    private static final List<Recipe> ALL = List.of(
            new Recipe(Tool.FURNACE, Item.IRON_ORE, Item.IRON_PLATE, Config.SMELT_TIME),
            new Recipe(Tool.ASSEMBLER, Item.IRON_PLATE, Item.GEAR, Config.ASSEMBLE_TIME)
    );

    /**
     * Найти рецепт для конкретной машины и её сырья.
     *
     * @return рецепт, если такая машина умеет перерабатывать этот предмет;
     *         иначе {@link Optional#empty()}
     */
    public static Optional<Recipe> find(Tool machine, Item input) {
        return ALL.stream()
                .filter(r -> r.machine == machine && r.input == input)
                .findFirst();
    }
}
