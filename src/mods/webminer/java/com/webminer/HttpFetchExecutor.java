package com.webminer;


import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;

/**
 * The real {@link FetchExecutor}: an actual outbound HTTP GET, run on a virtual thread per request
 * so whatever called {@link com.rustorio.domain.world.World#requestFetch} — the tick thread —
 * never waits on it. The one class in this whole slice allowed to import {@code java.net.http};
 * see {@link FetchExecutor}'s own javadoc for why that boundary exists at all.
 *
 * <p><b>Virtual threads, not a platform-thread pool.</b> {@link HttpClient#send} blocks its calling
 * thread for the whole round-trip; a fetch is exactly the "many concurrent blocking I/O calls"
 * workload virtual threads exist for (JEP 444) — {@code synchronized} no longer pins a virtual
 * thread to its carrier since JEP 491 (JDK 24), which is what made this safe to do unconditionally
 * rather than needing to audit {@link HttpClient}'s own internals for pinning first. Every virtual
 * thread is a daemon thread by construction, so unlike the platform-thread pool this replaces,
 * nothing here needs a custom {@code ThreadFactory} just to stop it keeping the JVM alive.
 *
 * <p><b>{@link #concurrencyLimiter} is the actual safety net</b> — virtual threads being cheap to
 * create is exactly why they do NOT, by themselves, cap how many outbound connections a thousand
 * simultaneously-placed {@code WebMiner}s could open in the same instant (a live bug report: this
 * class used to have no limit at all here, only {@link WebMiner}'s own per-instance cooldown, which
 * bounds how OFTEN one miner asks, never how many miners can all ask AT ONCE). {@link
 * #MAX_CONCURRENT_REQUESTS} bounds real simultaneous connections to whatever hosts miners are
 * configured with, independent of miner count or timing — {@code Semaphore} rather than a bounded
 * queue because a virtual thread blocked on {@link Semaphore#acquire} costs nothing to leave
 * waiting, unlike a platform thread.
 */
public final class HttpFetchExecutor implements FetchExecutor, AutoCloseable {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** How many outbound HTTP requests may be genuinely in flight at once, across every miner combined — see the class javadoc. */
    private static final int MAX_CONCURRENT_REQUESTS = 16;
    /**
     * How much of a response body {@link FetchResult#body} ever retains — a Monitor/Interpreter
     * reads this to display or extract a handful of JSON fields, not to archive an endpoint's full
     * output, and an unbounded body is real memory an arbitrary (or hostile) endpoint could spend on
     * the game process. Truncates AFTER download, not during — {@link HttpResponse.BodyHandlers}
     * has no built-in "stop after N bytes," so this caps what's RETAINED, not what's transferred.
     */
    private static final int MAX_BODY_LENGTH = 8192;

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(TIMEOUT)
            .build();

    private final Semaphore concurrencyLimiter = new Semaphore(MAX_CONCURRENT_REQUESTS);
    private final ExecutorService background = Executors.newVirtualThreadPerTaskExecutor();

    @Override
    public void submit(String url, Consumer<FetchResult> onComplete) {
        background.submit(() -> onComplete.accept(fetchWithinLimit(url)));
    }

    /**
     * Waits for a free slot under {@link #concurrencyLimiter}, then runs {@link #fetch} — the wait
     * itself is exactly as cheap as the fetch itself to leave a virtual thread parked in, which is
     * the whole reason this can enforce a real cap without a bounded queue rejecting excess work.
     */
    private FetchResult fetchWithinLimit(String url) {
        try {
            concurrencyLimiter.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FetchResult(FetchOutcome.ERROR, null);
        }
        try {
            return fetch(url);
        } finally {
            concurrencyLimiter.release();
        }
    }

    /**
     * Runs on a background (virtual) thread only — never called from {@link #submit} synchronously.
     * Every real-world failure mode (bad host, refused connection, timeout, a malformed URL the
     * player typed) collapses to the same {@link FetchOutcome#ERROR} a {@link WebMiner} already
     * knows how to show on the belt; none of them are exceptional from this class's own point of
     * view. {@code body} is only ever non-null on {@link FetchOutcome#SUCCESS} — an error response's
     * own body (if any) isn't useful to a Monitor/Interpreter that only sees a fetch as pass/fail.
     */
    private FetchResult fetch(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url)).timeout(TIMEOUT).GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return new FetchResult(FetchOutcome.ERROR, null);
            }
            String body = response.body();
            return new FetchResult(FetchOutcome.SUCCESS,
                    body.length() > MAX_BODY_LENGTH ? body.substring(0, MAX_BODY_LENGTH) : body);
        } catch (IOException e) {
            return new FetchResult(FetchOutcome.ERROR, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FetchResult(FetchOutcome.ERROR, null);
        } catch (IllegalArgumentException e) {
            return new FetchResult(FetchOutcome.ERROR, null); // not a valid URI — the player's own typo, same as an unreachable host
        }
    }

    /** Stops accepting new fetches and abandons any in flight — called once, on shutdown (see {@code GameScreen#dispose}). */
    @Override
    public void close() {
        background.shutdownNow();
    }
}
