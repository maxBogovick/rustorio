package com.rustorio.core;

/**
 * Что выбрано в панели (клавиши 1..5) — «чертёж», по которому строим здание.
 *
 * <p>{@code Tool} — это выбор игрока и одновременно «тип рецепта» для машин
 * (печь и сборщик ищут рецепты именно по своему {@code Tool}). Отображаемое
 * имя хранится рядом со значением, снова по идиоме «enum с данными».
 */
public enum Tool {
    MINER("Miner"),
    BELT("Belt"),
    FURNACE("Furnace"),
    CHEST("Chest"),
    ASSEMBLER("Assembler");

    private final String displayName;

    Tool(String displayName) {
        this.displayName = displayName;
    }

    /** Человекочитаемое имя для HUD. */
    public String displayName() {
        return displayName;
    }
}
