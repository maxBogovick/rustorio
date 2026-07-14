package com.rustorio.core;

/**
 * Что выбрано в панели — «чертёж», по которому строим здание.
 *
 * <p>{@code Tool} — это одновременно выбор игрока и «тип машины» для рецептов (печь и
 * сборщик ищут рецепты именно по своему {@code Tool}).
 *
 * <p><b>Про {@link #hotkeySlot()}.</b> Раньше клавиши были прибиты в слое ввода
 * лесенкой {@code if (нажата 1) … if (нажата 5)}. Добавь шестое здание — компилятор
 * промолчит, а здание молча окажется недоступным игроку. Теперь номер слота — часть
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
    CHEST("Chest", 4),
    ASSEMBLER("Assembler", 5),
    SPLITTER("Splitter", 6),
    UNDERGROUND("Underground", 7),
    LAB("Lab", 8);

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
