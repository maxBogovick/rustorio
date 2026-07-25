package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.OreLayout;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.SortRule;

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
            // Hardcoded, not restored: SplitterState carries no rule — see the "known compromise"
            // note on Splitter's class javadoc for why this always discards a customized rule.
            case BuildingMemento.SplitterState s -> new Splitter(SortRule.ORE_FORWARD, s.facing(), s.held());
            case BuildingMemento.LabState s -> new Lab(s.buffer(), s.cooldown());
            case BuildingMemento.UndergroundBeltState s -> new UndergroundBelt(s.kind(), s.direction(), s.held());
        };
        for (int i = 0; i < speedLevel; i++) {
            building = new SpeedModule(building);
        }
        return building;
    }
}
