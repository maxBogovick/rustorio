# 💡 Подсказка — П1 — `Config`

> Это **направление**, а не ответ. Если и после неё не пойдёт — открывайте решение.
>
> ← Назад в тетрадь: [C6-workbook.md](../C6-workbook.md)

---

- Импорт: `import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;`
- `fields().that().areDeclaredInClassesThat().haveSimpleName("Config")` — выбрать поля класса.
- `.should().beStatic().andShould().beFinal()` — они обязаны быть константами.
- В `.because(...)` пишите **последствие**, а не пересказ правила: сообщение об ошибке читает
  тот, кто правило нарушил, и ему нужно понять, **чем это плохо**.
