package com.webminer;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.ServiceKey;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The capability this mod registers with a world: "fetch a URL for the building standing on this
 * cell, and remember what came back." Reached through {@link com.rustorio.domain.building.TickContext#service}
 * under {@link #KEY} — the engine knows only that SOME service is registered under SOME id, never
 * that fetching exists.
 *
 * <p>Every field here used to be a field of {@code World} itself, alongside three {@code
 * TickContext} methods that named this mod's vocabulary in the engine's narrowest public contract.
 * They are per-cell bookkeeping for one archetype, and they belong to the archetype.
 *
 * <p><b>Threading.</b> {@link #request} runs on the tick thread; the callback {@link #executor}
 * invokes runs on the executor's own background thread. Those two meet only in the three
 * concurrent collections below — the tick thread never mutates anything else from inside a
 * callback, which is what keeps the rest of the world safe to touch without synchronization. The
 * collections are concurrent for exactly that reason and for no other; nothing here is contended
 * enough to need more.
 */
public final class FetchService implements AutoCloseable {

    /** How a building asks a world for this service — see {@link ServiceKey}. */
    public static final ServiceKey<FetchService> KEY =
            new ServiceKey<>(ContentId.of("webminer:fetch"), FetchService.class);

    /** A cell, as a map key. Its own record rather than {@code World.Coord} because that type belongs to a package this mod must not reach into — a mod keys its own maps by its own type. */
    private record Cell(int x, int y) {
    }

    private final FetchExecutor executor;

    /** Cells whose fetch is in flight, or resolved but not yet collected by {@link #poll} — the guard that stops a building calling {@link #request} every tick from queueing a second request for the first. */
    private final Set<Cell> pending = ConcurrentHashMap.newKeySet();

    /** Resolved outcomes waiting to be collected exactly once — written from the executor's thread, removed only from the tick thread. */
    private final Map<Cell, FetchOutcome> completed = new ConcurrentHashMap<>();

    /**
     * The most recent SUCCESSFUL response body per cell — what a downstream reader (a {@link
     * Monitor} or {@link Interpreter} standing in front of a {@link WebMiner}) shows. Deliberately
     * one entry per cell rather than a history, and deliberately not persisted: a response body can
     * be arbitrarily large, and this answers "what did the miner behind me just fetch," not "what
     * has it ever fetched." Overwritten by that same cell's NEXT fetch, which {@link
     * WebMiner#MIN_INTERVAL_TICKS} keeps at least a real minute away.
     */
    private final Map<Cell, String> lastBody = new ConcurrentHashMap<>();

    public FetchService(FetchExecutor executor) {
        this.executor = executor;
    }

    /**
     * Start fetching {@code url} for the building on {@code (x, y)}, unless one is already
     * outstanding there — a no-op in that case, so a building may call this every tick until it
     * sees a result without ever queueing a second request.
     */
    public void request(int x, int y, String url) {
        Cell cell = new Cell(x, y);
        if (!pending.add(cell)) {
            return;
        }
        executor.submit(url, result -> {
            String body = result.body();
            if (body != null) {
                lastBody.put(cell, body);
            }
            // Written LAST: a reader that sees the outcome is guaranteed to already see the body
            // that came with it. The other order would let a Monitor poll a success and find no
            // body for a window of a few instructions.
            completed.put(cell, result.outcome());
        });
    }

    /**
     * Collect and consume {@code (x, y)}'s outcome if it has resolved — empty while in flight, and
     * empty again after one collection. Clearing {@link #pending} only here is what makes an
     * outcome collectible exactly once and lets the NEXT {@link #request} through.
     */
    public Optional<FetchOutcome> poll(int x, int y) {
        Cell cell = new Cell(x, y);
        FetchOutcome outcome = completed.remove(cell);
        if (outcome != null) {
            pending.remove(cell);
        }
        return Optional.ofNullable(outcome);
    }

    /**
     * The last body fetched FROM {@code (x, y)} — unlike {@link #poll}, reading this does NOT
     * consume it: a Monitor and an Interpreter can both watch the same miner and must both see the
     * same body, every tick, not once between them.
     */
    public Optional<String> lastBody(int x, int y) {
        return Optional.ofNullable(lastBody.get(new Cell(x, y)));
    }

    /** Shuts the executor down — called by {@code WorldServices.closeAll} when the game owning this world goes away. */
    @Override
    public void close() throws Exception {
        if (executor instanceof AutoCloseable closeable) {
            closeable.close();
        }
    }
}
