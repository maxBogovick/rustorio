package com.rustorio.domain.building;

import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaItems;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trips each of the nine vanilla archetypes' own {@link Codec} — {@code
 * decode(encode(state)) == state} — without going anywhere near JSON or a file (that's E6-03's
 * job); this is purely the codec's own encode/decode contract, on realistic (not default-empty)
 * values, so a field silently dropped or misordered would actually be caught.
 */
class VanillaBuildingsCodecTest {

    private static final Registry<ItemType> ITEMS = VanillaItems.frozen();

    private static <S> S roundTrip(BuildingType type, S state) {
        @SuppressWarnings("unchecked")
        Codec<S> codec = (Codec<S>) VanillaBuildings.frozen().get(VanillaBuildings.idFor(type)).codec();
        Object encoded = codec.encode(state);
        return codec.decode(encoded, ITEMS);
    }

    @Test
    void minerStateRoundTrips() {
        MinerState state = new MinerState(Direction.RIGHT, 2, VanillaItems.IRON_ORE, 1);

        assertEquals(state, roundTrip(BuildingType.MINER, state));
    }

    @Test
    void minerStateWithNoHeldItemRoundTrips() {
        MinerState state = new MinerState(Direction.UP, 3, null, 0);

        assertEquals(state, roundTrip(BuildingType.MINER, state));
    }

    @Test
    void chestStateRoundTrips() {
        Map<ItemType, Integer> contents = Map.of(VanillaItems.IRON_PLATE, 12, VanillaItems.GEAR, 3);
        ChestState state = new ChestState(Direction.DOWN, contents, 2);

        assertEquals(state, roundTrip(BuildingType.CHEST, state));
    }

    @Test
    void furnaceStateRoundTrips() {
        FurnaceState state = new FurnaceState(
                Direction.RIGHT, List.of(2, 0), 4,
                VanillaItems.IRON_PLATE, VanillaItems.GEAR, 3,
                VanillaItems.CHASSIS, 1);

        assertEquals(state, roundTrip(BuildingType.FURNACE, state));
    }

    @Test
    void furnaceStateWithNoActiveRecipeRoundTrips() {
        FurnaceState state = new FurnaceState(Direction.LEFT, List.of(), 0, null, null, 0, null, 0);

        assertEquals(state, roundTrip(BuildingType.PRESS, state));
    }

    @Test
    void beltStateRoundTrips() {
        BeltState state = new BeltState(Direction.RIGHT, VanillaItems.IRON_ORE);

        assertEquals(state, roundTrip(BuildingType.BELT, state));
    }

    @Test
    void splitterStateRoundTrips() {
        SplitterState state = new SplitterState(Direction.UP, VanillaItems.GEAR, true);

        assertEquals(state, roundTrip(BuildingType.SPLITTER, state));
    }

    @Test
    void filterStateRoundTrips() {
        FilterState state = new FilterState(Direction.DOWN, VanillaItems.IRON_ORE, VanillaItems.BRONZE_ORE);

        assertEquals(state, roundTrip(BuildingType.FILTER, state));
    }

    @Test
    void inserterStateRoundTrips() {
        InserterState state = new InserterState(Direction.LEFT, VanillaItems.GEAR);

        assertEquals(state, roundTrip(BuildingType.INSERTER, state));
    }

    @Test
    void labStateRoundTrips() {
        LabState state = new LabState(List.of(VanillaItems.GEAR, VanillaItems.CHASSIS), 5, 1);

        assertEquals(state, roundTrip(BuildingType.LAB, state));
    }

    @Test
    void undergroundBeltStateRoundTrips() {
        UndergroundBeltState state = new UndergroundBeltState(UndergroundBelt.Kind.IN, Direction.RIGHT, VanillaItems.IRON_ORE);

        assertEquals(state, roundTrip(BuildingType.UNDERGROUND_IN, state));
    }
}
