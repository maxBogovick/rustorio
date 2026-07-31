package com.rustorio.domain.building;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link StateMigration}: proves the version-transition mechanism ADR-4 requires on a synthetic
 * shape change, before any real prototype needs one — none of today's 12 vanilla prototypes
 * require an actual reshape (E6-05, ENGINE_TASKS.md's own note), the same way {@link Codec} itself
 * was proven on a synthetic {@code Point} before any real archetype had one (E6-01, {@link
 * CodecTest}).
 */
class StateMigrationTest {

    /** v1 shape: {x, y}. v2 shape: {x, y, z}, z defaulting to 0 for anything migrated up from v1. */
    private static final StateMigration ADD_Z_AXIS = new StateMigration() {
        @Override
        public int fromVersion() {
            return 1;
        }

        @Override
        public int toVersion() {
            return 2;
        }

        @Override
        public Map<String, Object> migrate(Map<String, Object> oldState) {
            Map<String, Object> migrated = new LinkedHashMap<>(oldState);
            migrated.put("z", 0);
            return migrated;
        }
    };

    /** v2 shape: {x, y, z}. v3 shape: {x, y, z, w}, w defaulting to 1. */
    private static final StateMigration ADD_W_AXIS = new StateMigration() {
        @Override
        public int fromVersion() {
            return 2;
        }

        @Override
        public int toVersion() {
            return 3;
        }

        @Override
        public Map<String, Object> migrate(Map<String, Object> oldState) {
            Map<String, Object> migrated = new LinkedHashMap<>(oldState);
            migrated.put("w", 1);
            return migrated;
        }
    };

    @Test
    void applyMigratesAV1ShapeToTheV2ShapeAddingTheNewField() {
        Map<String, Object> v1 = Map.of("x", 3, "y", 4);

        Map<String, Object> migrated = StateMigration.apply(v1, 1, 2, List.of(ADD_Z_AXIS));

        assertEquals(Map.of("x", 3, "y", 4, "z", 0), migrated);
    }

    @Test
    void applyIsANoOpWhenTheStateIsAlreadyAtTheTargetVersion() {
        Map<String, Object> v2 = Map.of("x", 3, "y", 4, "z", 7);

        Map<String, Object> result = StateMigration.apply(v2, 2, 2, List.of(ADD_Z_AXIS));

        assertEquals(v2, result, "already-current state must pass through untouched, not consult the chain at all");
    }

    @Test
    void applyChainsMultipleHopsInOrder() {
        Map<String, Object> v1 = Map.of("x", 3, "y", 4);

        Map<String, Object> migrated = StateMigration.apply(v1, 1, 3, List.of(ADD_Z_AXIS, ADD_W_AXIS));

        assertEquals(Map.of("x", 3, "y", 4, "z", 0, "w", 1), migrated);
    }

    @Test
    void applyThrowsWhenAHopIsMissingFromTheChain() {
        Map<String, Object> v1 = Map.of("x", 3, "y", 4);

        assertThrows(IllegalStateException.class,
                () -> StateMigration.apply(v1, 1, 3, List.of(ADD_Z_AXIS)),
                "a gap in the chain (no v2->v3 hop registered) must fail loudly, not silently stop early");
    }
}
