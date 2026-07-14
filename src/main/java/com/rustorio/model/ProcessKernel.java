package com.rustorio.model;

import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Общее «ядро переработки»: копит сырьё на своём складе, по рецепту машины тратит его и
 * выдаёт продукт. Печь и сборщик отличаются только набором рецептов, поэтому оба ВЛАДЕЮТ
 * таким ядром (композиция), а не дублируют логику.
 *
 * <p><b>Что изменилось в задаче C2.</b> Раньше ядро хранило ОДИН предмет сырья
 * ({@code Item input}) — и этого хватало, пока рецепт был «один предмет → один предмет».
 * Составной рецепт («пластина + 2 шестерёнки → механизм») в такую модель не влезает: сырьё
 * приезжает по одному предмету за раз, и его надо где-то КОПИТЬ. Поэтому вместо одного поля
 * появился маленький склад {@link #stock}.
 *
 * <p><b>Почему выход — очередь.</b> Рецепт может выдать несколько предметов, а наружу
 * здание отдаёт их по одному (договор {@code Building.output()} не изменился и меняться не
 * должен). Очередь и есть мостик между «рецепт выдал пачку» и «сосед забирает по штуке».
 *
 * <p>Класс намеренно пакетно-приватный: это деталь реализации модели.
 */
final class ProcessKernel {

    private final Tool machine;

    /** Склад сырья: сколько чего уже приехало. Пустая карта — склад пуст. */
    private final Map<Item, Integer> stock = new EnumMap<>(Item.class);

    /** Готовая продукция, ждущая, когда её заберут (по одному предмету за раз). */
    private final Deque<Item> ready = new ArrayDeque<>();

    /**
     * Рецепт, который машина сейчас выполняет.
     *
     * <p>Выбирается ОДИН раз в начале цикла и не меняется до его конца — иначе приезд
     * нового предмета посреди работы мог бы «переключить» машину на другой рецепт, а
     * потраченное время пропало бы.
     */
    private @Nullable Recipe active;

    private float progress;
    /** Длительность текущего цикла с учётом апгрейдов (для полоски прогресса). */
    private float cycleTime;

    /**
     * Очки исследований, накопленные завершёнными циклами и ещё не забранные.
     *
     * <p>Ядро одно на все машины, и у печи со сборщиком тут всегда ноль — их рецепты очков
     * не дают. Заводить ради лаборатории ОТДЕЛЬНОЕ ядро значило бы дублировать всю логику
     * склада, выбора рецепта и прогресса; поле-счётчик стоит дешевле.
     */
    private int science;

    ProcessKernel(Tool machine) {
        this.machine = machine;
    }

    void update(TickContext ctx) {
        if (!ready.isEmpty()) {
            return; // выход занят: сперва пусть заберут готовое
        }
        if (active == null) {
            active = chooseRecipe();
            if (active == null) {
                progress = 0f;
                return; // сырья не хватает ни на один рецепт
            }
        }
        cycleTime = active.time() / ctx.balance().speed(machine);
        progress += ctx.dt();
        if (progress >= cycleTime) {
            complete(active);
            active = null;
            progress = 0f;
        }
    }

    /**
     * Какой рецепт машина может выполнить прямо сейчас.
     *
     * <p>{@link Recipe#forMachine} уже отдаёт рецепты «сложные сначала», поэтому первый
     * подошедший — самый требовательный из возможных. Благодаря этому сборщик, в который
     * приехали и пластины, и шестерёнки, соберёт механизм, а не будет молоть пластины в
     * шестерёнки.
     */
    private @Nullable Recipe chooseRecipe() {
        for (Recipe recipe : Recipe.forMachine(machine)) {
            if (recipe.isSatisfiedBy(stock)) {
                return recipe;
            }
        }
        return null;
    }

    /** Списать сырьё со склада и выложить продукцию в очередь выхода. */
    private void complete(Recipe recipe) {
        recipe.inputs().forEach((item, needed) -> {
            int left = stock.get(item) - needed;
            if (left == 0) {
                stock.remove(item); // не держим нули: пустой склад = пустая карта
            } else {
                stock.put(item, left);
            }
        });
        recipe.outputs().forEach((item, count) -> {
            for (int i = 0; i < count; i++) {
                ready.add(item);
            }
        });
        science += recipe.science(); // у производственных рецептов — ноль
    }

    /**
     * Забрать накопленные очки исследований (и обнулить счётчик).
     *
     * <p>Именно «забрать», а не «прочитать»: очки должны попасть в игру ровно один раз.
     * Геттер, который не обнуляет, — верный способ начислить их дважды.
     */
    int drainScience() {
        int drained = science;
        science = 0;
        return drained;
    }

    /**
     * Примет ли машина этот предмет.
     *
     * <p>Правило: предмет нужен какому-то моему рецепту И его на складе ещё НЕ ХВАТАЕТ.
     * Вторая половина важна: без неё сборщик набивался бы пластинами до бесконечности,
     * а лента перед ним никогда бы не забилась — то есть игрок не увидел бы, что где-то
     * затор.
     */
    boolean canAccept(Item item) {
        for (Recipe recipe : Recipe.forMachine(machine)) {
            int needed = recipe.inputs().getOrDefault(item, 0);
            if (needed > 0 && stock.getOrDefault(item, 0) < needed) {
                return true;
            }
        }
        return false;
    }

    void accept(Item item) {
        stock.merge(item, 1, Integer::sum);
    }

    Optional<Item> output() {
        return Optional.ofNullable(ready.peek());
    }

    void removeOutput() {
        ready.poll();
    }

    // ── Чтение состояния для отрисовки ───────────────────────────────

    /**
     * Что показать на машине: приоритет у готового продукта, иначе — любое сырьё со склада.
     *
     * <p>Метод существует РАДИ РЕНДЕРА и заменяет прежний {@code input()}: показать «одно
     * сырьё» из склада на несколько позиций всё равно нельзя, а рисовать надо что-то одно.
     */
    Optional<Item> displayItem() {
        Item product = ready.peek();
        if (product != null) {
            return Optional.of(product);
        }
        return stock.keySet().stream().findFirst();
    }

    /** Есть ли на складе хоть что-то (печь по этому решает, гореть ли ей). */
    boolean hasStock() {
        return !stock.isEmpty();
    }

    /** Доля выполненной работы 0..1 для полоски прогресса. */
    float progressFraction() {
        if (active == null || cycleTime <= 0f) {
            return 0f;
        }
        return Math.min(progress / cycleTime, 1f);
    }
}
