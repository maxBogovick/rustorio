package com.rustorio;

/**
 * Подземная лента: пара «вход/выход», которая переносит предмет через препятствие (несколько
 * клеток, занятых чем угодно — хоть другой лентой), минуя обычную передачу «соседу впереди».
 *
 * <p>Один класс на обе половинки пары — как печь и пресс, только различие называется не
 * {@code Recipe}, а {@link Kind}: вход берёт предмет от обычного соседа сзади и раз в тик ищет
 * впереди, по своему {@link Direction}, ближайший ВЫХОД той же ориентации в пределах
 * {@link #MAX_RANGE} клеток — и перекладывает предмет ему НАПРЯМУЮ, в обход {@code accept}
 * (иначе выходу пришлось бы принимать от кого угодно, а не только от своей пары). Выход дальше
 * толкает предмет вперёд как обычная {@link Belt}.
 */
public final class UndergroundBelt implements Building {

    /** Какая половинка пары — вход (берёт с поверхности) или выход (отдаёт на поверхность). */
    public enum Kind {
        IN, OUT
    }

    /** Максимальная длина туннеля — дальше пару искать не имеет смысла. */
    private static final int MAX_RANGE = 4;

    private final Kind kind;
    private final Direction direction;

    /** Предмет, который сейчас лежит в этой половинке, или {@code null}. */
    private Item held;

    public UndergroundBelt(Kind kind, Direction direction) {
        this.kind = kind;
        this.direction = direction;
    }

    /** Из обычных соседей предмет берёт только вход — выход наполняется лишь своей парой. */
    @Override
    public boolean accept(World world, Item item) {
        if (kind != Kind.IN || held != null) {
            return false;
        }
        held = item;
        return true;
    }

    @Override
    public void tick(World world, int x, int y) {
        if (held == null) {
            return;
        }
        if (kind == Kind.IN) {
            tickIn(world, x, y);
        } else {
            tickOut(world, x, y);
        }
    }

    /** Вход: ищет свою пару впереди по направлению и перекладывает предмет ей напрямую. */
    private void tickIn(World world, int x, int y) {
        UndergroundBelt partner = findPartner(world, x, y);
        if (partner != null && partner.held == null) {
            partner.held = held;
            held = null;
        }
        // пары не нашлось (нет второй половинки, слишком далеко или она занята) — предмет ждёт
    }

    /**
     * Найти вторую половинку туннеля впереди по направлению — ту же клетку, что ищет
     * {@link #tickIn}, но БЕЗ перекладывания груза. Нужен отдельно от тика — {@link
     * com.graphics.render.OverlayRenderer} зовёт его же (без груза в руках), чтобы подсветить
     * вход, у которого пары вообще НЕТ: иначе сломанный туннель выглядит НА ГЛАЗ точно так же,
     * как рабочий, — игрок не может отличить «дальность превышена» от «просто нечего везти».
     *
     * <p>{@link Building#unwrap} обязателен: апгрейженный выход лежит в карте как
     * {@link SpeedModule}, а не {@code UndergroundBelt} — без разворачивания вход бы решил, что
     * пары нет вовсе, и НАВСЕГДА перестал бы передавать груз апгрейженному выходу (не только
     * подсветка соврала бы — сломался бы сам туннель).
     */
    public UndergroundBelt findPartner(World world, int x, int y) {
        if (kind != Kind.IN) {
            return null;
        }
        int dx = direction.dx();
        int dy = direction.dy();
        for (int step = 1; step <= effectiveRange(world); step++) {
            Building candidate = world.peek(x + dx * step, y + dy * step);
            if (Building.unwrap(candidate) instanceof UndergroundBelt other
                    && other.kind == Kind.OUT
                    && other.direction == direction) {
                return other;
            }
        }
        return null;
    }

    /**
     * Дальность поиска пары с учётом технологии {@link Tech#LONG_TUNNEL} — открыта, значит
     * туннель достаёт вдвое дальше, глобально, для всех входов на карте.
     */
    private static int effectiveRange(World world) {
        return world.research().isUnlocked(Tech.LONG_TUNNEL) ? MAX_RANGE * 2 : MAX_RANGE;
    }

    /** Выход: как обычная лента — толкает предмет вперёд по своему направлению. */
    private void tickOut(World world, int x, int y) {
        if (world.offerForward(x + direction.dx(), y + direction.dy(), held)) {
            held = null;
        }
    }

    /** Груз в этой половинке туннеля — рисуется поверх тайла (виден и под землёй, чтобы было видно). */
    @Override
    public Item heldItem() {
        return held;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(kind == Kind.IN ? Sprite.UNDERGROUND_IN : Sprite.UNDERGROUND_OUT);
    }

    /** Обе половинки показывают одно и то же направление — куда идёт туннель, вход или выход. */
    @Override
    public Direction outputDirection() {
        return direction;
    }

    @Override
    public BuildingType type() {
        return kind == Kind.IN ? BuildingType.UNDERGROUND_IN : BuildingType.UNDERGROUND_OUT;
    }

    /**
     * Как у {@link Belt}: выход вправо/вниз пойдёт в обычном обходе мира, влево/вверх — нужен
     * обратный порядок (см. {@link World#tick()}). Вход НИЧЕГО не толкает соседу впереди — только
     * своей паре, поэтому направление обхода ему всегда безразлично.
     */
    @Override
    public boolean prefersDescendingTick() {
        return kind == Kind.IN || direction == Direction.RIGHT || direction == Direction.DOWN;
    }

    /** Состояние для сохранения: направление и груз — сорт (IN/OUT) уже в теге здания. */
    @Override
    public String save() {
        return direction.name() + " " + (held == null ? "-" : held.name());
    }

    /** Воссоздать половинку туннеля заданного сорта из сохранённого состояния. */
    static UndergroundBelt load(String data, Kind kind) {
        String[] parts = data.split(" ", 2);
        UndergroundBelt belt = new UndergroundBelt(kind, Direction.valueOf(parts[0]));
        if (!parts[1].equals("-")) {
            belt.held = Item.valueOf(parts[1]);
        }
        return belt;
    }
}
