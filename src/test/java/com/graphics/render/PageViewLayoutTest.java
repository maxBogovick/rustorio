package com.graphics.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PageViewLayout} — вся арифметика полноэкранного просмотра, которую можно проверить без
 * окна. Сам {@link PageViewRenderer} требует GL-контекста и в headless-прогоне не участвует, так
 * что здесь закрывается ровно то, что решает, куда и в каком масштабе ляжет картинка.
 */
class PageViewLayoutTest {

    private static final int SCREEN_W = 1280;
    private static final int SCREEN_H = 720;
    /** Ширина, в которой мод рисует страницу, — взята как реалистичная величина, а не как контракт. */
    private static final int PAGE_W = 720;

    @Test
    void anImageNarrowerThanTheWindowIsNotStretchedPastItsOwnSize() {
        assertEquals(1f, PageViewLayout.scale(PAGE_W, SCREEN_W),
                "страница уже окна — растянуть её значит размыть текст, а не показать больше");
    }

    @Test
    void anImageWiderThanTheWindowIsFittedToItsWidth() {
        float scale = PageViewLayout.scale(4000, SCREEN_W);

        assertTrue(scale < 1f, "картинка шире окна обязана уменьшиться: " + scale);
        assertEquals(PageViewLayout.panelWidth(SCREEN_W), 4000 * scale, 0.5f,
                "после вписывания она должна занимать ровно ширину окна");
    }

    @Test
    void aPageThatAlreadyFitsDoesNotScrollAtAll() {
        assertEquals(0, PageViewLayout.maxScroll(PAGE_W, 200, SCREEN_W, SCREEN_H),
                "прокрутка у страницы, которая и так видна целиком, — только способ увести её за край");
    }

    @Test
    void aLongPageScrollsExactlyToItsOwnBottom() {
        int imageHeight = 3000;

        int max = PageViewLayout.maxScroll(PAGE_W, imageHeight, SCREEN_W, SCREEN_H);

        assertEquals(imageHeight - PageViewLayout.visibleSourceHeight(PAGE_W, SCREEN_W, SCREEN_H), max,
                "в самом низу последняя строка картинки должна совпасть с низом окна");
    }

    /**
     * Прокрутка всегда в допустимых пределах — и это не формальность: окно можно растянуть, уже
     * прокрутив страницу вниз, и законное вчера значение сегодня показывает пустоту под её низом.
     */
    @Test
    void scrollIsClampedBothByTheWheelAndByTheWindowChangingSize() {
        int imageHeight = 3000;

        assertEquals(0, PageViewLayout.clampScroll(-500, PAGE_W, imageHeight, SCREEN_W, SCREEN_H),
                "выше начала страницы прокрутить нельзя");
        assertEquals(PageViewLayout.maxScroll(PAGE_W, imageHeight, SCREEN_W, SCREEN_H),
                PageViewLayout.clampScroll(999_999, PAGE_W, imageHeight, SCREEN_W, SCREEN_H),
                "ниже конца страницы — тоже");
        int scrolledToBottomOfASmallWindow = PageViewLayout.maxScroll(PAGE_W, imageHeight, SCREEN_W, 400);
        assertTrue(PageViewLayout.clampScroll(scrolledToBottomOfASmallWindow, PAGE_W, imageHeight, SCREEN_W, 1200)
                        <= PageViewLayout.maxScroll(PAGE_W, imageHeight, SCREEN_W, 1200),
                "после того как окно стало выше, старая прокрутка обязана подтянуться вверх");
    }

    /**
     * Layout takes the same logical window size HUD uses — not the Retina backbuffer. Passing
     * {@code 2×} width (what {@code getBackBufferWidth} returns on a 2× display) would size the
     * panel twice as wide as every other HUD surface.
     */
    @Test
    void panelWidthFollowsLogicalWindowSizeNotADoubledBackbuffer() {
        assertEquals(SCREEN_W - 2 * 40f, PageViewLayout.panelWidth(SCREEN_W), 0.01f);
        assertTrue(PageViewLayout.panelWidth(SCREEN_W * 2) > PageViewLayout.panelWidth(SCREEN_W) + 100f,
                "a doubled width must produce a clearly larger panel — proof the caller must pass logical size");
    }
}
