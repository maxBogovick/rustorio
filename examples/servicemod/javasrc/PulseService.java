package com.servicemod.jarmod;

import com.rustorio.api.content.ContentId;
import com.rustorio.domain.building.ServiceKey;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A capability the ENGINE has never heard of, provided entirely by this mod: "has another pulse
 * interval elapsed for the machine on this cell?"
 *
 * <p>The point of the fixture is not what it computes — it is that {@link PulseGenerator} cannot
 * work without it, that nothing in com.rustorio or com.graphics mentions it, and that the engine
 * nevertheless delivers it to the building. Before ServiceKey/registerService existed, a mod could
 * only get a capability into a world by having the game's own startup code construct it by name.
 *
 * <p>Counts calls rather than reading a clock: a test asserting on wall-clock time is a flaky test,
 * and this project's determinism rule forbids reading system time in tick logic anyway.
 */
public final class PulseService implements AutoCloseable {

    /** How a building asks a world for this service. The id is namespaced to this mod, exactly like its content. */
    public static final ServiceKey<PulseService> KEY =
            new ServiceKey<>(ContentId.of("servicemod:pulse"), PulseService.class);

    /** Every how many asks a pulse fires. Small so a test needs few ticks; nothing here depends on the exact value. */
    static final int PERIOD = 5;

    private final AtomicInteger asks = new AtomicInteger();

    /** True once every {@link #PERIOD} calls — the whole behavior {@link PulseGenerator} depends on. */
    public boolean nextPulse() {
        return asks.incrementAndGet() % PERIOD == 0;
    }

    /** Nothing to release — implemented only to show that a mod's service MAY hold a resource and will be closed with the world (see {@code WorldServices.closeAll}). */
    @Override
    public void close() {
    }
}
