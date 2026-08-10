package com.graphics.input;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.graphics.GfxConfig;
import com.graphics.render.BuildMenuLayout;
import com.graphics.render.CategoryTabsLayout;
import com.graphics.render.DisplayLabels;
import com.graphics.render.GameCamera;
import com.graphics.render.HudState;
import com.graphics.render.InspectionPanelLayout;
import com.graphics.render.PageView;
import com.graphics.render.PageViewLayout;
import com.graphics.render.QuickBarLayout;
import com.graphics.render.TilePos;
import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.TechType;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.action.ActionHistory;
import com.rustorio.domain.action.CompositeAction;
import com.rustorio.domain.action.GrabChestAction;
import com.rustorio.domain.action.PlaceAction;
import com.rustorio.domain.action.PlayerAction;
import com.rustorio.domain.action.RemoveAction;
import com.rustorio.domain.action.RotateAction;
import com.rustorio.domain.action.UpgradeSpeedAction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.BuildingImage;
import com.rustorio.domain.building.BuildingPrototype;
import com.rustorio.domain.building.EditableBuilding;
import com.rustorio.domain.building.Filter;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.RecipeSelectable;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.building.ViewableBuilding;
import com.rustorio.domain.world.World;
import com.rustorio.persistence.SaveRepository;
import com.rustorio.persistence.SaveResult;
import com.rustorio.persistence.UiSettings;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    /**
     * Настраиваемый хотбар — по умолчанию все ванильные прототипы, тем же порядком, что
     * {@code BuildingType.values()} раньше давал напрямую, чтобы клавиши 1-9 ощущались как прежде.
     * Закрепление в слот (клик по меню построек) заменяет элемент этого списка, не сам список.
     */
    private final QuickBar quickBar = new QuickBar();
    /** Какая вкладка открыта в постоянной панели построек — состояние UI, не мира, поэтому живёт здесь. */
    private int activeCategoryIndex;
    /** Реестр прототипов этого мира — вкладки перегруппировываются от него на каждый клик; не меняется за время экрана. */
    private final Registry<BuildingPrototype> buildings;
    /** Подписи с разрешёнными коллизиями — считаются ОДИН раз: реестр заморожен на всё время экрана, а на кадре такое строить нельзя. */
    private final DisplayLabels displayLabels;
    /** Where the pinned layout is read from and written back to — a file of the PLAYER's, not of a world. */
    private final UiSettings uiSettings;
    private ContentId selected; // строим это; клавиши 1-9/клик по хотбару меняют
    private Direction facing = Direction.RIGHT; // важно только ленте; клавиша R меняет
    /** Клетка под панелью инспекции (F-03, DEV_TASKS.md) — {@code null}, пока ничего не открыто. */
    private @Nullable TilePos inspected;
    /**
     * Какое здание сейчас показывает свою картинку во весь экран, и на сколько она прокручена.
     * Отдельно от {@link #inspected}: панель осмотра под просмотром остаётся открытой, и закрытие
     * просмотра возвращает игрока к ней, а не в пустоту.
     */
    private @Nullable TilePos viewedPage;
    private int pageScroll;
    /** Пересобирается раз в кадр в {@link #handle}: {@link #hudState()} мира не получает, а картинку отдаёт здание. */
    private @Nullable PageView pageView;
    /**
     * Закрыл ли Esc просмотр страницы ИМЕННО в этом кадре. Нужен потому, что {@code GameScreen}
     * проверяет {@link #hasOpenPanel()} уже ПОСЛЕ {@link #handle}: без этого флага один Esc и
     * закрыл бы страницу, и открыл меню паузы за тот же кадр.
     */
    private boolean escapeClosedPageView;
    /** Какой предмет графикуется на экране статистики (P-03, DEV_TASKS.md) — {@code N} переключает, пока экран открыт. */
    private ItemType statsItem = VanillaItems.IRON_ORE;
    /** Протяжка ЛКМ/ПКМ копится в одно {@link CompositeAction} на отпускание — см. {@link #handleDrag}. */
    private final DragCollector buildDrag = new DragCollector(Input.Buttons.LEFT);
    private final DragCollector removeDrag = new DragCollector(Input.Buttons.RIGHT);
    /**
     * Сколько секунд ещё показывать {@link #statusMessage} на HUD — живой баг-репорт: F5/F9 не
     * давали игроку вообще НИКАКОЙ обратной связи на экране, ни при успехе, ни при неудаче (только
     * строка в {@link #LOGGER}, которую не видно в оконном запуске не из терминала). {@code 0}
     * значит «сообщения нет» — {@link #hudState()} тогда отдаёт {@code null}.
     */
    private static final float STATUS_MESSAGE_SECONDS = 3f;
    private @Nullable String statusMessage;
    private float statusMessageTimeLeft;

    /**
     * The one settings editor every {@link EditableBuilding} shares ({@code WebMiner}, {@code
     * Interpreter} today) — opened by a plain click via {@link #handleInspectClick}, not a
     * dedicated hotkey per building anymore. See {@link SettingsModal}'s own javadoc for the live
     * bug report ("любое редактирование параметров сделано очень неудобно... тут так и просится
     * общий механизм") that replaced two near-identical hand-written editors with this one class.
     */
    private final SettingsModal settingsModal = new SettingsModal();

    public InputHandler(GameCamera camera, SaveRepository saveRepository, Registry<BuildingPrototype> buildings,
            UiSettings uiSettings) {
        this.camera = camera;
        this.saveRepository = saveRepository;
        this.cameraController = new CameraController(camera);
        this.uiSettings = uiSettings;
        this.buildings = buildings;
        this.displayLabels = DisplayLabels.of(buildings.iterate());
        // Only ids this game actually has: a mod uninstalled since the layout was written would
        // otherwise leave a cell pointing at nothing, and the renderer resolves every cell.
        List<ContentId> known = new ArrayList<>();
        for (ContentId pinned : uiSettings.quickBar()) {
            if (buildings.peek(pinned).isPresent()) {
                known.add(pinned);
            }
        }
        quickBar.restore(known);
        // Something has to be in hand before the first click, and the bar may legitimately be
        // empty — the first registered prototype is the fallback, never a hardcoded vanilla id.
        this.selected = quickBar.at(0).orElseGet(() -> buildings.iterate().get(0).id());
    }


    // Геттеры для HUD/GameScreen — паузу/скорость/книгу рецептов отдаёт SimulationControls.
    public ContentId selected() { return selected; }
    public Direction facing() { return facing; }
    public boolean isPaused() { return simulationControls.isPaused(); }
    /** Whether a HUD panel (recipe book/tech tree/stats/build menu/info) is open right now — {@code GameScreen} uses this to tell an Esc that closed a panel apart from one that should open its pause menu instead. */
    public boolean hasOpenPanel() {
        return simulationControls.hasOpenPanel() || viewedPage != null || escapeClosedPageView;
    }
    public int speed() { return simulationControls.speed(); }
    public boolean showRecipeBook() { return simulationControls.showRecipeBook(); }
    /**
     * Снимок для {@code Renderer}: своё («что строим» + линия протяжки F-02 + инспекция F-03 +
     * удержан ли Alt для F-04 + графикуемый предмет для P-03 + хотбар-слоты для Фазы 8) плюс то,
     * что знает {@link SimulationControls}.
     */
    public HudState hudState() {
        boolean altOverlay = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT) || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
        return simulationControls.hudState(selected, facing, buildDrag.inProgressTiles(), inspected, altOverlay,
                statsItem, quickBar.slots(), statusMessage, settingsModal.view(), activeCategoryIndex,
                hoveredPrototype(), displayLabels, pageView);
    }

    public void handle(World world, float delta) {
        escapeClosedPageView = false;
        refreshPageView(world);
        if (statusMessageTimeLeft > 0f) {
            statusMessageTimeLeft -= delta;
            if (statusMessageTimeLeft <= 0f) {
                statusMessage = null;
            }
        }
        if (settingsModal.isOpen()) {
            settingsModal.handleInput(world, history);
            return;
        }
        cameraController.handle(delta);
        simulationControls.handle();
        if (simulationControls.showBuildMenu()) {
            // Меню построек открыто (B) — клик по строке списка закрепляет прототип в хотбар,
            // а не выбор здания цифрами/кликом по самому хотбару — то же самое
            // разделение, что уже даёт дерево техов ниже.
            handleBuildMenuClick(world);
        } else if (simulationControls.showTechTree()) {
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
            // A click that lands on the open inspection panel (bottom-right, over whatever part of
            // the map happens to be scrolled under it) must never ALSO build/demolish/re-inspect
            // the world tile visually behind it — same "modal panel swallows its own click" rule
            // C3 already enforces for the recipe book/tech tree/stats/build menu above, just for a
            // panel that isn't full-screen and isn't gated by modalOpen().
            boolean swallowedByInspectionPanel = handleRecipePickClick(world);
            handleDrag(world, buildDrag, tile -> new PlaceAction(selected, tile.x(), tile.y(), facing),
                    swallowedByInspectionPanel);
            handleRemoveOrManualMineDrag(world, swallowedByInspectionPanel);
            if (!swallowedByInspectionPanel) {
                handleInspectClick(world);
            }
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
                        .filter(Furnace.class::isInstance)
                        .map(Furnace.class::cast)
                        .ifPresent(furnace -> {
                            Optional<ItemType> output = furnace.cycleRecipe();
                            LOGGER.log(System.Logger.Level.INFO, "Recipe selected: {0}",
                                    output.map(Object::toString).orElse("auto"));
                        });
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.F)) { // листнуть предмет фильтра (X-01, DEV_TASKS.md) — тот же приём, что C для рецепта печи/пресса
                TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
                world.peek(tile.x(), tile.y())
                        .filter(Filter.class::isInstance)
                        .map(Filter.class::cast)
                        .ifPresent(filter -> {
                            ItemType chosen = filter.cycleFilterItem();
                            LOGGER.log(System.Logger.Level.INFO, "Filter now passes: {0}", chosen.label());
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
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) && viewedPage != null) {
            // Просмотр страницы — верхний слой: Esc убирает его и оставляет панель осмотра, из
            // которой он открыт, а не сметает всё разом.
            closePageView();
            escapeClosedPageView = true;
        } else if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            // Живой баг-репорт: раньше ESC закрывал только панель инспекции — книга рецептов,
            // дерево техов, статистика и меню построек не реагировали на него вовсе, каждую нужно
            // было помнить закрывать своей собственной клавишей (TAB/T/V/B). Один ключ, который
            // всегда выводит из ЛЮБОЙ открытой панели, — то, что ожидает почти каждый игрок.
            inspected = null;
            simulationControls.closeAnyOpenPanel();
        }
        if (simulationControls.showStats() && Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            // Экран статистики открыт (V, P-03, DEV_TASKS.md) — N листает, какой предмет
            // графикуется, по кругу; вне этого экрана клавиша ничего не делает.
            Registry<ItemType> items = world.buildingFactory().items();
            statsItem = items.get((items.rawId(statsItem.id()) + 1) % items.size());
        }
        boolean ctrl = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Z)) {
            history.undo(world);
        }
        if (ctrl && Gdx.input.isKeyJustPressed(Input.Keys.Y)) {
            history.redo(world);
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F5)) {
            if (saveRepository.save(world) instanceof SaveResult.Failure failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Save failed: {0}", failure.reason());
                showStatus("Save failed: " + failure.reason());
            } else {
                showStatus("Saved");
            }
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.F9)) {
            if (load(saveRepository, world) instanceof SaveResult.Failure failure) {
                LOGGER.log(System.Logger.Level.WARNING, "Load failed: {0}", failure.reason());
                showStatus("Load failed: " + failure.reason());
            } else {
                showStatus("Loaded");
            }
        }
    }

    /**
     * Loads {@code world} from {@code repository} — same effect as the F9 hotkey above, exposed
     * for callers outside the per-frame key-handling loop ({@code MainMenuScreen}'s "Load
     * Game"/"Continue", via {@code GameScreen#loadFrom}). Clears {@link #history} on anything but
     * outright failure: a freshly loaded world invalidates whatever undo/redo stack belonged to
     * the world that was live before (P1-05) — the same reason F9 always cleared it inline before
     * this method existed to share that rule with a second caller.
     */
    public SaveResult load(SaveRepository repository, World world) {
        SaveResult result = repository.load(world);
        if (!(result instanceof SaveResult.Failure)) {
            history.clear();
        }
        return result;
    }

    /**
     * Живой баг-репорт: F5/F9 раньше не давали игроку вообще никакой обратной связи на экране —
     * ни при успехе, ни при неудаче ({@link #LOGGER} видно только из терминала, не из окна игры).
     * {@code message} держится {@link #STATUS_MESSAGE_SECONDS} секунд на HUD ({@link #hudState()}),
     * потом сам гаснет — не нужно отдельного действия, чтобы его убрать.
     */
    private void showStatus(String message) {
        statusMessage = message;
        statusMessageTimeLeft = STATUS_MESSAGE_SECONDS;
    }

    /**
     * Whether any full-screen panel (recipe book / tech tree / stats / build menu / info — {@link
     * SimulationControls#showRecipeBook()}/{@link SimulationControls#showTechTree()}/{@link
     * SimulationControls#showStats()}/{@link SimulationControls#showBuildMenu()}/{@link
     * SimulationControls#showInfo()}) is covering the world viewport right now (C3, live bug
     * report). See the call site in {@link #handle} for what this gates.
     */
    private boolean modalOpen() {
        return simulationControls.showRecipeBook() || simulationControls.showTechTree() || simulationControls.showStats()
                || simulationControls.showBuildMenu() || simulationControls.showInfo();
    }

    /**
     * ЛКМ по ячейке быстрой панели берёт закреплённое в руку; ПКМ — открепляет.
     *
     * <p>Открепление нужно не для порядка, а потому что панель конечна: без него девятая ячейка
     * закрывает панель навсегда, и сообщение «полна» становится тупиком. Момент нажатия, не
     * «зажато» (см. {@link DragCollector}).
     */
    private void handleHotbarClick() {
        int cell = QuickBarLayout.hitTest(Gdx.input.getX(), Gdx.input.getY(),
                Gdx.graphics.getHeight(), QuickBarLayout.COLUMNS, quickBar.visibleRows());
        if (Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT) && quickBar.at(cell).isPresent()) {
            quickBar.unpin(cell);
            uiSettings.saveQuickBar(quickBar.slots());
            return;
        }
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        if (quickBar.at(cell).isPresent()) {
            quickBar.at(cell).ifPresent(pinned -> selected = pinned);
            return;
        }
        handleCategoryPanelClick();
    }

    /**
     * ЛКМ по меню построек: сперва проверяет вкладки категорий (клик выставляет категорию
     * напрямую — см. {@link SimulationControls#setCategoryIndex}), потом — сетку иконок (клик
     * закрепляет прототип в текущий выбранный слот хотбара и делает его выбранным, то же самое
     * разрешение "какой именно слот", что и любой другой момент выбора здания). Клик мимо и вкладок,
     * и плиток (но по самой панели) молча ничего не делает — не должен провалиться в мир под меню,
     * см. {@link #modalOpen()}.
     */
    private void handleBuildMenuClick(World world) {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        Registry<BuildingPrototype> buildings = world.buildingFactory().buildings();
        List<BuildingPrototype> all = buildings.iterate();
        List<String> categories = BuildMenuLayout.categories(all);
        String activeCategory = BuildMenuLayout.activeCategory(categories, simulationControls.buildMenuCategoryCycle());
        List<BuildingPrototype> matches = BuildMenuLayout.filter(all, activeCategory, simulationControls.buildMenuQuery());
        int clampedScroll = BuildMenuLayout.clampScrollRows(matches.size(), simulationControls.buildMenuScrollOffset());
        List<BuildingPrototype> visible = BuildMenuLayout.visibleTiles(matches, clampedScroll);

        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        int tabCount = categories.size() + 1;
        int tab = BuildMenuLayout.hitTestTab(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH, visible.size(), tabCount);
        if (tab >= 0) {
            simulationControls.setCategoryIndex(tab);
            return;
        }
        int tile = BuildMenuLayout.hitTestTile(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH, visible.size());
        if (tile >= 0) {
            pinSelectedIntoHotbar(visible.get(tile).id());
        }
    }

    /**
     * Колесо мыши, пока меню построек открыто, — страница вперёд/назад по сетке иконок вместо зума
     * камеры ({@code com.graphics.screen.GameScreen}'s own scroll listener зовёт это ПЕРВЫМ и зумит
     * камеру, только если меню не открыто и вернулось {@code false} — см. тот вызов).
     */
    /**
     * Пересобирает {@link #pageView} под то, что здание отдаёт СЕЙЧАС: страница могла смениться
     * новой загрузкой, здание — исчезнуть под ковшом, а окно — измениться в размере, и прокрутка,
     * законная минуту назад, уехать за нижний край.
     *
     * <p>Раз в кадр и только пока просмотр открыт — {@code ViewableBuilding} ровно это и обещает
     * реализации, и ровно на это рассчитан её собственный кэш.
     */
    private void refreshPageView(World world) {
        if (viewedPage == null) {
            pageView = null;
            return;
        }
        Building building = world.peek(viewedPage.x(), viewedPage.y()).orElse(null);
        if (!(building instanceof ViewableBuilding viewable)) {
            closePageView(); // здание снесли, пока смотрели
            return;
        }
        BuildingImage image = viewable.image(world, viewedPage.x(), viewedPage.y()).orElse(null);
        if (image == null) {
            closePageView();
            return;
        }
        pageScroll = PageViewLayout.clampScroll(pageScroll, image.width(), image.height(),
                Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        pageView = new PageView(
                world.buildingFactory().prototype(building.prototypeId()).label(), image, pageScroll);
    }

    private void closePageView() {
        viewedPage = null;
        pageView = null;
        pageScroll = 0;
    }

    public boolean handleScroll(float amountY) {
        if (viewedPage != null) {
            // Просмотр страницы открыт — колесо листает её, а не зумит камеру под ней; тот же
            // принцип «открытая панель забирает колесо себе», что и у меню построек ниже.
            pageScroll += (int) Math.signum(amountY) * PageViewLayout.SCROLL_STEP;
            return true;
        }
        if (!simulationControls.showBuildMenu()) {
            return false;
        }
        simulationControls.scrollBuildMenu((int) Math.signum(amountY));
        return true;
    }

    /**
     * Что сейчас под курсором в нижней полосе — ячейка быстрой панели или иконка во вкладке, — или
     * {@code null}, если ни то ни другое. Читается каждый кадр для всплывающей подсказки: два
     * попадания-теста по прямоугольникам, без аллокаций и без обхода мира.
     */
    private @Nullable ContentId hoveredPrototype() {
        int screenH = Gdx.graphics.getHeight();
        int cell = QuickBarLayout.hitTest(Gdx.input.getX(), Gdx.input.getY(), screenH,
                QuickBarLayout.COLUMNS, quickBar.visibleRows());
        if (quickBar.at(cell).isPresent()) {
            return quickBar.at(cell).orElseThrow();
        }
        Map<ContentId, List<BuildingPrototype>> grouped = CategoryTabsLayout.byCategory(buildings.iterate());
        if (grouped.isEmpty()) {
            return null;
        }
        List<ContentId> categories = List.copyOf(grouped.keySet());
        List<BuildingPrototype> shown =
                grouped.get(categories.get(Math.floorMod(activeCategoryIndex, categories.size())));
        if (shown == null) {
            return null;
        }
        int visible = CategoryTabsLayout.visibleIcons(Gdx.graphics.getWidth(), shown.size());
        int icon = CategoryTabsLayout.hitTestIcon(Gdx.input.getX(), Gdx.input.getY(), screenH, visible);
        return icon >= 0 ? shown.get(icon).id() : null;
    }

    /**
     * Клик по постоянной панели построек: сперва вкладка, потом иконка под ней. Иконка ЗАКРЕПЛЯЕТ
     * здание в быстрой панели и берёт в руку — по решению владельца это единственный способ туда
     * что-то положить, поэтому одного клика достаточно и второго жеста нет.
     */
    private void handleCategoryPanelClick() {
        int screenH = Gdx.graphics.getHeight();
        List<BuildingPrototype> all = buildings.iterate();
        Map<ContentId, List<BuildingPrototype>> grouped = CategoryTabsLayout.byCategory(all);
        if (grouped.isEmpty()) {
            return;
        }
        List<ContentId> categories = List.copyOf(grouped.keySet());
        int tab = CategoryTabsLayout.hitTestTab(Gdx.input.getX(), Gdx.input.getY(), screenH, categories.size());
        if (tab >= 0) {
            activeCategoryIndex = tab;
            return;
        }
        // Локальная переменная, а не повторный get: ключ пришёл из keySet этой же карты, но
        // компилятору это не видно — правило NullAway этого репозитория именно про такой случай.
        List<BuildingPrototype> shown =
                grouped.get(categories.get(Math.floorMod(activeCategoryIndex, categories.size())));
        if (shown == null) {
            return;
        }
        int visible = CategoryTabsLayout.visibleIcons(Gdx.graphics.getWidth(), shown.size());
        int icon = CategoryTabsLayout.hitTestIcon(Gdx.input.getX(), Gdx.input.getY(), screenH, visible);
        if (icon >= 0) {
            pinSelectedIntoHotbar(shown.get(icon).id());
        }
    }

    /**
     * Закрепляет прототип в первой свободной ячейке быстрой панели и берёт его в руку — единственный
     * способ туда что-то положить, по решению владельца. Раньше выбранный слот ЗАМЕЩАЛСЯ: панель
     * была фиксированной длины, свободных ячеек в ней не бывало, и закрепить новое можно было только
     * выбросив что-то другое.
     *
     * <p>Полная панель отказывает, и об этом надо сказать вслух: молчаливый отказ выглядит как
     * сломанный клик. Взять в руку при этом всё равно даёт — игрок хотел строить, а не настраивать.
     */
    private void pinSelectedIntoHotbar(ContentId prototypeId) {
        selected = prototypeId;
        if (quickBar.pin(prototypeId) < 0) {
            showStatus("Quick bar is full - unpin something first (right-click a cell)");
            return;
        }
        uiSettings.saveQuickBar(quickBar.slots());
    }

    /**
     * ЛКМ по строке рецепта в уже открытой панели инспекции выбирает ЕЁ рецепт напрямую —
     * {@link Furnace#selectRecipe}, тот же {@code selectedRecipe}, что и клавиша {@code C} листает
     * по одному, только сразу нужный, без пролистывания. Возвращает {@code true}, если клик вообще
     * попал в панель (неважно, в конкретную строку рецепта или нет) — такой клик {@link #handle}
     * не должен пускать дальше в {@link #handleDrag}/{@link #handleRemoveOrManualMineDrag}/{@link
     * #handleInspectClick}, иначе он бы ещё и что-то построил/снёс/переключил на клетке карты, что
     * визуально оказалась ПОД панелью в правом нижнем углу.
     */
    private boolean handleRecipePickClick(World world) {
        if (inspected == null || !Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return false;
        }
        Optional<Building> building = world.peek(inspected.x(), inspected.y());
        if (building.isEmpty()) {
            return false;
        }
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        List<String> lines = InspectionPanelLayout.inspectionLines(world, world.buildingFactory().items(), inspected, building.get());
        if (!InspectionPanelLayout.isOverPanel(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH, lines.size())) {
            return false;
        }
        if (InspectionPanelLayout.hitTestOpenPage(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH, lines)) {
            // Клик по последней строке панели открывает картинку здания во весь экран. Сама
            // картинка достаётся в refreshPageView на следующем кадре — здесь только «какая
            // клетка», чтобы этот обработчик не начал зависеть ещё и от содержимого страницы.
            viewedPage = inspected;
            pageScroll = 0;
            return true;
        }
        List<Recipe> recipes = InspectionPanelLayout.clickableRecipes(building.get());
        InspectionPanelLayout.hitTestRecipe(Gdx.input.getX(), Gdx.input.getY(), screenW, screenH, lines.size(), recipes)
                .ifPresent(recipe -> {
                    ((RecipeSelectable) building.get()).selectRecipe(recipe);
                    LOGGER.log(System.Logger.Level.INFO, "Recipe selected: {0}", recipe.output());
                });
        return true;
    }

    /**
     * ЛКМ по уже занятой клетке карты открывает панель инспекции (F-03, DEV_TASKS.md) — клик по той
     * же клетке снова закрывает, клик по пустой клетке или другому зданию переключает/закрывает.
     * Это тот же самый клик, что и {@link #handleDrag} для {@link #buildDrag} — постройка поверх
     * занятой клетки и так молча проваливается ({@code World.place} видит {@code !isFree}) и сразу
     * возвращает потраченное ({@code PlaceAction.apply}), так что у этого клика уже нет другого
     * осмысленного эффекта на занятой клетке — не нужно ничего блокировать, только добавить сюда
     * инспекцию как дополнительный побочный эффект того же самого нажатия.
     *
     * <p>Building an {@link EditableBuilding} ({@code WebMiner}, {@code Interpreter}) opens {@link
     * #settingsModal} instead of the read-only panel — the live bug report {@link SettingsModal}'s
     * own javadoc explains ("тут так и просится общий механизм"): a click on one of these used to
     * open the SAME read-only panel every other building gets, with editing hidden behind a
     * separate, undiscoverable hotkey. Now the click itself IS the "edit this" affordance.
     */
    private void handleInspectClick(World world) {
        if (!Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            return;
        }
        int screenW = Gdx.graphics.getWidth();
        int screenH = Gdx.graphics.getHeight();
        if (QuickBarLayout.hitTest(Gdx.input.getX(), Gdx.input.getY(), screenH,
                QuickBarLayout.COLUMNS, quickBar.visibleRows()) >= 0
                || !cursorOverWorld(screenH)) {
            return; // клик по хотбару или по одной из HUD-полос — не по карте
        }
        TilePos tile = camera.pickTile(Gdx.input.getX(), Gdx.input.getY());
        Optional<Building> building = world.peek(tile.x(), tile.y());
        if (building.isPresent() && building.get() instanceof EditableBuilding) {
            settingsModal.openIfEditable(building.get(),
                    world.buildingFactory().prototype(building.get().prototypeId()).label(),
                    tile.x(), tile.y());
            return;
        }
        inspected = tile.equals(inspected) ? null : building.isPresent() ? tile : null;
    }

    /** Курсор между верхней и нижней HUD-полосами — общая формула {@link GfxConfig#isOverWorld}, ей же пользуются призрак постройки и {@link DragCollector}. */
    private static boolean cursorOverWorld(int screenH) {
        return GfxConfig.isOverWorld(Gdx.input.getY(), screenH);
    }

    /** Общее для ЛКМ/ПКМ-протяжки: каждый задетый тайл — своё действие, все — в одном {@link CompositeAction}. {@code alsoBlocked} — этот жест начался поверх открытой панели инспекции, см. {@link #handleRecipePickClick}. */
    private void handleDrag(World world, DragCollector drag, Function<TilePos, PlayerAction> toAction, boolean alsoBlocked) {
        List<TilePos> tiles = drag.poll(camera, quickBar.slots().size(), alsoBlocked);
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
    private void handleRemoveOrManualMineDrag(World world, boolean alsoBlocked) {
        List<TilePos> tiles = removeDrag.poll(camera, quickBar.slots().size(), alsoBlocked);
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
     * Клавиши 1..N выбирают ЗАКРЕПЛЁННЫЙ в слоте хотбара прототип — {@code NUM_1 + индекс слота},
     * {@link #hotbarSlots} задаёт порядок (было — {@code BuildingType} напрямую).
     * Ограничено девятью (P4-08, BUG_FIX_PROGRESS.md) — {@code NUM_1..NUM_9} в libGDX кончаются на
     * девятой клавише; десятый и далее слоты выбираются только мышью по хотбару.
     */
    private void handleBuildSelection() {
        for (int i = 0; i < QuickBarLayout.MAX_CELLS; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                int cell = i;
                quickBar.at(cell).ifPresent(pinned -> selected = pinned);
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
        // Тот же живой реестр и в том же порядке, что рисует TechTreeRenderer: номер строки на
        // экране и номер клавиши обязаны совпадать, а ванильный список разошёлся бы с панелью,
        // как только мод добавит свою технологию.
        List<TechType> techs = world.research().techs().iterate();
        for (int i = 0; i < techs.size() && i < 9; i++) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.NUM_1 + i)) {
                world.tryUnlockTech(techs.get(i).id());
            }
        }
    }
}
