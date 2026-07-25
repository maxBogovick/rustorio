package com.rustorio.domain.building;

/**
 * Countdown to the next finished batch — the part {@link Furnace} and {@link Lab} share instead
 * of each keeping its own copy of the same "count ticks down, then reset" loop (composition over
 * duplication, Effective Java Item 18).
 *
 * <p>Knows nothing about items, recipes or technologies — it just counts. What "effective time"
 * means for a given batch (a tech upgrade might halve it) is the caller's decision, passed in
 * fresh on every {@link #tick}; that keeps this class trivially unit-testable in isolation.
 */
final class ProcessTimer {

    private int cooldown;

    ProcessTimer(int initialTime) {
        this.cooldown = initialTime;
    }

    /**
     * Live one tick. Returns {@code true} exactly on the tick a batch becomes ready, resetting the
     * countdown to {@code nextTime} (already tech-adjusted) in the same call.
     */
    boolean tick(int nextTime) {
        if (--cooldown > 0) {
            return false;
        }
        cooldown = nextTime;
        return true;
    }

    int cooldown() {
        return cooldown;
    }

    void restore(int cooldown) {
        this.cooldown = cooldown;
    }
}
