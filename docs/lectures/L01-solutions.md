# L1 — Решения

> ⚠️ Это ответы к [L01-workbook.md](L01-workbook.md). Заходите по ссылке из своего
> шага: сначала 💡 подсказка (П), и только если не помогла — 📄 решение (Р).
> Скопировали решение — прочитайте его javadoc и комментарии: они часть ответа.

---

## П1 — Item: направление

Файл из шести строк: `public enum Item { ... }` с двумя элементами. Вся ценность —
в javadoc: напишите, что это за предметы и когда список будет расти.

## Р1 — Item целиком

```java
package com.rustorio.core;

/**
 * Предметы, которые бегают по лентам и превращаются друг в друга по рецептам.
 *
 * <p>Добавляешь предмет? Только сюда (и спрайт в слой отрисовки). Больше нигде —
 * компилятор сам покажет все switch, которые нужно дополнить.
 */
public enum Item {
    IRON_ORE,
    IRON_PLATE
}
```

---

## П2 — Direction: направление

- Ответ на «Предскажи-1»: ось Y в модели растёт **вниз** (строка 0 — верх карты,
  как вы видели в `Grid` на L0). «На север» = «на строку выше» = `y - 1`. Отрисовка
  сама переведёт в экранные координаты — домен про экран не знает.
- Конструктор enum: `NORTH(0, -1, "N")` + приватные `final` поля + геттеры.
- `rotateCw`: элементы уже объявлены по часовой — верните следующий по кругу через
  `values()` и `ordinal()`. Деление по модулю `% values().length` замыкает круг.

## Р2 — Direction целиком

```java
package com.rustorio.core;

/**
 * Куда «смотрит» здание: бур/лента/печь отдают предмет в эту сторону.
 *
 * <p>Ось Y растёт ВНИЗ (как в координатах поля), поэтому {@link #NORTH} имеет
 * смещение {@code dy = -1}. Отрисовка сама переводит это в свою систему
 * координат — домен про экран ничего не знает.
 *
 * <p>Смещение хранится прямо в перечислении (каноничная Java-идиома «enum с
 * данными и поведением»), поэтому {@link #dx()}/{@link #dy()} — просто чтение
 * поля, без {@code switch}.
 */
public enum Direction {
    NORTH(0, -1, "N"),
    EAST(1, 0, "E"),
    SOUTH(0, 1, "S"),
    WEST(-1, 0, "W");

    private final int dx;
    private final int dy;
    private final String shortName;

    Direction(int dx, int dy, String shortName) {
        this.dx = dx;
        this.dy = dy;
        this.shortName = shortName;
    }

    /** Смещение по X на соседнюю клетку в этом направлении. */
    public int dx() {
        return dx;
    }

    /** Смещение по Y на соседнюю клетку (ось вниз: North = -1). */
    public int dy() {
        return dy;
    }

    /** Короткое имя для интерфейса (N/E/S/W). */
    public String shortName() {
        return shortName;
    }

    /**
     * Повернуть по часовой стрелке (клавиша R).
     *
     * <p>Порядок объявления N→E→S→W уже совпадает с поворотом по часовой,
     * поэтому берём следующий элемент по кругу — данные задают поведение,
     * дублировать логику в {@code switch} не нужно.
     */
    public Direction rotateCw() {
        Direction[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
```

---

## П3 — Tool: направление

То же устройство, что у `Direction`: два поля (`displayName`, `hotkeySlot`),
конструктор, геттеры. Слоты: MINER — 1, BELT — 2, FURNACE — 3, CHEST — 4.
Ответ на «Предскажи-2»: компилятор пропустит **чужой или невозможный номер слота**
(два инструмента на клавише 1; слот 12, которого нет на клавиатуре) — это и ловит
`ToolTest`.

## Р3 — Tool целиком

```java
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
```

---

## П4 — GameState: направление

Два поля со стартовыми значениями, два геттера, два мутатора — по образцу уже
существующих `paused`/`togglePause`.

Ответ на «Предскажи-3»: с `public Tool tool;` **кто угодно** может записать в поле
что угодно и когда угодно — включая рендер, которому менять состояние запрещено.
Приватное поле + метод означает: (1) все изменения проходят через одно место —
там можно ставить точку останова, логировать, проверять; (2) по СИГНАТУРАМ класса
видно, что с ним можно делать; (3) `rotateDirection()` вообще не даёт записать
произвольное направление — только повернуть. Инкапсуляция — это не «так принято»,
это сужение того, что придётся держать в голове при поиске бага.

## Р4 — GameState: что добавить

```java
// поля (рядом с paused):
private Tool tool = Tool.MINER;
private Direction direction = Direction.EAST;
```

```java
// чтение (рядом с isPaused):
public Tool tool() {
    return tool;
}

public Direction direction() {
    return direction;
}
```

```java
// изменение (рядом с togglePause):
public void selectTool(Tool tool) {
    this.tool = tool;
}

public void rotateDirection() {
    this.direction = direction.rotateCw();
}
```

Плюс импорты `com.rustorio.core.Direction` и `com.rustorio.core.Tool`.

---

## П5 — InputHandler: направление

Цикл `for (Tool tool : Tool.values())` + `Gdx.input.isKeyJustPressed(...)`.
Коды цифровых клавиш в libGDX идут подряд: `Input.Keys.NUM_0 + 1` — это клавиша «1».
`isKeyJustPressed` (а не `isKeyPressed`) — иначе выбор будет срабатывать каждый
кадр, пока держите клавишу.

## Р5 — InputHandler: что добавить

В `handle`, после блока с паузой:

```java
// Выбор инструмента: идём по СПИСКУ инструментов, а не по руками написанной
// лесенке «if (нажата 1) … if (нажата 4)». Номер слота объявлен в самом Tool,
// и забыть его нельзя: не скомпилируется.
for (Tool tool : Tool.values()) {
    if (Gdx.input.isKeyJustPressed(keyForSlot(tool.hotkeySlot()))) {
        game.selectTool(tool);
    }
}

// Поворот направления постройки.
if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
    game.rotateDirection();
}
```

И метод в конце класса:

```java
/**
 * Номер слота (1..9) → код клавиши libGDX.
 *
 * <p>Перевод живёт ЗДЕСЬ, а не в {@code Tool}: {@code Tool} лежит в {@code core},
 * которому запрещено знать про движок (это стережёт {@code ArchitectureTest}).
 * Слой ввода про движок знать обязан — вот пусть он и переводит.
 */
private static int keyForSlot(int slot) {
    return Input.Keys.NUM_0 + slot;
}
```

Плюс импорт `com.rustorio.core.Tool`.

---

## П6 — HUD: направление

Верхняя строка: конкатенация из `game.tool().displayName()`,
`game.direction().shortName()`, координат hover и `[PAUSED]`. Подсказка —
`StringBuilder` + цикл по `Tool.values()`.

## Р6 — HudRenderer: новое тело render

```java
void render(GameState game) {
    float top = Gdx.graphics.getHeight();
    batch.begin();

    // Клетка под курсором — живое доказательство, что камера и пик работают.
    String cell = game.hover()
            .map(c -> "(" + c.x() + ", " + c.y() + ")")
            .orElse("—");
    String status = "Tool: " + game.tool().displayName()
            + "   Dir: " + game.direction().shortName()
            + "   Cell: " + cell
            + (game.isPaused() ? "   [PAUSED]" : "");
    font.setColor(Color.WHITE);
    font.getData().setScale(1.15f);
    font.draw(batch, status, 20, top - 16);

    font.setColor(Palette.HINT);
    font.getData().setScale(0.9f);
    // Панель собирается из СПИСКА инструментов: добавили здание — подсказка
    // обновилась сама. Захардкоженная строка врала бы уже через две лекции.
    StringBuilder hints = new StringBuilder();
    for (Tool t : Tool.values()) {
        hints.append(t.hotkeySlot()).append(' ').append(t.displayName()).append("  ");
    }
    hints.append("   |    R rotate   Space pause   WASD/MMB pan   wheel zoom");
    font.draw(batch, hints.toString(), 20, top - 46);
    font.getData().setScale(1f);

    batch.end();
}
```

Плюс импорт `com.rustorio.core.Tool`.

> Зачем `render` читает `game.tool()` каждый кадр, а не запоминает строку? Потому
> что HUD — функция от состояния: состояние поменялось — картинка поменялась сама.
> Кэшировать строку — значит завести вторую копию правды и однажды показать старую.
