package com.rustorio.domain.building;

import com.rustorio.domain.Cell;
import java.util.List;

/**
 * Place/remove wiring for fluid and power networks — called only from {@code World}. Kept off
 * {@link BuildingFactory} so that factory (which behavior lambdas name) can ship in {@code
 * rustorio-api} without pulling {@link FluidNetwork}/{@link PowerNetwork} into the mod surface.
 */
public final class NetworkWiring {

    private NetworkWiring() {
    }

    public static void attachFluidNode(FluidNode node, int x, int y, List<FluidNode> neighbors) {
        FluidNetwork.attach(node, new Cell(x, y), neighbors);
    }

    public static void detachFluidNode(FluidNode node, int x, int y) {
        FluidNetwork.detach(node, new Cell(x, y));
    }

    public static void attachPowerNode(PowerNode node, int x, int y, List<PowerNode> neighbors) {
        PowerNetwork.attach(node, new Cell(x, y), neighbors);
    }

    public static void detachPowerNode(PowerNode node, int x, int y, PowerNetwork.Reach reach) {
        PowerNetwork.detach(node, new Cell(x, y), reach);
    }
}
