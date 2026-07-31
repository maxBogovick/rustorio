package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.render.HudState;
import com.graphics.render.TilePos;
import com.rustorio.api.content.ContentId;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
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
     * Открыта ли книга рецептов ({@code Renderer} рисует панель поверх экрана, если {@code true})
     * — TAB переключает. Мир при этом продолжает тикать: книга — справочник, а не пауза; кто
     * хочет разглядывать рецепты без спешки, ставит паузу отдельно (SPACE).
     */
    private boolean showRecipeBook;

    /**
     * Открыто ли дерево техов (P-02, DEV_TASKS.md) — {@code T} переключает. Пока открыто, {@link
     * InputHandler} перенаправляет цифровые клавиши 1-9 на выбор теха для разблокировки вместо
     * выбора здания в хотбаре — тот же приём, что уже применён к книге рецептов: показ не трогает
     * мир и не ставит игру на паузу сам по себе.
     */
    private boolean showTechTree;

    /**
     * Открыт ли экран статистики (P-03, DEV_TASKS.md) — {@code V} переключает. Тот же чистый
     * показ-без-побочных-эффектов, что у книги рецептов/дерева техов; пока открыт, {@link
     * InputHandler} перенаправляет {@code N} на переключение графикуемого предмета.
     */
    private boolean showStats;

    /**
     * Открыто ли меню построек (Фаза 8) — {@code B} переключает. Пока открыто, буквы A-Z идут в
     * {@link #searchQuery}, а не в свои обычные однобуквенные горячие клавиши (TAB/T/V/B сами) —
     * см. {@link #handle()}, единственная панель, что перехватывает ввод целиком, а не просто
     * добавляет свой собственный переключатель показа.
     */
    private boolean showBuildMenu;

    /** Текст поиска по подписи здания, накапливается посимвольно, пока меню открыто — очищается при закрытии. */
    private final StringBuilder searchQuery = new StringBuilder();

    /**
     * Сырой счётчик TAB-нажатий, пока меню открыто — во ЧТО он превращается (номер категории)
     * решает {@code BuildMenuRenderer}, который один знает, сколько категорий сейчас реально
     * зарегистрировано (namespace'ов); этот класс о реестре построек ничего не знает и не должен.
     */
    private int categoryCycle;

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
        if (showBuildMenu) {
            // Меню — единственная панель с текстовым вводом: пока оно открыто, буквы/TAB/BACKSPACE
            // управляют ИМ целиком, не своими обычными значениями (книга рецептов/дерево техов
            // тоже висели бы на T/TAB, которые здесь заняты поиском/категорией).
            handleSearchInput();
            if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
                categoryCycle++;
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
                showBuildMenu = false;
                searchQuery.setLength(0);
                categoryCycle = 0;
            }
            return;
        }
        // Книга рецептов (TAB): чистый переключатель показа, мира не касается вовсе.
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            showRecipeBook = !showRecipeBook;
        }
        // Дерево техов (T): тот же чистый переключатель показа.
        if (Gdx.input.isKeyJustPressed(Input.Keys.T)) {
            showTechTree = !showTechTree;
        }
        // Экран статистики (V, P-03, DEV_TASKS.md): тот же чистый переключатель показа.
        if (Gdx.input.isKeyJustPressed(Input.Keys.V)) {
            showStats = !showStats;
        }
        // Меню построек (B, Фаза 8): тот же чистый переключатель показа.
        if (Gdx.input.isKeyJustPressed(Input.Keys.B)) {
            showBuildMenu = true;
        }
    }

    /** Буквы A-Z дописывают строку поиска, BACKSPACE стирает последний символ — единственный текстовый ввод в этом проекте, см. класс-javadoc. */
    private void handleSearchInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE) && searchQuery.length() > 0) {
            searchQuery.setLength(searchQuery.length() - 1);
        }
        for (int key = Input.Keys.A; key <= Input.Keys.Z; key++) {
            if (Gdx.input.isKeyJustPressed(key)) {
                // Input.Keys.toString даёт "A".."Z" для этого диапазона — не полагаемся на то, что
                // числовые коды идут в алфавитном порядке без пропусков, даже если сегодня так и есть.
                searchQuery.append(Input.Keys.toString(key).toLowerCase(java.util.Locale.ROOT));
            }
        }
    }

    boolean isPaused() {
        return paused;
    }

    int speed() {
        return SPEEDS[speedIndex];
    }

    boolean showRecipeBook() {
        return showRecipeBook;
    }

    boolean showTechTree() {
        return showTechTree;
    }

    boolean showStats() {
        return showStats;
    }

    boolean showBuildMenu() {
        return showBuildMenu;
    }

    /**
     * {@link InputHandler} supplies {@code selected}/{@code facing}/{@code dragTiles}/{@code
     * inspected}/{@code altOverlay}/{@code statsItem}/{@code hotbarSlots} — those are its own
     * business, not ours.
     */
    HudState hudState(ContentId selected, Direction facing, List<TilePos> dragTiles, @Nullable TilePos inspected,
            boolean altOverlay, ItemType statsItem, List<ContentId> hotbarSlots) {
        return new HudState(selected, facing, paused, speed(), showRecipeBook, showTechTree, dragTiles, inspected,
                altOverlay, showStats, statsItem, hotbarSlots, showBuildMenu, searchQuery.toString(), categoryCycle);
    }
}
