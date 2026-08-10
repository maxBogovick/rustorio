package com.graphics.render;

/**
 * Геометрия полноэкранного просмотра картинки, которую отдало здание ({@code ViewableBuilding}) —
 * чистая арифметика без libGDX, как {@link InspectionPanelLayout}: окно игры в headless-сессии не
 * поднимается, поэтому всё, что можно посчитать без GL, считается здесь и проверяется тестом, а в
 * {@link PageViewRenderer} остаётся только рисование.
 *
 * <p>Картинка вписывается по ШИРИНЕ и прокручивается по вертикали: страница по своей природе
 * длинная и узкая, вписать её целиком — значит показать нечитаемую полоску. Увеличивать сверх
 * масштаба 1 нельзя: пиксели растянутся, а резкости не прибавится.
 */
public final class PageViewLayout {

    /** Поля вокруг окна — под ними видно игру, чтобы просмотр читался как окно поверх мира, а не как смена экрана. */
    private static final float MARGIN = 40f;
    /** Полоса заголовка сверху: подпись и подсказка «Esc — закрыть». */
    static final float TITLE_HEIGHT = 24f;
    /** На сколько пикселей ИСХОДНОЙ картинки прокручивает одна засечка колеса. Public — колесо ловит {@code com.graphics.input.InputHandler} из другого пакета, та же причина, что у {@link InspectionPanelLayout#hitTestRecipe}. */
    public static final int SCROLL_STEP = 120;

    private PageViewLayout() {
    }

    static float panelX(int screenWidth) {
        return MARGIN;
    }

    static float panelY(int screenHeight) {
        return MARGIN;
    }

    static float panelWidth(int screenWidth) {
        return Math.max(1f, screenWidth - 2 * MARGIN);
    }

    static float panelHeight(int screenHeight) {
        return Math.max(1f, screenHeight - 2 * MARGIN);
    }

    /** Высота области, в которой рисуется сама картинка, — окно без полосы заголовка. */
    static float viewportHeight(int screenHeight) {
        return Math.max(1f, panelHeight(screenHeight) - TITLE_HEIGHT);
    }

    /** Масштаб «вписать по ширине», но не крупнее оригинала: растянутый пиксель-в-два не делает текст читаемее. */
    static float scale(int imageWidth, int screenWidth) {
        return Math.min(1f, panelWidth(screenWidth) / imageWidth);
    }

    /**
     * Сколько строк ИСХОДНОЙ картинки помещается в окно при текущем масштабе. Считается в
     * исходных пикселях, а не в экранных, потому что прокрутка тоже в исходных: так одна засечка
     * колеса прокручивает одинаково при любом размере окна.
     */
    static int visibleSourceHeight(int imageWidth, int screenWidth, int screenHeight) {
        return Math.max(1, (int) (viewportHeight(screenHeight) / scale(imageWidth, screenWidth)));
    }

    /** Наибольшая допустимая прокрутка: ноль, если картинка целиком помещается. */
    static int maxScroll(int imageWidth, int imageHeight, int screenWidth, int screenHeight) {
        return Math.max(0, imageHeight - visibleSourceHeight(imageWidth, screenWidth, screenHeight));
    }

    /** Прокрутка, загнанная в допустимые пределы — вызывается и при колесе, и при изменении размера окна, иначе после сжатия окна картинка «уезжает» за нижний край. */
    public static int clampScroll(int scroll, int imageWidth, int imageHeight, int screenWidth, int screenHeight) {
        return Math.clamp(scroll, 0, maxScroll(imageWidth, imageHeight, screenWidth, screenHeight));
    }
}
