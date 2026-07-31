package com.examplemod.jarmod;

/**
 * The code half of this fixture mod's acceptance test: registers a brand-new transport node
 * ("conveyor") that fuses into the same belt segment cascade a vanilla Belt joins - proving a
 * REAL, externally-compiled .jar can add genuinely new Java behavior through the mod loader, not
 * just data. The ore/recipe/data-configured-building half of the same mod is plain JSON under
 * content/ next to this source file, loaded automatically by the same loader.
 */
public final class ExampleModEntryPoint implements com.rustorio.api.mod.RustorioMod {

    public static final com.rustorio.api.content.ContentId CONVEYOR_ID =
            com.rustorio.api.content.ContentId.of("examplemod:conveyor");

    @Override
    public void registerContent(com.rustorio.api.mod.RegistrationContext ctx) {
        com.rustorio.api.content.ContentId ironPlateId = com.rustorio.api.content.ContentId.of("rustorio:iron_plate");
        com.rustorio.domain.ItemType ironPlate = ctx.items().peek(ironPlateId).orElseThrow(
                () -> new IllegalStateException("rustorio:iron_plate not visible yet - dependency order is wrong"));

        com.rustorio.domain.building.BuildingPrototype vanillaBelt =
                com.rustorio.domain.building.VanillaBuildings.frozen().get(
                        com.rustorio.domain.building.VanillaBuildings.idFor(com.rustorio.domain.BuildingType.BELT));

        ctx.buildings().register(CONVEYOR_ID, new com.rustorio.domain.building.BuildingPrototype(
                CONVEYOR_ID,
                "Conveyor",
                new com.rustorio.domain.building.BuildingCost(ironPlate, 1),
                com.rustorio.domain.building.PlacementRule.NEEDS_PASSABLE_TERRAIN,
                com.rustorio.domain.VanillaSprites.BELT_EMPTY,
                0, 1, false,
                (self, direction, factory) -> new ExampleModConveyor(self.id(), direction),
                (self, decodedState, factory) -> {
                    com.rustorio.domain.building.BeltState state = (com.rustorio.domain.building.BeltState) decodedState;
                    return new ExampleModConveyor(self.id(), state.direction(), state.held());
                },
                vanillaBelt.codec()));
    }
}
