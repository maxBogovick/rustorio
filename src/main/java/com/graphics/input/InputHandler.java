package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.HotbarLayout;
import com.graphics.render.TilePos;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.CompositeAction;
import com.rustorio.domain.action.PlaceAction;
import com.rustorio.domain.action.PlayerAction;
import com.rustorio.domain.action.RemoveAction;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import java.util.ArrayList;
import java.util.List;

/**
 * Ввод: управление камерой (скролл WASD/стрелки, драг средней кнопкой) и постройка зданий.
 *
 * <p>Раньше на каждое здание был свой жест (ЛКМ/ПКМ/F) — и это упёрлось в потолок. Теперь игрок
 * СНАЧАЛА ВЫБИРАЕТ здание клавишами 1/2/3, а строит всегда одинаково: ЛКМ ставит выбранное, ПКМ
 * сносит. Один жест постройки на любое число зданий.
 *
 * <p>ЛКМ/ПКМ не зовут мир напрямую — они заворачивают намерение в {@link PlaceAction}/
 * {@link RemoveAction} и отдают {@link ActionHistory} (урок 15): так у отмены (Ctrl+Z) и повтора
 * (Ctrl+Y) есть что откатывать. Кнопку можно и ЗАЖАТЬ — протянуть по нескольким клеткам подряд
 * ({@link #handleBuildDrag}/{@link #handleRemoveDrag}): тогда все задетые тайлы уйдут в историю
 * ОДНИМ {@link CompositeAction} (Урок 15) вместо отдельного действия на каждую клетку — тянешь
 * ленту через полкарты, отменяешь одним Ctrl+Z. Ввод НЕ решает, где можно строить, — он лишь
 * сообщает миру «игрок ткнул сюда выбранным зданием». Ставить или нет (клетка на руде?
 * свободна?) решает мир ({@link World#place}): правило места — про мир, а не про кнопку. Зум
 * колесом ловит экран ({@code InputProcessor}) и отдаёт камере: колесо в libGDX — событие, а не
 * состояние.
 */
public final class InputHandler {

    /**
     * {@code java.lang.System.Logger} — встроенный в JDK9+ фасад логирования, без новой
     * зависимости: единственный реальный путь ошибки в оконной игре (F5/F9) заслуживает
     * уровня/фильтруемости, а не голого {@code System.err.println}.
     */
    private static final System.Logger LOGGER = System.getLogger(InputHandler.class.getName());

    /** Скорости симуляции по кругу — клавиши {@code [}/{@code ]} двигают индекс в этом массиве. */
    private static final int[] SPEEDS = {1, 2, 4};

    private final GameCamera camera;
    private final ActionHistory history = new ActionHistory();
    private final SaveRepository saveRepository;

    /** Что игрок сейчас строит. ЛКМ ставит именно это; меняется клавишами 1/2/3. */
    private BuildingType selected = BuildingType.MINER;

    /** Куда повёрнута следующая постройка — важно только ленте (урок 19). Меняется клавишей R. */
    private Direction facing = Direction.RIGHT;

    /** Прошлая точка драга средней кнопкой (валидна, только пока {@link #dragging}). */
    private float lastDragX;
    private float lastDragY;
    private boolean dragging;

    /** На паузе {@link com.graphics.screen.GameScreen} не зовёт {@code world.tick()} вовсе. */
    private boolean paused;
    /** Индекс в {@link #SPEEDS} — во сколько раз чаще, чем обычно, тикает мир, пока не на паузе. */
    private int speedIndex;

    /**
     * Открыта ли книга рецептов ({@link com.graphics.render.Renderer} рисует панель поверх экрана,
     * если {@code true}) — TAB переключает. Мир при этом продолжает тикать: книга — справочник,
     * а не пауза; кто хочет разглядывать рецепты без спешки, ставит паузу отдельно (SPACE).
     */
    private boolean showRecipeBook;

    /**
     * Тайлы, задетые протяжкой ЛКМ с момента нажатия, собираются в ОДНО {@link CompositeAction}
     * на отпускание кнопки: тянешь линию лент через полкарты — отменяется одним Ctrl+Z, а не по
     * клетке. {@link DragCollector} — общая логика для этого и для протяжки ПКМ ({@link
     * #removeDrag}), которая раньше была продублирована в двух почти одинаковых методах.
     */
    private final DragCollector buildDrag = new DragCollector(Input.Buttons.LEFT);
    /** То же самое для ПКМ — протяжкой можно снести полосу построек одним действием. */
    private final DragCollector removeDrag = new DragCollector(Input.Buttons.RIGHT);

    public InputHandler(GameCamera camera, SaveRepository saveRepository) {
        this.camera = camera;
        this.saveRepository = saveRepository;
    }

    /** Что выбрано в панели постройки — HUD показывает это игроку. */
    public BuildingType selected() {
        return selected;
    }

    /** Куда повёрнута следующая постройка — HUD показывает это игроку. */
    public Direction facing() {
        return facing;
    }

    /** На паузе ли мир — {@code GameScreen} читает это, чтобы решить, звать ли {@code tick()}. */
    public boolean isPaused() {
        return paused;
    }

    /** Во сколько раз чаще обычного должен тикать мир за кадр, пока не на паузе. */
    public int speed() {
        return SPEEDS[speedIndex];
    }

    /** Открыта ли книга рецептов — {@code Renderer} читает это, чтобы решить, рисовать ли панель. */
    public boolean showRecipeBook() {
        return showRecipeBook;
    }

    public void handle(World world, float delta) {
        handleCamera(delta);
        handleBuildSelection();
        handleHotbarClick();
        handleBuildDrag(world);
        handleRemoveDrag(world);

        // Поворот следующей постройки (урок 19): R крутит направление ленты по кругу.
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            facing = facing.rotate();
        }

        // Модуль скорости (урок 18): U под курсором ускоряет здание вдвое. Тоже через историю —
        // отмена снимает обёртку, как и было задумано в UpgradeSpeedAction.
        if (Gdx.input.isKeyJustPressed(Input.Keys.U)) {
            TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
            history.perform(world, new UpgradeSpeedAction(tile.x(), tile.y()));
        }

        // Отмена/повтор (урок 15): Ctrl+Z откатывает последнее действие, Ctrl+Y повторяет откаченное.
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            history.undo(world);
        }
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            history.redo(world);
        }

        // Сохранение/загрузка (урок 10): F5 — записать мир на диск, F9 — прочитать обратно.
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            if (saveRepository.save(world) instanceof SaveResult.Failure failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Save failed: {0}", failure.reason());
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            if (saveRepository.load(world) instanceof SaveResult.Failure failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Load failed: {0}", failure.reason());
            }
        }

        // Книга рецептов (TAB): чистый переключатель показа, мира не касается вовсе.
        if (Gdx.input.isKeyJustPressed(Input.Keys.TAB)) {
            showRecipeBook = !showRecipeBook;
        }

        // Пауза и скорость: не трогают ни камеру, ни постройку — только то, сколько раз (и вообще,
        // сколько ли раз) GameScreen позовёт world.tick() в этом кадре.
        if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
            paused = !paused;
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.LEFT_BRACKET)) {
            speedIndex = Math.max(0, speedIndex - 1);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.RIGHT_BRACKET)) {
            speedIndex = Math.min(SPEEDS.length - 1, speedIndex + 1);
        }
    }

    /**
     * ЛКМ по панели построек снизу выбирает здание — тот же результат, что клавиша с тем же
     * номером, только мышью и с иконкой перед глазами, а не по памяти. Срабатывает на МОМЕНТ
     * нажатия (не «зажато»): протяжка от клика по панели дальше, в мир, — уже не постройка (та же
     * защита, что и в {@link DragCollector}, только для одиночного клика, а не для протяжки).
     */
    private void handleHotbarClick() {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        int index = HotbarLayout.hitTest(Gdx.input.getX(), Gdx.input.getY(),
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        if (index >= 0) {
            selected = BuildingType.values()[index];
        }
    }

    /**
     * ЛКМ зажата — {@link #buildDrag} копит тайлы под курсором; отпущена — собрать их в одно
     * {@link PlaceAction} на клетку и одно {@link CompositeAction} на всё разом, отдать в
     * историю. Одиночный клик без протяжки даёт список из одного тайла — то же поведение, что
     * было до протяжки, без регрессии.
     */
    private void handleBuildDrag(World world) {
        List<TilePos> tiles = buildDrag.poll(camera);
        if (tiles == null) {
            return;
        }
        List<PlayerAction> actions = new ArrayList<>();
        for (TilePos tile : tiles) {
            actions.add(new PlaceAction(selected, tile.x(), tile.y(), facing));
        }
        history.perform(world, new CompositeAction(actions));
    }

    /** Симметрично {@link #handleBuildDrag}, только ПКМ и {@link RemoveAction}. */
    private void handleRemoveDrag(World world) {
        List<TilePos> tiles = removeDrag.poll(camera);
        if (tiles == null) {
            return;
        }
        List<PlayerAction> actions = new ArrayList<>();
        for (TilePos tile : tiles) {
            actions.add(new RemoveAction(tile.x(), tile.y()));
        }
        history.perform(world, new CompositeAction(actions));
    }

    /**
     * Клавиши 1..N выбирают здание из панели — номер клавиши = порядок в {@link BuildingType}
     * (урок 13). {@code Input.Keys.NUM_1..NUM_9} в libGDX идут подряд, поэтому «клавиша под
     * номером» — это просто {@code NUM_1 + ordinal}, без явного перечисления каждой.
     */
    private void handleBuildSelection() {
        for (BuildingType type : BuildingType.values()) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + type.ordinal())) {
                selected = type;
            }
        }
    }

    /** Управление камерой: WASD/стрелки — скролл, зажатая средняя кнопка — драг. */
    private void handleCamera(float delta) {
        float step = GfxConfig.CAMERA_PAN_SPEED * delta;
        float dx = 0f;
        float dy = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            dy += step;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            dy -= step;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            dx -= step;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            dx += step;
        }
        if (dx != 0f || dy != 0f) {
            camera.pan(dx, dy);
        }

        if (Gdx.input.isButtonPressed(Input.Buttons.MIDDLE)) {
            float mx = Gdx.input.getX();
            float my = Gdx.input.getY();
            if (dragging) {
                // Мир едет ЗА курсором: сдвиг камеры с обратным знаком; мышиный Y растёт вниз,
                // мировой — вверх, отсюда разные знаки.
                camera.pan(lastDragX - mx, my - lastDragY);
            }
            lastDragX = mx;
            lastDragY = my;
            dragging = true;
        } else {
            dragging = false;
        }
    }
}
