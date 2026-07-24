package com.rustorio;

/**
 * Лента: держит ОДИН предмет и раз в тик подталкивает его к соседу В СВОЁМ НАПРАВЛЕНИИ.
 *
 * <p>Раньше направление было зашито намертво (всегда {@code +x}, урок 11). Теперь оно —
 * {@link Direction}, выбранная игроком при постройке (клавиша {@code R} перед ЛКМ) и хранимая
 * в самой ленте, как когда-то предмет-груз или буфер печи. Чтобы предмет проехал дальше одной
 * клетки, ставь ленты подряд ПО НАПРАВЛЕНИЮ движения: каждая подхватывает то, что выронила
 * соседняя позади, и передаёт дальше соседней впереди.
 *
 * <p><b>Тайл — не сам себе хозяин движения.</b> Прямая цепочка одинаково направленных лент —
 * это {@link BeltSegment}: он решает, в каком порядке грузу можно продвинуться, и владеет ОДНИМ
 * общим проходом на весь свой тик, вместо N независимых. Сам тайл по-прежнему хранит СВОЙ груз
 * ({@link #held}) — сегмент лишь переставляет его между тайлами; {@link #accept} и
 * {@link #heldItem} поэтому не изменились ни строкой.
 */
public final class Belt implements Building {

    private final Direction direction;

    /** Предмет, который лента сейчас везёт, или {@code null}, если пусто. */
    private Item held;

    /** Сегмент, которому принадлежит этот тайл — назначается {@link World} при постройке. */
    private BeltSegment segment;

    public Belt(Direction direction) {
        this.direction = direction;
    }

    Direction direction() {
        return direction;
    }

    @Override
    public Direction outputDirection() {
        return direction;
    }

    /** Вступить в сегмент (или покинуть его — {@code null} при сносе); зовёт только {@link World}. */
    void joinSegment(BeltSegment segment) {
        this.segment = segment;
    }

    BeltSegment segment() {
        return segment;
    }

    /** Свой груз для {@link BeltSegment} — то же, что {@link #heldItem}, но виднее внутри пакета. */
    Item held() {
        return held;
    }

    void setHeld(Item item) {
        held = item;
    }

    void clearHeld() {
        held = null;
    }

    @Override
    public boolean accept(World world, Item item) {
        if (held != null) {
            return false;              // уже что-то везём — вторая порция пока не помещается
        }
        held = item;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (!segment.isTail(this)) {
            return; // не хвост — сегмент этого тика уже продвинут своим хвостом-«водителем»
        }
        int exitX = x + direction.dx() * segment.size();
        int exitY = y + direction.dy() * segment.size();
        segment.tick(item -> world.offerForward(exitX, exitY, item));
    }

    /** Груз, который лента сейчас везёт, — рисуется поверх тайла, пока сосед впереди не заберёт. */
    @Override
    public Item heldItem() {
        return held;
    }

    @Override
    public Appearance appearance() {
        // Честно: спрайт не поворачивается по направлению — тот же класс ограничений ассетов,
        // что у пресса в уроке 16 (выглядит как печь). Куда толкает лента, видно по движению
        // предмета на экране, не по картинке самой ленты. Поворот спрайта — тема отдельного
        // визуального улучшения, не этого урока.
        return held == null ? Appearance.of(Sprite.BELT_EMPTY) : Appearance.of(Sprite.BELT_FULL);
    }

    @Override
    public BuildingType type() {
        return BuildingType.BELT;
    }

    /**
     * Ленте вправо/вниз подходит обычный обход мира «от больших координат к меньшим» (урок 11) —
     * она сама и есть та лента, ради которой этот порядок придумали. Ленте влево/вверх нужен
     * ОБРАТНЫЙ порядок: иначе она подтолкнёт соседа, который в этом кадре ещё не тикал, а тот —
     * следующего, и предмет проедет всю цепочку за один тик вместо одной клетки (см. урок 19).
     */
    @Override
    public boolean prefersDescendingTick() {
        return direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    /** Состояние для сохранения: направление и что везём (или {@code "-"}, если пусто). */
    @Override
    public String save() {
        return direction.name() + " " + (held == null ? "-" : held.name());
    }

    /** Воссоздать ленту из сохранённого состояния. */
    static Belt load(String data) {
        String[] parts = data.split(" ", 2);
        Belt belt = new Belt(Direction.valueOf(parts[0]));
        if (!parts[1].equals("-")) {
            belt.held = Item.valueOf(parts[1]);
        }
        return belt;
    }
}
