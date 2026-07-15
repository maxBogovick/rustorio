package com.rustorio.persist;

import com.rustorio.core.Tech;

import java.util.List;

/**
 * Снимок исследований: накопленные очки и список открытых технологий.
 *
 * <p>Технологии — список имён {@link Tech}, а не флаги: добавится пятая технология, и старые
 * сохранения останутся читаемыми (в списке её просто нет). Порядок при снятии фиксирован
 * (по {@code ordinal}), чтобы одинаковое состояние давало байт-в-байт одинаковый JSON — это
 * и делает возможной проверку round-trip.
 */
public record ResearchDto(int points, List<Tech> unlocked) {

    public ResearchDto {
        unlocked = List.copyOf(unlocked);
    }
}
