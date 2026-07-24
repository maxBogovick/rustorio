package com.rustorio;

/**
 * Сортировщик: принимает предмет и раздаёт его в одно из ДВУХ направлений — вперёд по своему
 * {@link #facing} или на один поворот по часовой стрелке от него ({@link Direction#rotate}) — по
 * правилу, которое ему дали при постройке.
 *
 * <p>Сам сортировщик не решает, что вперёд, а что в сторону, — он лишь спрашивает у своего
 * {@link SortRule}, куда положить ЭТОТ КОНКРЕТНЫЙ предмет, и толкает туда. Захочешь другое
 * правило для другого сортировщика — не трогай этот класс, дай ему другой {@link SortRule} при
 * постройке (см. {@link World#placeSplitter}).
 *
 * <p><b>Направление раньше было зашито намертво</b> ({@code +x}/{@code +y}, независимо от того,
 * что игрок выбрал клавишей {@code R} перед постройкой, — молчаливое расхождение с лентой/печью/
 * туннелем, у которых та же клавиша реально поворачивает здание). Теперь {@link #facing} —
 * обычное поле, как у {@link Belt}, а вторая сторона высчитывается поворотом от неё, а не второй
 * зашитой константой. При {@code facing == RIGHT} (значение по умолчанию до первого {@code R})
 * поведение бит-в-бит то же, что было: вперёд — {@code +x}, поворот по часовой — {@code DOWN} —
 * {@code +y}.
 */
public final class Splitter implements Building {

    private final SortRule rule;
    private final Direction facing;
    private Item held;

    public Splitter(SortRule rule, Direction facing) {
        this.rule = rule;
        this.facing = facing;
    }

    @Override
    public boolean accept(World world, Item item) {
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
        // Решение куда — НЕ здесь, у правила; решение КАК ИМЕННО выглядят «вперёд»/«в сторону» —
        // здесь, и оно про facing, а не про мировые оси (см. javadoc класса).
        Direction direction = rule.forward(held) ? facing : facing.rotate();
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    /** Груз, который сортировщик сейчас держит, — рисуется поверх тайла до раздачи дальше. */
    @Override
    public Item heldItem() {
        return held;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(Sprite.SPLITTER);
    }

    @Override
    public BuildingType type() {
        return BuildingType.SPLITTER;
    }

    /** «Вперёд» на стрелке направления. */
    @Override
    public Direction outputDirection() {
        return facing;
    }

    /** Второй выход на стрелке направления — тот же поворот, что и в {@link #tick}. */
    @Override
    public Direction secondaryOutputDirection() {
        return facing.rotate();
    }

    /**
     * Состояние для сохранения: направление и что везём (или {@code "-"}) — как у {@link Belt}.
     * Само ПРАВИЛО не сохраняется — в игре сейчас все сортировщики строятся с одним и тем же
     * {@link SortRule#ORE_FORWARD}, менять его можно только правкой {@link World#placeSplitter}
     * (см. урок 12, задание 3).
     */
    @Override
    public String save() {
        return facing.name() + " " + (held == null ? "-" : held.name());
    }

    /** Воссоздать сортировщик из сохранённого состояния — с тем же правилом по умолчанию. */
    static Splitter load(String data) {
        String[] parts = data.split(" ", 2);
        Splitter splitter = new Splitter(SortRule.ORE_FORWARD, Direction.valueOf(parts[0]));
        if (!parts[1].equals("-")) {
            splitter.held = Item.valueOf(parts[1]);
        }
        return splitter;
    }
}
