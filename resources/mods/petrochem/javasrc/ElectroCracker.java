package com.petrochem;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.PowerSpec;
import com.rustorio.domain.building.TickContext;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Электрический крекер: превращает нефтеносный песок в пластик, платя за каждый тик работы
 * электричеством, а не углём.
 *
 * <p>Зачем ему своя Java, если у движка уже есть архетип сборщика: сборщик читает {@code
 * power.demand}, но не запрашивает мощность в тике — питание сегодня спрашивает только бур. Здание,
 * которое реально встаёт без тока и при этом варит рецепт, данными не описывается, и это ровно тот
 * случай, ради которого существует jar-мод.
 *
 * <p>Рецепт здесь захардкожен парой «предмет на входе → предмет на выходе», а не берётся из
 * {@code RecipeBook}: пул рецептов умеет читать архетип печи, а этот класс демонстрирует
 * собственное поведение, и лишний слой поиска рецепта только спрятал бы то, что показывается.
 *
 * <p>Технология {@code petrochem:oil_processing} тоже данными не выражается: JSON описывает только
 * узел дерева (id, цена, предки — см. {@code TechJsonLoader}), а её эффект всегда код. Из зданий
 * этого мода эффект может спросить только этот класс — {@code refinery}/{@code plastic_plant} и
 * прочие JSON-архетипы к {@code TickContext.research()} вообще не обращаются, — так что здесь и
 * только здесь открытая технология вдвое ускоряет партию, тем же приёмом, что {@code Miner} читает
 * {@code FAST_MINING}.
 */
final class ElectroCracker implements Building, InspectableBuilding {

    /** Тиков на партию и сколько песка влезает во входной буфер — те же порядки величин, что у ванильных машин. */
    private static final int CRACK_TICKS = 20;

    private static final int INPUT_MAX = 5;

    private static final ContentId OIL_PROCESSING = ContentId.of("petrochem:oil_processing");

    private final BuildingPrototype prototype;
    private final Direction direction;
    private final ItemType input;
    private final ItemType output;
    /** Общий счётчик мода — его наполняет подписчик события, а это здание только показывает. */
    private final PlasticCounter counter;

    private int buffered;
    private int ticksLeft;
    private @Nullable ItemType held;
    /** Пересчитывается раз в тик, а не раз в кадр — так же, как у всех архетипов движка. */
    private BuildingStatus status = BuildingStatus.NO_INPUT;

    ElectroCracker(BuildingPrototype prototype, Direction direction, ItemType input, ItemType output,
            PlasticCounter counter) {
        this.prototype = prototype;
        this.direction = direction;
        this.input = input;
        this.output = output;
        this.counter = counter;
    }

    /** Конструктор восстановления из сейва — им пользуется зарегистрированная {@code RestoreFactory}. */
    ElectroCracker(BuildingPrototype prototype, Direction direction, ItemType input, ItemType output,
            PlasticCounter counter, int buffered, int ticksLeft, @Nullable ItemType held) {
        this(prototype, direction, input, output, counter);
        this.buffered = buffered;
        this.ticksLeft = ticksLeft;
        this.held = held;
    }

    /** Берёт только свой вход и только пока есть место — иначе лента с чужим предметом забила бы буфер. */
    @Override
    public boolean accept(TickContext world, ItemType item) {
        if (!item.equals(input) || buffered >= INPUT_MAX) {
            return false;
        }
        buffered++;
        return true;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        if (held != null) {
            deliver(world, x, y);
            return;
        }
        if (buffered == 0) {
            status = BuildingStatus.NO_INPUT;
            return;
        }
        PowerSpec spec = prototype.power();
        int demand = spec == null ? 0 : spec.demand();
        if (demand > 0 && !world.drawPower(x, y, demand)) {
            // Ток списывается за каждый тик работы, а не за партию: машина, не получившая
            // мощность, не двигает таймер и честно говорит игроку, почему стоит.
            status = BuildingStatus.NO_POWER;
            return;
        }
        if (ticksLeft == 0) {
            ticksLeft = effectiveCrackTicks(world);
        }
        status = BuildingStatus.WORKING;
        if (--ticksLeft > 0) {
            return;
        }
        buffered--;
        held = output;
        world.notifyProduced(output);
        deliver(world, x, y);
    }

    /** {@code CRACK_TICKS} halved (floor 1) once {@code oil_processing} is unlocked — see the class javadoc. */
    private static int effectiveCrackTicks(TickContext world) {
        return world.research().fasterIfUnlocked(OIL_PROCESSING, CRACK_TICKS);
    }

    private void deliver(TickContext world, int x, int y) {
        ItemType ready = held;
        if (ready == null) {
            return;
        }
        if (world.offerForward(x + direction.dx(), y + direction.dy(), ready)) {
            held = null;
            status = BuildingStatus.WORKING;
        } else {
            status = BuildingStatus.OUTPUT_FULL;
        }
    }

    /** Что показать в панели осмотра — панель не знает про этот класс и знать не должна. */
    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        return List.of("Сырьё в буфере: " + buffered + "/" + INPUT_MAX,
                "До партии: " + (ticksLeft == 0 ? effectiveCrackTicks(world) : ticksLeft) + " тиков",
                "Пластика за сессию: " + counter.produced());
    }

    @Override
    public BuildingStatus status() {
        return status;
    }

    @Override
    public Appearance appearance() {
        return buffered > 0
                ? Appearance.of(prototype.texture(), buffered, status)
                : Appearance.of(prototype.texture(), status);
    }

    @Override
    public Optional<ItemType> heldItem() {
        return Optional.ofNullable(held);
    }

    @Override
    public Optional<Direction> outputDirection() {
        return Optional.of(direction);
    }

    @Override
    public Optional<Building> rotatedClockwise() {
        return Optional.of(new ElectroCracker(prototype, direction.rotate(), input, output, counter,
                buffered, ticksLeft, held));
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public CrackerState state() {
        return new CrackerState(direction, buffered, ticksLeft, held);
    }

    /** Состояние этого архетипа: запись из простых типов, как того просит контракт {@code Codec}. */
    record CrackerState(Direction direction, int buffered, int ticksLeft, @Nullable ItemType held) {
    }
}
