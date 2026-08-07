package com.rustorio.domain.building;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * {@link WorldServices} — the bag a world's capabilities live in. Its shutdown behaviour is the
 * part worth pinning: it runs when a game closes, which is exactly when nobody is watching and
 * nobody is left to report a failure to, so "one service throwing must not strand the others" can
 * only ever be established here rather than noticed in play.
 */
class WorldServicesTest {

    private static final ServiceKey<Recorder> FIRST =
            new ServiceKey<>(ContentId.of("test:first"), Recorder.class);
    private static final ServiceKey<Recorder> SECOND =
            new ServiceKey<>(ContentId.of("test:second"), Recorder.class);
    private static final ServiceKey<Recorder> ABSENT =
            new ServiceKey<>(ContentId.of("test:absent"), Recorder.class);

    @Test
    void aRegisteredServiceComesBackAsTheVerySameInstance() {
        Recorder service = new Recorder("only", new ArrayList<>(), false);
        WorldServices services = WorldServices.builder().with(FIRST, () -> service).build();

        assertSame(service, services.get(FIRST).orElseThrow(),
                "a building must get exactly what its mod's provider returned, not a copy");
    }

    /**
     * Two worlds, one set of providers: each gets its OWN service instance. This is the whole point
     * of registering a provider rather than an object, and the live crash it prevents is a player
     * returning to the main menu and starting a second game — the first game's shutdown used to
     * close the thread pool the second one was about to use.
     */
    @Test
    void everyBuildCreatesFreshInstancesSoOneWorldCannotCloseAnothers() {
        List<String> closed = new ArrayList<>();
        WorldServices.Builder providers = WorldServices.builder()
                .with(FIRST, () -> new Recorder("first", closed, false));

        WorldServices firstWorld = providers.build();
        WorldServices secondWorld = providers.build();

        assertNotSame(firstWorld.get(FIRST).orElseThrow(), secondWorld.get(FIRST).orElseThrow(),
                "each world must get its own instance, or closing one breaks the other");

        firstWorld.closeAll();
        assertEquals(List.of("first"), closed, "closing one world closes exactly one instance");
    }

    @Test
    void anUnregisteredKeyIsEmptyRatherThanAnError() {
        WorldServices services = WorldServices.builder()
                .with(FIRST, () -> new Recorder("only", new ArrayList<>(), false))
                .build();

        assertEquals(Optional.empty(), services.get(ABSENT),
                "a world whose provider mod isn't installed must answer 'no such service', not throw");
        assertEquals(Optional.empty(), WorldServices.NONE.get(FIRST),
                "NONE is a legitimate world, not a broken one");
    }

    /**
     * The failure mode this method exists to survive: a service that throws on close must not stop
     * the ones registered after it from being closed. Without this, one badly-behaved mod would
     * leave every other mod's thread pool running for the life of the JVM.
     */
    @Test
    void closingKeepsGoingAfterOneServiceThrows() {
        List<String> closed = new ArrayList<>();
        WorldServices services = WorldServices.builder()
                .with(FIRST, () -> new Recorder("first", closed, true))
                .with(SECOND, () -> new Recorder("second", closed, false))
                .build();

        services.closeAll();

        assertEquals(List.of("first", "second"), closed,
                "both services must have been asked to close, in registration order");
    }

    @Test
    void closingAWorldWithNoServicesIsAHarmlessNoOp() {
        WorldServices.NONE.closeAll();
        assertTrue(true, "reaching this line without an exception is the assertion");
    }

    /** Records that it was asked to close, and optionally throws afterwards — the shape a misbehaving mod's service has. */
    private record Recorder(String name, List<String> closedLog, boolean throwOnClose) implements AutoCloseable {

        @Override
        public void close() throws Exception {
            closedLog.add(name);
            if (throwOnClose) {
                throw new IllegalStateException("this service fails to shut down cleanly");
            }
        }
    }
}
