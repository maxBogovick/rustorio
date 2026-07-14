# C6 — Подсказки и решения

> ⚠️ **Это ответы.** Спутник тетради [C6-workbook.md](C6-workbook.md).

---

## П1 — `Config`

- Импорт: `import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;`
- `fields().that().areDeclaredInClassesThat().haveSimpleName("Config")` — выбрать поля класса.
- `.should().beStatic().andShould().beFinal()` — они обязаны быть константами.
- В `.because(...)` пишите **последствие**, а не пересказ правила: сообщение об ошибке читает
  тот, кто правило нарушил, и ему нужно понять, **чем это плохо**.

---

## Р1 — `Config`

```java
    /**
     * [5] {@code Config} — только неизменяемые константы.
     *
     * <p>В спринте 0 баланс уехал из {@code Config} в {@code Balance}: константы времени
     * компиляции нельзя менять, а прогрессия обязана менять числа во время игры. Это правило
     * стережёт, чтобы через полгода, когда все забудут почему, изменяемое поле не приползло
     * обратно в {@code Config}.
     */
    @ArchTest
    static final ArchRule config_holds_only_constants =
            fields().that().areDeclaredInClassesThat().haveSimpleName("Config")
                    .should().beStatic().andShould().beFinal()
                    .because("изменяемый баланс живёт в Balance, а не в Config");
```

---

## П2 — Детерминизм

- `noClasses().that().resideInAnyPackage("..model..", "..sim..", "..game..")` — **только**
  домен. Рендер не трогаем: там время и анимации законны.
- `.should().callMethod(Math.class, "random")` и через `.orShould()` — ещё
  `System.currentTimeMillis` и `System.nanoTime`.

---

## Р2 — Детерминизм

```java
    /**
     * [6] Симуляция ДЕТЕРМИНИРОВАНА: никакой случайности и никаких «настенных часов».
     *
     * <p>Одинаковые действия игрока обязаны давать одинаковый мир. Без этого не будет ни
     * сохранений (загруженная фабрика поедет иначе, чем сохранённая), ни надёжных тестов
     * (падает через раз — ищи потом причину).
     *
     * <p>Отрисовки правило не касается: там {@code System.nanoTime()} и анимации законны —
     * они не меняют мир.
     */
    @ArchTest
    static final ArchRule simulation_is_deterministic =
            noClasses().that().resideInAnyPackage("..model..", "..sim..", "..game..")
                    .should().callMethod(Math.class, "random")
                    .orShould().callMethod(System.class, "currentTimeMillis")
                    .orShould().callMethod(System.class, "nanoTime")
                    .because("симуляция должна быть воспроизводимой: без этого не будет "
                            + "ни сохранений, ни надёжных тестов");
```

---

## П3 — Слоты

- Это обычный JUnit-тест, ArchUnit тут не нужен: правило про **значения**, а не про структуру
  кода.
- `Set<Integer> used` + `used.add(slot)` возвращает `false`, если слот уже занят.
- Сообщение об ошибке должно называть **оба** инструмента-конфликтёра, иначе искать придётся
  глазами.

---

## Р3 — Слоты

```java
/**
 * Последняя щель в «карте задач от компилятора».
 *
 * <p>Компилятор заставит новый инструмент объявить номер слота — забыть его нельзя. Но он НЕ
 * заметит, если вы напишете чужой номер: два здания на одной клавише скомпилируются прекрасно,
 * а в игре второе окажется недоступным. Ловится это только тестом.
 */
class ToolTest {

    @Test
    @DisplayName("Слоты инструментов уникальны и помещаются на клавиатуру (1..9)")
    void hotkeySlotsAreUniqueAndValid() {
        Set<Integer> used = new HashSet<>();
        for (Tool tool : Tool.values()) {
            int slot = tool.hotkeySlot();
            assertTrue(slot >= 1 && slot <= 9,
                    tool + ": слот " + slot + " вне диапазона 1..9 — на клавиатуре его нет");
            assertTrue(used.add(slot),
                    tool + " занял слот " + slot + ", который уже занят другим инструментом");
        }
    }
}
```

---

## Р4 — Как заставить правило упасть

```bash
# 1. Внести нарушение (временно!)
#    в Simulation.step():  double poison = Math.random();

$ ./gradlew test --tests '*ArchitectureTest*'
ArchitectureTest > simulation_is_deterministic FAILED
    java.lang.AssertionError at ArchRule.java:94

# 2. Откатить и убедиться, что снова зелено
$ ./gradlew test
BUILD SUCCESSFUL
```

Проделайте это для **каждого** из трёх правил. Минута работы — и вы точно знаете, что они
живые, а не декоративные.
