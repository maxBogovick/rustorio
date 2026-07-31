package com.rustorio.domain.building;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A live bug report: a mod building reusing an existing archetype (a modded "voron" furnace, say)
 * rendered on the map with the vanilla archetype's OWN sprite instead of the modded prototype's
 * own {@code texture} — every archetype's {@code appearance()} used to hardcode a {@link
 * VanillaSprites} constant rather than reading {@link BuildingPrototype#texture()}, even though
 * that field is exactly what {@code BuildingJsonLoader} populates from a mod's JSON. Menu/HUD/ghost
 * rendering already read {@link BuildingPrototype#texture()} directly and looked correct; only the
 * PLACED building (via {@code Building#appearance()}) did not.
 *
 * <p>{@link Miner} stands in for every non-{@link Furnace} archetype here — the same fix (a
 * {@code prototype} field threaded through the constructor, read by {@code appearance()}) was
 * applied identically to all eight of them.
 */
class ModdedBuildingTextureTest {

    private static final ContentId CUSTOM_TEXTURE = ContentId.of("examplemod:deep_miner");

    @Test
    void moddedMinerDrawsItsOwnPrototypeTextureNotTheVanillaMinerSprite() {
        BuildingPrototype deepMiner = new BuildingPrototype(
                ContentId.of("examplemod:deep_miner"),
                "Deep Miner",
                new BuildingCost(VanillaItems.IRON_PLATE, 10),
                PlacementRule.NEEDS_ORE,
                CUSTOM_TEXTURE,
                0, 1, false,
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.MINER)).behavior(),
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.MINER)).restoreBehavior(),
                VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.MINER)).codec());

        Miner miner = new Miner(PatchOreLayout.standard(), Direction.RIGHT, deepMiner);

        assertEquals(CUSTOM_TEXTURE, miner.appearance().sprite(),
                "a modded miner must draw its OWN prototype's texture, not the vanilla one");
        assertNotEquals(VanillaSprites.MINER, miner.appearance().sprite());
    }

    @Test
    void vanillaMinerStillDrawsTheVanillaSprite() {
        Miner miner = new Miner(PatchOreLayout.standard(), Direction.RIGHT);

        assertEquals(VanillaSprites.MINER, miner.appearance().sprite(),
                "the convenience constructor must still default to the vanilla prototype's own texture");
    }
}
