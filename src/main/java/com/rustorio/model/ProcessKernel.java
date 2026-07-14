package com.rustorio.model;

import com.rustorio.core.Item;
import com.rustorio.core.Tool;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Общее «ядро переработки»: берёт сырьё, копит прогресс и по рецепту машины
 * выдаёт продукт. Печь и сборщик отличаются только своим набором рецептов,
 * поэтому оба ВЛАДЕЮТ таким ядром (композиция), а не дублируют логику.
 *
 * <p>Почему композиция, а не общий базовый класс: наследование затянуло бы в
 * sealed-иерархию {@link Building} лишний абстрактный тип. Композиция оставляет
 * список {@code permits} чистым (5 конкретных зданий) и следует правилу
 * «предпочитай композицию наследованию» (Effective Java, Item 18).
 *
 * <p>Класс намеренно пакетно-приватный: это деталь реализации модели, наружу
 * его знать не нужно.
 */
final class ProcessKernel {

    private final Tool machine;
    /** Загруженное сырьё (или {@code null}). */
    private @Nullable Item input;
    private float progress;
    /** Готовый продукт (или {@code null} — выход пуст). */
    private @Nullable Item output;

    ProcessKernel(Tool machine) {
        this.machine = machine;
    }

    void update(float dt) {
        // Перерабатываем, только пока выход свободен и есть сырьё.
        if (output == null && input != null) {
            Recipe recipe = Recipe.find(machine, input).orElseThrow(() ->
                    new IllegalStateException(
                            "canAccept должен был гарантировать рецепт для " + input));
            progress += dt;
            if (progress >= recipe.time()) {
                output = recipe.output();
                input = null;
                progress = 0f;
            }
        }
    }

    boolean canAccept(Item item) {
        // Свободен ко входу и для этого сырья у машины есть рецепт.
        return input == null && Recipe.find(machine, item).isPresent();
    }

    void accept(Item item) {
        this.input = item;
    }

    Optional<Item> output() {
        return Optional.ofNullable(output);
    }

    void removeOutput() {
        this.output = null;
    }

    // ── Чтение состояния для отрисовки ───────────────────────────────
    Optional<Item> input() {
        return Optional.ofNullable(input);
    }

    /**
     * Доля выполненной работы 0..1 для полоски прогресса.
     *
     * <p>Знаменатель берётся из текущего рецепта, поэтому полоска у сборщика и
     * печи правильная у каждого своя (в Rust-версии для обоих ошибочно
     * использовалось {@code SMELT_TIME}).
     */
    float progressFraction() {
        if (input == null) {
            return 0f;
        }
        return Recipe.find(machine, input)
                .map(r -> Math.min(progress / r.time(), 1f))
                .orElse(0f);
    }
}
