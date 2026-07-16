package com.rustorio.model;

import com.rustorio.core.Config;
import com.rustorio.core.Direction;
import com.rustorio.core.Item;
import com.rustorio.core.TickContext;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Клетка ленты. Сама почти ничего не хранит: предметы живут в
 * {@link BeltSegment} — транспортной линии, куда эта клетка входит.
 *
 * <p><b>Почему привязка к линии происходит не в конструкторе.</b>
 * {@link Building#create} делает {@code new Belt(dir)}, ещё НЕ ЗНАЯ, куда ленту
 * поставят: координаты появляются только внутри {@link World#place}. Поэтому мир,
 * поставив ленту, сообщает об этом {@link BeltNetwork}, а та решает — начать новую
 * линию или удлинить соседнюю — и вызывает {@link #attach}.
 *
 * <p>Наружу клетка ленты остаётся обычным зданием: договор
 * {@code output/canAccept/accept} не изменился, поэтому бур, печь и сборщик
 * ничего не знают о том, что ленты переписаны.
 */
public final class Belt implements Building {

    private final Direction dir;
    /** Линия, в которую входит эта клетка (ставится при постройке). */
    private @Nullable BeltSegment segment;
    /** Какая я по счёту клетка в своей линии (0 — хвост). */
    private int indexInSegment = -1;

    public Belt(Direction dir) {
        this.dir = dir;
    }

    // ── Привязка к линии (этим управляет только BeltNetwork) ──────────
    void attach(BeltSegment segment, int indexInSegment) {
        this.segment = segment;
        this.indexInSegment = indexInSegment;
    }

    void detach() {
        this.segment = null;
        this.indexInSegment = -1;
    }

    public @Nullable BeltSegment segment() {
        return segment;
    }

    public int indexInSegment() {
        return indexInSegment;
    }

    // ── Здание ────────────────────────────────────────────────────────

    @Override
    public void update(TickContext ctx) {
        // Лента сама не работает: предметы двигает система moveBelts в Simulation.
    }

    /**
     * Отдаёт наружу только ГОЛОВНАЯ клетка линии и только тогда, когда предмет
     * доехал до последнего слота. Все остальные клетки линии молчат — иначе
     * предмет «вылезал» бы из середины ленты.
     */
    @Override
    public Optional<Handoff> output() {
        if (segment == null || indexInSegment != segment.tileCount() - 1) {
            return Optional.empty();
        }
        return segment.headItem().map(item -> new Handoff(item, dir));
    }

    @Override
    public boolean canAccept(Item incoming) {
        return segment != null && segment.canInsertAt(entrySlot());
    }

    @Override
    public void accept(Item incoming) {
        if (segment == null) {
            throw new IllegalStateException("лента не привязана к линии");
        }
        segment.insert(incoming, entrySlot());
    }

    @Override
    public void removeOutput() {
        if (segment != null) {
            segment.removeHeadItem();
        }
    }

    @Override
    public Optional<Direction> direction() {
        return Optional.of(dir);
    }

    /** Предмет попадает на ленту в ПЕРВЫЙ слот своей клетки — «с заднего края». */
    private int entrySlot() {
        return indexInSegment * Config.SLOTS_PER_TILE;
    }

    // ── Чтение для отрисовки и тестов ─────────────────────────────────
    public Direction dir() {
        return dir;
    }

    /** Передний предмет на ЭТОЙ клетке, если есть. */
    public Optional<Item> item() {
        return segment == null ? Optional.empty() : segment.itemOnTile(indexInSegment);
    }
}
