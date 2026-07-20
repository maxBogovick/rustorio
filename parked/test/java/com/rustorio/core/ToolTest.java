package com.rustorio.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Последняя щель в «карте задач от компилятора».
 *
 * <p>Компилятор заставит новый инструмент объявить номер слота — забыть его нельзя. Но он
 * НЕ заметит, если вы напишете чужой номер: два здания на одной клавише скомпилируются
 * прекрасно, а в игре второе окажется недоступным. Ловится это только тестом.
 */
class ToolTest {

    @Test
    @DisplayName("Слоты инструментов уникальны и помещаются на клавиатуру (0..9)")
    void hotkeySlotsAreUniqueAndValid() {
        Set<Integer> used = new HashSet<>();
        for (Tool tool : Tool.values()) {
            int slot = tool.hotkeySlot();
            assertTrue(slot >= 0 && slot <= 9,
                    tool + ": слот " + slot + " вне диапазона 0..9 — на клавиатуре его нет");
            assertTrue(used.add(slot),
                    tool + " занял слот " + slot + ", который уже занят другим инструментом");
        }
    }
}
