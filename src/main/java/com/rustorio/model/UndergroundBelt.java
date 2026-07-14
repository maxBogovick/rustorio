package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Подземная лента: предмет ныряет под постройки и выходит через несколько клеток.
 *
 * <p><b>Почему подземка не «телепорт».</b> Соблазн: принял предмет на входе — сразу выдал на
 * выходе. Тогда четыре клетки преодолеваются за один тик вместо четырёх, подземка становится
 * СТРОГО быстрее обычной ленты, и вся игра уезжает под землю. Механика ломается.
 *
 * <p>Значит, предмет обязан ЕХАТЬ под землёй — несколько тиков, со своей позицией, не
 * обгоняя соседей. Это дословное определение транспортной линии: подземка не обходит
 * {@link BeltSegment}, а требует такой же логики. Здесь она в упрощённом виде — очередь с
 * таймерами, потому что под землёй нечего рисовать и не о что спотыкаться.
 *
 * <p><b>Роль (вход или выход) — не свойство здания, а свойство МЕСТА.</b> Здание не знает
 * своих координат, поэтому пару находит симуляция: она видит мир и каждый тик сообщает
 * подземке её роль. То же решение, что с буром и рудой.
 */
public final class UndergroundBelt implements Building {

    /** Кто я в паре: вход, выход или одиночка (пары нет — значит, ничего не делаю). */
    public enum Role { NONE, ENTRANCE, EXIT }

    private final Direction dir;

    private Role role = Role.NONE;
    /** Сколько тиков предмет едет под землёй (считает симуляция из расстояния и скорости). */
    private int travelTicks = 1;
    /** Сколько предметов помещается в трубе. */
    private int capacity;

    /** Предметы в пути: у каждого свой обратный отсчёт. */
    private final Deque<Transit> transit = new ArrayDeque<>();
    /** Предметы, доехавшие до выхода и ждущие соседа. */
    private final Deque<Item> arrived = new ArrayDeque<>();

    public UndergroundBelt(Direction dir) {
        this.dir = dir;
    }

    @Override
    public void update(TickContext ctx) {
        // Движение под землёй ведёт фаза подземок в симуляции: только она знает про пару.
    }

    /** Отдаёт наружу только ВЫХОД, и только то, что уже доехало. */
    @Override
    public Optional<Handoff> output() {
        Item item = arrived.peek();
        return item == null ? Optional.empty() : Optional.of(new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item incoming) {
        return role == Role.ENTRANCE && transit.size() < capacity;
    }

    @Override
    public void accept(Item incoming) {
        transit.add(new Transit(incoming, travelTicks));
    }

    @Override
    public void removeOutput() {
        arrived.poll();
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    // ── Для фазы подземок в симуляции ─────────────────────────────────

    /**
     * Симуляция сообщает подземке, кто она и как далеко её пара.
     *
     * @param distance расстояние до пары в клетках (для входа); 0 — если пары нет
     */
    public void setRole(Role role, int distance, int slotsPerTick) {
        this.role = role;
        if (role == Role.ENTRANCE) {
            // Ровно столько же слотов, сколько предмет проехал бы ПО ЗЕМЛЕ на том же отрезке:
            // от входного слота первой клетки до последнего слота клетки-выхода. Возьмёшь
            // меньше — подземка станет быстрее ленты, и вся игра уедет под землю.
            int slots = distance * Config.SLOTS_PER_TILE + (Config.SLOTS_PER_TILE - 1);
            this.travelTicks = Math.max(1, (slots + slotsPerTick - 1) / slotsPerTick);
            this.capacity = Math.max(1, distance);
        } else {
            this.capacity = 0;
        }
    }

    public Role role() {
        return role;
    }

    /**
     * Продвинуть предметы под землёй на тик.
     *
     * @return предметы, доехавшие до конца (их симуляция передаст выходу)
     */
    public List<Item> advance() {
        List<Item> done = new ArrayList<>();
        for (Transit t : transit) {
            t.ticksLeft--;
        }
        while (!transit.isEmpty() && transit.peek().ticksLeft <= 0) {
            done.add(transit.poll().item);
        }
        return done;
    }

    /** Предмет доехал: положить его на выход. */
    public void deliver(Item item) {
        arrived.add(item);
    }

    public Direction dir() {
        return dir;
    }

    /** Сколько предметов сейчас под землёй (для тестов и инварианта «ничего не потеряно»). */
    public int inTransit() {
        return transit.size() + arrived.size();
    }

    private static final class Transit {
        final Item item;
        int ticksLeft;

        Transit(Item item, int ticksLeft) {
            this.item = item;
            this.ticksLeft = ticksLeft;
        }
    }
}
