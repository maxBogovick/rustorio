package com.rustorio.model;

import com.rustorio.core.Appearance;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import com.rustorio.core.Tool;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Optional;

/**
 * Развилка: принимает предмет и отдаёт его по очереди в разные стороны.
 *
 * <p><b>Почему сплиттер НЕ пользуется общим протоколом {@code output()}.</b> Тот отдаёт ОДИН
 * {@link Handoff} — то есть одно направление. Сплиттеру этого мало: его суть в том, чтобы
 * «левый выход занят → отдаю в правый <b>в этом же тике</b>». Через {@code output()} он мог
 * бы назвать только одно направление и узнал бы об отказе лишь на следующем тике — то есть
 * терял бы тик на каждом переключении, а при забитом выходе просел бы вдвое. Необъяснимо для
 * игрока.
 *
 * <p>Поэтому {@code output()} у сплиттера ВСЕГДА пуст, а его предмет двигает отдельная фаза
 * симуляции: у неё есть доступ к миру, и она может перебрать выходы по кругу, пропуская
 * занятые. Договор {@code Building} при этом не тронут — он заморожен и остаётся общим для
 * машин и лент.
 *
 * <p><b>Куда отдаёт.</b> Прямо (по своему направлению), затем направо, затем налево — по
 * кругу, пропуская занятые. Это «мягкая» очередь, а не честный балансировщик: так делает
 * большинство игр жанра, и это предсказуемо для игрока.
 */
public final class Splitter implements Building {

    private final Direction dir;
    /** Единственный предмет внутри (или {@code null}). */
    private @Nullable Item item;
    /** С какого выхода начинать в следующий раз — это и есть «по очереди». */
    private int nextOutput;

    public Splitter(Direction dir) {
        this.dir = dir;
    }

    /** Восстановить развилку с удерживаемым предметом и позицией круга — для загрузки (B2). */
    public Splitter(Direction dir, @Nullable Item item, int nextOutput) {
        this.dir = dir;
        this.item = item;
        this.nextOutput = nextOutput;
    }

    /** Удерживаемый предмет для снимка ({@code null}, если пусто). */
    public @Nullable Item heldItem() {
        return item;
    }

    /** С какого выхода начнём в следующий раз — для снимка. */
    public int nextOutput() {
        return nextOutput;
    }

    @Override
    public void update(TickContext ctx) {
        // Сплиттер сам ничего не делает: его предмет двигает фаза сплиттеров в симуляции.
    }

    /** Всегда пусто: см. javadoc класса. Предмет забирает фаза сплиттеров. */
    @Override
    public Optional<Handoff> output() {
        return Optional.empty();
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
        // Предмет забирает фаза сплиттеров через take().
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    @Override
    public Tool tool() {
        return Tool.SPLITTER;
    }

    @Override
    public Appearance appearance() {
        // Накладок нет: направление читается по повороту спрайта, а не по стрелке.
        return Appearance.of("Splitter");
    }

    // ── Для фазы сплиттеров в симуляции ───────────────────────────────

    /** Предмет, ждущий отправки. */
    public Optional<Item> held() {
        return Optional.ofNullable(item);
    }

    /** Куда сплиттер пробует отдать — по кругу, начиная с текущего. */
    public List<Direction> outputsInOrder() {
        List<Direction> all = List.of(dir, right(dir), left(dir));
        return List.of(all.get(nextOutput),
                all.get((nextOutput + 1) % 3),
                all.get((nextOutput + 2) % 3));
    }

    /**
     * Предмет ушёл в направлении {@code taken}: убрать его и сдвинуть очередь.
     *
     * <p>Очередь сдвигается на выход, СЛЕДУЮЩИЙ за использованным, а не «на один вперёд от
     * прошлого». Иначе после пропуска занятого выхода круг сбивался бы, и поток перестал бы
     * делиться поровну.
     */
    public void take(Direction taken) {
        List<Direction> all = List.of(dir, right(dir), left(dir));
        nextOutput = (all.indexOf(taken) + 1) % 3;
        item = null;
    }

    public Direction dir() {
        return dir;
    }

    private static Direction right(Direction d) {
        return d.rotateCw();
    }

    private static Direction left(Direction d) {
        return d.rotateCw().rotateCw().rotateCw();
    }
}
