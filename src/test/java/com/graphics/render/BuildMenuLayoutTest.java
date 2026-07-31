package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaBuildings;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BuildMenuLayout} — pure logic (no libGDX types), shared by {@link BuildMenuRenderer}
 * (drawing) and {@code com.graphics.input.InputHandler} (hit-testing a click), same reason {@link
 * HotbarLayoutTest} exists for the hotbar's own geometry.
 */
class BuildMenuLayoutTest {

    private static final List<BuildingPrototype> VANILLA = VanillaBuildings.frozen().iterate();

    @Test
    void categoriesAreSortedNamespacesWithNoDuplicates() {
        BuildingPrototype fromAnotherMod = modded("examplemod", "widget");

        List<String> categories = BuildMenuLayout.categories(List.of(
                VANILLA.get(0), VANILLA.get(1), fromAnotherMod));

        assertEquals(List.of("examplemod", "rustorio"), categories, "sorted, one entry per namespace regardless of how many prototypes share it");
    }

    @Test
    void activeCategoryIsNullAtCycleZero() {
        List<String> categories = List.of("examplemod", "rustorio");

        assertNull(BuildMenuLayout.activeCategory(categories, 0), "cycle 0 means \"all\"");
    }

    @Test
    void activeCategoryStepsThroughEachNamespaceThenWraps() {
        List<String> categories = List.of("examplemod", "rustorio");

        assertEquals("examplemod", BuildMenuLayout.activeCategory(categories, 1));
        assertEquals("rustorio", BuildMenuLayout.activeCategory(categories, 2));
        assertNull(BuildMenuLayout.activeCategory(categories, 3), "cycle 3 wraps back to \"all\" for a 2-category list");
        assertEquals("examplemod", BuildMenuLayout.activeCategory(categories, 4), "and cycle 4 wraps back to the first category");
    }

    @Test
    void activeCategoryIsNullWhenNothingIsRegisteredAtAll() {
        assertNull(BuildMenuLayout.activeCategory(List.of(), 5));
    }

    @Test
    void filterByCategoryKeepsOnlyMatchingNamespace() {
        BuildingPrototype fromAnotherMod = modded("examplemod", "widget");
        List<BuildingPrototype> all = List.of(VANILLA.get(0), fromAnotherMod);

        List<BuildingPrototype> onlyExampleMod = BuildMenuLayout.filter(all, "examplemod", "");

        assertEquals(List.of(fromAnotherMod), onlyExampleMod);
    }

    @Test
    void nullCategoryMatchesEverything() {
        List<BuildingPrototype> all = List.of(VANILLA.get(0), VANILLA.get(1));

        assertEquals(all, BuildMenuLayout.filter(all, null, ""));
    }

    @Test
    void searchMatchesLabelCaseInsensitiveSubstring() {
        BuildingPrototype miner = prototypeFor(BuildingType.MINER); // label "Miner"

        assertTrue(BuildMenuLayout.filter(List.of(miner), null, "min").contains(miner));
        assertTrue(BuildMenuLayout.filter(List.of(miner), null, "MIN").contains(miner));
        assertTrue(BuildMenuLayout.filter(List.of(miner), null, "").contains(miner), "blank query matches everything");
        assertTrue(BuildMenuLayout.filter(List.of(miner), null, "zzz").isEmpty());
    }

    @Test
    void searchMatchesALabelContainingASpace() {
        // Live bug report (fixed in SimulationControls, proven here at the filter level): "Tunnel
        // in"/"Tunnel out" are two-word labels, and a search box that swallowed the space key made
        // them unfindable by typing their name naturally — the query "tunnelin" is NOT a substring
        // of "tunnel in". filter() itself was always correct; this pins that down so a future
        // regression in the caller (dropping the space again) shows up as a behavior change here too.
        BuildingPrototype tunnelIn = prototypeFor(BuildingType.UNDERGROUND_IN); // label "Tunnel in"

        assertTrue(BuildMenuLayout.filter(List.of(tunnelIn), null, "tunnel in").contains(tunnelIn));
        assertTrue(BuildMenuLayout.filter(List.of(tunnelIn), null, "tunnelin").isEmpty(), "no space is NOT a substring match for a label that has one");
    }

    @Test
    void clampScrollRowsNeverGoesNegative() {
        assertEquals(0, BuildMenuLayout.clampScrollRows(3, -5), "a stray negative raw offset clamps to the top");
    }

    @Test
    void clampScrollRowsCapsAtTheLastPage() {
        int matchCount = BuildMenuLayout.COLUMNS * (BuildMenuLayout.VISIBLE_TILE_ROWS + 2); // two rows past one page
        assertEquals(2, BuildMenuLayout.clampScrollRows(matchCount, 999), "scrolling past the end lands on the last row that still has content");
    }

    @Test
    void clampScrollRowsIsZeroWhenEverythingFitsOnOnePage() {
        assertEquals(0, BuildMenuLayout.clampScrollRows(BuildMenuLayout.MAX_VISIBLE_TILES, 999), "nothing to scroll to when one page holds every match");
    }

    @Test
    void visibleTilesSlidesByOneRowPerScrollStep() {
        // scrollRows is a sliding-window offset (one wheel notch = one row = COLUMNS items), not a
        // discrete page index — scrolling by 1 moves the window COLUMNS items forward, not a whole
        // MAX_VISIBLE_TILES page forward.
        List<BuildingPrototype> tooMany = stress(BuildMenuLayout.MAX_VISIBLE_TILES + BuildMenuLayout.COLUMNS + 3);

        List<BuildingPrototype> atTop = BuildMenuLayout.visibleTiles(tooMany, 0);
        List<BuildingPrototype> scrolledOneRow = BuildMenuLayout.visibleTiles(tooMany, 1);

        assertEquals(BuildMenuLayout.MAX_VISIBLE_TILES, atTop.size());
        assertEquals(tooMany.subList(0, BuildMenuLayout.MAX_VISIBLE_TILES), atTop);
        assertEquals(tooMany.subList(BuildMenuLayout.COLUMNS, BuildMenuLayout.COLUMNS + BuildMenuLayout.MAX_VISIBLE_TILES), scrolledOneRow);
    }

    @Test
    void visibleTilesAtTheMaxClampedScrollAlwaysReachesTheLastItem() {
        int total = BuildMenuLayout.MAX_VISIBLE_TILES + BuildMenuLayout.COLUMNS + 3;
        List<BuildingPrototype> tooMany = stress(total);
        int maxScroll = BuildMenuLayout.clampScrollRows(total, Integer.MAX_VALUE);

        List<BuildingPrototype> atMaxScroll = BuildMenuLayout.visibleTiles(tooMany, maxScroll);

        assertEquals(tooMany.subList(maxScroll * BuildMenuLayout.COLUMNS, total), atMaxScroll);
        assertTrue(atMaxScroll.contains(tooMany.get(total - 1)), "the very last item is always reachable by scrolling far enough");
    }

    @Test
    void visibleTilesOnAShorterListIsReturnedAsIs() {
        List<BuildingPrototype> few = stress(3);

        assertEquals(3, BuildMenuLayout.visibleTiles(few, 0).size());
    }

    @Test
    void hitTestTileFindsTheTileUnderThePointAndMissesOutsideTheGrid() {
        int screenW = 1280;
        int screenH = 800;
        int visibleCount = BuildMenuLayout.COLUMNS * 2 + 3; // three rows, last one partial
        int targetIndex = BuildMenuLayout.COLUMNS + 1; // second row, second column

        float panelX = BuildMenuLayout.panelX(screenW);
        float panelH = BuildMenuLayout.panelHeight(3);
        float panelY = BuildMenuLayout.panelY(screenH, 3);
        int row = targetIndex / BuildMenuLayout.COLUMNS;
        int col = targetIndex % BuildMenuLayout.COLUMNS;
        float tileHudY = BuildMenuLayout.tileY(panelY, panelH, row) + 5f; // a point inside the tile, not on its exact edge
        float tileScreenX = BuildMenuLayout.tileX(col, panelX) + 5f;
        float tileScreenY = screenH - tileHudY;

        assertEquals(targetIndex, BuildMenuLayout.hitTestTile(tileScreenX, tileScreenY, screenW, screenH, visibleCount));
        assertEquals(-1, BuildMenuLayout.hitTestTile(panelX - 10, tileScreenY, screenW, screenH, visibleCount), "left of the panel entirely");
        assertEquals(-1, BuildMenuLayout.hitTestTile(tileScreenX, 0, screenW, screenH, visibleCount), "top of the screen, above the panel");
    }

    @Test
    void hitTestTileMissesAPointInTheGapBetweenTwoTiles() {
        int screenW = 1280;
        int screenH = 800;
        int visibleCount = BuildMenuLayout.COLUMNS;
        float panelX = BuildMenuLayout.panelX(screenW);
        float panelH = BuildMenuLayout.panelHeight(1);
        float panelY = BuildMenuLayout.panelY(screenH, 1);
        float rowHudY = BuildMenuLayout.tileY(panelY, panelH, 0) + 5f;
        // Just past the first tile's right edge, inside the gap before the second tile starts.
        float gapScreenX = BuildMenuLayout.tileX(0, panelX) + BuildMenuLayout.TILE_SIZE + 2f;

        assertEquals(-1, BuildMenuLayout.hitTestTile(gapScreenX, screenH - rowHudY, screenW, screenH, visibleCount));
    }

    @Test
    void hitTestTabFindsTheTabUnderThePointAndMissesBelowTheTabsRow() {
        int screenW = 1280;
        int screenH = 800;
        int visibleCount = BuildMenuLayout.COLUMNS; // one row
        int tabCount = 3;
        float panelX = BuildMenuLayout.panelX(screenW);
        float panelH = BuildMenuLayout.panelHeight(1);
        float panelY = BuildMenuLayout.panelY(screenH, 1);
        float tabsHudY = BuildMenuLayout.tabsY(panelY, panelH) + 5f;
        float tabScreenX = BuildMenuLayout.tabX(1, panelX, tabCount) + 5f;

        assertEquals(1, BuildMenuLayout.hitTestTab(tabScreenX, screenH - tabsHudY, screenW, screenH, visibleCount, tabCount));
        assertEquals(-1, BuildMenuLayout.hitTestTab(tabScreenX, screenH, screenW, screenH, visibleCount, tabCount), "bottom of the screen, below the tabs row");
    }

    @Test
    void isOverPanelIsTrueInsideAndFalseOutside() {
        int screenW = 1280;
        int screenH = 800;
        int visibleCount = BuildMenuLayout.COLUMNS;
        float panelX = BuildMenuLayout.panelX(screenW);

        assertTrue(BuildMenuLayout.isOverPanel(panelX + 10, screenH / 2f, screenW, screenH, visibleCount));
        assertFalse(BuildMenuLayout.isOverPanel(panelX - 10, screenH / 2f, screenW, screenH, visibleCount));
    }

    private static BuildingPrototype prototypeFor(BuildingType type) {
        return VanillaBuildings.frozen().get(VanillaBuildings.idFor(type));
    }

    private static BuildingPrototype modded(String namespace, String path) {
        BuildingPrototype vanillaBelt = prototypeFor(BuildingType.BELT);
        ContentId id = new ContentId(namespace, path);
        return new BuildingPrototype(id, path, vanillaBelt.cost(), vanillaBelt.placementRule(), vanillaBelt.texture(),
                0, 1, false, vanillaBelt.behavior(), vanillaBelt.restoreBehavior(), vanillaBelt.codec());
    }

    private static List<BuildingPrototype> stress(int count) {
        return IntStream.range(0, count).mapToObj(i -> modded("stress", "item_" + i)).toList();
    }
}
