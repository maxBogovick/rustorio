# Как изменить или убрать чужой рецепт

Рецепт — такой же адресуемый контент, как предмет: у него есть свой `ContentId`, и его можно
править, не переписывая. Для этого нужен jar-мод: правки выполняет код, а не данные.

## Узнать идентификатор

Идентификатор рецепта — это `<мод>:<имя файла без .json>`. Ванильная плавка железа лежит в
`resources/mods/rustorio/content/recipes/iron_plate.json`, значит её id — `rustorio:iron_plate`.

Свой файл может назвать себя иначе, задав поле `"path"`.

## Сделать плавку вдвое дольше

```java
public final class BalanceMod implements RustorioMod {
    @Override
    public void modifyContent(RegistrationContext ctx) {
        ContentId id = ContentId.of("rustorio:iron_plate");
        ctx.recipes().update(id, old -> new Recipe(
                old.id(), old.ingredients(), old.output(), old.time() * 2, old.type()));
    }
}
```

`modifyContent`, а не `registerContent`: второй раунд идёт после того, как **все** моды
зарегистрировали свой контент, поэтому чужой рецепт к этому моменту точно существует.

`update` заменяет значение, а не добавляет второе: конкурирующего рецепта не появится.

## Убрать рецепт совсем

```java
ctx.recipes().remove(ContentId.of("rustorio:chassis_press"));
```

Удаление того, чего нет, — ошибка, а не тихий no-op: опечатку иначе никто не заметит.

## Что важно знать

- Правка и удаление легальны только до заморозки реестров, то есть внутри трёх раундов
  (`registerContent` / `modifyContent` / `finalFixes`). После загрузки игры реестр только читается.
- Кто чей контент переписал, видно в логе загрузки строками
  `A later mod overwrote the recipe '...'`.
- Если после твоего удаления чей-то предмет остался без единого способа быть сделанным — это
  твоя ответственность: загрузчик проверяет только висящие ссылки, а не достижимость.
