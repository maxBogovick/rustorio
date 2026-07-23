package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.graphics.GfxConfig;
import com.rustorio.Item;
import com.rustorio.World;

/**
 * Слой «предметы»: груз, который здание держит «в пути» ({@link
 * com.rustorio.Building#heldItem()}) — на ленте, в буре, в сортировщике, в подземке.
 *
 * <p>Раньше этот слой был пустым каркасом: логика двигала предметы между клетками, а на экране
 * это было НИКАК не видно — только тайл ленты сам переключался между «пустым» и «полным»
 * спрайтом. Игрок не мог глазами проверить, что груз вообще куда-то едет (см. принцип GDD
 * эталона «всегда виден результат»). Теперь каждый тик здесь рисуется маленький кружок предмета
 * НАД спрайтом здания — не своя отдельная сущность, а прямое отражение {@code heldItem()}:
 * пропал груз из руки бура — тем же тиком пропал и кружок.
 *
 * <p><b>Почему кружок с обводкой, а не {@code textures.itemTexture}.</b> Настоящие спрайты
 * предметов в {@code resources/} — заготовки 3×4/4×4 пикселя, где все три ступени одной цепочки
 * (руда/пластина/шестерня) перекрашены практически в один и тот же серый или оранжевый: на глаз
 * неотличимы при любом масштабе. Пока нет настоящей художки — цвет свой на каждый сорт (см.
 * {@link #itemColor}), а форма — КРУЖОК, а не квадрат: плоский цветной квадрат читается как
 * техническая заглушка («тут должна быть картинка»), кружок с тёмной обводкой — уже узнаваемо
 * как окатыш руды или деталь, а не как недорисованный плейсхолдер.
 */
final class ItemRenderer {

    /** Диаметр кружка меньше клетки — чтобы отличаться от здания под ним, а не перекрывать его. */
    private static final float DIAMETER_SCALE = 0.46f;
    /** Тёмная обводка — то, что превращает плоское пятно в узнаваемый «предмет» с краем. */
    private static final Color OUTLINE = new Color(0f, 0f, 0f, 0.55f);

    private final ShapeRenderer shapes;
    private final Grid grid;

    ItemRenderer(ShapeRenderer shapes, Grid grid) {
        this.shapes = shapes;
        this.grid = grid;
    }

    void render(World world) {
        float radius = GfxConfig.TILE * DIAMETER_SCALE / 2f;
        float half = GfxConfig.TILE / 2f;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        world.forEachBuilding((x, y, building) -> {
            Item held = building.heldItem();
            if (held == null) {
                return;
            }
            shapes.setColor(itemColor(held));
            shapes.circle(grid.x(x) + half, grid.yBottom(y) + half, radius, 20);
        });
        shapes.end();

        // Обводка — вторым проходом: ShapeRenderer рисует один ShapeType за begin/end, заливку и
        // линию нельзя намешать в одном вызове.
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(OUTLINE);
        world.forEachBuilding((x, y, building) -> {
            if (building.heldItem() == null) {
                return;
            }
            shapes.circle(grid.x(x) + half, grid.yBottom(y) + half, radius, 20);
        });
        shapes.end();
    }

    /** Цвет кружка для предмета — новый сорт получит цвет здесь, одной строкой (см. Palette). */
    private static Color itemColor(Item item) {
        return switch (item) {
            case IRON_ORE -> Palette.ITEM_IRON_ORE;
            case IRON_PLATE -> Palette.ITEM_IRON_PLATE;
            case GEAR -> Palette.ITEM_GEAR;
            case BRONZE_ORE -> Palette.ITEM_BRONZE_ORE;
            case BRONZE_PLATE -> Palette.ITEM_BRONZE_PLATE;
            case MECHANISM -> Palette.ITEM_MECHANISM;
            case ENGINE -> Palette.ITEM_ENGINE;
        };
    }
}
