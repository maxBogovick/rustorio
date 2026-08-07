package com.webminer;

import java.util.function.Consumer;

/**
 * Runs an HTTP request for {@link World} without {@code World} — or anything else in this package
 * — ever importing an HTTP client. Real network I/O is blocking and non-deterministic (a slow or
 * unreachable host, a response that differs between runs), so it can never happen on the tick
 * thread: {@code AGENTS.md}'s "любая многопоточность в тике — нельзя" is about the tick loop
 * ITSELF staying single-threaded and non-blocking, not about forbidding this port. {@link #submit}
 * is expected to return immediately and invoke {@code onComplete} later, from whatever thread the
 * implementation runs on — {@code World} only ever touches the result through thread-safe
 * collections (see {@code World#requestFetch}/{@code World#pollFetch}), never by mutating its own
 * state from inside {@code onComplete} directly.
 *
 * <p>The real implementation (a background {@code java.net.http.HttpClient}) lives outside the
 * domain entirely, same as the rendering layer does for graphics — this interface is the whole
 * vocabulary a building needs, mirroring how narrow {@link TickContext} itself is against {@code
 * World}. Tests use a fake that never calls back at all, to prove the tick loop survives a request
 * that never resolves.
 */
public interface FetchExecutor {

    /**
     * Never calls back — the default a {@link World} gets when nothing wires in a real one (tests,
     * dev tools, any world that never places a {@link WebMiner}). Not an error: a {@code WebMiner}
     * with no working executor behind it just idles pending forever, the same soft-degradation
     * discipline {@link Miner} already follows for a cell with no ore.
     */
    FetchExecutor NOOP = (url, onComplete) -> { };

    /** Start fetching {@code url}; call {@code onComplete} exactly once, later, with the result. */
    void submit(String url, Consumer<FetchResult> onComplete);
}
