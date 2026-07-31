package com.rustorio.api.mod;

/** Published once at the end of every world tick — {@code tickCount} is the tick that just finished. */
public record TickEvent(long tickCount) {
}
