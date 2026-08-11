package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.AuthoredMap;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.building.BuildingFactory;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The accent stripe's category is read from a prototype's declared data, so it stays correct for a
 * mod's own fluid or power building with no change to the renderer. Pinned against the real vanilla
 * prototypes: the electrical trio (pole, generator, electric miner) reads POWER, the fluid machines
 * (pump, boiler) read FLUID, and everything with neither — pipe, tank, furnace, belt — reads NONE.
 */
class BuildingAccentTest {

    private static final BuildingFactory FACTORY = new BuildingFactory(
            AuthoredOreLayout.from(new AuthoredMap(ContentId.of("test:flat"), List.of(), List.of())),
            RecipeBook.standard());

    private static BuildingAccent accentOf(String id) {
        return BuildingAccent.forPrototype(FACTORY.prototype(ContentId.of(id)));
    }

    @Test
    void poleGeneratorAndElectricMinerReadAsPower() {
        assertEquals(BuildingAccent.POWER, accentOf("rustorio:pole"));
        assertEquals(BuildingAccent.POWER, accentOf("rustorio:generator"),
                "a generator burns steam too, but power wins — it is the electrical end of the chain");
        assertEquals(BuildingAccent.POWER, accentOf("rustorio:electric_miner"));
    }

    @Test
    void pumpAndBoilerReadAsFluidMachines() {
        assertEquals(BuildingAccent.FLUID, accentOf("rustorio:pump"));
        assertEquals(BuildingAccent.FLUID, accentOf("rustorio:boiler"));
    }

    @Test
    void plainBuildingsAndPassiveFluidTilesReadAsNone() {
        assertEquals(BuildingAccent.NONE, accentOf("rustorio:pipe"),
                "a pipe shows fluid through its joints and fill bar, not a stripe");
        assertEquals(BuildingAccent.NONE, accentOf("rustorio:tank"));
        assertEquals(BuildingAccent.NONE, accentOf("rustorio:furnace"));
        assertEquals(BuildingAccent.NONE, accentOf("rustorio:belt"));
    }
}
