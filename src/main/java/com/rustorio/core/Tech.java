package com.rustorio.core;

/**
 * Технологии, которые можно открыть за очки исследований.
 *
 * <p>Это ИМЕНА, а не описания: «сколько стоит», «что требует», «что даёт» — всё это данные,
 * и живут они в таблице {@code Technology.ALL}. Добавить технологию = добавить сюда имя и
 * одну строку данных. Ни одного нового {@code switch}.
 */
public enum Tech {
    // Имена на экране — латиницей: встроенный шрифт движка кириллицу не рисует.
    FAST_BELT("Fast belt"),
    FAST_FURNACE("Fast furnace"),
    FAST_MINER("Fast miner"),
    LONG_UNDERGROUND("Long underground");

    private final String displayName;

    Tech(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
