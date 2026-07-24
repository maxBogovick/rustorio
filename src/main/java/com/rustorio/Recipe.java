package com.rustorio;

import java.util.List;

/**
 * Рецепт печи: что на входе, что на выходе, за сколько тиков — и какой пункт меню постройки
 * ему соответствует.
 *
 * <p>Раньше вход/выход/время были ПОВЕДЕНИЕМ, зашитым прямо в тело {@code Furnace.accept}/
 * {@code tick}: «беру только IRON_ORE», «делаю IRON_PLATE», «5 тиков». Теперь это ДАННЫЕ — новый
 * рецепт добавляется КОНСТАНТОЙ, без единой правки {@link Furnace}. Класс остаётся закрытым для
 * изменений, а набор рецептов — открытым для расширения (принцип Open/Closed, урок 16).
 *
 * <p>Поле {@code type} держит связку «рецепт ⟷ пункт меню» в ОДНОМ месте: печь и пресс — один
 * Java-класс с разными рецептами, но игроку они видны как разные постройки, и {@link
 * Building#type()} обязан отвечать правильно для сохранения. Хранить {@code BuildingType}
 * ВТОРЫМ отдельным параметром при постройке было бы опаснее — его можно было бы случайно
 * рассинхронизировать с рецептом.
 *
 * <p>Бронзовая цепочка (см. {@link Item#BRONZE_ORE}) добавлена ровно так, как обещал урок 16:
 * двумя новыми константами, без единой правки {@link Furnace}. Что печь/пресс не строится под
 * фиксированный рецепт, а сама подбирает его среди {@link #ALL} по первому пришедшему предмету —
 * см. {@link Furnace#accept}.
 *
 * <p>{@code input2} — второй, необязательный вход ({@code null} у всех рецептов с одним входом).
 * {@link #ENGINE} — первый рецепт, где он реально нужен: собрать мотор можно, только если
 * пресс получает И шестерёнку, И механизм, с двух разных лент одновременно (см. {@link
 * Furnace#accept}, где заведён отдельный буфер под каждый вход).
 */
public record Recipe(Item input, Item input2, Item output, int time, BuildingType type) {

    /** Рецепт с ОДНИМ входом — второй попросту {@code null}. Все ступени ниже, кроме мотора. */
    public Recipe(Item input, Item output, int time, BuildingType type) {
        this(input, null, output, time, type);
    }

    /** Первая ступень железной цепочки: руда в пластину, 5 тиков — печь. */
    public static final Recipe IRON = new Recipe(Item.IRON_ORE, Item.IRON_PLATE, 5, BuildingType.FURNACE);

    /** Вторая ступень железной цепочки: пластина в шестерёнку, 8 тиков — пресс. */
    public static final Recipe GEAR = new Recipe(Item.IRON_PLATE, Item.GEAR, 8, BuildingType.PRESS);

    /** Первая ступень бронзовой цепочки: та же печь, другая руда. */
    public static final Recipe BRONZE = new Recipe(Item.BRONZE_ORE, Item.BRONZE_PLATE, 5, BuildingType.FURNACE);

    /** Вторая ступень бронзовой цепочки: тот же пресс, другая пластина. */
    public static final Recipe MECHANISM = new Recipe(Item.BRONZE_PLATE, Item.MECHANISM, 8, BuildingType.PRESS);

    /** Мотор: шестерёнка И механизм → мотор, 12 тиков — тот же пресс, но с ДВУМЯ входами. */
    public static final Recipe ENGINE =
            new Recipe(Item.GEAR, Item.MECHANISM, Item.ENGINE, 12, BuildingType.PRESS);

    /**
     * Шасси: мотор И ещё одна шестерёнка → шасси, 15 тиков — новый верхний предмет цепочки.
     * {@code ENGINE} — ПЕРВЫЙ вход не случайно: {@link #find} подбирает рецепт по ПЕРВОМУ
     * пришедшему предмету, а {@link Item#GEAR} уже занят рецептом {@link #ENGINE} тем же приёмом
     * (первый пришедший — не однозначный, если начать с шестерёнки). Начать сборку шасси нужно
     * с мотора — он ни для какого другого рецепта прессa входом не служит, поэтому однозначен.
     */
    public static final Recipe CHASSIS =
            new Recipe(Item.ENGINE, Item.GEAR, Item.CHASSIS, 15, BuildingType.PRESS);

    /**
     * Сплав: железная пластина И бронзовая пластина → сплав, 10 тиков — {@code FURNACE}, а не
     * {@code PRESS}: первый рецепт ПЕЧИ с двумя входами (до сих пор двумя входами пользовался
     * только пресс — {@link #ENGINE}, {@link #CHASSIS}). Однозначен без всяких уловок с порядком
     * подачи: {@code Recipe#find} фильтрует ещё и по {@code type()}, а среди {@code FURNACE}-
     * рецептов ни {@link #IRON}, ни {@link #BRONZE} не берут пластину на вход (только руду) — то
     * есть {@link Item#IRON_PLATE} и {@link Item#BRONZE_PLATE} в печи узнаваемы только этим
     * рецептом, с любого из двух входов первым.
     */
    public static final Recipe ALLOY =
            new Recipe(Item.IRON_PLATE, Item.BRONZE_PLATE, Item.ALLOY_PLATE, 10, BuildingType.FURNACE);

    /**
     * Потребитель сплава: сплав → шестерня из сплава, 10 тиков, ОДИН вход — тот же приём, что
     * {@link #GEAR}/{@link #MECHANISM}, только сырьё дороже (сплав уже стоил двух пластин и своих
     * 10 тиков). Однозначен без всяких оговорок: {@link Item#ALLOY_PLATE} до сих пор нигде не был
     * входом — только выходом {@link #ALLOY}, — так что это первый и единственный рецепт пресса,
     * который вообще на него откликается.
     */
    public static final Recipe ALLOY_GEAR =
            new Recipe(Item.ALLOY_PLATE, Item.ALLOY_GEAR, 10, BuildingType.PRESS);

    /** Все рецепты игры — по нему печь/пресс ищут себе подходящий (см. {@link #find}). */
    public static final List<Recipe> ALL =
            List.of(IRON, GEAR, BRONZE, MECHANISM, ENGINE, CHASSIS, ALLOY, ALLOY_GEAR);

    /**
     * Найти рецепт для здания сорта {@code kind} ({@code FURNACE} или {@code PRESS}), который
     * принимает {@code input} на вход (первый ИЛИ второй) — или {@code null}, если такого нет.
     */
    public static Recipe find(BuildingType kind, Item input) {
        for (Recipe recipe : ALL) {
            if (recipe.type() == kind && (recipe.input() == input || recipe.input2() == input)) {
                return recipe;
            }
        }
        return null;
    }

    /**
     * Найти рецепт по ВЫХОДУ, а не входу, — нужен {@link Furnace#save()}/{@code load}: у
     * дуального рецепта ({@link #ENGINE}) сохранённый в файле вход неоднозначен (какой из двух?),
     * а выход в пределах {@link #ALL} для одного {@code kind} всегда один-единственный.
     */
    public static Recipe findByOutput(BuildingType kind, Item output) {
        for (Recipe recipe : ALL) {
            if (recipe.type() == kind && recipe.output() == output) {
                return recipe;
            }
        }
        return null;
    }
}
