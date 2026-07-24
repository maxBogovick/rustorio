package com.rustorio;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * {@link Building#outputDirection()}: только отрисовка (стрелка направления) читает этот метод,
 * но {@link SpeedModule} обязан его делегировать, как уже делегирует {@code prefersDescendingTick}
 * — забытая строка была бы РОВНО тем багом, что уже дважды находился в этой сессии (апгрейд
 * прячет настоящее здание за {@code SpeedModule}).
 */
class OutputDirectionTest {

    @Test
    void beltReportsItsOwnDirection() {
        Belt belt = new Belt(Direction.DOWN);
        assertEquals(Direction.DOWN, belt.outputDirection());
    }

    @Test
    void furnaceReportsItsOwnDirection() {
        Furnace furnace = new Furnace(BuildingType.PRESS, Direction.LEFT);
        assertEquals(Direction.LEFT, furnace.outputDirection());
    }

    @Test
    void undergroundBeltReportsItsDirectionOnBothHalves() {
        UndergroundBelt in = new UndergroundBelt(UndergroundBelt.Kind.IN, Direction.UP);
        UndergroundBelt out = new UndergroundBelt(UndergroundBelt.Kind.OUT, Direction.UP);
        assertEquals(Direction.UP, in.outputDirection());
        assertEquals(Direction.UP, out.outputDirection());
    }

    @Test
    void speedModuleDelegatesOutputDirectionToWhatItWraps() {
        Building upgraded = new SpeedModule(new Belt(Direction.RIGHT));
        assertEquals(Direction.RIGHT, upgraded.outputDirection());
    }

    @Test
    void doublyWrappedSpeedModuleStillDelegatesThroughBothLayers() {
        Building twiceUpgraded = new SpeedModule(new SpeedModule(new Furnace(BuildingType.FURNACE, Direction.DOWN)));
        assertEquals(Direction.DOWN, twiceUpgraded.outputDirection());
    }

    @Test
    void splitterReportsItsFacingAsItsForwardOutput() {
        Splitter splitter = new Splitter(SortRule.ORE_FORWARD, Direction.UP);
        assertEquals(Direction.UP, splitter.outputDirection());
    }

    @Test
    void splitterReportsTheClockwiseRotationAsItsSecondaryOutput() {
        // Direction.rotate(): RIGHT->DOWN->LEFT->UP->RIGHT — UP повёрнутый по часовой это RIGHT.
        Splitter splitter = new Splitter(SortRule.ORE_FORWARD, Direction.UP);
        assertEquals(Direction.RIGHT, splitter.secondaryOutputDirection());
    }

    @Test
    void onlySplitterHasASecondaryOutput() {
        assertNull(new Belt(Direction.RIGHT).secondaryOutputDirection());
        assertNull(new Furnace(BuildingType.FURNACE, Direction.RIGHT).secondaryOutputDirection());
        assertNull(new UndergroundBelt(UndergroundBelt.Kind.OUT, Direction.RIGHT).secondaryOutputDirection());
    }

    @Test
    void speedModuleDelegatesSecondaryOutputDirectionToo() {
        Building upgraded = new SpeedModule(new Splitter(SortRule.ORE_FORWARD, Direction.LEFT));
        // rotate(LEFT) = UP
        assertEquals(Direction.UP, upgraded.secondaryOutputDirection());
    }

    @Test
    void buildingsWithoutADirectionReportNull() {
        assertNull(new Chest().outputDirection());
        assertNull(new Miner().outputDirection());
        assertNull(new Lab().outputDirection());
    }
}
