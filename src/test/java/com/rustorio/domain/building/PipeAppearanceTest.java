package com.rustorio.domain.building;

import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.FluidFill;
import com.rustorio.api.content.model.FluidType;
import com.rustorio.api.content.vanilla.VanillaFluids;
import com.rustorio.domain.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * A fluid tile draws a real fill bar in its fluid's colour, not the bare percentage badge that
 * stood in for it before {@link Appearance} grew a {@link FluidFill} field. The number is the same
 * either way — how full the whole one-bucket network is — but a bar the renderer can colour needs
 * the fluid, not just an integer, so the two are carried together and the old badge is gone from a
 * pipe entirely (showing both would say the same thing twice).
 */
class PipeAppearanceTest {

    private static final FluidType WATER = VanillaFluids.WATER;

    @Test
    void aHalfFullPipeShowsAFillBarInItsFluidNotABadge() {
        World world = new World(20, 20);
        world.place(BuildingType.PIPE, 5, 5);
        world.place(BuildingType.PIPE, 6, 5); // two pipes = one 200-unit bucket
        world.fluidPort(4, 5, Direction.RIGHT).orElseThrow().insert(WATER, 100); // half full

        Appearance look = world.peek(5, 5).orElseThrow().appearance();

        FluidFill fill = look.fill();
        assertNotNull(fill, "a pipe carrying fluid draws a real bar, not the bare percentage badge it used to");
        assertEquals(WATER, fill.fluid());
        assertEquals(50, fill.percent(), "one bucket half full — every tile of the network reports the same fraction");
        assertFalse(look.hasBadge(), "the fill bar replaces the percentage badge; showing both would say the same thing twice");
    }

    @Test
    void anEmptyPipeShowsNeitherBarNorBadge() {
        World world = new World(20, 20);
        world.place(BuildingType.PIPE, 5, 5);

        Appearance look = world.peek(5, 5).orElseThrow().appearance();

        assertNull(look.fill(), "an empty network has no fluid to colour a bar with");
        assertFalse(look.hasBadge());
    }
}
