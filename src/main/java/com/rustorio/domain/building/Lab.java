package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Item;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Sprite;
import com.rustorio.domain.Tech;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Spends finished goods on research points instead of passing them along — the chain's terminus
 * rather than another link in it. Accepts every {@link Item#isResearchGrade} item alike (P2-08,
 * BUG_FIX_PROGRESS.md — the list of which items qualify lives on {@link Item} itself, not here).
 *
 * <p><b>Owner decision (P-01, DEV_TASKS.md; scale revisited in N15, NEW_BUGS_PROGRESS.md):</b> a
 * batch's points are proportional to {@link RecipeBook#depthOf}, not a flat 1 regardless of item — the previous behavior (still what {@link
 * #effectiveTime} charges for: every item, however deep, cooks in the same time) let a rational
 * player dominate research by spamming the shallowest research-grade good (a {@code GEAR}, 13
 * ticks deep) instead of ever building the deeper ones (a {@code CHASSIS}, 66 ticks deep) — see
 * §2.4 of the design audit. {@link #researchPoints} is the one thing that changed; the timer is
 * still the same flat {@link #RESEARCH_TIME} for every item, a deliberate simplification this
 * class's javadoc already named before this task and which this task doesn't revisit.
 *
 * <p>Buffered items are now a FIFO ({@link #buffer}, a queue) instead of a plain count: awarding
 * the right number of points for a finished batch requires knowing WHICH item just finished, not
 * just that "an item" did — a {@code GEAR} and a {@code CHASSIS} sitting in the same buffer must
 * be told apart when their turn comes.
 *
 * <p>Matches {@link Furnace}'s {@code ProcessTimer} policy (P2-05, BUG_FIX_PROGRESS.md): the timer
 * is created lazily, on the first {@link #accept}, from whatever {@link Tech#FAST_LAB} state holds
 * at that moment — not eagerly at construction. A lab built after the tech is already unlocked
 * must not cook its first batch at the un-halved rate just because nobody had fed it yet.
 */
public final class Lab implements Building {

    private static final int BUFFER_MAX = 5;
    private static final int RESEARCH_TIME = 10;

    /**
     * What one batch of the shallowest research-grade item ({@code GEAR}) is worth — the unit
     * everything else in {@link #researchPoints} is measured against (N15, NEW_BUGS_PROGRESS.md,
     * owner decision).
     *
     * <p>It used to be 1, which made the whole scale useless below a 50% depth difference: an item
     * 45% deeper than a {@code GEAR} rounded to the same single point, so "points are proportional
     * to production cost" (P-01) held only for the few items that happened to be far enough apart.
     * Ten points per {@code GEAR} gives the ratio a place to land — every {@link Tech#cost()} was
     * multiplied by ten in the same change, so the tree costs the same number of lab batches as
     * before; only the resolution changed, not the pacing.
     */
    private static final int POINTS_PER_GEAR = 10;

    private final RecipeBook recipeBook;
    private final Deque<Item> buffer = new ArrayDeque<>();
    private @Nullable ProcessTimer timer;

    public Lab(RecipeBook recipeBook) {
        this.recipeBook = recipeBook;
    }

    /** Package-private restore constructor used by {@link BuildingFactory#restore}. */
    Lab(RecipeBook recipeBook, List<Item> buffer, int cooldown) {
        this(recipeBook);
        this.buffer.addAll(buffer);
        if (!this.buffer.isEmpty()) {
            // Non-positive means "nothing recorded", not "ready now" — see the same guard in
            // Furnace's restore constructor (N10, NEW_BUGS_PROGRESS.md). RESEARCH_TIME rather than
            // the tech-adjusted time: no TickContext exists at restore time, and the timer's next
            // reset picks the adjusted value up anyway (see tick).
            this.timer = new ProcessTimer(cooldown > 0 ? cooldown : RESEARCH_TIME);
        }
    }

    @Override
    public boolean accept(TickContext world, Item item) {
        if (!item.isResearchGrade() || buffer.size() >= BUFFER_MAX) {
            return false;
        }
        if (timer == null) {
            timer = new ProcessTimer(effectiveTime(world));
        }
        buffer.addLast(item);
        return true;
    }

    @Override
    public void tick(TickContext world, int x, int y) {
        ProcessTimer current = timer;
        if (buffer.isEmpty() || current == null) {
            return;
        }
        if (!current.tick(effectiveTime(world))) {
            return;
        }
        Item finished = buffer.removeFirst();
        world.addResearchPoints(researchPoints(finished));
        // current already reset its own cooldown to effectiveTime(world) inside the tick() call
        // above (see ProcessTimer#tick) — reuse it for the next queued item as-is; only clear it
        // once the queue is actually empty and there's nothing left to time.
        if (buffer.isEmpty()) {
            timer = null;
        }
    }

    /**
     * Points for one finished batch of {@code item} — {@link RecipeBook#depthOf}, normalized so
     * {@code GEAR} (the shallowest research-grade item, and the only one this Lab awarded before
     * P-01) is worth exactly {@link #POINTS_PER_GEAR}, everything deeper scaling up from that same
     * baseline instead of flatly matching it. {@code Math.round} plus a floor of 1: no
     * research-grade item can ever be worth zero points just because it happens to be shallower
     * than {@code GEAR} (none currently are, but this method doesn't assume that stays true forever).
     *
     * <p>{@code gearDepth == 0} is guarded explicitly (code review finding): the standard {@link
     * RecipeBook} always gives {@code GEAR} a recipe, so this never fires today, but {@code
     * RecipeBook} is an explicit extension point ("an alternative rule set — a mod, a test fixture
     * — is a constructor argument, not a change to production code", per its own javadoc) — an
     * injected book that treats {@code GEAR} as a raw material would otherwise divide by zero,
     * producing {@code Float.POSITIVE_INFINITY} and, via {@code Math.round}, {@code
     * Integer.MAX_VALUE} research points for the very first item ever researched.
     */
    private int researchPoints(Item item) {
        int gearDepth = recipeBook.depthOf(Item.GEAR);
        int itemDepth = recipeBook.depthOf(item);
        if (gearDepth <= 0) {
            return POINTS_PER_GEAR; // no baseline to scale against — award the flat, pre-P-01 rate
        }
        return Math.max(1, Math.round(POINTS_PER_GEAR * itemDepth / (float) gearDepth));
    }

    private static int effectiveTime(TickContext world) {
        return world.research().fasterIfUnlocked(Tech.FAST_LAB, RESEARCH_TIME);
    }

    @Override
    public Appearance appearance() {
        return buffer.isEmpty() ? Appearance.of(Sprite.LAB) : Appearance.of(Sprite.LAB, buffer.size());
    }

    @Override
    public BuildingType type() {
        return BuildingType.LAB;
    }

    @Override
    public BuildingMemento memento() {
        ProcessTimer current = timer;
        return new BuildingMemento.LabState(List.copyOf(buffer), current == null ? 0 : current.cooldown());
    }
}
