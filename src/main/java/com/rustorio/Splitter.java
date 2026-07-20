package com.rustorio;

/**
 * Сортировщик: принимает предмет и раздаёт его в одно из ДВУХ направлений — вперёд ({@code +x})
 * или вниз ({@code +y}) — по правилу, которое ему дали при постройке.
 *
 * <p>Сам сортировщик не решает, что вперёд, а что вниз, — он лишь спрашивает у своего
 * {@link SortRule}, куда положить ЭТОТ КОНКРЕТНЫЙ предмет, и толкает туда. Захочешь другое
 * правило для другого сортировщика — не трогай этот класс, дай ему другой {@link SortRule} при
 * постройке (см. {@link World#placeSplitter}).
 */
public final class Splitter implements Building {

    private final SortRule rule;
    private Item held;

    public Splitter(SortRule rule) {
        this.rule = rule;
    }

    @Override
    public boolean accept(Item item) {
        if (held != null) {
            return false;               // уже что-то везём
        }
        held = item;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (held == null) {
            return;                     // раздавать нечего
        }
        boolean forward = rule.forward(held);   // решение НЕ здесь — у правила
        int tx = forward ? x + 1 : x;
        int ty = forward ? y : y + 1;
        if (world.offerForward(tx, ty, held)) {
            held = null;
        }
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.SPLITTER);
    }

    @Override
    public BuildingType type() {
        return BuildingType.SPLITTER;
    }

    /**
     * Состояние для сохранения: что везём, или {@code "-"}. Само ПРАВИЛО не сохраняется — в игре
     * сейчас все сортировщики строятся с одним и тем же {@link SortRule#ORE_FORWARD}, менять его
     * можно только правкой {@link World#placeSplitter} (см. урок 12, задание 3).
     */
    @Override
    public String save() {
        return held == null ? "-" : held.name();
    }

    /** Воссоздать сортировщик из сохранённого состояния — с тем же правилом по умолчанию. */
    static Splitter load(String data) {
        Splitter splitter = new Splitter(SortRule.ORE_FORWARD);
        if (!data.equals("-")) {
            splitter.held = Item.valueOf(data);
        }
        return splitter;
    }
}
