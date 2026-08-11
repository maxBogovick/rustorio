package com.graphics.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.domain.world.World;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * {@link InventoryPanelLayout} — pure geometry + stack listing, shared by HUD draw and input
 * hit-testing. No libGDX window required.
 */
class InventoryPanelLayoutTest {

    @Test
    void visibleStacksListsOnlyPositiveAmountsInRegistryIterateOrder() {
        World world = new World(4, 4);
        world.creditItem(VanillaItems.GEAR, 2);

        List<ItemType> stacks = InventoryPanelLayout.visibleStacks(
                world.buildingFactory().items(), world.inventory());

        List<ItemType> expected = new ArrayList<>();
        for (ItemType item : world.buildingFactory().items().iterate()) {
            if (world.inventory().amount(item) > 0) {
                expected.add(item);
            }
        }
        assertEquals(expected, stacks);
        assertTrue(stacks.contains(VanillaItems.IRON_PLATE), "starting plates are visible");
        assertTrue(stacks.contains(VanillaItems.GEAR));
    }

    @Test
    void hitTestFindsTheTopLeftCellAndMissesTheGap() {
        int screenW = 1280;
        int screenH = 800;
        int rows = 2;
        float x = InventoryPanelLayout.cellX(0, screenW) + 2f;
        float hudY = InventoryPanelLayout.cellY(0, rows) + 2f;
        float screenY = screenH - hudY;

        assertEquals(0, InventoryPanelLayout.hitTest(x, screenY, screenW, screenH, rows));

        float gapX = InventoryPanelLayout.cellX(0, screenW) + InventoryPanelLayout.CELL_SIZE + 1f;
        assertEquals(-1, InventoryPanelLayout.hitTest(gapX, screenY, screenW, screenH, rows),
                "gaps between cells are not part of either neighbour");
    }

    @Test
    void reservedWidthLeavesRoomForTheCategoryStrip() {
        assertTrue(InventoryPanelLayout.reservedWidth() > InventoryPanelLayout.totalWidth(3));
    }
}
