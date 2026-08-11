package com.rustorio.domain.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.vanilla.VanillaSprites;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

/**
 * Mid-craft must survive an empty input buffer: starting a batch consumes one buffered unit, and
 * the remaining work ticks must still run. The bug this closes reset {@code ticksLeft} whenever
 * {@code buffered == 0}, aborting every multi-tick craft after the first tick.
 */
class SimpleCrafterTest {

    @Test
    void craftFinishesAfterInputBufferIsConsumedIntoTheTimer() {
        SimpleCrafterSpec spec = SimpleCrafterSpec.of(
                ContentId.of("rustorio:iron_ore"), ContentId.of("rustorio:iron_plate"), 3);
        BuildingPrototype prototype = new BuildingPrototype(
                ContentId.of("test:crafter"),
                "Crafter",
                new BuildingCost(VanillaItems.IRON_PLATE, 1),
                PlacementRule.NEEDS_PASSABLE_TERRAIN,
                VanillaSprites.ASSEMBLER,
                1, 1, 5, 1, false,
                (self, direction, factory) -> SimpleCrafter.create(self, direction, spec, factory.items()),
                (self, decoded, factory) ->
                        SimpleCrafter.restore(self, (SimpleCrafter.State) decoded, spec, factory.items()),
                SimpleCrafter.CODEC);

        com.rustorio.api.registry.Registry<BuildingPrototype> buildings = new com.rustorio.api.registry.Registry<>();
        buildings.register(prototype.id(), prototype);
        buildings.freeze();
        BuildingFactory factory = new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard(),
                VanillaItems.frozen(), buildings);
        World world = new World(4, 4, factory);
        SimpleCrafter crafter = (SimpleCrafter) factory.create(prototype.id(), Direction.RIGHT);

        assertTrue(crafter.accept(world, VanillaItems.IRON_ORE));
        assertEquals(1, crafter.state().buffered());

        crafter.tick(world, 0, 0);
        assertEquals(0, crafter.state().buffered(), "first tick consumes the buffered unit into the timer");
        assertNull(crafter.state().held(), "not done after 1 of 3 ticks");
        assertTrue(crafter.state().ticksLeft() > 0);

        crafter.tick(world, 0, 0);
        assertNull(crafter.state().held());

        crafter.tick(world, 0, 0);
        assertNotNull(crafter.state().held(), "third tick must finish even though the buffer stayed empty");
        assertEquals(VanillaItems.IRON_PLATE, crafter.state().held());
    }
}
