package com.rustorio.core;

/**
 * Технологии, которые можно открыть за очки исследований.
 *
 * <p>Это ИМЕНА, а не описания: «сколько стоит», «что требует», «что даёт» — всё это данные,
 * и живут они в таблице {@code Technology.ALL}. Добавить технологию = добавить сюда имя и
 * одну строку данных. Ни одного нового {@code switch}.
 */
public enum Tech {
    FAST_BELT("Быстрая лента"),
    FAST_FURNACE("Быстрая печь"),
    FAST_MINER("Быстрый бур"),
    LONG_UNDERGROUND("Длинная подземка");

    private final String displayName;

    Tech(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
