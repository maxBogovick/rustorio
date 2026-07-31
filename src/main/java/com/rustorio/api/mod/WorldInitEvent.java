package com.rustorio.api.mod;

/** Published once, right after a world is constructed — before anything is placed or ticked. */
public record WorldInitEvent(int width, int height) {
}
