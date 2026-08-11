package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.render.DisplayLabels;
import com.graphics.render.HudState;
import com.graphics.render.PageView;
import com.graphics.render.SettingsModalView;
import com.graphics.render.TilePos;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Пауза, скорость симуляции и переключатель книги рецептов — view-state, которое читают {@code
 * GameScreen} (звать ли {@code world.tick()}) и {@code Renderer} (через {@link HudState}), но
 * которое не трогает ни камеру, ни постройку. Вынесено из {@link InputHandler} (P3-07,
 * BUG_FIX_PROGRESS.md): раньше этот класс попутно был ещё и view-model'ю HUD, теперь ей — этот.
 */
final class SimulationControls {

    /** Скорости симуляции по кругу — клавиши {@code [}/{@code ]} двигают индекс в этом массиве. */
    private static final int[] SPEEDS = {1, 2, 4};

    /** На паузе {@code GameScreen} не зовёт {@code world.tick()} вовсе. */
    private boolean paused;
    /** Индекс в {@link #SPEEDS} — во сколько раз чаще, чем обычно, тикает мир, пока не на паузе. */
    private int speedIndex;

    /**
     * Какая из четырёх модальных панелей открыта прямо сейчас — не четыре независимых {@code
     * boolean}, как было раньше (live bug report): TAB, потом T, потом V каждый молча включал
     * СВОЙ собственный флаг, не трогая остальные — три панели рисовались друг поверх друга в одном
     * и том же месте экрана одновременно, и игрок не мог понять, где чья строка. Один {@code enum}
     * делает «максимум одна панель открыта» инвариантом, а не случайным совпадением: {@link
     * #toggleOrSwitch} либо закрывает уже открытую панель, либо ПЕРЕКЛЮЧАЕТ на другую вместо того,
     * чтобы открыть её поверх.
     */
    enum OverlayPanel { NONE, RECIPE_BOOK, TECH_TREE, STATS, BUILD_MENU, INFO }

    /**
     * Мир при любой открытой панели продолжает тикать как обычно: все четыре — справочник/меню, а
     * не пауза; кто хочет разглядывать без спешки, ставит паузу отдельно (SPACE).
     */
    private OverlayPanel openPanel = OverlayPanel.NONE;

    /** Текст поиска по подписи здания, накапливается посимвольно, пока меню открыто — очищается при закрытии. */
    private final StringBuilder searchQuery = new StringBuilder();

    /**
     * Сырой счётчик TAB-нажатий, пока меню открыто — во ЧТО он превращается (номер категории)
     * решает {@code BuildMenuRenderer}, который один знает, сколько категорий сейчас реально
     * зарегистрировано (namespace'ов); этот класс о реестре построек ничего не знает и не должен.
     * Клик по вкладке (см. {@link #setCategoryIndex}) выставляет то же самое поле напрямую —
     * TAB и клик по вкладке ведут к одному и тому же результату, не двум параллельным состояниям.
     */
    private int categoryCycle;

    /**
     * Сырой счётчик прокрутки сетки построек (колесо мыши) — та же раздельная ответственность,
     * что {@link #categoryCycle}: этот класс копит нажатия/notch'и колеса, а во сколько строк
     * это реально упирается (сколько всего совпадений сейчас) решает {@code BuildMenuLayout},
     * единственный, кто знает текущий список совпадений.
     */
    private int scrollOffsetRows;

    /**
     * Full hotkey legend (was three permanent HUD rows, now hidden by default) and FPS/UPS
     * (was an always-on debug line) — both independent of {@link #openPanel}: neither is a
     * full-screen panel, and either can be on at the same time as any panel or as each other.
     * HUD redesign, live design feedback ("выглядит громоздко") — see {@code HudRenderer}'s own
     * javadoc for the row layout these gate.
     */
    private boolean showHints;
    private boolean showFpsUps;

    void handle() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            paused = !paused;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) {
            speedIndex = Math.max(0, speedIndex - 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) {
            speedIndex = Math.min(SPEEDS.length - 1, speedIndex + 1);
        }
        if (openPanel == OverlayPanel.BUILD_MENU) {
            // Меню — единственная панель с текстовым вводом: пока оно открыто, буквы/TAB/BACKSPACE
            // управляют ИМ целиком, не своими обычными значениями (книга рецептов/дерево техов
            // тоже висели бы на T/TAB, которые здесь заняты поиском/категорией).
            //
            // Живой баг-репорт: B раньше ЗАКРЫВАЛ меню, даже посреди набора текста — строку с буквой
            // "b" («Belt», «Assembler», «Underground Belt») набрать было физически невозможно: даже
            // первая же буква "b" искомого запроса схлопывала меню и стирала уже введённое. Пробовали
            // компромисс «B закрывает, только пока строка пуста» — но и это ломало запросы, начинающиеся
            // именно на "b" («Belt» — реальная подпись здания), раз первая буква и есть B. Правильный
            // фикс: пока меню открыто, B — ВСЕГДА обычная буква, без исключений; единственный выход —
            // ESC (см. InputHandler, closeAnyOpenPanel), который одинаково закрывает любую из четырёх
            // модальных панелей — тот же ключ везде, а не свой на каждую.
            handleSearchInput();
            if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
                categoryCycle++;
                scrollOffsetRows = 0; // new category means a different, possibly shorter, match list
            }
            return;
        }
        // Книга рецептов (TAB) / дерево техов (T) / статистика (V): чистые переключатели показа,
        // мира не касаются вовсе. Переключают ЕДИНУЮ openPanel, а не свой отдельный boolean — нажатие
        // клавой ДРУГОЙ панели, пока эта уже открыта, ПЕРЕКЛЮЧАЕТ на неё, а не открывает поверх
        // (см. openPanel/toggleOrSwitch).
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            toggleOrSwitch(OverlayPanel.RECIPE_BOOK);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            toggleOrSwitch(OverlayPanel.TECH_TREE);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            toggleOrSwitch(OverlayPanel.STATS);
        }
        // Меню построек (B): та же логика — этот путь достижим, только когда BUILD_MENU ещё не
        // открыто (см. проверку openPanel в начале метода), так что здесь он всегда именно открывает.
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            toggleOrSwitch(OverlayPanel.BUILD_MENU);
        }
        // Экран Info (I) — то, во что теперь переехали Produced/Inventory/Research/Recent/полный
        // список алертов с постоянной верхней панели (HUD-редизайн): тот же переключатель-панель,
        // что TAB/T/V/B, просто ещё один вариант в openPanel.
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) {
            toggleOrSwitch(OverlayPanel.INFO);
        }
        // H/P — не панели, независимые флаги (см. их собственный javadoc): работают одинаково,
        // открыта ли сейчас какая-то из панелей выше или нет.
        if (Gdx.input.isKeyJustPressed(Input.Keys.H)) {
            showHints = !showHints;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            showFpsUps = !showFpsUps;
        }
    }

    /**
     * Закрыть уже открытую {@code panel} или переключиться на неё вместо того, чтобы открыть
     * поверх — единственное место, что трогает {@link #openPanel}, кроме {@link
     * #closeAnyOpenPanel}. Package-private, не {@code private} (как {@code
     * OverlayRenderer#canPlaceHere}) — сама логика переключения не трогает {@code Gdx} вовсе, так
     * что {@code SimulationControlsTest} прогоняет её напрямую, без окна.
     */
    void toggleOrSwitch(OverlayPanel panel) {
        openPanel = openPanel == panel ? OverlayPanel.NONE : panel;
    }

    /**
     * ESC — единый выход из ЛЮБОЙ из четырёх модальных панелей (live bug report: раньше ESC
     * трогал только панель инспекции — {@code InputHandler}'s own {@code inspected}, — и ни книгу
     * рецептов, ни дерево техов, ни статистику, ни меню построек; закрыть их можно было только той
     * же самой клавишей, что открыла, и для каждой — своей). Вызывается из {@code InputHandler}
     * вместе со сбросом {@code inspected}, вне зависимости от того, что сейчас открыто — не-открытая
     * панель просто не заметит вызова ({@link #openPanel} и так {@code NONE}).
     */
    void closeAnyOpenPanel() {
        if (openPanel == OverlayPanel.BUILD_MENU) {
            searchQuery.setLength(0);
            categoryCycle = 0;
            scrollOffsetRows = 0;
        }
        openPanel = OverlayPanel.NONE;
    }

    /**
     * Буквы A-Z, цифры, пробел и BACKSPACE — единственный текстовый ввод в этом проекте, см.
     * класс-javadoc.
     *
     * <p>Живой баг-репорт: раньше здесь ловились только A-Z — пробел и цифры просто ничего не
     * делали (не печатались, но и не терялись, buildMenuClick их тоже не читал). Здание «Tunnel
     * in» набрать было нельзя: без пробела запрос схлопывался в «tunnelin», а
     * {@code "tunnel in".contains("tunnelin")} — {@code false}, реального совпадения в списке не
     * находилось, хотя здание там есть. Пробел и цифры теперь дописываются точно так же, как буквы.
     */
    private void handleSearchInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && searchQuery.length() > 0) {
            searchQuery.setLength(searchQuery.length() - 1);
            scrollOffsetRows = 0;
        }
        for (int key = Input.Keys.A; key <= Input.Keys.Z; key++) {
            if (Gdx.input.isKeyJustPressed(key)) {
                // Input.Keys.toString даёт "A".."Z" для этого диапазона — не полагаемся на то, что
                // числовые коды идут в алфавитном порядке без пропусков, даже если сегодня так и есть.
                searchQuery.append(Input.Keys.toString(key).toLowerCase(java.util.Locale.ROOT));
                scrollOffsetRows = 0;
            }
        }
        for (int key = Input.Keys.NUM_0; key <= Input.Keys.NUM_9; key++) {
            if (Gdx.input.isKeyJustPressed(key)) {
                // Same reasoning as the letters above: Input.Keys.toString gives "0".."9" for this
                // range without assuming the codes themselves are laid out in order.
                searchQuery.append(Input.Keys.toString(key));
                scrollOffsetRows = 0;
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            searchQuery.append(' ');
            scrollOffsetRows = 0;
        }
    }

    boolean isPaused() {
        return paused;
    }

    int speed() {
        return SPEEDS[speedIndex];
    }

    boolean showRecipeBook() {
        return openPanel == OverlayPanel.RECIPE_BOOK;
    }

    boolean showTechTree() {
        return openPanel == OverlayPanel.TECH_TREE;
    }

    boolean showStats() {
        return openPanel == OverlayPanel.STATS;
    }

    boolean showBuildMenu() {
        return openPanel == OverlayPanel.BUILD_MENU;
    }

    boolean showInfo() {
        return openPanel == OverlayPanel.INFO;
    }

    /** Whether ANY of the four modal panels is open right now — {@code InputHandler}'s own Esc handling uses this to tell "Esc just closed a panel" apart from "nothing was open, Esc should open the pause menu instead". */
    boolean hasOpenPanel() {
        return openPanel != OverlayPanel.NONE;
    }

    boolean showHints() {
        return showHints;
    }

    boolean showFpsUps() {
        return showFpsUps;
    }

    /** Current search text, pending menu click handling in {@code InputHandler} — same value {@link #hudState} hands the renderer. */
    String buildMenuQuery() {
        return searchQuery.toString();
    }

    /** Current raw category cycle, for {@code InputHandler}'s own click handling — see the field's own javadoc for why it's raw. */
    int buildMenuCategoryCycle() {
        return categoryCycle;
    }

    /** Current raw scroll offset, for {@code InputHandler}'s own click handling — see {@link #scrollOffsetRows}'s own javadoc for why it's raw. */
    int buildMenuScrollOffset() {
        return scrollOffsetRows;
    }

    /**
     * A tab click sets the category directly, instead of cycling forward through categories one
     * TAB press at a time — {@code index} is the tab's own position (0 = "all", 1..N = the Nth
     * registered namespace), the same numbering {@link #activeCategory} in {@code BuildMenuLayout}
     * already gives {@link #categoryCycle}. Resets the scroll: a different category is a different,
     * possibly shorter, match list.
     */
    void setCategoryIndex(int index) {
        categoryCycle = index;
        scrollOffsetRows = 0;
    }

    /**
     * Mouse wheel over the build menu — {@code deltaRows} is which way it turned, not a distance in
     * pixels (one notch, one row). Never goes negative here; the upper bound depends on how many
     * matches exist right now, which only {@code BuildMenuLayout.clampScrollRows} knows.
     */
    void scrollBuildMenu(int deltaRows) {
        scrollOffsetRows = Math.max(0, scrollOffsetRows + deltaRows);
    }

    /**
     * {@link InputHandler} supplies {@code selected}/{@code facing}/{@code dragTiles}/{@code
     * inspected}/{@code altOverlay}/{@code statsItem}/{@code hotbarSlots} — those are its own
     * business, not ours.
     */
    HudState hudState(ContentId selected, Direction facing, List<TilePos> dragTiles, @Nullable TilePos inspected,
            boolean altOverlay, ItemType statsItem, List<ContentId> hotbarSlots, @Nullable String statusMessage,
            @Nullable SettingsModalView settingsModal, int activeCategoryIndex,
            @Nullable ContentId hoveredPrototype, DisplayLabels displayLabels,
            @Nullable PageView pageView, @Nullable ItemType selectedInventoryItem) {
        return new HudState(selected, facing, paused, speed(), showRecipeBook(), showTechTree(), dragTiles, inspected,
                altOverlay, showStats(), statsItem, hotbarSlots, showBuildMenu(), searchQuery.toString(), categoryCycle,
                scrollOffsetRows, statusMessage, showInfo(), showHints, showFpsUps, settingsModal,
                activeCategoryIndex, hoveredPrototype, displayLabels, pageView, selectedInventoryItem);
    }
}
