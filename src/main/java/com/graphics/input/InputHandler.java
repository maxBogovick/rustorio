package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.render.GameCamera;
import com.graphics.render.HotbarLayout;
import com.graphics.render.HudState;
import com.graphics.render.TilePos;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.Item;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.CompositeAction;
import com.rustorio.domain.action.PlaceAction;
import com.rustorio.domain.action.PlayerAction;
import com.rustorio.domain.action.RemoveAction;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

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
    /** Снимок для {@code Renderer}: своё («что строим») плюс то, что знает {@link SimulationControls}. */
    public HudState hudState() { return simulationControls.hudState(selected, facing); }

    public void handle(World world, float delta) {
        cameraController.handle(delta);
        simulationControls.handle();
        handleBuildSelection();
        handleHotbarClick();
        handleDrag(world, buildDrag, tile -> new PlaceAction(selected, tile.x(), tile.y(), facing));
        handleDrag(world, removeDrag, tile -> new RemoveAction(tile.x(), tile.y()));
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { // поворот следующей постройки по кругу
            facing = facing.rotate();
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
}
