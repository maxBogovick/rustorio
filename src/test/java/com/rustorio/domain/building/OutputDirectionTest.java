package com.rustorio.domain.building;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link Building#outputDirection()}: only rendering (the direction arrow) reads this method, but
 * {@link SpeedModule} must delegate it, exactly like it already delegates {@code
 * prefersDescendingTick} — a forgotten line here would be the same bug already found twice in
 * this codebase's history (an upgrade hides the real building behind {@code SpeedModule}).
 */
class OutputDirectionTest {

    private static final RecipeBook RECIPES = RecipeBook.standard();

    @Test
    void beltReportsItsOwnDirection() {
        Belt belt = new Belt(Direction.DOWN);
        assertEquals(Optional.of(Direction.DOWN), belt.outputDirection());
    }

    @Test
    void furnaceReportsItsOwnDirection() {
        Furnace furnace = new Furnace(BuildingType.PRESS, Direction.LEFT, RECIPES);
        assertEquals(Optional.of(Direction.LEFT), furnace.outputDirection());
    }

    @Test
    void undergroundBeltReportsItsDirectionOnBothHalves() {
        UndergroundBelt in = new UndergroundBelt(UndergroundBelt.Kind.IN, Direction.UP);
        UndergroundBelt out = new UndergroundBelt(UndergroundBelt.Kind.OUT, Direction.UP);
        assertEquals(Optional.of(Direction.UP), in.outputDirection());
        assertEquals(Optional.of(Direction.UP), out.outputDirection());
    }

    @Test
    void speedModuleDelegatesOutputDirectionToWhatItWraps() {
        Building upgraded = new SpeedModule(new Belt(Direction.RIGHT));
        assertEquals(Optional.of(Direction.RIGHT), upgraded.outputDirection());
    }

    @Test
    void doublyWrappedSpeedModuleStillDelegatesThroughBothLayers() {
        Building twiceUpgraded =
                new SpeedModule(new SpeedModule(new Furnace(BuildingType.FURNACE, Direction.DOWN, RECIPES)));
        assertEquals(Optional.of(Direction.DOWN), twiceUpgraded.outputDirection());
    }

    @Test
    void splitterReportsItsFacingAsItsForwardOutput() {
        Splitter splitter = new Splitter(Direction.UP);
        assertEquals(Optional.of(Direction.UP), splitter.outputDirection());
    }

    @Test
    void splitterReportsTheClockwiseRotationAsItsSecondaryOutput() {
        // Direction.rotate(): RIGHT->DOWN->LEFT->UP->RIGHT — UP повёрнутый по часовой это RIGHT.
        Splitter splitter = new Splitter(Direction.UP);
        assertEquals(Optional.of(Direction.RIGHT), splitter.secondaryOutputDirection());
    }

    /** (X-01, DEV_TASKS.md) Filter also has two outputs now — see its own class javadoc. */
    @Test
    void filterReportsBothOutputsTooJustLikeSplitter() {
        Filter filter = new Filter(Direction.UP, VanillaItems.IRON_ORE);
        assertEquals(Optional.of(Direction.UP), filter.outputDirection());
        assertEquals(Optional.of(Direction.RIGHT), filter.secondaryOutputDirection());
    }

    @Test
    void mostBuildingsHaveNoSecondaryOutput() {
        assertEquals(Optional.empty(), new Belt(Direction.RIGHT).secondaryOutputDirection());
        assertEquals(Optional.empty(),
                new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES).secondaryOutputDirection());
        assertEquals(Optional.empty(),
                new UndergroundBelt(UndergroundBelt.Kind.OUT, Direction.RIGHT).secondaryOutputDirection());
        assertEquals(Optional.empty(), new Inserter(Direction.RIGHT).secondaryOutputDirection());
    }

    @Test
    void speedModuleDelegatesSecondaryOutputDirectionToo() {
        Building upgraded = new SpeedModule(new Splitter(Direction.LEFT));
        // rotate(LEFT) = UP
        assertEquals(Optional.of(Direction.UP), upgraded.secondaryOutputDirection());
    }

    @Test
    void inserterReportsItsOwnDirection() {
        Inserter inserter = new Inserter(Direction.DOWN);
        assertEquals(Optional.of(Direction.DOWN), inserter.outputDirection());
    }

    @Test
    void buildingsWithoutADirectionReportEmpty() {
        assertEquals(Optional.empty(), new Lab(RECIPES).outputDirection());
    }

    /** Since D-01 (DEV_TASKS.md): a miner delivers to one addressed neighbor, so it needs a direction too. */
    @Test
    void minerReportsItsOwnDirection() {
        Miner miner = new Miner(PatchOreLayout.standard(), Direction.LEFT);
        assertEquals(Optional.of(Direction.LEFT), miner.outputDirection());
    }

    /** Since D-02 (DEV_TASKS.md): a chest pushes its contents out one addressed neighbor, so it needs a direction too. */
    @Test
    void chestReportsItsOwnDirection() {
        Chest chest = new Chest(Direction.DOWN);
        assertEquals(Optional.of(Direction.DOWN), chest.outputDirection());
    }
}
