package com.rustorio.domain.world;

/** Observer pattern: notified once at the end of every {@link World#tick()}, with the tick that just finished. */
@FunctionalInterface
public interface TickListener {
    void onTick(long tickCount);
}
