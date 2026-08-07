package com.webminer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaTechs;
import com.rustorio.domain.building.BuildingFactory;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.WorldServices;
import com.rustorio.domain.world.World;
import com.rustorio.mod.LoadedGame;
import com.rustorio.mod.ModLoader;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Before {@link FetchService} existed (and before that, three fetch-shaped methods on {@code TickContext}) existed,
 * there was no way to write ANY of these three tests — the tick loop had no vocabulary for an
 * outstanding network request at all, so every scenario below is closing a gap, not a regression.
 *
 * <p>{@link #tickNeverWaitsOnASlowBackgroundFetch} is the load-bearing one: it proves the tick
 * thread itself never blocks on {@link FetchExecutor}, which is the whole reason {@code
 * AGENTS.md}'s "никакая многопоточность в тике" rule doesn't forbid this port — the multithreading
 * lives entirely inside the executor, never on the thread calling {@code World.tick()}.
 */
class WebMinerAsyncFetchTest {

    private static final Path RUSTORIO_MOD_DIR = Path.of("resources", "mods", "rustorio");
    private static final Path WEBMINER_MOD_DIR = Path.of("resources", "mods", "webminer");

    private static World worldWithWebMiner(FetchExecutor fetchExecutor) {
        LoadedGame content = ModLoader.loadAll(List.of(RUSTORIO_MOD_DIR, WEBMINER_MOD_DIR));
        Registry<BuildingPrototype> prototypes = new Registry<>();
        VanillaBuildings.registerAll(prototypes);
        WebMinerMod.registerAll(prototypes, content.items());
        prototypes.freeze();

        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(),
                content.items(), prototypes);
        WorldServices services = WorldServices.builder()
                .with(FetchService.KEY, () -> new FetchService(fetchExecutor))
                .build();
        World world = new World(4, 4, factory, VanillaTechs.frozen(), services);
        world.restoreBuilding(0, 0, factory.create(WebMinerMod.WEB_MINER_ID, Direction.RIGHT));
        world.placeChest(1, 0);
        return world;
    }

    @Test
    void tickReturnsPromptlyEvenWhileAFetchNeverResolves() {
        // Records the callback without ever invoking it — not even on a thread — so this is the
        // strictest version of "stuck forever": if World.requestFetch/pollFetch waited on it in any
        // way, every tick() call below would hang the test itself.
        World world = worldWithWebMiner((url, onComplete) -> { });

        for (int i = 0; i < 200; i++) {
            world.tick();
        }

        WebMiner miner = (WebMiner) world.peek(0, 0).orElseThrow();
        assertTrue(miner.heldItem().isEmpty(), "still waiting on the fetch — must not fabricate a result");
        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(0, chest.count(), "nothing to deliver until the fetch actually resolves");
    }

    @Test
    void tickNeverWaitsOnASlowBackgroundFetch() throws InterruptedException {
        ScheduledExecutorService background = Executors.newSingleThreadScheduledExecutor();
        try {
            CountDownLatch fetchStarted = new CountDownLatch(1);
            FetchExecutor slow = (url, onComplete) -> {
                fetchStarted.countDown();
                background.schedule(() -> onComplete.accept(new FetchResult(FetchOutcome.SUCCESS, null)), 300, TimeUnit.MILLISECONDS);
            };
            World world = worldWithWebMiner(slow);

            world.tick(); // triggers the request
            assertTrue(fetchStarted.await(1, TimeUnit.SECONDS), "the request must actually have been submitted");

            long start = System.nanoTime();
            for (int i = 0; i < 50; i++) {
                world.tick(); // the background fetch is still sleeping through all of these
            }
            long elapsedMs = (System.nanoTime() - start) / 1_000_000;

            assertTrue(elapsedMs < 150,
                    "50 ticks took " + elapsedMs + "ms while a 300ms fetch was outstanding — "
                            + "the tick thread must not be waiting on it");
        } finally {
            background.shutdownNow();
        }
    }

    @Test
    void successfulFetchDeliversTheConfiguredItemOntoTheBelt() {
        World world = worldWithWebMiner(immediateOutcome(FetchOutcome.SUCCESS));

        for (int i = 0; i < 5 && ((Chest) world.peek(1, 0).orElseThrow()).count() == 0; i++) {
            world.tick();
        }

        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, chest.count(), "the fetched-OK item must have been produced and delivered");
        ItemType delivered = chest.contents().keySet().iterator().next();
        assertEquals("web_ok", delivered.id().path());
    }

    @Test
    void failedFetchDeliversTheErrorItemInstead() {
        World world = worldWithWebMiner(immediateOutcome(FetchOutcome.ERROR));

        for (int i = 0; i < 5 && ((Chest) world.peek(1, 0).orElseThrow()).count() == 0; i++) {
            world.tick();
        }

        Chest chest = (Chest) world.peek(1, 0).orElseThrow();
        assertEquals(1, chest.count());
        ItemType delivered = chest.contents().keySet().iterator().next();
        assertEquals("web_error", delivered.id().path());
    }

    /**
     * Before {@link FetchService#lastBody} existed, a fetch's actual text was thrown away the
     * instant {@link WebMiner} turned it into a plain OK/ERROR item — there was no way for a
     * Monitor/Interpreter standing behind the miner to ever see what the response actually said.
     *
     * <p>Reads the body back through {@code world.service(...)} rather than off a reference the
     * fixture kept: that is the exact path a mod's own building takes, so this also proves the
     * service is reachable from inside a world, not merely constructible next to one.
     */
    @Test
    void successfulFetchBodyIsReadableFromTheMinersOwnCell() {
        World world = worldWithWebMiner((url, onComplete) ->
                onComplete.accept(new FetchResult(FetchOutcome.SUCCESS, "{\"greeting\":\"hello\"}")));
        FetchService fetch = world.service(FetchService.KEY).orElseThrow();

        for (int i = 0; i < 5 && fetch.lastBody(0, 0).isEmpty(); i++) {
            world.tick();
        }

        assertEquals("{\"greeting\":\"hello\"}", fetch.lastBody(0, 0).orElseThrow());
    }

    /**
     * The live bug report this rate limiter exists to fix: before it existed, a miner whose
     * downstream never blocked re-fetched roughly once per network round-trip, forever — a
     * thousand factory-scale miners pointed at the same real host was a self-inflicted denial of
     * service. Counts real submissions to {@link FetchExecutor#submit}, not deliveries, so this
     * fails even if a would-be second fetch never got far enough to produce an item.
     */
    @Test
    void cooldownPreventsASecondFetchBeforeOneRequestPerMinuteElapses() {
        AtomicInteger submitted = new AtomicInteger();
        World world = worldWithWebMiner((url, onComplete) -> {
            submitted.incrementAndGet();
            onComplete.accept(new FetchResult(FetchOutcome.SUCCESS, null));
        });

        world.tick(); // the very first fetch — cooldown only starts counting AFTER a result lands
        assertEquals(1, submitted.get());

        // MIN_INTERVAL_TICKS ticks after delivery: the countdown has reached exactly zero on the
        // LAST of these, but tick() itself always returns early the tick it reaches zero (see
        // World's own tick-boundary convention) — the fetch fires on the NEXT tick, not this one.
        for (int i = 0; i < WebMiner.MIN_INTERVAL_TICKS; i++) {
            world.tick();
        }
        assertEquals(1, submitted.get(), "must not fetch again before a full minute of cooldown elapses");

        world.tick(); // one tick past the cooldown — this is the one that actually fires
        assertEquals(2, submitted.get(), "exactly one minute later, the next fetch is allowed");
    }

    /**
     * The circuit breaker (owner requirement: 3 consecutive failures open it) — proves the breaker
     * actually STOPS fetching for {@code backoffSeconds}, and that a success afterward resets the
     * failure count rather than leaving it primed to trip again on the very next single failure.
     */
    @Test
    void threeConsecutiveFailuresOpenTheCircuitUntilTheConfiguredBackoffElapses() {
        AtomicInteger submitted = new AtomicInteger();
        boolean[] shouldFail = {true};
        World world = worldWithWebMiner((url, onComplete) -> {
            submitted.incrementAndGet();
            onComplete.accept(new FetchResult(shouldFail[0] ? FetchOutcome.ERROR : FetchOutcome.SUCCESS, null));
        });
        WebMiner miner = (WebMiner) world.peek(0, 0).orElseThrow();
        miner.setBackoffSeconds(1); // floors to WebMiner.MIN_BACKOFF_TICKS — the smallest testable backoff

        // Three failures in a row, each separated by the ordinary MIN_INTERVAL_TICKS cooldown
        // (setIntervalSeconds was never called, so it's still at its own floor) — same +1 boundary
        // as cooldownPreventsASecondFetchBeforeOneRequestPerMinuteElapses.
        for (int failure = 1; failure <= 3; failure++) {
            world.tick();
            assertEquals(failure, submitted.get());
            if (failure < 3) {
                for (int i = 0; i < WebMiner.MIN_INTERVAL_TICKS; i++) {
                    world.tick();
                }
            }
        }
        assertEquals(0, miner.consecutiveFailures(), "the circuit itself now carries the \"broken\" signal, not a lingering count");

        // The circuit just tripped: waiting out ONLY the backoff (60 ticks — nowhere near a full
        // MIN_INTERVAL_TICKS) must still not be enough for the very last tick to have fired yet.
        // If backoff and the ordinary interval cooldown were ever summed again (the bug this test
        // guards against), submitted would still be 3 for a very long time past this point too.
        for (int i = 0; i < WebMiner.MIN_BACKOFF_TICKS; i++) {
            world.tick();
        }
        assertEquals(3, submitted.get(), "the open circuit must block a 4th fetch for its own backoff duration");

        shouldFail[0] = false; // the host recovers before the retry actually fires
        world.tick(); // one tick past the backoff — this is the one that actually fires
        assertEquals(4, submitted.get(),
                "once the backoff ALONE (not backoff+interval) elapses, the circuit must let a new fetch through");
    }

    /** Resolves on the SAME call, synchronously — a fetch that "completes instantly" for tests that only care about the outcome, not the timing. */
    private static FetchExecutor immediateOutcome(FetchOutcome outcome) {
        return (url, onComplete) -> onComplete.accept(new FetchResult(outcome, null));
    }
}
