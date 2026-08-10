package com.webminer;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.FieldSpec;
import com.rustorio.domain.building.FieldType;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.TraitKey;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Sits anywhere passable and repeatedly fetches {@link #url} through this mod's own {@link
 * FetchService} — the same "hold until delivered" discipline a miner follows for ore, except what
 * it mines is a real HTTP response instead of a cell's contents.
 *
 * <p>The fetch itself never touches this class, or the tick thread at all — see {@link
 * FetchExecutor}'s own javadoc for why. {@link #tick} only ever does two cheap, non-blocking
 * things: ask {@link FetchService#request} for {@link #url} (a no-op while one is already
 * outstanding for this cell) and ask {@link FetchService#poll} whether it resolved yet. A slow or
 * unreachable host just means more ticks pass with nothing held — the same soft degradation a miner
 * shows on a cell with no ore, not a stall or an exception.
 *
 * <p>{@link #successItem}/{@link #errorItem} are which item {@link #tick} produces for {@link
 * FetchOutcome#SUCCESS}/{@link FetchOutcome#ERROR} — read off this miner's own {@link #prototype}'s
 * traits ({@link #SUCCESS_ITEM}/{@link #ERROR_ITEM}) at construction, the same "traits, not a new
 * constructor rung" extension point the engine already uses for a machine-specific property with no
 * vanilla meaning. Content never carries the response body itself (see {@link FetchOutcome}'s own
 * javadoc) — only which of two ordinary, countable items a fetch produced, so the result is visible
 * on the belt the same way ore is, without the engine needing to carry per-instance payloads yet.
 *
 * <p>Deliberately has no {@code speedLevel}: a real network round-trip takes as long as the actual
 * server takes, so "twice as fast" has no meaning here the way it does for a mechanical miner —
 * this archetype's {@link BuildingPrototype#acceptsSpeedEffects} is registered {@code false}, and
 * {@link #speedLevel()}/{@code withSpeedLevel} are left at {@link Building}'s own defaults.
 *
 * <p><b>Rate limiting and circuit breaker — live bug report.</b> Before these existed, {@link
 * #tick} started a brand-new fetch the very tick a delivered item freed {@link #held}, throttled by
 * nothing but network latency and belt backpressure. A miner whose downstream never blocks (a chest
 * something keeps draining) fetched roughly once per round-trip, forever — thousands of
 * factory-scale miners pointed at the same real host is a self-inflicted denial-of-service, not a
 * hypothetical. {@link #cooldownRemaining} enforces {@link #intervalTicks} between the END of one
 * fetch and the START of the next (never faster than {@link #MIN_INTERVAL_TICKS}, one request per
 * real minute); {@link #consecutiveFailures} reaching {@link #FAILURE_THRESHOLD} opens a circuit —
 * {@link #backoffRemaining} ticks pass with no fetch at all — so a broken or rate-limiting endpoint
 * stops being hammered instead of failing, and immediately retrying, forever.
 */
public final class WebMiner implements Building, EditableBuilding, InspectableBuilding {

    /** Which item {@link #tick} produces on {@link FetchOutcome#SUCCESS} — see the class javadoc. */
    public static final TraitKey<ItemType> SUCCESS_ITEM =
            new TraitKey<>(ContentId.of("rustorio:web_miner_success_item"), "successItem", ItemType.class);

    /** Which item {@link #tick} produces on {@link FetchOutcome#ERROR} — see the class javadoc. */
    public static final TraitKey<ItemType> ERROR_ITEM =
            new TraitKey<>(ContentId.of("rustorio:web_miner_error_item"), "errorItem", ItemType.class);

    /** The simulation's own fixed step (1/60 s) — how tick-counted durations here translate to real seconds. */
    private static final int TICKS_PER_SECOND = 60;
    /** "Не чаще раза в минуту" (owner requirement) — the floor {@link #setIntervalSeconds} clamps to, not just a default. */
    public static final int MIN_INTERVAL_TICKS = 60 * TICKS_PER_SECOND;
    /** At least a second — zero/negative would mean "retry every tick," defeating the whole point of a backoff. */
    public static final int MIN_BACKOFF_TICKS = TICKS_PER_SECOND;
    private static final int DEFAULT_BACKOFF_TICKS = 30 * TICKS_PER_SECOND;
    /** Fixed, not player-configurable (owner requirement: "после 3 неудачных") — public so the settings modal can show "failures: N/{@link #FAILURE_THRESHOLD}" without duplicating the number. */
    public static final int FAILURE_THRESHOLD = 3;

    private final Direction direction;
    private final ItemType successItem;
    private final ItemType errorItem;
    /** Which sprite {@link #appearance} draws — injected rather than hardcoded, so a prototype reusing this archetype can ship its own art. */
    private final BuildingPrototype prototype;

    /** The player's chosen target — plain data, mutated by {@link #setUrl}. */
    private String url;
    /** Minimum ticks between the end of one fetch and the start of the next — see the class javadoc. Never below {@link #MIN_INTERVAL_TICKS}. */
    private int intervalTicks = MIN_INTERVAL_TICKS;
    /** How long a fully-open circuit stays open — see the class javadoc. Never below {@link #MIN_BACKOFF_TICKS}. */
    private int backoffTicks = DEFAULT_BACKOFF_TICKS;
    /** Counts down to zero after each delivered result; no new fetch starts while positive. */
    private int cooldownRemaining;
    /** Consecutive {@link FetchOutcome#ERROR} results since the last success — resets to 0 the moment a fetch succeeds, or the moment {@link #FAILURE_THRESHOLD} trips a backoff. */
    private int consecutiveFailures;
    /** Ticks left in an open circuit; no fetch is even requested while positive — see the class javadoc. */
    private int backoffRemaining;
    private @Nullable ItemType held;
    /** Recomputed once per {@link #tick}, not once per render frame. */
    private BuildingStatus status = BuildingStatus.WORKING;

    /**
     * This world's {@link FetchService}, resolved on the first tick that actually needs it and kept
     * afterwards — {@code null} means either "not asked yet" or "this world has no provider," and
     * {@link #serviceResolved} is what tells those two apart without a second lookup per tick.
     *
     * <p>Not resolved in the constructor: a building is built by the engine's factory, which is
     * handed no world at all (deliberately — the same factory builds buildings for a world that
     * does not exist yet, during a save load). The first tick is the earliest moment a world is in
     * hand.
     *
     * <p>Not re-resolved per tick either: {@link TickContext#service} is a map lookup, and this
     * project's tick budget does not spend one per building per tick for an answer that cannot
     * change — a world's services are fixed at construction.
     */
    private @Nullable FetchService fetchService;
    private boolean serviceResolved;

    public WebMiner(Direction direction, String url, BuildingPrototype prototype) {
        this(direction, url, null, MIN_INTERVAL_TICKS, DEFAULT_BACKOFF_TICKS, 0, 0, 0, prototype);
    }

    /** Restore constructor — public because this archetype is registered from this mod's own jar, outside the engine. */
    public WebMiner(Direction direction, String url, @Nullable ItemType held, int intervalTicks, int backoffTicks,
            int cooldownRemaining, int consecutiveFailures, int backoffRemaining, BuildingPrototype prototype) {
        this.direction = direction;
        this.url = url;
        this.held = held;
        this.intervalTicks = Math.max(MIN_INTERVAL_TICKS, intervalTicks);
        this.backoffTicks = Math.max(MIN_BACKOFF_TICKS, backoffTicks);
        this.cooldownRemaining = cooldownRemaining;
        this.consecutiveFailures = consecutiveFailures;
        this.backoffRemaining = backoffRemaining;
        this.prototype = prototype;
        this.successItem = prototype.traits().get(SUCCESS_ITEM).orElseThrow(() -> new IllegalStateException(
                "WEB_MINER prototype " + prototype.id() + " declares no 'successItem' trait"));
        this.errorItem = prototype.traits().get(ERROR_ITEM).orElseThrow(() -> new IllegalStateException(
                "WEB_MINER prototype " + prototype.id() + " declares no 'errorItem' trait"));
    }

    /** The player's remedy for "pointed at the wrong site" — free text rather than a cycle. */
    public void setUrl(String url) {
        this.url = url;
    }

    /** Which URL this miner fetches — for the settings modal/save. */
    public String url() {
        return url;
    }

    /** Clamped to {@link #MIN_INTERVAL_TICKS} — the player can slow this miner down, never speed it past once a minute. */
    public void setIntervalSeconds(int seconds) {
        this.intervalTicks = Math.max(MIN_INTERVAL_TICKS, seconds * TICKS_PER_SECOND);
    }

    public int intervalSeconds() {
        return intervalTicks / TICKS_PER_SECOND;
    }

    /** Clamped to {@link #MIN_BACKOFF_TICKS} — see {@link #setIntervalSeconds} for why zero isn't allowed either. */
    public void setBackoffSeconds(int seconds) {
        this.backoffTicks = Math.max(MIN_BACKOFF_TICKS, seconds * TICKS_PER_SECOND);
    }

    public int backoffSeconds() {
        return backoffTicks / TICKS_PER_SECOND;
    }

    /** How many fetches in a row have failed since the last success — {@link #FAILURE_THRESHOLD} opens the circuit. */
    public int consecutiveFailures() {
        return consecutiveFailures;
    }

    /** Seconds left before this miner will even ask for another fetch, whichever of cooldown or an open circuit is later — 0 means it's free to try right now. */
    public int waitingSeconds() {
        return Math.max(cooldownRemaining, backoffRemaining) / TICKS_PER_SECOND;
    }

    @Override
    public List<FieldSpec> editableFields() {
        return List.of(
                new FieldSpec("URL", FieldType.TEXT),
                new FieldSpec("Interval (s, min 60)", FieldType.NUMBER),
                new FieldSpec("Backoff (s)", FieldType.NUMBER));
    }

    @Override
    public List<String> currentFieldValues() {
        return List.of(url, String.valueOf(intervalSeconds()), String.valueOf(backoffSeconds()));
    }

    /** Order matches {@link #editableFields}: URL, then interval, then backoff. A blank number field floors to this archetype's own minimum, not an error. */
    @Override
    public void applyEdits(List<String> newValues) {
        setUrl(newValues.get(0));
        setIntervalSeconds(parseIntOrZero(newValues.get(1)));
        setBackoffSeconds(parseIntOrZero(newValues.get(2)));
    }

    @Override
    public List<String> readOnlyInfo() {
        List<String> info = new ArrayList<>();
        info.add("Failures: " + consecutiveFailures + "/" + FAILURE_THRESHOLD);
        if (waitingSeconds() > 0) {
            info.add("Next request in: " + waitingSeconds() + "s");
        }
        return info;
    }

    /** Defensive against anything that isn't a plain non-negative integer — a settings modal is expected to only ever let digits through, but this method must not throw on whatever it's actually handed. */
    private static int parseIntOrZero(String value) {
        try {
            return value.isBlank() ? 0 : Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        ItemType alreadyHeld = held;
        if (alreadyHeld != null) {
            deliver(world, x, y, alreadyHeld);
            return;
        }
        if (backoffRemaining > 0) {
            backoffRemaining--;
            status = BuildingStatus.WORKING;
            return;
        }
        if (cooldownRemaining > 0) {
            cooldownRemaining--;
            status = BuildingStatus.WORKING;
            return;
        }
        FetchService fetch = fetchService(world);
        if (fetch == null) {
            // No provider wired into this world (a headless test, a dev tool). Idle exactly the way
            // a miner on a cell with no ore does — see FetchService's own KEY javadoc.
            status = BuildingStatus.WORKING;
            return;
        }
        fetch.request(x, y, url);
        Optional<FetchOutcome> outcome = fetch.poll(x, y);
        if (outcome.isEmpty()) {
            status = BuildingStatus.WORKING;
            return;
        }
        ItemType justFetched = outcome.get() == FetchOutcome.SUCCESS ? successItem : errorItem;
        if (outcome.get() == FetchOutcome.SUCCESS) {
            consecutiveFailures = 0;
            cooldownRemaining = intervalTicks;
        } else {
            consecutiveFailures++;
            if (consecutiveFailures >= FAILURE_THRESHOLD) {
                // The circuit's own backoff IS the wait — not on top of the ordinary interval
                // cooldown below (a live bug caught before it shipped: the first draft set BOTH,
                // so a tripped circuit waited backoff+interval, not just backoff).
                backoffRemaining = backoffTicks;
                consecutiveFailures = 0; // the circuit itself now carries the "this host is broken" signal
            } else {
                cooldownRemaining = intervalTicks;
            }
        }
        held = justFetched;
        world.notifyProduced(justFetched);
        deliver(world, x, y, justFetched);
    }

    private @Nullable FetchService fetchService(TickContext world) {
        if (!serviceResolved) {
            fetchService = world.service(FetchService.KEY).orElse(null);
            serviceResolved = true;
        }
        return fetchService;
    }

    /** Try to push {@code item} (== {@link #held}, passed explicitly so NullAway sees the non-null guarantee at every call site) onto the belt ahead — same one-item-at-a-time discipline a miner's own tick follows. */
    private void deliver(TickContext world, int x, int y, ItemType item) {
        if (world.offerForward(x + direction.dx(), y + direction.dy(), item)) {
            held = null;
            status = BuildingStatus.WORKING;
        } else {
            status = BuildingStatus.OUTPUT_FULL;
        }
    }

    /** The status field this archetype already keeps, handed over without building an {@link Appearance}. */
    /**
     * What this miner is pointed at and what it is doing about it.
     *
     * <p>Every one of these is a field this class already keeps, and none of them were visible
     * anywhere: a player could SET the url through the settings modal and then had no way to read
     * back what they had set, let alone see that three failures had opened the circuit and nothing
     * would be fetched for the next half minute. A miner sitting still looked exactly like a miner
     * between intervals, which is the state it is in almost all the time.
     *
     * <p>Field reads only, as {@link InspectableBuilding} requires of a method the panel calls
     * every frame — no service lookup, no formatting of anything unbounded.
     */
    @Override
    public List<String> inspectionDetails(TickContext world, int x, int y) {
        List<String> lines = new ArrayList<>();
        lines.add("URL: " + url);
        lines.add("Fetches every " + seconds(intervalTicks) + " s");
        if (backoffRemaining > 0) {
            lines.add("Paused after " + FAILURE_THRESHOLD + " failures — retrying in "
                    + seconds(backoffRemaining) + " s");
        } else if (cooldownRemaining > 0) {
            lines.add("Next fetch in " + seconds(cooldownRemaining) + " s");
        } else {
            lines.add("Fetching now");
        }
        if (consecutiveFailures > 0 && backoffRemaining == 0) {
            lines.add("Failures in a row: " + consecutiveFailures + "/" + FAILURE_THRESHOLD);
        }
        return lines;
    }

    /** Ticks as whole seconds, rounded UP: "0 s" on a countdown that has not finished reads as a stuck miner. */
    private static int seconds(int ticks) {
        return (ticks + TICKS_PER_SECOND - 1) / TICKS_PER_SECOND;
    }

    @Override
    public BuildingStatus status() {
        return status;
    }

    @Override
    public Appearance appearance() {
        return Appearance.of(prototype.texture(), status);
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
        return Optional.of(new WebMiner(direction.rotate(), url, held, intervalTicks, backoffTicks,
                cooldownRemaining, consecutiveFailures, backoffRemaining, prototype));
    }

    @Override
    public ContentId prototypeId() {
        return prototype.id();
    }

    @Override
    public WebMinerState state() {
        return new WebMinerState(direction, url, held, intervalTicks, backoffTicks, cooldownRemaining,
                consecutiveFailures, backoffRemaining);
    }
}
