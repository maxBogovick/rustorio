package com.graphics.render;

import com.badlogic.gdx.graphics.Color;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Cell;
import com.rustorio.domain.FluidType;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Все цвета отрисовки в одном месте (перенесены из render.rs Rust-версии). */
final class Palette {

    // Индустриальная палитра «сталь/янтарь/тил/ржавчина» (арт-редизайн, см. approved mockup
    // rustorio_art_direction_proposal): раньше BG/GROUND/GRID были случайным нейтральным серым без
    // единой идеи — теперь всё в файле собрано вокруг пяти опорных тонов: тёмная сталь (фон/панели),
    // тёплая земля, янтарь (работает/выбрано), тил (руда/наука), ржавчина (тревога/пауза).
    static final Color BG = rgb(28, 31, 38);       // сталь
    static final Color GROUND = rgb(58, 64, 72);   // земля — заметно светлее стали, читается как «пол»
    static final Color ORE = rgb(53, 116, 122);        // железная руда — тил (роднит с наукой/технологиями)
    static final Color ORE_BRONZE = rgb(138, 106, 60);  // бронзовая руда — тёплая медь
    static final Color ORE_COAL = rgb(24, 22, 21);     // уголь — почти чёрный (D-05, DEV_TASKS.md)
    static final Color GRID = rgb(78, 84, 94);

    // Рельеф (X-02, DEV_TASKS.md): непроходимые клетки должны читаться на глаз ДО первого клика
    // по ним, а не только через отказ World.place. Вода — синяя, но заметно ярче/голубее ORE
    // (руда и вода на одном экране не должны путаться); скала — нейтральный тёмно-серый камень,
    // темнее GROUND, чтобы отличаться и от земли, и от воды одним взглядом.
    static final Color TERRAIN_WATER = rgb(42, 98, 148);
    static final Color TERRAIN_ROCK = rgb(40, 38, 36);

    static final Color HINT = rgb(196, 190, 172);
    static final Color WORKING = rgb(224, 161, 54);  // янтарь — «работает» (был чистый зелёный)
    static final Color IDLE = rgb(201, 80, 47);      // ржавчина — «пауза/тревога» (был чистый красный)
    // Компактная верхняя панель (HUD-редизайн): «алертов нет» — единственное место, где нужен
    // именно спокойный зелёный, а не янтарь WORKING (который уже занят под «машина работает») и не
    // ржавчина IDLE (уже «тревога/пауза») — своя, не пересекающаяся с ними семантика.
    static final Color OK = rgb(122, 176, 100);

    // Индикатор статуса здания (F-01, DEV_TASKS.md, §6.5 аудита): четыре разных цвета, чтобы
    // причины отличались друг от друга с одного взгляда, не только от «всё в порядке» (для
    // WORKING индикатор вообще не рисуется — см. BuildingRenderer). Подобраны не пересекающимися
    // с ORE/TERRAIN_* — руда и статус здания на одной клетке не должны читаться как одно и то же.
    static final Color STATUS_NO_ORE = rgb(214, 64, 64);      // красный — ресурс кончился
    static final Color STATUS_NO_FUEL = rgb(201, 80, 47);     // ржавчина — нужен уголь (тот же тон, что IDLE)
    static final Color STATUS_NO_INPUT = rgb(232, 196, 90);   // светлый янтарь — ждёт материал
    static final Color STATUS_OUTPUT_FULL = rgb(158, 100, 199); // фиолетовый — некуда сдать
    static final Color STATUS_NO_POWER = rgb(96, 176, 232);   // холодная синь — обесточено, ни один тон выше не занят

    // Подложка полоски заполнения трубы/бака: полупрозрачная темнота, чтобы пустая часть шкалы
    // читалась как «дно», а не сливалась с землёй. Сам заполненный кусок красится цветом жидкости.
    static final Color FLUID_BAR_TRACK = new Color(0f, 0f, 0f, 0.45f);

    // Стык трубы: нейтральная сталь, НЕ цвет жидкости — стык рисуется и у пустой трубы, где
    // жидкости ещё нет, а «здесь соединено» нужно видеть всегда, как направление ленты.
    static final Color PIPE_JOINT = rgb(150, 160, 170);

    // Полоска-акцент сверху здания: к какой системе оно относится. Спрайт говорит «машина», но не
    // говорит, что именно эта машина — трубопроводная, а соседняя запитана; членство в системе
    // иначе с одного взгляда не читается. Электрожёлтый — ток, тил — жидкостная машина.
    // См. BuildingAccent (там же — почему у трубы полоски нет).
    static final Color ACCENT_POWER = rgb(240, 214, 92);
    static final Color ACCENT_FLUID = rgb(96, 190, 214);

    // Подсветка непарного входа подземки (см. OverlayRenderer).
    static final Color T_BAD = new Color(1.00f, 0.30f, 0.30f, 0.35f);

    // Рамка призрака постройки под курсором (F-02, DEV_TASKS.md) — зелёная, если клетка свободна,
    // проходима и по карману; красная, если хоть одно из этого не так. Полупрозрачные, не сплошные
    // WORKING/IDLE выше: рамка рисуется ПОВЕРХ реальной карты, а не вместо текста HUD.
    static final Color GHOST_VALID = new Color(0.35f, 0.78f, 0.45f, 0.9f);
    static final Color GHOST_INVALID = new Color(0.90f, 0.25f, 0.25f, 0.9f);

    // Панели HUD (см. HudRenderer): тёмная полупрозрачная подложка под текстом/иконками, чтобы
    // они читались поверх ЛЮБОГО фона мира, а не сливались с ним, как голый текст без подложки.
    static final Color PANEL_BG = new Color(0.11f, 0.12f, 0.15f, 0.88f);
    // Тонкая грань, отделяющая панель от мира за ней (мокап HUD) — раньше панель была плоским
    // прямоугольником без края и «плавала» поверх сцены без визуальной опоры.
    static final Color PANEL_BORDER = new Color(1f, 1f, 1f, 0.08f);
    static final Color SLOT_BG = new Color(1f, 1f, 1f, 0.06f);
    static final Color SLOT_BORDER = new Color(1f, 1f, 1f, 0.25f);
    static final Color SLOT_SELECTED = rgb(224, 161, 54); // янтарь — тот же тон, что WORKING
    // Build menu tile under the cursor, but not (yet) the equipped building — brighter than
    // SLOT_BORDER so hovering gives visible feedback before the click commits to anything, distinct
    // from SLOT_SELECTED's amber so "about to pick" never reads as "already equipped".
    static final Color TILE_HOVER = new Color(1f, 1f, 1f, 0.6f);

    // Стрелка направления поверх здания (см. OverlayRenderer) — яркая и нейтральная, чтобы
    // читаться на любом спрайте под ней, а не сливаться с конкретным цветом конкретного здания.
    static final Color DIRECTION_ARROW = new Color(1f, 1f, 1f, 0.85f);

    /** Memoizes {@link #itemColor} by packed rgb int — see that method's own javadoc for why. */
    private static final Map<Integer, Color> ITEM_COLORS = new HashMap<>();

    private Palette() {
    }

    /**
     * Cargo color — read straight off the prototype's own {@link ItemType#colorRgb()} instead of
     * switching on item identity: a new item gets a color the moment it's registered, with no
     * change to this file at all.
     *
     * <p>Cached by the raw packed int, not by {@link ItemType} itself (code review finding S5):
     * {@code itemColor} is called once per visible cargo/chip EVERY FRAME, and the old code
     * allocated a fresh {@link Color} on every single call. Keying by {@code colorRgb} rather than
     * by the item avoids a subtler staleness bug a by-item cache would have — {@link ItemType}'s
     * identity is its {@link com.rustorio.api.content.ContentId} alone (see that class's own
     * javadoc), so a by-item cache would keep handing out a stale color forever after a mod's
     * {@code Registry.update()} changed {@code colorRgb} for an id already seen once; keying on
     * the int itself means a new color value always gets its own (correct) cache entry.
     */
    static Color itemColor(ItemType item) {
        return ITEM_COLORS.computeIfAbsent(item.colorRgb(),
                rgb -> rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
    }

    /**
     * A fill bar's colour — the fluid's own {@link FluidType#colorRgb()}, decoded and memoized by
     * the packed int exactly as {@link #itemColor} does it, for the same per-frame reasons. Shares
     * that cache on purpose: a colour is a colour, and two content kinds that happen to name the
     * same {@code 0xRRGGBB} should resolve to the same {@link Color} instance, not two.
     */
    static Color fluidColor(FluidType fluid) {
        return ITEM_COLORS.computeIfAbsent(fluid.colorRgb(),
                rgb -> rgb((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF));
    }

    /**
     * Opaque hues the network overlay tells one network from another by — six is enough that a
     * screen's worth of grids rarely collides, and a small fixed set keeps the overlay from turning
     * into confetti. Which hue a network gets is {@link NetworkTint#paletteIndex} of its anchor, so
     * the choice is deterministic; the caller dials in its own alpha with {@code setColor(r,g,b,a)}
     * (a faint fill for fluid membership, a firmer line for a pole's reach) rather than this holding
     * two tinted copies of every colour.
     */
    private static final Color[] NETWORK_HUES = {
        rgb(90, 200, 250),   // cyan
        rgb(250, 170, 60),   // amber
        rgb(140, 220, 120),  // green
        rgb(220, 120, 210),  // magenta
        rgb(240, 220, 90),   // yellow
        rgb(120, 150, 250),  // indigo
    };

    static Color networkHue(Cell anchor) {
        return NETWORK_HUES[NetworkTint.paletteIndex(anchor, NETWORK_HUES.length)];
    }

    /**
     * Which silhouette a cargo circle draws as — {@link ItemType#shape()} directly; see that
     * field's own javadoc (in {@code com.rustorio.domain}) for why shape exists alongside color.
     */
    static ItemShape itemShape(ItemType item) {
        return item.shape();
    }

    /**
     * Marker color for a building's {@link BuildingStatus} — empty for {@link
     * BuildingStatus#WORKING} on purpose: {@link BuildingRenderer} draws nothing at all for a
     * healthy building, so a marker's mere PRESENCE already means "look here," not just its color.
     */
    static Optional<Color> statusColor(BuildingStatus status) {
        return switch (status) {
            case WORKING -> Optional.empty();
            case NO_ORE -> Optional.of(STATUS_NO_ORE);
            case NO_FUEL -> Optional.of(STATUS_NO_FUEL);
            case NO_INPUT -> Optional.of(STATUS_NO_INPUT);
            case OUTPUT_FULL -> Optional.of(STATUS_OUTPUT_FULL);
            case NO_POWER -> Optional.of(STATUS_NO_POWER);
        };
    }

    /** The accent-stripe colour for a building's system, or empty for a building that belongs to neither. */
    static Optional<Color> accentColor(BuildingAccent accent) {
        return switch (accent) {
            case NONE -> Optional.empty();
            case POWER -> Optional.of(ACCENT_POWER);
            case FLUID -> Optional.of(ACCENT_FLUID);
        };
    }

    private static Color rgb(int r, int g, int b) {
        return new Color(r / 255f, g / 255f, b / 255f, 1f);
    }
}
