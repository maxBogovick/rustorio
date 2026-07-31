package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaBuildings;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link BuildMenuLayout} — pure logic (no libGDX types, A1 CODE_REVIEW_2026-07-28.md), shared by
 * {@link BuildMenuRenderer} (drawing) and {@code com.graphics.input.InputHandler} (hit-testing a
 * click), same reason {@link HotbarLayoutTest} exists for the hotbar's own geometry.
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
    void visibleRowsTruncatesToTheMaxWithoutErroringOnAShorterList() {
        List<BuildingPrototype> tooMany = java.util.stream.IntStream.range(0, BuildMenuLayout.MAX_VISIBLE_ROWS + 5)
                .mapToObj(i -> modded("stress", "item_" + i))
                .toList();

        assertEquals(BuildMenuLayout.MAX_VISIBLE_ROWS, BuildMenuLayout.visibleRows(tooMany).size());
        assertEquals(3, BuildMenuLayout.visibleRows(tooMany.subList(0, 3)).size(), "a shorter list is returned as-is");
    }

    @Test
    void hitTestRowFindsTheRowUnderThePointAndMissesOutsideThePanel() {
        int screenW = 1280;
        int screenH = 800;
        int rows = 5;
        float panelX = BuildMenuLayout.panelX(screenW);
        float panelH = BuildMenuLayout.panelHeight(rows);
        float panelY = BuildMenuLayout.panelY(screenH, rows);
        float rowHudY = BuildMenuLayout.rowY(panelY, panelH, 2);
        float screenY = screenH - rowHudY; // hitTestRow flips Y itself, same convention as HotbarLayout

        assertEquals(2, BuildMenuLayout.hitTestRow(panelX + 10, screenY, screenW, screenH, rows));
        assertEquals(-1, BuildMenuLayout.hitTestRow(panelX - 10, screenY, screenW, screenH, rows), "left of the panel entirely");
        assertEquals(-1, BuildMenuLayout.hitTestRow(panelX + 10, 0, screenW, screenH, rows), "top of the screen, above the panel");
    }

    @Test
    void isOverPanelIsTrueInsideAndFalseOutside() {
        int screenW = 1280;
        int screenH = 800;
        int rows = 5;
        float panelX = BuildMenuLayout.panelX(screenW);

        assertTrue(BuildMenuLayout.isOverPanel(panelX + 10, screenH / 2f, screenW, screenH, rows));
        assertFalse(BuildMenuLayout.isOverPanel(panelX - 10, screenH / 2f, screenW, screenH, rows));
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
}
