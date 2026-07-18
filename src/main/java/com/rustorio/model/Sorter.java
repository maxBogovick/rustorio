package com.rustorio.model;

import com.rustorio.core.Appearance;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import com.rustorio.model.routing.RoutingPolicy;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Сортировщик: принимает один предмет и отдаёт его в тот из трёх выходов, который назначит
 * его {@link RoutingPolicy}.
 *
 * <p><b>Здесь живёт шаблон «Стратегия».</b> Обратите внимание, чего этот класс НЕ делает: он
 * не решает, куда направить предмет. Он держит политику и спрашивает её ({@link #output()}).
 * Поменяйте политику — поменяется поведение, а код здания не изменится ни на строчку. Именно
 * это и есть Стратегия: «алгоритм как объект, который можно подставить».
 *
 * <p><b>Почему это лучше, чем ветки внутри здания.</b> Правил маршрутизации — семейство. Если
 * бы {@code Sorter} сам перебирал их в {@code switch}, он знал бы обо всех сразу, рос с каждым
 * новым правилом, и одно правило нельзя было бы понять в отрыве от здания. Вынесенная за
 * интерфейс политика разрывает это: см. {@link RoutingPolicy}.
 *
 * <p><b>Как встроен в игру.</b> Сортировщик играет по ОБЫЧНОМУ протоколу {@link Building}:
 * {@link #output()} называет предмет и направление (которое дала политика), а стандартная фаза
 * передачи сама доставит его соседу. Никакой отдельной фазы в {@code Simulation} не нужно — и
 * это важное отличие от {@link Splitter}, которому своя фаза нужна, потому что он в один тик
 * перебирает выходы. Сортировщику перебор не нужен: занят его выход — предмет ЖДЁТ, и это
 * правильно (руда не должна утечь в чужую ленту).
 */
public final class Sorter implements Building {

    private final Direction dir;
    /** Стратегия маршрутизации. Здание её только хранит и спрашивает — не подменяет собой. */
    private final RoutingPolicy policy;
    /** Единственный предмет внутри (или {@code null}). */
    private @Nullable Item item;

    public Sorter(Direction dir, RoutingPolicy policy) {
        this.dir = dir;
        this.policy = policy;
    }

    /** Восстановить сортировщик с удерживаемым предметом — для загрузки сохранения. */
    public Sorter(Direction dir, RoutingPolicy policy, @Nullable Item item) {
        this(dir, policy);
        this.item = item;
    }

    @Override
    public void update(TickContext ctx) {
        // Пассивен: держит предмет, отдаёт через output(). Решение о направлении — у политики.
    }

    @Override
    public Optional<Handoff> output() {
        if (item == null) {
            return Optional.empty();
        }
        // Считаем три выхода и отдаём ВЫБОР политике. Здание — про геометрию, политика — про смысл.
        Direction right = dir.rotateCw();
        Direction left = right.rotateCw().rotateCw();
        return Optional.of(new Handoff(item, policy.route(item, dir, right, left)));
    }

    @Override
    public boolean canAccept(Item incoming) {
        return item == null;
    }

    @Override
    public void accept(Item incoming) {
        this.item = incoming;
    }

    @Override
    public void removeOutput() {
        this.item = null;
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    @Override
    public Tool tool() {
        return Tool.SORTER;
    }

    @Override
    public Appearance appearance() {
        // Стрелка — куда «смотрит» здание (реальный выход зависит от предмета); иконка — что внутри.
        return Appearance.of("Sorter")
                .arrow(dir, item != null)
                .icon(item);
    }

    // ── Чтение для сохранения ─────────────────────────────────────────
    public Direction dir() {
        return dir;
    }

    public RoutingPolicy policy() {
        return policy;
    }

    /** Удерживаемый предмет ({@code null}, если пусто) — для снимка. */
    public @Nullable Item heldItem() {
        return item;
    }
}
