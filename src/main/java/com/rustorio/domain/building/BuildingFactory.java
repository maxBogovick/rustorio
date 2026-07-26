package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.SortRule;
import org.jspecify.annotations.Nullable;

/**
 * Factory Method: the one place that turns "build a {@link BuildingType} facing this way" or "here
 * is a captured {@link BuildingMemento}" into a live {@link Building} instance. Everything each
 * concrete building needs beyond its own state — the {@link OreLayout} a {@link Miner} reads, the
 * {@link RecipeBook} a {@link Furnace} searches — is injected here once, at construction, instead
 * of every building reaching for static, shared state on its own.
 *
 * <p>Consolidates what used to be two separate hand-written {@code switch} statements living in
 * two different classes ({@code World}'s placement dispatch and the save/load file's loading
 * dispatch) into one place that both {@code World} (new buildings) and the persistence layer
 * (restored buildings) call through.
 */
public final class BuildingFactory {

    private final OreLayout oreLayout;
    private final RecipeBook recipeBook;

    public BuildingFactory(OreLayout oreLayout, RecipeBook recipeBook) {
        this.oreLayout = oreLayout;
        this.recipeBook = recipeBook;
    }

    /** The game's default factory: the standard ore map and the standard recipe set. */
    public static BuildingFactory standard() {
        return new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard());
    }

    public OreLayout oreLayout() {
        return oreLayout;
    }

    public RecipeBook recipeBook() {
        return recipeBook;
    }

    /**
     * Whether {@code type} satisfies its {@link PlacementRule} at {@code (x, y)} — the type-
     * specific half of {@code World.place}'s check; the free+in-bounds half is {@code World}'s own
     * business and stays there. See P3-04, BUG_FIX_PROGRESS.md.
     */
    public boolean canPlace(BuildingType type, int x, int y) {
        return PlacementRule.forType(type).test(x, y, oreLayout);
    }

    /** Build a brand-new building of {@code type}, facing {@code direction} where that matters. */
    public Building create(BuildingType type, Direction direction) {
        return switch (type) {
            case MINER -> new Miner(oreLayout);
            case CHEST -> new Chest();
            case FURNACE -> new Furnace(BuildingType.FURNACE, direction, recipeBook);
            case PRESS -> new Furnace(BuildingType.PRESS, direction, recipeBook);
            case BELT -> new Belt(direction);
            case SPLITTER -> new Splitter(SortRule.ORE_FORWARD, direction);
            case UNDERGROUND_IN -> new UndergroundBelt(UndergroundBelt.Kind.IN, direction);
            case UNDERGROUND_OUT -> new UndergroundBelt(UndergroundBelt.Kind.OUT, direction);
            case LAB -> new Lab();
        };
    }

    /**
     * Rebuild a building from a captured {@link BuildingMemento}, then re-apply {@code
     * speedLevel} layers of {@link SpeedModule} — the wrapper's own state lives outside the
     * memento entirely (see {@link Building#speedLevel()}), so the persistence layer tracks it
     * separately and this method re-wraps rather than trying to recover it from the memento.
     *
     * <p>The pattern-matching {@code switch} over the sealed {@link BuildingMemento} hierarchy is
     * exhaustive by construction: adding a new building kind without a matching case here is a
     * compile error, not a runtime surprise. Unlike {@link #create}, this takes no separate {@link
     * BuildingType} — every memento variant already carries everything needed to rebuild its exact
     * building, including {@link BuildingMemento.FurnaceState#kind()} for telling {@code FURNACE}
     * apart from {@code PRESS}, so there is exactly one source of truth for "what kind is this."
     */
    public Building restore(BuildingMemento memento, int speedLevel) {
        Building building = switch (memento) {
            case BuildingMemento.MinerState s -> new Miner(oreLayout, s.cooldown(), s.held());
            case BuildingMemento.ChestState s -> new Chest(s.count());
            case BuildingMemento.FurnaceState s -> new Furnace(s, recipeBook);
            case BuildingMemento.BeltState s -> new Belt(s.direction(), s.held());
            // null rule (P4-10, BUG_FIX_PROGRESS.md): a save written before SplitterState carried
            // one — treat "unknown" as the default rather than failing a save that was fine
            // before this field existed.
            case BuildingMemento.SplitterState s ->
                    new Splitter(s.rule() == null ? SortRule.ORE_FORWARD : SortRule.byId(s.rule()),
                            s.facing(), s.held());
            case BuildingMemento.LabState s -> new Lab(s.buffer(), s.cooldown());
            case BuildingMemento.UndergroundBeltState s -> new UndergroundBelt(s.kind(), s.direction(), s.held());
        };
        for (int i = 0; i < speedLevel; i++) {
            building = new SpeedModule(building);
        }
        return building;
    }

    /**
     * The narrow public door {@code World} reaches {@link Belt#attachToNeighbors} through (P3-02,
     * BUG_FIX_PROGRESS.md): {@code World} owns the cell map and finds which neighbors (if any) sit
     * behind/ahead of a freshly placed or restored belt, but everything past that — actually
     * wiring tiles into a {@link BeltSegment} — is this package's business, not {@code World}'s.
     */
    public static void attachBelt(Belt belt, @Nullable Belt behind, @Nullable Belt ahead) {
        belt.attachToNeighbors(behind, ahead);
    }

    /**
     * The narrow public door {@code World} reaches {@link Belt#leaveSegment} through (P3-02,
     * BUG_FIX_PROGRESS.md) — on demolition, or to make a re-restore idempotent (see {@code
     * World.restoreBuilding}).
     */
    public static void detachBelt(Belt belt) {
        belt.leaveSegment();
    }

    /**
     * The narrow public door {@code TickScheduler} reaches {@link Belt#clearArrivalMark} through
     * (P3-03, BUG_FIX_PROGRESS.md) — called once per belt, once per world tick, before either
     * traversal pass runs.
     */
    public static void clearArrivalMark(Belt belt) {
        belt.clearArrivalMark();
    }

    /**
     * Same door, for {@link UndergroundBelt}'s own arrival mark (found in a post-Phase-4 review —
     * the tunnel analogue of the {@link Belt} case above; see {@link UndergroundBelt#arrivedThisTick}).
     */
    public static void clearArrivalMark(UndergroundBelt tunnel) {
        tunnel.clearArrivalMark();
    }
}
