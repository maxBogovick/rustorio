# 📄 Решение — Р3 — `Research`

> Это **готовый код**. Полезнее всего открыть его ПОСЛЕ своей попытки — и сравнить.
>
> ← Назад в тетрадь: [C4-workbook.md](../C4-workbook.md)

---

```java
public final class Research {

    private final Balance balance;
    private final Set<Tech> unlocked = EnumSet.noneOf(Tech.class);
    private int points;

    public Research(Balance balance) {
        this.balance = balance;
    }

    /** Забрать очки, накопленные всеми лабораториями мира. Зовётся раз в тик. */
    public void collect(World world) {
        world.forEachBuilding((x, y, building) -> {
            if (building instanceof Lab lab) {
                points += lab.drainPoints();
            }
        });
    }

    public int points() {
        return points;
    }

    public Set<Tech> unlocked() {
        return Collections.unmodifiableSet(unlocked);
    }

    public boolean isUnlocked(Tech tech) {
        return unlocked.contains(tech);
    }

    /** Не открыта + предпосылки готовы + очков хватает. */
    public boolean canResearch(Tech tech) {
        Technology technology = Technology.of(tech);
        return !unlocked.contains(tech)
                && technology.isAvailableWith(unlocked)
                && points >= technology.cost();
    }

    /** Что игрок может открыть прямо сейчас (для интерфейса). */
    public List<Technology> available() {
        return Technology.all().stream()
                .filter(t -> canResearch(t.id()))
                .toList();
    }

    /**
     * Открыть технологию: списать очки и применить её эффекты.
     *
     * <p>Проверка canResearch стоит ВНУТРИ, а не «пусть вызывающий проверит сам»: иначе однажды
     * кто-то забудет проверить, и очки уйдут в минус — или технология откроется дважды, а её
     * эффект применится дважды (лента поедет вчетверо быстрее вместо вдвое).
     *
     * @return true, если открыли; false, если было нельзя
     */
    public boolean research(Tech tech) {
        if (!canResearch(tech)) {
            return false;
        }
        Technology technology = Technology.of(tech);
        points -= technology.cost();
        unlocked.add(tech);
        for (Effect effect : technology.effects()) {
            effect.applyTo(balance);
        }
        return true;
    }
}
```
