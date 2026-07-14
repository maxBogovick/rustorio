# 📄 Решение — Р1 — `Config`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C6-workbook.md](../C6-workbook.md)

---

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
