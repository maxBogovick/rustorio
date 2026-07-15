package com.rustorio.persist;

import com.rustorio.core.Item;

import java.util.List;
import java.util.Map;

/**
 * Буфер перерабатывающей машины (печь/сборщик/лаборатория): что накоплено на складе и что
 * уже готово и ждёт отправки.
 *
 * <p>Аналоговый прогресс текущего цикла НЕ хранится намеренно — только дискретные предметы.
 * Сырьё недоделанного цикла всё равно лежит в {@code stock} (ингредиенты списываются лишь в
 * конце), поэтому ни один предмет не теряется, а таймер просто пойдёт заново.
 */
public record MachineDto(Map<Item, Integer> stock, List<Item> ready) {

    public MachineDto {
        stock = Map.copyOf(stock);
        ready = List.copyOf(ready);
    }
}
