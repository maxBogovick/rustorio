package com.examplemod;

import com.rustorio.domain.BuildingType;
import com.rustorio.domain.building.Building;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * E5-06's own acceptance criterion: a class outside {@code com.rustorio.domain.building},
 * implementing {@link Building} directly, must compile and behave like any other building where
 * it overrides the interface — this was flatly impossible while {@code Building} was {@code
 * sealed} with a fixed {@code permits} list naming only the ten vanilla classes.
 */
class BuildingOpennessTest {

    @Test
    void aForeignClassOutsideDomainBuildingCanImplementBuildingDirectly() {
        Building foreign = new ExampleModBuilding();

        assertEquals(BuildingType.CHEST, foreign.type());
        assertEquals(0, foreign.speedLevel(), "capability defaults still apply to a foreign implementer");
        assertEquals(1, foreign.footprintWidth());
    }

    @Test
    void stateStillNeedsARegisteredCodecToOpenUpSeparately() {
        Building foreign = new ExampleModBuilding();

        assertThrows(UnsupportedOperationException.class, foreign::state,
                "this stand-in class registers no prototype/Codec of its own anywhere");
    }
}
