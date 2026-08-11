package com.rustorio.domain.building;

import java.util.List;
import java.util.Map;

/**
 * One pure transformation of a decoded state's own {@code Map<String,Object>} shape from one
 * {@code state_version} to the very next one — the mechanism ADR-4 requires so that any FUTURE
 * reshape of a prototype's state travels as a function between versions, not another format break
 * the way {@link com.rustorio.persistence.WorldSnapshot#CURRENT_VERSION} bump already was (that
 * record's own javadoc calls the codec-based break Phase 6 makes the last one it allows). Chained
 * one hop at a time, via {@link #apply}, from whatever version a save recorded up to a prototype's
 * current one.
 *
 * <p>{@link BuildingPrototype#decodeState} runs {@link #apply} on every load. Stamping
 * {@code state_version} on encode waits until a prototype's {@link BuildingPrototype#stateVersion()}
 * rises above 1 together with a real hop list — writing the field onto every vanilla save today
 * would change the on-disk shape for an unreachable override (this type is a record). Proven on a
 * synthetic shape change ({@code StateMigrationTest}) the same way {@link Codec} itself was proven
 * on a synthetic {@code Point} before any real archetype had one.
 */
public interface StateMigration {

    /** The {@code state_version} this migration reads. */
    int fromVersion();

    /** The {@code state_version} this migration produces — always {@code fromVersion() + 1}, one hop, never a jump. */
    int toVersion();

    /** The pure transformation itself: one version's decoded shape in, the next version's shape out. */
    Map<String, Object> migrate(Map<String, Object> oldState);

    /**
     * Walks {@code migrations} one hop at a time from {@code fromVersion} to {@code toVersion}. A
     * no-op if the two are already equal (state already at the target version). Throws if any
     * intermediate hop is missing — a gap in the chain means the state genuinely can't be migrated,
     * which must fail loudly rather than silently stop partway through.
     */
    static Map<String, Object> apply(
            Map<String, Object> state, int fromVersion, int toVersion, List<StateMigration> migrations) {
        Map<String, Object> current = state;
        int version = fromVersion;
        while (version < toVersion) {
            int atVersion = version;
            StateMigration hop = migrations.stream()
                    .filter(candidate -> candidate.fromVersion() == atVersion)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "no state migration registered from version " + atVersion
                                    + " (need to reach " + toVersion + ")"));
            current = hop.migrate(current);
            version = hop.toVersion();
        }
        return current;
    }
}
