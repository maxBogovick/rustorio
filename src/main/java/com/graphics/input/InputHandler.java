package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.GameCamera;
import com.graphics.render.HotbarLayout;
import com.graphics.render.HudState;
import com.graphics.render.TilePos;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.Tech;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.CompositeAction;
import com.rustorio.domain.action.GrabChestAction;
import com.rustorio.domain.action.PlaceAction;
import com.rustorio.domain.action.PlayerAction;
import com.rustorio.domain.action.RemoveAction;
import com.rustorio.domain.action.RotateAction;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Ввод про здания: выбор (1-9/клик), постройка/снос (ЛКМ/ПКМ, протяжкой), апгрейд (U), рецепт
 * (C), отмена/повтор (Ctrl+Z/Y), save/load (F5/F9) — через {@link ActionHistory}. Камера и
 * пауза/скорость/книга рецептов вынесены в {@link CameraController}/{@link SimulationControls}
 * (P3-07, BUG_FIX_PROGRESS.md) — этот класс больше не их view-model.
 */
public final class InputHandler {

    /** Единственный реальный путь ошибки в оконной игре (F5/F9) заслуживает уровня/фильтруемости. */
    private static final System.Logger LOGGER = System.getLogger(InputHandler.class.getName());

    private final GameCamera camera;
    private final ActionHistory history = new ActionHistory();
    private final SaveRepository saveRepository;
    private final CameraController cameraController;
    private final SimulationControls simulationControls = new SimulationControls();
    private BuildingType selected = BuildingType.MINER; // строим это; клавиши 1-9 меняют
    private Direction facing = Direction.RIGHT; // важно только ленте; клавиша R меняет
    /** Клетка под панелью инспекции (F-03, DEV_TASKS.md) — {@code null}, пока ничего не открыто. */
    private @Nullable TilePos inspected;
    /** Какой предмет графикуется на экране статистики (P-03, DEV_TASKS.md) — {@code N} переключает, пока экран открыт. */
    private Item statsItem = Item.IRON_ORE;
    /** Протяжка ЛКМ/ПКМ копится в одно {@link CompositeAction} на отпускание — см. {@link #handleDrag}. */
    private final DragCollector buildDrag = new DragCollector(Input.Buttons.LEFT);
    private final DragCollector removeDrag = new DragCollector(Input.Buttons.RIGHT);

    public InputHandler(GameCamera camera, SaveRepository saveRepository) {
        this.camera = camera;
        this.saveRepository = saveRepository;
        this.cameraController = new CameraController(camera);
    }

    // Геттеры для HUD/GameScreen — паузу/скорость/книгу рецептов отдаёт SimulationControls.
    public BuildingType selected() { return selected; }
    public Direction facing() { return facing; }
    public boolean isPaused() { return simulationControls.isPaused(); }
    public int speed() { return simulationControls.speed(); }
    public boolean showRecipeBook() { return simulationControls.showRecipeBook(); }
    /**
     * Снимок для {@code Renderer}: своё («что строим» + линия протяжки F-02 + инспекция F-03 +
     * удержан ли Alt для F-04 + графикуемый предмет для P-03) плюс то, что знает {@link
     * SimulationControls}.
     */
    public HudState hudState() {
        boolean altOverlay = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        return simulationControls.hudState(selected, facing, buildDrag.inProgressTiles(), inspected, altOverlay,
                statsItem);
    }

    public void handle(World world, float delta) {
        cameraController.handle(delta);
        simulationControls.handle();
        if (simulationControls.showTechTree()) {
            // Дерево техов открыто (T) — цифры 1-9 выбирают тех для разблокировки (P-02,
            // DEV_TASKS.md), а не здание в хотбаре; иначе один и тот же нажатый «1» тихо делал бы
            // оба сразу.
            handleTechSelection(world);
        } else {
            handleBuildSelection();
            handleHotbarClick();
        }
        // C3 (live bug report): the recipe book / tech tree / stats panels render OVER the middle
        // of the world viewport, exactly the zone GameCamera#pickTile still happily maps clicks
        // into — every handler below acts on "whatever cell is under the cursor," so none of them
        // may fire while a modal panel covers that cell, or a click meant for the panel silently
        // builds/mines/rotates in the world the player can't even see.
        if (!modalOpen()) {
            handleDrag(world, buildDrag, tile -> new PlaceAction(selected, tile.x(), tile.y(), facing));
            handleRemoveOrManualMineDrag(world);
            handleInspectClick(world);
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
                if (world.peek(tile.x(), tile.y()).isPresent()) {
                    // курсор над уже поставленным зданием — поворачиваем ЕГО (D-01, DEV_TASKS.md),
                    // а не facing для следующей постройки; тихий no-op, если здание не умеет поворачиваться
                    history.perform(world, new RotateAction(tile.x(), tile.y()));
                } else {
                    facing = facing.rotate(); // клетка пуста — поворот следующей постройки, как раньше
                }
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.U)) { // ускорить здание под курсором вдвое
                TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
                history.perform(world, new UpgradeSpeedAction(tile.x(), tile.y()));
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.C)) { // листнуть рецепт печи/пресса (P2-02)
                TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
                world.peek(tile.x(), tile.y())
                        .map(Building::unwrap)
                        .filter(Furnace.class::isInstance)
                        .map(Furnace.class::cast)
                        .ifPresent(furnace -> {
                            Optional<Item> output = furnace.cycleRecipe();
                            LOGGER.log(System.Logger.Level.INFO, "Recipe selected: {0}",
                                    output.map(Object::toString).orElse("auto"));
                        });
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F)) { // листнуть предмет фильтра (X-01, DEV_TASKS.md) — тот же приём, что C для рецепта печи/пресса
                TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
                world.peek(tile.x(), tile.y())
                        .map(Building::unwrap)
                        .filter(Filter.class::isInstance)
                        .map(Filter.class::cast)
                        .ifPresent(filter -> {
                            Item chosen = filter.cycleFilterItem();
                            LOGGER.log(System.Logger.Level.INFO, "Filter now passes: {0}", chosen);
                        });
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.G)) {
                // Забрать содержимое ящика под курсором в инвентарь игрока (live bug report): без
                // этого содержимое ящика и то, чем реально можно строить, были двумя никак не
                // связанными хранилищами — тихий no-op, если под курсором не ящик или ящик пуст.
                TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
                history.perform(world, new GrabChestAction(tile.x(), tile.y()));
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            inspected = null;
        }
        if (simulationControls.showStats() && Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            // Экран статистики открыт (V, P-03, DEV_TASKS.md) — N листает, какой предмет
            // графикуется, по кругу; вне этого экрана клавиша ничего не делает.
            Item[] items = Item.values();
            statsItem = items[(statsItem.ordinal() + 1) % items.length];
        }
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            history.undo(world);
        }
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            history.redo(world);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)
                && saveRepository.save(world) instanceof SaveResult.Failure failure) {
            LOGGER.log(System.Logger.Level.WARNING, "Save failed: {0}", failure.reason());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            if (saveRepository.load(world) instanceof SaveResult.Failure failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Load failed: {0}", failure.reason());
            } else {
                history.clear(); // новый мир — старая история недействительна (P1-05)
            }
        }
    }

    /**
     * Whether any full-screen panel (recipe book / tech tree / stats — {@link
     * SimulationControls#showRecipeBook()}/{@link SimulationControls#showTechTree()}/{@link
     * SimulationControls#showStats()}) is covering the world viewport right now (C3, live bug
     * report). See the call site in {@link #handle} for what this gates.
     */
    private boolean modalOpen() {
        return simulationControls.showRecipeBook() || simulationControls.showTechTree() || simulationControls.showStats();
    }

    /** ЛКМ по панели построек снизу выбирает здание — момент нажатия, не «зажато» (см. {@link DragCollector}). */
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
     * ЛКМ по уже занятой клетке карты открывает панель инспекции (F-03, DEV_TASKS.md) — клик по той
     * же клетке снова закрывает, клик по пустой клетке или другому зданию переключает/закрывает.
     * Это тот же самый клик, что и {@link #handleDrag} для {@link #buildDrag} — постройка поверх
     * занятой клетки и так молча проваливается ({@code World.place} видит {@code !isFree}) и сразу
     * возвращает потраченное ({@code PlaceAction.apply}), так что у этого клика уже нет другого
     * осмысленного эффекта на занятой клетке — не нужно ничего блокировать, только добавить сюда
     * инспекцию как дополнительный побочный эффект того же самого нажатия.
     */
    private void handleInspectClick(World world) {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        if (HotbarLayout.hitTest(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH) >= 0
                || !cursorOverWorld(screenH)) {
            return; // клик по хотбару или по одной из HUD-полос — не по карте
        }
        TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
        inspected = tile.equals(inspected) ? null : world.peek(tile.x(), tile.y()).isPresent() ? tile : null;
    }

    /** Курсор между верхней и нижней HUD-полосами — та же проверка, что нужна {@code OverlayRenderer} для F-02 призрака. */
    private static boolean cursorOverWorld(int screenH) {
        float screenY = Gdx.input.getY();
        return screenY > GfxConfig.HUD_TOP_HEIGHT && screenY < screenH - GfxConfig.HUD_BOTTOM_HEIGHT;
    }

    /** Общее для ЛКМ/ПКМ-протяжки: каждый задетый тайл — своё действие, все — в одном {@link CompositeAction}. */
    private void handleDrag(World world, DragCollector drag, Function<TilePos, PlayerAction> toAction) {
        List<TilePos> tiles = drag.poll(camera);
        if (tiles == null) {
            return;
        }
        List<PlayerAction> actions = new ArrayList<>();
        for (TilePos tile : tiles) {
            actions.add(toAction.apply(tile));
        }
        history.perform(world, new CompositeAction(actions));
    }

    /**
     * ПКМ-протяжка: та же клетка, разное действие в зависимости от того, что под курсором. Занятая
     * клетка сносится как раньше ({@link RemoveAction}, через {@link ActionHistory} — отменяемо).
     * Пустая клетка с рудой добывается вручную ({@link World#tryManualMine}) — не через {@link
     * PlayerAction}/{@link ActionHistory} вовсе: отмена одной вручную добытой руды не стоит
     * отдельного API для «вернуть предмет в инвентарь и написать вымышленный откат в
     * невоспроизводимый {@code OreLayout#extract}» (D-04 делает добычу вероятностной у истощённой
     * клетки — честного «отмени именно это» тут просто нет). Занятость проверяется ДО применения
     * {@code RemoveAction} этой же протяжки — снесённая только что клетка не должна тут же
     * добываться вручную в том же самом жесте.
     */
    private void handleRemoveOrManualMineDrag(World world) {
        List<TilePos> tiles = removeDrag.poll(camera);
        if (tiles == null) {
            return;
        }
        List<PlayerAction> removals = new ArrayList<>();
        for (TilePos tile : tiles) {
            if (world.peek(tile.x(), tile.y()).isPresent()) {
                removals.add(new RemoveAction(tile.x(), tile.y()));
            } else {
                world.tryManualMine(tile.x(), tile.y());
            }
        }
        if (!removals.isEmpty()) {
            history.perform(world, new CompositeAction(removals));
        }
    }

    /**
     * Клавиши 1..N выбирают здание — {@code NUM_1 + ordinal}, {@link BuildingType} задаёт порядок.
     * Ограничено девятью (P4-08, BUG_FIX_PROGRESS.md) — {@code NUM_1..NUM_9} в libGDX кончаются на
     * девятой клавише; десятое здание (если появится) выбирается только мышью по хотбару.
     */
    private void handleBuildSelection() {
        for (BuildingType type : BuildingType.values()) {
            if (type.ordinal() < 9 && Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + type.ordinal())) {
                selected = type;
            }
        }
    }

    /**
     * Пока дерево техов открыто (T) — клавиши 1..N пытаются потратить очки на соответствующий тех
     * (P-02, DEV_TASKS.md), тем же приёмом нумерации, что {@link #handleBuildSelection}. Тихий
     * no-op через {@link com.rustorio.domain.world.World#tryUnlockTech}, если тех не по карману,
     * уже открыт, или не хватает предпосылок — экран дерева техов покажет игроку, почему именно.
     */
    private void handleTechSelection(World world) {
        Tech[] techs = Tech.values();
        for (int i = 0; i < techs.length && i < 9; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                world.tryUnlockTech(techs[i]);
            }
        }
    }
}
