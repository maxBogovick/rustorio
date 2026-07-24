package com.rustorio;

/**
 * Печь: принимает предмет от соседа, перерабатывает его по подходящему {@link Recipe} и отдаёт
 * результат дальше.
 *
 * <p>Третье здание — и первое, что стоит В СЕРЕДИНЕ цепочки: у него есть и вход, и выход.
 * Раньше «беру только руду» и «делаю пластину» были зашиты в код класса; потом (урок 16) это
 * стало РЕЦЕПТОМ, который печи давали при постройке. С приходом бронзовой цепочки этого уже
 * недостаточно: печь ещё НЕ ПОСТРОЕНА, когда игрок выбирает, где её ставить, — она не знает
 * заранее, повезёт ли ей по ленте железную руду или бронзовую. Поэтому теперь печь знает только
 * свой СОРТ ({@link BuildingType#FURNACE} или {@link BuildingType#PRESS} — какая у неё роль в
 * цепочке), а конкретный {@link Recipe} подбирает САМА, по первому предмету, который ей
 * предложат ({@link Recipe#find}). Опустеет буфер — печь забывает выбор и готова подстроиться
 * под другой материал (как в жизни: остыла — можно закладывать другую руду).
 *
 * <p>Как и бур, печь — активное здание: переопределяет {@link #tick}. В отличие от ящика, она
 * принимает НЕ что угодно — только вход подходящего рецепта своего сорта и только пока в буфере
 * есть место.
 *
 * <p>Готовый выход печь ДЕРЖИТ у себя, пока его не заберёт сосед, — как бур держит руду в руках
 * ({@link Miner}). Раньше готовая порция сразу же «уходила» через {@code offerToNeighbor}
 * НЕЗАВИСИМО от того, нашёлся ли сосед: если ящика/ленты дальше не было, пластина просто исчезала,
 * а печь как ни в чём не бывало начинала готовить следующую — руда расходовалась вхолостую
 * бесконечно. Теперь, пока готовая порция не пристроена, печь НЕ начинает готовить следующую —
 * она ждёт, как и всё остальное на конвейере.
 *
 * <p><b>Почему у печи есть {@code direction}, хотя раньше не было.</b> Пока выход пытался
 * пристроиться К ЛЮБОМУ из четырёх соседей подряд (как когда-то {@link Miner}), нашёлся способ
 * сломать конвейер НАВСЕГДА: если сосед-приёмник (например, ящик справа) в момент готовности ещё
 * не стоял, печь толкала готовую пластину НАЗАД — на свою же ленту-вход слева. Пластина застревала
 * там намертво (сама печь никогда не примет ГОТОВЫЙ ПРОДУКТ на вход — это не её рецепт) и глушила
 * всю линию позади, включая бур, — молча, без единого сообщения об ошибке (воспроизведено и
 * подтверждено тестом на реальном сохранении). Теперь, как у ленты, у печи есть ОДНО направление
 * выдачи, выбранное игроком при постройке (клавиша {@code R}, по умолчанию — вправо): выход идёт
 * туда и только туда, собственный вход толкнуть некуда и нечем.
 *
 * <p><b>Почему {@code bufferA}/{@code bufferB}, а не один {@code buffer}.</b> {@link
 * Recipe#ENGINE} — первый рецепт с ДВУМЯ входами (шестерёнка и механизм с двух разных лент).
 * Печь копит их РАЗДЕЛЬНО и не начинает варить, пока не накопилось хотя бы по одному каждого —
 * иначе первый же пришедший ингредиент расходовался бы вхолостую (та же болезнь, что уже дважды
 * ловилась в этом классе: печь не должна тратить что-то, не будучи готовой довести дело до
 * конца). У рецептов с одним входом {@code bufferB} просто никогда не используется.
 */
public final class Furnace implements Building {

    /** Сколько предметов печь готова держать в очереди на переработку (по каждому входу). */
    private static final int BUFFER_MAX = 5;

    /** Роль в цепочке — {@code FURNACE} или {@code PRESS}; не меняется всю жизнь здания. */
    private final BuildingType kind;

    /**
     * Куда идёт готовый выход. Принимать сырьё печь готова с ЛЮБОЙ стороны (см. {@link #accept}),
     * а вот отдавать — только в одну, выбранную при постройке: это и не даёт готовому продукту
     * случайно уйти назад, на собственный же вход.
     */
    private final Direction direction;

    /** Рецепт, под который печь подстроилась, — или {@code null}, если ещё ничего не приносили. */
    private Recipe recipe;

    /** Сколько единиц ПЕРВОГО входа рецепта сейчас ждёт переработки. */
    private int bufferA;
    /** Сколько единиц ВТОРОГО входа ждёт переработки — используется, только если он есть у рецепта. */
    private int bufferB;
    /**
     * Отсчёт до готовности текущей порции — {@code null}, пока {@link #recipe} не выбран (считать
     * ещё нечего). Общий с {@link Lab} счётчик (см. {@link ProcessTimer}) вместо своего же
     * {@code cooldown}, скопированного в оба класса.
     */
    private ProcessTimer timer;
    /** Готовая порция, которую ещё не забрал сосед, — или {@code null}, если печь не держит выход. */
    private Item pendingOutput;

    public Furnace(BuildingType kind, Direction direction) {
        this.kind = kind;
        this.direction = direction;
    }

    /**
     * Принять предмет от соседа.
     *
     * <p>Если печь ещё ни на что не настроилась ({@code recipe == null}) — ищет среди
     * {@link Recipe#ALL} подходящий её сорту рецепт, где {@code item} — первый ИЛИ второй вход;
     * не найдёт — предмет не тот даже для этой роли, отказ. Дальше предмет уходит в буфер ТОГО
     * входа, которому соответствует (первый — {@code bufferA}, второй, если есть, — {@code
     * bufferB}), пока в этом буфере есть место.
     *
     * @return {@code true}, если приняли; {@code false} иначе (сосед оставит предмет себе / отдаст
     *         другому)
     */
    @Override
    public boolean accept(World world, Item item) {
        if (recipe == null) {
            Recipe found = Recipe.find(kind, item);
            if (found == null) {
                return false;
            }
            recipe = found;
            // effectiveTime(world), а не «голое» recipe.time(): раньше первая порция после
            // подстройки печи под рецепт варилась по БАЗОВОМУ времени, и только вторая и
            // дальше — по тех-модифицированному (FAST_SMELTING брался в расчёт лишь при сбросе
            // cooldown в tick()). ProcessTimer убирает эту рассинхронизацию — обе точки, где
            // отсчёт получает значение, теперь читают одну и ту же формулу.
            timer = new ProcessTimer(effectiveTime(world));
        }
        int max = effectiveBufferMax(world);
        if (item == recipe.input() && bufferA < max) {
            bufferA++;
            return true;
        }
        if (recipe.input2() != null && item == recipe.input2() && bufferB < max) {
            bufferB++;
            return true;
        }
        return false;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (pendingOutput == null) {
            boolean secondInputReady = recipe == null || recipe.input2() == null || bufferB > 0;
            if (bufferA == 0 || !secondInputReady) {
                return;                     // перерабатывать нечего — или ждём второй ингредиент
            }
            if (!timer.tick(effectiveTime(world))) {
                return;                     // ещё работаем
            }
            bufferA--;                      // первый вход израсходован
            if (recipe.input2() != null) {
                bufferB--;                  // и второй, если рецепт его требует
            }
            pendingOutput = recipe.output();
            world.notifyProduced(pendingOutput); // засчитать РОВНО ОДИН РАЗ — в момент готовности
            if (bufferA == 0 && (recipe.input2() == null || bufferB == 0)) {
                recipe = null;               // оба буфера пусты — печь снова готова под любой материал
                timer = null;                // считать больше нечего, пока не подстроимся заново
            }
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), pendingOutput)) {
            pendingOutput = null;           // сосед забрал — можно готовить следующую порцию
        }
        // сосед занят или его нет — готовая порция ждёт, новая варка не начинается
    }

    /**
     * Время готовки одной порции с учётом технологии {@link Tech#FAST_SMELTING} — открыта, значит
     * печь и пресс работают вдвое быстрее (глобальный апгрейд, в отличие от точечного
     * {@link SpeedModule}).
     */
    private int effectiveTime(World world) {
        return world.research().fasterIfUnlocked(Tech.FAST_SMELTING, recipe.time());
    }

    /**
     * Вместимость буфера (для каждого входа отдельно) с учётом технологии {@link
     * Tech#BIG_BUFFER} — открыта, значит печь и пресс копят вдвое больше про запас.
     */
    private static int effectiveBufferMax(World world) {
        return world.research().biggerIfUnlocked(Tech.BIG_BUFFER, BUFFER_MAX);
    }

    /** Сколько единиц первого входа в буфере — печь показывает это число бейджем. */
    public int oreBuffer() {
        return bufferA;
    }

    @Override
    public Direction outputDirection() {
        return direction;
    }

    /** Готовая порция, которую печь держит, пока сосед не заберёт, — рисуется поверх как груз. */
    @Override
    public Item heldItem() {
        return pendingOutput;
    }

    @Override
    public Appearance appearance() {
        // Печь решает сама: есть что-то в буфере — горячий спрайт с числом; пусто — холодный.
        // Рецепт на спрайт не влияет — печь и пресс сейчас выглядят одинаково (см. урок 16).
        return bufferA > 0
                ? Appearance.of(Sprite.FURNACE_HOT, bufferA)
                : Appearance.of(Sprite.FURNACE_COLD);
    }

    @Override
    public BuildingType type() {
        return kind;
    }

    /**
     * Состояние для сохранения: направление выдачи, оба буфера, таймер, ВЫХОД текущего рецепта
     * (или {@code "-"}, если печь ещё не настроилась) и застрявший на выходе предмет (или
     * {@code "-"}). Рецепт опознаётся по выходу, а не входу ({@link Recipe#findByOutput}): у
     * {@link Recipe#ENGINE} вход неоднозначен (какой из двух был первым?), а выход в пределах
     * одного {@code kind} всегда один-единственный.
     */
    @Override
    public String save() {
        return direction.name() + " " + bufferA + " " + bufferB + " "
                + (timer == null ? 0 : timer.cooldown()) + " "
                + (recipe == null ? "-" : recipe.output().name())
                + " " + (pendingOutput == null ? "-" : pendingOutput.name());
    }

    /**
     * Воссоздать печь из сохранённого состояния для здания сорта {@code kind}.
     *
     * <p>{@code kind} передаётся СНАРУЖИ, а не хранится в файле: его уже знает вызывающий — по
     * тегу здания ({@code FURNACE} или {@code PRESS}, см. {@link SaveGame}).
     */
    static Furnace load(String data, BuildingType kind) {
        String[] fields = data.split(" ");
        Furnace furnace = new Furnace(kind, Direction.valueOf(fields[0]));
        furnace.bufferA = Integer.parseInt(fields[1]);
        furnace.bufferB = Integer.parseInt(fields[2]);
        int cooldown = Integer.parseInt(fields[3]);
        if (!fields[4].equals("-")) {
            furnace.recipe = Recipe.findByOutput(kind, Item.valueOf(fields[4]));
            furnace.timer = new ProcessTimer(cooldown);
        }
        if (!fields[5].equals("-")) {
            furnace.pendingOutput = Item.valueOf(fields[5]);
        }
        return furnace;
    }
}
