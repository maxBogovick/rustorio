package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Item;
import com.rustorio.core.Tool;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Рецепт: что машина берёт, что выдаёт и за сколько секунд.
 *
 * <p><b>Почему входов и выходов теперь МНОЖЕСТВО, а не по одному.</b> Раньше рецепт был
 * жёстко «один предмет → один предмет». Из-за этого:
 * <ul>
 *   <li>нельзя было написать «пластина + шестерёнка → механизм» — а вся глубина жанра в
 *       том, что цепочки СЛИВАЮТСЯ, а не тянутся ниточкой;</li>
 *   <li>{@link Lab} не строилась в принципе: она ест предметы, а выдаёт очки, а не предмет.</li>
 * </ul>
 *
 * <p><b>Ключевая идея расширяемости не изменилась:</b> 500 рецептов = 500 строк ДАННЫХ в
 * {@link #ALL}, ноль нового кода и ноль новых {@code switch}. Машина ищет рецепт только
 * среди своих ({@code machine}), иначе печь могла бы «случайно» принять чужое сырьё.
 *
 * <p><b>Про поле {@code science}.</b> Лаборатория выдаёт не предмет, а очки исследований —
 * их некуда положить в {@code outputs}, потому что очки не ездят по лентам. Поэтому у
 * рецепта есть отдельное поле «сколько очков даёт цикл»; у производственных рецептов оно
 * равно нулю (для них есть короткий конструктор без него).
 *
 * <p>{@code record} — идеальный носитель чистых данных: неизменяемый, с готовыми
 * {@code equals/hashCode/toString}.
 */
public record Recipe(Tool machine, Map<Item, Integer> inputs, Map<Item, Integer> outputs,
                     float time, int science) {

    /** Копируем карты в неизменяемые: рецепт — это данные, их никто не должен править. */
    public Recipe {
        inputs = Map.copyOf(inputs);
        outputs = Map.copyOf(outputs);
    }

    /** Обычный производственный рецепт: очков исследований не даёт. */
    public Recipe(Tool machine, Map<Item, Integer> inputs, Map<Item, Integer> outputs, float time) {
        this(machine, inputs, outputs, time, 0);
    }

    /** Таблица рецептов игры. Единственное место, где они перечислены. */
    private static final List<Recipe> ALL = List.of(
            new Recipe(Tool.FURNACE,
                    Map.of(Item.IRON_ORE, 1),
                    Map.of(Item.IRON_PLATE, 1),
                    Config.SMELT_TIME),
            new Recipe(Tool.ASSEMBLER,
                    Map.of(Item.IRON_PLATE, 1),
                    Map.of(Item.GEAR, 1),
                    Config.ASSEMBLE_TIME),
            // Первый СОСТАВНОЙ рецепт — ради него затевалась задача C2.
            new Recipe(Tool.ASSEMBLER,
                    Map.of(Item.IRON_PLATE, 1, Item.GEAR, 2),
                    Map.of(Item.MECHANISM, 1),
                    Config.MECHANISM_TIME),
            // Рецепты ЛАБОРАТОРИИ: предметы тратятся, а на выходе НЕ предмет, а очки.
            // Пустая карта выходов — это не «забыли заполнить», а осмысленное «ничего
            // материального не производится».
            new Recipe(Tool.LAB,
                    Map.of(Item.GEAR, 1),
                    Map.of(),
                    Config.RESEARCH_TIME, 1),
            new Recipe(Tool.LAB,
                    Map.of(Item.MECHANISM, 1),
                    Map.of(),
                    Config.RESEARCH_TIME, 3)
    );

    /** Рецепты машины, отсортированные «сложные сначала» — см. {@link #forMachine}. */
    private static final Map<Tool, List<Recipe>> BY_MACHINE = ALL.stream()
            .collect(java.util.stream.Collectors.groupingBy(
                    Recipe::machine,
                    java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toList(),
                            list -> list.stream()
                                    .sorted(Comparator.comparingInt(
                                            (Recipe r) -> r.inputs().size()).reversed())
                                    .toList())));

    /**
     * Все рецепты машины — <b>от самого требовательного к самому простому</b>.
     *
     * <p><b>Зачем такой порядок.</b> У сборщика их два: «пластина → шестерёнка» и
     * «пластина + 2 шестерёнки → механизм». Если внутри лежат и пластина, и шестерёнки,
     * подходят ОБА, и машине надо как-то выбрать. Правило: <b>побеждает рецепт с бо́льшим
     * числом разных ингредиентов</b> (ничья — по порядку в таблице).
     *
     * <p>Так сборщик, в который приехали и пластины, и шестерёнки, соберёт механизм — а не
     * будет тупо молоть пластины в шестерёнки. И правило это ДЕТЕРМИНИРОВАНО: одинаковый
     * склад — одинаковый выбор, всегда. Без такого правила поведение машины зависело бы от
     * порядка перечисления рецептов, то есть от случайности.
     */
    public static List<Recipe> forMachine(Tool machine) {
        return BY_MACHINE.getOrDefault(machine, List.of());
    }

    /** Хватает ли {@code stock} на этот рецепт? */
    boolean isSatisfiedBy(Map<Item, Integer> stock) {
        for (Map.Entry<Item, Integer> need : inputs.entrySet()) {
            if (stock.getOrDefault(need.getKey(), 0) < need.getValue()) {
                return false;
            }
        }
        return true;
    }
}
