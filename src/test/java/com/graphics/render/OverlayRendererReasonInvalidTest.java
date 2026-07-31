package com.graphics.render;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link OverlayRenderer#reasonInvalid} — pure logic (no libGDX), extracted to package-private
 * specifically so it can be checked headless, same reason as {@link OverlayRenderer#infoLine}
 * (see {@link OverlayRendererInfoLineTest}). Proves the fix for code review finding S3: the
 * "can't afford" message used to interpolate {@link com.rustorio.domain.ItemType} directly, which
 * printed the record's default every-field dump instead of the item's name.
 */
class OverlayRendererReasonInvalidTest {

    @Test
    void reportsTheMissingItemsLabelNotARecordDump() {
        // A fresh World starts with IRON_PLATE but no GEAR at all, and LAB costs 10 GEAR
        // (BuildingCost.forType) — guaranteed unaffordable without placing/producing anything.
        World world = new World(4, 4);

        String reason = OverlayRenderer.reasonInvalid(world,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.LAB)), 1, 1);

        assertEquals("need 10 Gear (have 0)", reason);
    }
}
