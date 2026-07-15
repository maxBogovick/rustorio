package com.rustorio.persist;

import com.rustorio.core.Tool;

import java.util.Map;

/**
 * Снимок баланса: множители скорости машин и два числа-апгрейда.
 *
 * <p>Храним РЕЗУЛЬТАТ (готовые множители), а не историю апгрейдов. Так снимок не зависит от
 * того, в каком порядке игрок открывал технологии, и загрузка не «переигрывает» прогрессию.
 */
public record BalanceDto(
        Map<Tool, Float> speed,
        int beltSlotsPerTick,
        int undergroundReach) {

    public BalanceDto {
        speed = Map.copyOf(speed);
    }
}
