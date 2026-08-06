package com.rustorio.domain.building;

/**
 * Everything a building's prototype says about electricity, in one component rather than three
 * loose fields on {@link BuildingPrototype}: a pole's coverage {@code radius}, a generator's {@code
 * output} per tick, a machine's {@code demand} per tick. Any given building uses exactly one of the
 * three and leaves the others at zero; a building with no {@code PowerSpec} at all (which is almost
 * all of them) has nothing to do with electricity.
 *
 * <p>Grouped rather than spread out because that is what makes {@code power} optional as a WHOLE —
 * "this building is not electrical" is one {@code null}, not three zeros that each have to mean
 * "unset". It is also the shape a JSON author writes: {@code "power": { "radius": 5 }}.
 *
 * <p><b>Demand is what makes electricity optional</b> (owner decision): only a building that
 * declares one is ever gated on power. A miner, a furnace and a lab keep working with no
 * electricity anywhere on the map, exactly as they did before this existed — no migration, no
 * rebalancing of a factory somebody already built.
 */
public record PowerSpec(int radius, int output, int demand) {

    public PowerSpec {
        if (radius < 0 || output < 0 || demand < 0) {
            throw new IllegalArgumentException(
                    "PowerSpec values must not be negative: radius=" + radius + " output=" + output
                            + " demand=" + demand);
        }
    }

    /** A pole: covers a square of {@code radius} cells around itself and neither makes nor uses power. */
    public static PowerSpec pole(int radius) {
        return new PowerSpec(radius, 0, 0);
    }

    /** A generator: contributes {@code output} per tick to whatever network covers it. */
    public static PowerSpec generator(int output) {
        return new PowerSpec(0, output, 0);
    }

    /** A machine that needs {@code demand} per tick to run at all. */
    public static PowerSpec consumer(int demand) {
        return new PowerSpec(0, 0, demand);
    }
}
