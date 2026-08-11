package com.rustorio.domain.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.Research;
import com.rustorio.api.content.model.TechType;
import com.rustorio.api.content.vanilla.VanillaItems;
import com.rustorio.api.content.vanilla.VanillaTechEffects;
import com.rustorio.api.content.vanilla.VanillaTechs;
import com.rustorio.domain.world.World;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Buffer / tunnel bonuses must key off {@link VanillaTechEffects}, not a hardcoded tech id — so a
 * mod can grant {@code big_buffer_effect} / {@code long_tunnel_effect} from its own technology
 * without unlocking the vanilla tech node.
 */
class TechEffectGameplayTest {

    @Test
    void chestCapacityDoublesWhenBigBufferEffectIsGrantedByAForeignTech() {
        Research research = researchWithForeignEffect(VanillaTechEffects.BIG_BUFFER);
        TickContext world = researchOnly(research);
        Chest chest = new Chest();
        int filled = 0;
        while (chest.accept(world, VanillaItems.IRON_ORE)) {
            filled++;
        }
        assertEquals(200, filled);
        assertFalse(research.isUnlocked(VanillaTechs.BIG_BUFFER));
    }

    @Test
    void furnaceBufferDoublesWhenBigBufferEffectIsGrantedByAForeignTech() {
        Registry<TechType> techs = techsWithForeignEffect(VanillaTechEffects.BIG_BUFFER);
        World world = new World(8, 4, new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard()), techs);
        ContentId foreign = ContentId.of("mymod:bonus");
        world.addResearchPoints(1);
        assertTrue(world.tryUnlockTech(foreign));

        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RecipeBook.standard());
        int bufferMax = VanillaBuildings.frozen().get(VanillaBuildings.idFor(BuildingType.FURNACE)).bufferMax();
        for (int i = 0; i < bufferMax; i++) {
            assertTrue(furnace.accept(world, VanillaItems.IRON_ORE));
        }
        assertTrue(furnace.accept(world, VanillaItems.IRON_ORE),
                "foreign tech granting big_buffer_effect must double the furnace input buffer");
        assertFalse(world.research().isUnlocked(VanillaTechs.BIG_BUFFER));
    }

    @Test
    void undergroundFindsPartnerBeyondBaseRangeWhenLongTunnelEffectIsGranted() {
        Registry<TechType> techs = techsWithForeignEffect(VanillaTechEffects.LONG_TUNNEL);
        World world = new World(12, 4, new BuildingFactory(PatchOreLayout.standard(), RecipeBook.standard()), techs);
        ContentId foreign = ContentId.of("mymod:bonus");
        world.addResearchPoints(1);
        assertTrue(world.tryUnlockTech(foreign));

        // Base MAX_RANGE is 4; place OUT at step 5 — unreachable without the effect, reachable with it.
        world.placeUndergroundIn(0, 0, Direction.RIGHT);
        world.placeUndergroundOut(5, 0, Direction.RIGHT);
        UndergroundBelt in = (UndergroundBelt) world.peek(0, 0).orElseThrow();

        assertTrue(in.findPartner(world, 0, 0).isPresent(),
                "long_tunnel_effect from a foreign tech must extend tunnel search range");
        assertFalse(world.research().isUnlocked(VanillaTechs.LONG_TUNNEL));
    }

    private static Research researchWithForeignEffect(ContentId effect) {
        Registry<TechType> techs = techsWithForeignEffect(effect);
        Research research = new Research(techs);
        research.addPoints(1);
        assertTrue(research.unlock(ContentId.of("mymod:bonus")));
        return research;
    }

    private static Registry<TechType> techsWithForeignEffect(ContentId effect) {
        Registry<TechType> techs = new Registry<>();
        VanillaTechs.registerAll(techs);
        ContentId foreign = ContentId.of("mymod:bonus");
        techs.register(foreign, new TechType(foreign, "Bonus", 1, List.of(), List.of(effect)));
        techs.freeze();
        return techs;
    }

    private static TickContext researchOnly(Research research) {
        return new TickContext() {
            @Override
            public boolean offerForward(int x, int y, ItemType item) {
                return false;
            }

            @Override
            public java.util.Optional<Building> peek(int x, int y) {
                return java.util.Optional.empty();
            }

            @Override
            public com.rustorio.domain.ResearchView research() {
                return research;
            }

            @Override
            public void notifyProduced(ItemType item) {
            }

            @Override
            public void addResearchPoints(int amount) {
            }

            @Override
            public java.util.Optional<FluidPort> fluidPort(int x, int y, Direction side) {
                return java.util.Optional.empty();
            }

            @Override
            public boolean drawPower(int x, int y, long amount) {
                return true;
            }

            @Override
            public <T> java.util.Optional<T> service(ServiceKey<T> key) {
                return java.util.Optional.empty();
            }
        };
    }
}
