package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.rustorio.domain.Item;

/** Все цвета отрисовки в одном месте (перенесены из render.rs Rust-версии). */
final class Palette {

    static final Color BG = rgb(26, 26, 31);
    static final Color GROUND = rgb(42, 46, 54);
    static final Color ORE = rgb(51, 71, 115);        // железная руда — синий
    static final Color ORE_BRONZE = rgb(122, 78, 39);  // бронзовая руда — тёплый коричневый
    static final Color GRID = rgb(90, 96, 110);

    // Цвета груза на ленте/буре/сортировщике/подземке (см. ItemRenderer). Настоящие спрайты
    // предметов (iron_ore.png и т.п.) — заготовки 3×4/4×4 пикселя, где руда/пластина/шестерня
    // одной цепочки перекрашены в один и тот же серый (бронза — в один и тот же оранжевый):
    // на глаз неразличимы ни при каком масштабе. Пока нет настоящей художки — однозначная
    // цветная метка вместо неё, тот же приём, что уже красит плашку лаборатории.
    // Руда на земле (см. ORE выше) — синяя, так исторически закрашены рудные пятна в этой игре
    // ещё до всех правок. Но добытый КУСОК руды в руках/на ленте — не то же самое, что клетка
    // карты: тут ожидание другое (камень/металл, не вода), поэтому цвет предмета — нейтральный
    // тёмно-серый, а не синий.
    static final Color ITEM_IRON_ORE = rgb(105, 100, 95);       // тёмно-серый камень — сырьё
    static final Color ITEM_IRON_PLATE = rgb(170, 172, 178);    // светлее ore, но НЕ белый
    static final Color ITEM_GEAR = rgb(230, 195, 60);
    static final Color ITEM_BRONZE_ORE = rgb(110, 80, 60);      // тёмно-коричневый камень — сырьё
    static final Color ITEM_BRONZE_PLATE = rgb(214, 122, 44);
    static final Color ITEM_MECHANISM = rgb(163, 68, 40);
    static final Color ITEM_ENGINE = rgb(90, 170, 90);
    static final Color ITEM_CHASSIS = rgb(60, 90, 150);
    static final Color ITEM_ALLOY_PLATE = rgb(150, 140, 130); // между серым железом и рыжей бронзой
    static final Color ITEM_ALLOY_GEAR = rgb(190, 170, 90); // темнее ITEM_GEAR — материал дороже
    static final Color HINT = rgb(179, 179, 199);
    static final Color WORKING = Color.GREEN;
    static final Color IDLE = Color.RED;

    // Подсветка непарного входа подземки (см. OverlayRenderer).
    static final Color T_BAD = new Color(1.00f, 0.30f, 0.30f, 0.35f);

    // Панели HUD (см. HudRenderer): тёмная полупрозрачная подложка под текстом/иконками, чтобы
    // они читались поверх ЛЮБОГО фона мира, а не сливались с ним, как голый текст без подложки.
    static final Color PANEL_BG = new Color(0.05f, 0.05f, 0.08f, 0.72f);
    static final Color SLOT_BG = new Color(1f, 1f, 1f, 0.06f);
    static final Color SLOT_BORDER = new Color(1f, 1f, 1f, 0.25f);
    static final Color SLOT_SELECTED = rgb(255, 200, 60);

    // Стрелка направления поверх здания (см. OverlayRenderer) — яркая и нейтральная, чтобы
    // читаться на любом спрайте под ней, а не сливаться с конкретным цветом конкретного здания.
    static final Color DIRECTION_ARROW = new Color(1f, 1f, 1f, 0.85f);

    private Palette() {
    }

    /**
     * Цвет кружка для предмета — новый сорт получит цвет здесь, одной строкой. Раньше жил только
     * внутри {@link ItemRenderer} (груз на ленте); теперь используется ещё и {@link
     * RecipeBookRenderer} (иконка рецепта) — единственное место с этим {@code switch}, а не два
     * места, которые рано или поздно разойдутся при добавлении предмета.
     */
    static Color itemColor(Item item) {
        return switch (item) {
            case IRON_ORE -> ITEM_IRON_ORE;
            case IRON_PLATE -> ITEM_IRON_PLATE;
            case GEAR -> ITEM_GEAR;
            case BRONZE_ORE -> ITEM_BRONZE_ORE;
            case BRONZE_PLATE -> ITEM_BRONZE_PLATE;
            case MECHANISM -> ITEM_MECHANISM;
            case ENGINE -> ITEM_ENGINE;
            case CHASSIS -> ITEM_CHASSIS;
            case ALLOY_PLATE -> ITEM_ALLOY_PLATE;
            case ALLOY_GEAR -> ITEM_ALLOY_GEAR;
        };
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }
}
