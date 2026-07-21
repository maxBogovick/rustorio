package com.rustorio;

/** Поставить здание выбранного сорта в клетку лицом в направление — и откатить, снеся именно его. */
public final class PlaceAction implements PlayerAction {

    private final BuildingType type;
    private final int x;
    private final int y;
    private final Direction direction;

    public PlaceAction(BuildingType type, int x, int y) {
        this(type, x, y, Direction.RIGHT); // направление важно только ленте (урок 19)
    }

    public PlaceAction(BuildingType type, int x, int y, Direction direction) {
        this.type = type;
        this.x = x;
        this.y = y;
        this.direction = direction;
    }

    @Override
    public boolean apply(World world) {
        return world.place(type, x, y, direction);
    }

    @Override
    public void undo(World world) {
        // Что бы тут ни выросло за время между apply и undo (руда в печи, счётчик ящика) — снос
        // есть снос: постройка была ЭТИМ действием, значит откат — убрать ровно эту клетку.
        world.removeBuilding(x, y);
    }
}
