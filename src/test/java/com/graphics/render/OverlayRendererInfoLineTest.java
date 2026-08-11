package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.api.content.model.ItemShape;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link OverlayRenderer#infoLine} — pure logic (no libGDX), extracted to package-private
 * specifically so it can be checked headless, same reason as {@link CameraViewport}. Proves the
 * fix for code review finding S2: this used to always walk a hardcoded {@code
 * VanillaItems.frozen()}, so a modded item sitting in a chest would never show up in its on-map
 * label no matter what registry the game was actually running with.
 */
class OverlayRendererInfoLineTest {

    @Test
    void infoLineReportsAModdedItemWhenGivenARegistryThatKnowsAboutIt() {
        ItemType copperOre = new ItemType(ContentId.of("test:copper_ore"), "Copper Ore", false, 0, ItemShape.CIRCLE);
        Registry<ItemType> modded = new Registry<>();
        modded.register(copperOre.id(), copperOre);
        modded.freeze();

        World world = new World(4, 4);
        Chest chest = new Chest();
        chest.accept(world, copperOre);

        String line = OverlayRenderer.infoLine(chest, modded);

        assertTrue(line.contains("Copper Ore:1"), "expected the modded item in the info line, got: " + line);
    }
}
