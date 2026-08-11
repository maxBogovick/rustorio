package com.graphics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.VanillaCategories;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * {@link CategoryTabsLayout} — grouping and geometry, both testable without a window, which is the
 * whole reason they live apart from {@link HudRenderer}.
 */
class CategoryTabsLayoutTest {

    private static final int SCREEN_HEIGHT = 800;
    private static final List<BuildingPrototype> VANILLA = VanillaBuildings.frozen().iterate();

    @Test
    void everyVanillaBuildingLandsInACategoryAndNoneInTheCatchAll() {
        Map<ContentId, List<BuildingPrototype>> grouped = CategoryTabsLayout.byCategory(VANILLA);

        int total = grouped.values().stream().mapToInt(List::size).sum();
        assertEquals(VANILLA.size(), total, "grouping must not lose or duplicate a building");
        assertFalse(grouped.containsKey(VanillaCategories.OTHER),
                "vanilla declares a category for every one of its buildings, so the catch-all tab "
                        + "must not even appear on a stock install");
    }

    /** Tab order is the declared order, not whatever a hash produced — otherwise tabs move under the cursor between launches. */
    @Test
    void tabsComeOutInTheDeclaredOrderEveryTime() {
        // Distinct list instances so an identity-keyed cache cannot make this compare a map to itself.
        List<ContentId> first = List.copyOf(CategoryTabsLayout.byCategory(new ArrayList<>(VANILLA)).keySet());
        List<ContentId> second = List.copyOf(CategoryTabsLayout.byCategory(new ArrayList<>(VANILLA)).keySet());

        assertEquals(first, second);
        assertEquals(VanillaCategories.MINING, first.get(0), "Mining leads, as declared");
        assertTrue(first.indexOf(VanillaCategories.LOGISTICS) < first.indexOf(VanillaCategories.FLUIDS));
    }

    /**
     * A category nobody filled gets no tab. Clicking a tab that opens onto nothing is a dead end,
     * and with no mods installed the catch-all is exactly that.
     */
    @Test
    void anEmptyCategoryGetsNoTab() {
        Map<ContentId, List<BuildingPrototype>> grouped = CategoryTabsLayout.byCategory(List.of());

        assertTrue(grouped.isEmpty(), "no buildings at all means no tabs at all");
    }

    /** A category a mod invented is grouped too, appended after the vanilla ones rather than dropped. */
    @Test
    void aCategoryAModInventedStillGetsItsOwnTab() {
        BuildingPrototype odd = VANILLA.get(0);
        Map<ContentId, List<BuildingPrototype>> grouped = CategoryTabsLayout.byCategory(List.of(odd));

        assertEquals(1, grouped.size());
        assertEquals(VanillaCategories.of(odd), grouped.keySet().iterator().next());
    }

    @Test
    void hitTestFindsTheTabAndTheIconRowsApart() {
        float tabHudY = CategoryTabsLayout.TABS_Y + CategoryTabsLayout.TAB_HEIGHT / 2f;
        float tabScreenY = SCREEN_HEIGHT - tabHudY;
        float secondTabX = CategoryTabsLayout.tabX(1) + CategoryTabsLayout.TAB_WIDTH / 2f;

        assertEquals(1, CategoryTabsLayout.hitTestTab(secondTabX, tabScreenY, SCREEN_HEIGHT, 5));
        assertEquals(-1, CategoryTabsLayout.hitTestIcon(secondTabX, tabScreenY, SCREEN_HEIGHT, 5),
                "a click on the tab row must not also count as a click on an icon below it");

        float iconHudY = CategoryTabsLayout.ICONS_Y + CategoryTabsLayout.ICON_SIZE / 2f;
        float iconScreenY = SCREEN_HEIGHT - iconHudY;
        float thirdIconX = CategoryTabsLayout.iconX(2) + CategoryTabsLayout.ICON_SIZE / 2f;

        assertEquals(2, CategoryTabsLayout.hitTestIcon(thirdIconX, iconScreenY, SCREEN_HEIGHT, 5));
        assertEquals(-1, CategoryTabsLayout.hitTestTab(thirdIconX, iconScreenY, SCREEN_HEIGHT, 5));
    }

    /** The panel starts clear of the quick bar — the two must never draw over each other in the corner. */
    @Test
    void thePanelStartsToTheRightOfTheQuickBar() {
        float quickBarRightEdge = QuickBarLayout.MARGIN_LEFT
                + QuickBarLayout.totalWidth(QuickBarLayout.COLUMNS);

        assertTrue(CategoryTabsLayout.LEFT > quickBarRightEdge,
                "tabs at " + CategoryTabsLayout.LEFT + " would overlap the grid ending at " + quickBarRightEdge);
    }

    @Test
    void iconsAreCutAtTheWindowEdgeRatherThanShrunkToFit() {
        // Exactly enough for three: three icons, the two gaps BETWEEN them, and the right padding.
        // Not three icon-plus-gap units — the trailing gap does not exist, and getting that wrong
        // is an off-by-one that shows up as a clipped icon rather than as an exception.
        int widthForThree = (int) (CategoryTabsLayout.LEFT
                + 3 * CategoryTabsLayout.ICON_SIZE + 2 * CategoryTabsLayout.ICON_GAP + 8f);
        assertEquals(3, CategoryTabsLayout.visibleIcons(widthForThree, 8),
                "only what fits is drawn — shrinking icons is how the old strip became unreadable");
        assertEquals(2, CategoryTabsLayout.visibleIcons(widthForThree - 1, 8),
                "one pixel short of the third icon shows two, not a clipped third");
        assertEquals(5, CategoryTabsLayout.visibleIcons(4000, 5), "a wide window shows the whole category");
        assertEquals(0, CategoryTabsLayout.visibleIcons(4000, 0));
    }
}
