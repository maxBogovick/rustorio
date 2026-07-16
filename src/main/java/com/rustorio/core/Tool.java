package com.rustorio.core;

/**
 * Что выбрано в панели — «чертёж», по которому строим здание.
 *
 * <p><b>Про {@link #hotkeySlot()}.</b> Если клавиши прибиты в слое ввода лесенкой
 * {@code if (нажата 1) … if (нажата 4)}, то добавив пятое здание, компилятор
 * промолчит — и здание молча окажется недоступным игроку. Номер слота — часть
 * самого инструмента: забыть его нельзя, новый элемент enum обязан его объявить.
 *
 * <p>Слот — это ЧИСЛО, а не клавиша libGDX: {@code core} не имеет права знать про
 * движок (это проверяет {@code ArchitectureTest}). Превращение слота в реальную
 * клавишу — работа слоя ввода, которому про движок знать и положено.
 */
public enum Tool {
    MINER("Miner", 1),
    BELT("Belt", 2),
    FURNACE("Furnace", 3),
    CHEST("Chest", 4);

    private final String displayName;
    private final int hotkeySlot;

    Tool(String displayName, int hotkeySlot) {
        this.displayName = displayName;
        this.hotkeySlot = hotkeySlot;
    }

    /** Человекочитаемое имя для HUD. */
    public String displayName() {
        return displayName;
    }

    /** Номер слота панели (1..9). Слой ввода сам сопоставит его клавише. */
    public int hotkeySlot() {
        return hotkeySlot;
    }
}
