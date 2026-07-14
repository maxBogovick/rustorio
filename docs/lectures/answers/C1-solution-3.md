# 📄 Решение — Р3 — Запуск: задача Gradle

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C1-workbook.md](../C1-workbook.md)

---

Чтобы бенчмарк запускался одной командой, добавьте в `build.gradle`:

```groovy
// Бенчмарк симуляции: `./gradlew benchmark`. Окно не открывается — домен не зависит
// от libGDX, поэтому симуляция запускается как обычная консольная программа.
tasks.register('benchmark', JavaExec) {
    group = 'verification'
    description = 'Замер: сколько миллисекунд занимает один шаг симуляции'
    classpath = sourceSets.main.runtimeClasspath
    mainClass = 'com.rustorio.bench.SimulationBenchmark'
}
```

> `build.gradle` — **общий файл** (см. карту владения в [TASKS.md](../../../TASKS.md)).
> Правьте его **отдельным маленьким PR**, не вперемешку с кодом: конфликт в сборочном
> файле останавливает всех троих.
