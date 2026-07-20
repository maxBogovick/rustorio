package com.rustorio;

/**
 * Печь: принимает руду от соседа, переплавляет её в железную пластину и отдаёт пластину дальше.
 *
 * <p>Третье здание — и первое, что стоит В СЕРЕДИНЕ цепочки: у него есть и вход, и выход.
 * Бур кладёт в печь руду, печь копит её в маленьком буфере, раз в несколько тиков превращает
 * одну руду в пластину и предлагает пластину соседу (обычно ящику). Получается конвейер
 * бур → печь → ящик.
 *
 * <p>Как и бур, печь — активное здание: переопределяет {@link #tick}. В отличие от ящика, она
 * принимает НЕ что угодно — только руду и только пока в буфере есть место.
 */
public final class Furnace implements Building {

    /** За сколько тиков переплавляется одна руда. */
    private static final int SMELT_TIME = 5;
    /** Сколько руды печь готова держать в очереди на переплавку. */
    private static final int BUFFER_MAX = 5;

    /** Сколько руды сейчас ждёт переплавки. */
    private int oreBuffer;
    /** Сколько тиков осталось до готовности текущей пластины. */
    private int cooldown = SMELT_TIME;

    /**
     * Принять предмет от соседа. В отличие от ящика, печь разборчива — берёт только руду и только
     * пока в буфере есть место.
     *
     * @return {@code true}, если приняли; {@code false}, если это не руда или буфер уже полон
     *         (тогда сосед оставит предмет себе / отдаст другому)
     */
    @Override
    public boolean accept(Item item) {
        if (item != Item.IRON_ORE || oreBuffer >= BUFFER_MAX) {
            return false;
        }
        oreBuffer++;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (oreBuffer == 0) {
            return;                 // плавить нечего
        }
        if (--cooldown > 0) {
            return;                 // ещё плавим
        }
        cooldown = SMELT_TIME;      // завод на следующую пластину
        oreBuffer--;                // одна руда израсходована
        world.offerToNeighbor(x, y, Item.IRON_PLATE); // готовую пластину — соседу
    }

    /** Сколько руды в буфере. */
    public int oreBuffer() {
        return oreBuffer;
    }

    @Override
    public Appearance appearance() {
        // Печь решает сама: есть руда в буфере — горячий спрайт с числом; пусто — холодный, без числа.
        return oreBuffer > 0
                ? Appearance.of(Sprite.FURNACE_HOT, oreBuffer)
                : Appearance.of(Sprite.FURNACE_COLD);
    }

    @Override
    public BuildingType type() {
        return BuildingType.FURNACE;
    }

    /** Состояние для сохранения: буфер и таймер — два числа через пробел, печь сама решает формат. */
    @Override
    public String save() {
        return oreBuffer + " " + cooldown;
    }

    /** Воссоздать печь из сохранённого состояния (обратный разбор строки из {@link #save()}). */
    static Furnace load(String data) {
        String[] fields = data.split(" ");
        Furnace furnace = new Furnace();
        furnace.oreBuffer = Integer.parseInt(fields[0]);
        furnace.cooldown = Integer.parseInt(fields[1]);
        return furnace;
    }
}
