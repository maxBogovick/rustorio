package com.rustorio.sim;

import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.model.Building;
import com.rustorio.model.Handoff;
import com.rustorio.model.Splitter;
import com.rustorio.model.UndergroundBelt;
import com.rustorio.model.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Симуляция: как мир продвигается на один тик.
 *
 * <p>«Система» — это функция над миром: читает и меняет его данные. Один шаг мира —
 * конвейер систем, выполненных по порядку. Хочешь новое поведение мира («руда
 * истощается»)? Пишешь новый приватный метод-систему и добавляешь вызов в {@link #step}.
 *
 * <p><b>Почему это ОБЪЕКТ, а не набор статических методов, как было раньше.</b>
 * Прошлая версия ({@code Systems}) была красива: класс без состояния, его невозможно
 * испортить. Но фаза передачи предметов каждый тик создавала заново список передач и
 * набор отметок «в эту клетку уже кладут». На большой фабрике это работа и мусор на
 * ровном месте — а главное, набор отметок размером «со весь мир» в чанковом мире
 * просто не построить: там нет общего числа клеток.
 *
 * <p>Поэтому симуляция стала объектом, который создаётся один раз и владеет своими
 * буферами: список передач переиспользуется, а отметки живут прямо на клетках в виде
 * «штампа тика» (см. {@code Tile#claim}). <b>Это честный размен: мы потеряли красоту
 * «класса без состояния» и получили отсутствие мусора.</b> В учебном проекте такие
 * размены нельзя делать молча.
 */
public final class Simulation {

    private final World world;

    /**
     * Переиспользуемый буфер запланированных передач.
     *
     * <p>Живёт между тиками и очищается в начале каждого — вместо {@code new ArrayList<>()}
     * на каждом шаге. Одна «тарелка», которую моют, вместо новой одноразовой на каждый обед.
     */
    private final List<Move> moves = new ArrayList<>();

    public Simulation(World world) {
        this.world = world;
    }

    /** ОДИН шаг симуляции = конвейер систем по порядку. */
    public void step(TickContext ctx) {
        world.beginTick(); // счётчик штампов живёт в мире — там же, где сами штампы
        runMachines(ctx);
        moveBelts(ctx);
        moveUnderground(ctx);
        moveItems();
        moveSplitters();
    }

    /**
     * Система: предметы едут ПОД ЗЕМЛЁЙ.
     *
     * <p>Роль подземки (вход/выход) — свойство МЕСТА, а не здания: она зависит от того, стоит
     * ли поблизости пара. Здание своих координат не знает, поэтому пару ищет симуляция — она
     * видит мир — и каждый тик сообщает подземке её роль.
     */
    private void moveUnderground(TickContext ctx) {
        int reach = ctx.balance().undergroundReach();
        int slots = ctx.balance().beltSlotsPerTick();

        world.forEachBuilding((x, y, building) -> {
            if (!(building instanceof UndergroundBelt entry)) {
                return;
            }
            int distance = findPartner(x, y, entry.dir(), reach);
            if (distance == 0) {
                entry.setRole(UndergroundBelt.Role.NONE, 0, slots);
                return;
            }
            entry.setRole(UndergroundBelt.Role.ENTRANCE, distance, slots);

            UndergroundBelt exit = (UndergroundBelt) world.tile(
                    x + entry.dir().dx() * distance, y + entry.dir().dy() * distance).building();
            exit.setRole(UndergroundBelt.Role.EXIT, 0, slots);

            for (Item item : entry.advance()) {
                exit.deliver(item);
            }
        });
    }

    /**
     * Найти пару подземки: ближайшую подземку того же направления впереди.
     *
     * @return расстояние в клетках, либо 0, если пары нет
     */
    private int findPartner(int x, int y, Direction dir, int reach) {
        for (int step = 1; step <= reach; step++) {
            int nx = x + dir.dx() * step;
            int ny = y + dir.dy() * step;
            if (!world.inBounds(nx, ny)) {
                return 0;
            }
            if (world.tile(nx, ny).building() instanceof UndergroundBelt other
                    && other.dir() == dir) {
                return step;
            }
        }
        return 0;
    }

    /**
     * Система: развилки раздают предметы.
     *
     * <p>Отдельная фаза, потому что общий протокол {@code output()} отдаёт ОДНО направление, а
     * сплиттеру нужно перебрать выходы по кругу и пропустить занятые — в одном и том же тике.
     * Здесь у нас есть мир, поэтому мы можем спросить каждого соседа по очереди.
     */
    private void moveSplitters() {
        world.forEachBuilding((x, y, building) -> {
            if (!(building instanceof Splitter splitter)) {
                return;
            }
            Optional<Item> held = splitter.held();
            if (held.isEmpty()) {
                return;
            }
            Item item = held.get();
            for (var dir : splitter.outputsInOrder()) {
                Building receiver = world.neighborBuilding(x, y, dir);
                if (receiver == null || !receiver.canAccept(item)) {
                    continue; // занят или там стена — пробуем следующий выход
                }
                if (world.claimNeighbor(x, y, dir)) {
                    receiver.accept(item);
                    splitter.take(dir);
                    return;
                }
            }
            // Все выходы заняты — предмет остаётся в сплиттере. Так и должно быть: затор виден.
        });
    }

    /** Система №1: каждое здание делает свою внутреннюю работу (бур копает, печь плавит). */
    private void runMachines(TickContext ctx) {
        world.forEachBuilding((x, y, building) -> building.update(ctx));
    }

    /**
     * Система №2: предметы едут ВНУТРИ транспортных линий.
     *
     * <p>Отдельная фаза, потому что лента больше не «здание с одним предметом»:
     * непрерывная цепочка — одна линия, и двигать её надо целиком, с головы. Стык
     * «машина ↔ лента» остался прежним и обслуживается {@link #moveItems}, поэтому бур,
     * печь и сборщик не знают, что ленты переписаны.
     */
    private void moveBelts(TickContext ctx) {
        world.belts().step(ctx.balance().beltSlotsPerTick());
    }

    /**
     * Система №3: передача предметов между зданиями.
     *
     * <p>Приём в две фазы:
     * <ol>
     *   <li><b>Планируем</b>, только ЧИТАЯ поле: собираем список передач.</li>
     *   <li><b>Применяем</b>, меняя поле.</li>
     * </ol>
     * Так получается честная «одновременная» передача без артефактов порядка обхода.
     * Штамп тика на клетке гарантирует, что за тик в неё отдаст предмет только один
     * сосед — иначе две ленты пропихнули бы два предмета в один слот.
     */
    private void moveItems() {
        moves.clear(); // очистить, а не создать заново

        // Фаза 1: планирование (только чтение).
        world.forEachBuilding((x, y, source) -> {
            Optional<Handoff> handoff = source.output();
            if (handoff.isEmpty()) {
                return;
            }
            Item item = handoff.get().item();
            Building receiver = world.neighborBuilding(x, y, handoff.get().direction());
            if (receiver == null || !receiver.canAccept(item)) {
                return;
            }
            // Столбим клетку последней: если сосед успел раньше — передачи не будет,
            // и «занимать» её мы не имеем права.
            if (world.claimNeighbor(x, y, handoff.get().direction())) {
                moves.add(new Move(source, receiver, item));
            }
        });

        // Фаза 2: применение (можно менять).
        for (Move move : moves) {
            move.source().removeOutput();
            move.target().accept(move.item());
        }
    }

    /** Запланированная передача предмета от здания-источника к приёмнику. */
    private record Move(Building source, Building target, Item item) {
    }
}
