package com.rustorio.domain;

/**
 * Logical appearance name — not a texture. The domain says "I look like a hot furnace"; turning
 * that name into actual pixels is the renderer's job ({@code com.graphics.render.Textures}).
 */
public enum Sprite {
    MINER,
    CHEST,
    FURNACE_HOT,
    FURNACE_COLD,
    BELT_EMPTY,
    BELT_FULL,
    SPLITTER,
    FILTER,
    INSERTER,
    UNDERGROUND_IN,
    UNDERGROUND_OUT,
    LAB,
    ASSEMBLER
}
