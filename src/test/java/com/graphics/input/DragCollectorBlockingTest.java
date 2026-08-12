package com.graphics.input;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.graphics.GfxConfig;
import com.graphics.render.CategoryTabsLayout;
import com.graphics.render.QuickBarLayout;
import org.junit.jupiter.api.Test;

/**
 * Какие нажатия вообще становятся жестом в мире — живой баг-репорт: игрок находил здания тех
 * сортов, которыми только что строила, в местах, где их не ставила, прокрутив карту немного вниз.
 *
 * <p>Ставились они по-настоящему. Жест отменялся только над ячейками быстрой панели, а вкладки
 * категорий, иконки нижней панели построек и пустое место рядом считались миром — {@code pickTile}
 * же переводит в клетку любую точку окна, и точка под нижней полосой попадает на клетку за нижним
 * краем видимой области. Призрак постройки над HUD не рисуется, поэтому на экране не было вообще
 * никакого следа.
 *
 * <p>Решение блокировать берётся из экранной точки и высоты окна — чистая функция, и тест
 * специально не поднимает libGDX (ловушка из правил тестов: {@code Gdx.input} без окна падает).
 */
class DragCollectorBlockingTest {

    /** Окно по умолчанию: полосы HUD считаются от его высоты. */
    private static final int SCREEN_H = GfxConfig.WINDOW_H;
    /** Три закреплённых здания — быстрая панель ровно в один ряд, самый низкий. */
    private static final int PINNED = 3;

    /** Y середины ряда вкладок категорий, переведённый в координаты {@code Gdx.input} (Y сверху). */
    private static float tabsScreenY() {
        return SCREEN_H - (CategoryTabsLayout.TABS_Y + CategoryTabsLayout.TAB_HEIGHT / 2f);
    }

    /**
     * Та самая точка, на которой всё ломалось: первая вкладка категорий — правее быстрой панели,
     * поэтому её проверкой не закрыта, и внутри нижней полосы, поэтому мира под ней нет.
     */
    @Test
    void aPressOnTheCategoryTabsNeverBecomesAWorldGesture() {
        float x = CategoryTabsLayout.tabX(0) + CategoryTabsLayout.TAB_WIDTH / 2f;

        assertTrue(DragCollector.blocksGestureStart(x, tabsScreenY(), SCREEN_H, PINNED, false),
                "клик по вкладке выбирает категорию и не должен ещё и строить за краем экрана");
    }

    /** То же для иконки здания в нижней панели: один клик по ней закрепляет прототип, а не строит. */
    @Test
    void aPressOnABuildPanelIconNeverBecomesAWorldGesture() {
        float x = CategoryTabsLayout.iconX(0) + CategoryTabsLayout.ICON_SIZE / 2f;
        float y = SCREEN_H - (CategoryTabsLayout.ICONS_Y + CategoryTabsLayout.ICON_SIZE / 2f);

        assertTrue(DragCollector.blocksGestureStart(x, y, SCREEN_H, PINNED, false),
                "клик по иконке панели построек закрепляет здание, а не ставит его вслепую");
    }

    /** Верхняя полоса симметрична нижней — там клик уезжал в клетку НАД видимой областью. */
    @Test
    void aPressOnTheTopHudBandNeverBecomesAWorldGesture() {
        assertTrue(DragCollector.blocksGestureStart(600f, GfxConfig.HUD_TOP_HEIGHT / 2f, SCREEN_H, PINNED, false),
                "верхняя полоса — такой же интерфейс, как нижняя");
    }

    /**
     * Проверка полосы НЕ заменяет проверку быстрой панели: три ряда ячеек выше нижней полосы, их
     * верхний ряд лежит уже над миром. Прямоугольники пересекаются, ни один не содержит другой —
     * поэтому нужны оба условия.
     */
    @Test
    void theTopRowOfAFullQuickBarStandsOverTheWorldAndStillBlocks() {
        int fullBar = QuickBarLayout.MAX_CELLS;
        int rows = QuickBarLayout.visibleRowsFor(fullBar);
        float hudY = QuickBarLayout.cellY(0, QuickBarLayout.COLUMNS, rows) + QuickBarLayout.CELL_SIZE / 2f;
        float screenY = SCREEN_H - hudY;
        float x = QuickBarLayout.cellX(0, QuickBarLayout.COLUMNS) + QuickBarLayout.CELL_SIZE / 2f;

        assertTrue(GfxConfig.isOverWorld(screenY, SCREEN_H),
                "верхний ряд полной панели действительно торчит над миром — иначе тест ниже ничего не доказывает");
        assertTrue(DragCollector.blocksGestureStart(x, screenY, SCREEN_H, fullBar, false),
                "ячейка панели остаётся защищённой и там, где под ней виден мир");
    }

    /** А обычный клик по карте по-прежнему строит — иначе «починка» просто выключила бы игру. */
    @Test
    void aPressOverTheMapItselfStillStartsAGesture() {
        float middleOfTheWorld = GfxConfig.HUD_TOP_HEIGHT
                + (SCREEN_H - GfxConfig.HUD_TOP_HEIGHT - GfxConfig.HUD_BOTTOM_HEIGHT) / 2f;

        assertFalse(DragCollector.blocksGestureStart(640f, middleOfTheWorld, SCREEN_H, PINNED, false),
                "клик по карте — это постройка, ради неё всё и делается");
        assertTrue(DragCollector.blocksGestureStart(640f, middleOfTheWorld, SCREEN_H, PINNED, true),
                "но панель инспекции по-прежнему съедает свой клик сама");
    }
}
