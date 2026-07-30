package com.rustorio;

import com.rustorio.core.Item;

import java.util.EnumMap;
import java.util.Map;

public class ProductionStats {
    private final Map<Item, Long> totals = new EnumMap<>(Item.class);

    void record(Item item){
        /*Long old = totals.get(item);
        if (old == null) {
            totals.put(item, 1L);
        } else {
            totals.put(item, old + 1L);
        }*/
        /*If the specified key is not already associated with a value or is associated with null,
         associates it with the given non-null value (optional operation). Otherwise, replaces
          the associated value with the results of the given remapping function, or removes if
           the result is null.
         */
        totals.merge(item, 1L, Long::sum);
    }

    public long total(Item item){
        /*if (totals.containsKey(item)) {
            return totals.get(item);
        } else {
            return 0L;
        }*/
        return totals.getOrDefault(item, 0L);
    }

    void clear(){
        totals.forEach((k,v) -> totals.remove(k));
    }

    void set(Item item, long value) {
        totals.put(item, value);
    }
}
