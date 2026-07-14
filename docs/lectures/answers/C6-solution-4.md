# 📄 Решение — Р4 — Как заставить правило упасть

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C6-workbook.md](../C6-workbook.md)

---

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
