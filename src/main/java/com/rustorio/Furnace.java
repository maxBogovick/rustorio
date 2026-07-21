package com.rustorio;

/**
 * Печь: принимает предмет от соседа, перерабатывает его по своему {@link Recipe} и отдаёт
 * результат дальше.
 *
 * <p>Третье здание — и первое, что стоит В СЕРЕДИНЕ цепочки: у него есть и вход, и выход.
 * Раньше «беру только руду» и «делаю пластину» были зашиты в код класса. Теперь это РЕЦЕПТ —
 * значение, которое печи дают при постройке (см. {@link World#placeFurnace}), а сам класс не
 * знает и знать не должен, руда это или что-то ещё (урок 16).
 *
 * <p>Как и бур, печь — активное здание: переопределяет {@link #tick}. В отличие от ящика, она
 * принимает НЕ что угодно — только вход своего рецепта и только пока в буфере есть место.
 */
public final class Furnace implements Building {

    /** Сколько предметов печь готова держать в очереди на переработку. */
    private static final int BUFFER_MAX = 5;

    private final Recipe recipe;

    /** Сколько предметов сейчас ждёт переработки. */
    private int buffer;
    /** Сколько тиков осталось до готовности текущей порции. */
    private int cooldown;

    public Furnace(Recipe recipe) {
        this.recipe = recipe;
        this.cooldown = recipe.time();
    }

    /**
     * Принять предмет от соседа. В отличие от ящика, печь разборчива — берёт только вход СВОЕГО
     * рецепта и только пока в буфере есть место.
     *
     * @return {@code true}, если приняли; {@code false}, если это не тот предмет или буфер уже
     *         полон (тогда сосед оставит предмет себе / отдаст другому)
     */
    @Override
    public boolean accept(Item item) {
        if (item != recipe.input() || buffer >= BUFFER_MAX) {
            return false;
        }
        buffer++;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (buffer == 0) {
            return;                     // перерабатывать нечего
        }
        if (--cooldown > 0) {
            return;                     // ещё работаем
        }
        cooldown = recipe.time();       // завод на следующую порцию — тем же временем рецепта
        buffer--;                       // один вход израсходован
        world.offerToNeighbor(x, y, recipe.output()); // готовый выход — соседу
    }

    /** Сколько предметов в буфере. */
    public int oreBuffer() {
        return buffer;
    }

    @Override
    public Appearance appearance() {
        // Печь решает сама: есть что-то в буфере — горячий спрайт с числом; пусто — холодный.
        // Рецепт на спрайт не влияет — печь и пресс сейчас выглядят одинаково (см. урок 16).
        return buffer > 0
                ? Appearance.of(Sprite.FURNACE_HOT, buffer)
                : Appearance.of(Sprite.FURNACE_COLD);
    }

    @Override
    public BuildingType type() {
        // Печь и пресс — один класс с разными рецептами; какой пункт меню я такое, знает рецепт.
        return recipe.type();
    }

    /** Состояние для сохранения: буфер и таймер. Сам рецепт не хранится — см. {@link #load}. */
    @Override
    public String save() {
        return buffer + " " + cooldown;
    }

    /**
     * Воссоздать печь из сохранённого состояния с заданным рецептом.
     *
     * <p>Рецепт передаётся СНАРУЖИ, а не хранится в файле: его уже знает вызывающий — по тегу
     * здания ({@code FURNACE} или {@code PRESS}, см. {@link SaveGame}). Дублировать эту
     * информацию внутри сохранения незачем.
     */
    static Furnace load(String data, Recipe recipe) {
        String[] fields = data.split(" ");
        Furnace furnace = new Furnace(recipe);
        furnace.buffer = Integer.parseInt(fields[0]);
        furnace.cooldown = Integer.parseInt(fields[1]);
        return furnace;
    }
}
