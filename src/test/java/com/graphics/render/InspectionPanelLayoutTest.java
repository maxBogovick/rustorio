package com.graphics.render;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.Appearance;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.ItemShape;
import com.rustorio.domain.ItemType;
import com.rustorio.domain.Recipe;
import com.rustorio.domain.RecipeBook;
import com.rustorio.domain.VanillaItems;
import com.rustorio.domain.VanillaSprites;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.Chest;
import com.rustorio.domain.building.Furnace;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.building.TickContext;
import com.rustorio.domain.building.VanillaBuildings;
import com.rustorio.domain.world.World;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link InspectionPanelLayout} — pure logic (no libGDX window needed), shared by {@link
 * HudRenderer} (drawing) and {@code com.graphics.input.InputHandler} (hit-testing a click on a
 * recipe row), same reason {@link BuildMenuLayoutTest} exists for the build menu's own geometry.
 */
class InspectionPanelLayoutTest {

    private static final RecipeBook RECIPES = RecipeBook.standard();

    @Test
    void clickableRecipesReturnsAFurnacesPossibleRecipesInOrder() {
        Furnace furnace = new Furnace(BuildingType.FURNACE, Direction.RIGHT, RECIPES);

        assertEquals(furnace.possibleRecipes(), InspectionPanelLayout.clickableRecipes(furnace));
        assertFalse(InspectionPanelLayout.clickableRecipes(furnace).isEmpty(), "the vanilla FURNACE kind has real recipes to list");
    }

    @Test
    void clickableRecipesIsEmptyForABuildingThatDoesNotSelectRecipes() {
        assertTrue(InspectionPanelLayout.clickableRecipes(new Chest()).isEmpty());
    }

    /**
     * A building the panel has never heard of gets its own lines shown — the whole point of {@code
     * InspectableBuilding}. Before it, describing a building meant adding a branch to {@link
     * InspectionPanelLayout} naming that building's class, so a mod's archetype could not be
     * described at all without editing the engine.
     */
    @Test
    void aBuildingThePanelHasNeverHeardOfStillGetsItsOwnLinesShown() {
        World world = new World(4, 4);
        SelfDescribing building = new SelfDescribing(List.of("Custom: 42"));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);

        assertTrue(lines.contains("Custom: 42"),
                "the building's own line must reach the panel unchanged: " + lines);
    }

    /**
     * One over-long fact is wrapped across rows and capped, rather than drawn off the edge of the
     * panel or allowed to push everything else off it — a raw HTTP response body is the real case.
     * The cap's arithmetic is what this pins: the truncating branch indexes into the last row, and
     * an off-by-one there is a {@link StringIndexOutOfBoundsException} in the middle of drawing.
     */
    @Test
    void oneVeryLongFactIsWrappedAcrossRowsAndCappedWithAnEllipsis() {
        World world = new World(4, 4);
        SelfDescribing building = new SelfDescribing(List.of("x".repeat(5000)));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);
        List<String> wrapped = lines.subList(2, lines.size()); // past the shared label/status header

        assertEquals(6, wrapped.size(), "an unbounded body must be capped, not drawn in full: " + wrapped.size());
        assertTrue(wrapped.getLast().endsWith("..."), "the cut must be visible: " + wrapped.getLast());
        assertTrue(wrapped.stream().allMatch(line -> line.length() <= 56),
                "no row may exceed the panel's own width");
    }

    /**
     * A fact carrying line breaks of its own becomes separate rows, not one row with the breaks
     * left in it. That was a live "the text is drawn on top of itself" report, and the mechanism is
     * worth stating: {@code inspectionDetails} returns one string per row, the panel sizes itself
     * from how many strings it got, but {@code BitmapFont.draw} obeys a {@code '\n'} regardless —
     * so a pretty-printed JSON body, which is what a web miner pointed at a real API fetches, drew
     * thirty lines of text inside the eighteen pixels reserved for one and spilled over everything
     * below it. Whitespace is flattened rather than honoured: a fact is a line, and breaks inside
     * it are incidental to the data, not layout the building asked for.
     */
    @Test
    void aFactCarryingLineBreaksIsFlattenedIntoRowsInsteadOfDrawnOverTheRowsBelowIt() {
        World world = new World(4, 4);
        String prettyPrintedBody = "{\n  \"login\": \"octocat\",\n  \"id\": 583231,\n  \"type\": \"User\"\n}";
        SelfDescribing building = new SelfDescribing(List.of(prettyPrintedBody));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);
        List<String> wrapped = lines.subList(2, lines.size()); // past the shared label/status header

        assertTrue(wrapped.stream().noneMatch(row -> row.indexOf('\n') >= 0),
                "a row the panel counts as one row must draw as one row: " + wrapped);
        assertTrue(String.join(" ", wrapped).contains("\"login\": \"octocat\""),
                "flattening must not lose the body itself: " + wrapped);
        assertTrue(wrapped.stream().noneMatch(row -> row.startsWith(" ")),
                "the body's own indentation is not worth panel width: " + wrapped);
    }

    /**
     * No row is drawn wider than the panel it sits in — the second half of the same report. The
     * wrap used to count 56 characters per row on the assumption that the HUD font is fixed-width;
     * it is libGDX's built-in proportional one, where 56 capitals measure well over twice the
     * panel's usable width and were simply drawn through its right border and off the screen edge.
     *
     * <p>The assertion reads the layout's own measurement rather than a character count copied into
     * this file: a count would have to be re-derived by hand every time the panel, the font scale
     * or the inset changes, and a stale one passes while the panel overflows.
     */
    @Test
    void noRowIsWiderThanThePanelEvenWhenEveryGlyphIsAWideOne() {
        World world = new World(4, 4);
        SelfDescribing building = new SelfDescribing(List.of("W".repeat(300)));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);

        for (String row : lines.subList(2, lines.size())) {
            assertTrue(HudText.widthOf(row) <= InspectionPanelLayout.ROW_WIDTH,
                    "row overflows the panel by " + (HudText.widthOf(row) - InspectionPanelLayout.ROW_WIDTH)
                            + " px: " + row);
        }
    }

    /** Prose breaks at a space when there is one late enough in the row — a fact that reads as a sentence should not be chopped mid-word just because a URL has to be. */
    @Test
    void aSentenceBreaksAtASpaceRatherThanMidWord() {
        World world = new World(4, 4);
        String sentence = "The monitor shows the last response body fetched by whatever building stands behind it";
        SelfDescribing building = new SelfDescribing(List.of(sentence));

        List<String> lines = InspectionPanelLayout.inspectionLines(
                world, VanillaItems.frozen(), new TilePos(1, 1), building);
        String firstRow = lines.get(2); // past the shared label/status header

        assertTrue(sentence.startsWith(firstRow), "the first row must be a prefix of the fact: " + firstRow);
        assertEquals(' ', sentence.charAt(firstRow.length()),
                "the break landed inside a word: " + firstRow);
    }

    /**
     * The fitting reaches every line the panel draws, not only the ones a mod supplies through
     * {@code InspectableBuilding}. A chest listing an item is the cheapest way to prove it, and not
     * a contrived one: an item's label is a mod's own string, and the line the panel builds around
     * it ("  {label}: {amount}") used to be measured by nobody at all.
     */
    @Test
    void aLineThePanelBuildsItselfIsFittedToo() {
        World world = new World(4, 4);
        Registry<ItemType> items = new Registry<>();
        ItemType verbose = new ItemType(ContentId.of("test:verbose"),
                "Compressed Iron Alloy Plating Subassembly, Grade II, Factory Sealed", false, 0x998877, ItemShape.SQUARE);
        items.register(verbose.id(), verbose);
        items.freeze();
        Chest chest = new Chest();
        chest.restore(Map.of(verbose, 7));

        List<String> lines = InspectionPanelLayout.inspectionLines(world, items, new TilePos(1, 1), chest);

        assertTrue(lines.stream().anyMatch(row -> row.contains("Compressed Iron Alloy")),
                "the chest's contents must still be listed: " + lines);
        for (String row : lines) {
            assertTrue(HudText.widthOf(row) <= InspectionPanelLayout.ROW_WIDTH,
                    "row overflows the panel: " + row);
        }
    }

    /**
     * Клик ПО САМОЙ строке попадает в эту строку — не в соседнюю снизу и не мимо панели.
     *
     * <p>Живой отчёт игрока: «по слову не работает, надо нажать в нелогичном месте». Причина
     * арифметическая: строки рисуются от {@code panelY + panelH - FIRST_ROW_TOP} вниз, а
     * попадание считалось от {@code panelY + panelH}. Из примерно пятнадцати пикселей высоты
     * букв в свою полосу попадали четыре верхних, остальные одиннадцать — в полосу следующей
     * строки; у последней строки промах уходил вовсе за панель, и она не нажималась никак.
     *
     * <p>Проверка идёт по той же формуле, по которой {@code HudRenderer} рисует, и через ту же
     * общую константу — иначе тест закреплял бы собственную копию раскладки, а не настоящую.
     */
    @Test
    void aClickOnTheGlyphsOfARowSelectsThatRowAndNotTheOneBelowIt() {
        int lineCount = 5;
        int screenW = 1280;
        int screenH = 720;
        float panelY = InspectionPanelLayout.panelY(screenH, lineCount);
        float panelH = InspectionPanelLayout.panelHeight(lineCount);
        float insidePanelX = InspectionPanelLayout.panelX(screenW) + InspectionPanelLayout.TEXT_PAD + 5f;

        for (int row = 0; row < lineCount; row++) {
            // Верх строки — ровно там, где HudRenderer ставит font.draw. Целимся в СЕРЕДИНУ букв
            // (и в их низ), а не под самый верх: у строчки высотой около двенадцати пикселей
            // старая раскладка отдавала своей полосе только верхние четыре, и промах начинался
            // ровно там, куда игрок и целится.
            float rowTopHudY = panelY + panelH - InspectionPanelLayout.FIRST_ROW_TOP
                    - row * InspectionPanelLayout.LINE_HEIGHT;
            for (float intoGlyphs : new float[] {2f, 8f, 12f}) {
                float screenY = screenH - (rowTopHudY - intoGlyphs);
                assertEquals(row, InspectionPanelLayout.hitTestLine(insidePanelX, screenY, screenW, screenH, lineCount),
                        "клик на " + intoGlyphs + " px ниже верха строки " + row + " обязан выбрать её саму");
            }
        }
    }

    /** A building whose only interesting property is that this test file — and the panel — know nothing about its type. */
    private record SelfDescribing(List<String> details) implements Building, InspectableBuilding {

        @Override
        public List<String> inspectionDetails(TickContext world, int x, int y) {
            return details;
        }

        @Override
        public Appearance appearance() {
            return Appearance.of(VanillaSprites.CHEST);
        }


        @Override
        public Object state() {
            return details;
        }

        /**
         * A REGISTERED id, even though this fixture's Java class is unknown to everything. The
         * panel reads the prototype to title itself, and every building that can actually exist
         * came from the factory and therefore has one — a fixture claiming an unregistered id would
         * be testing a state the game cannot reach.
         */
        @Override
        public ContentId prototypeId() {
            return VanillaBuildings.idFor(BuildingType.CHEST);
        }
    }

    @Test
    void hitTestRecipeFindsTheRowUnderThePointAndMissesEverythingAboveTheRecipeSection() {
        int screenW = 1280;
        int screenH = 800;
        Recipe iron = RECIPES.find(BuildingType.FURNACE, VanillaItems.IRON_ORE).orElseThrow();
        Recipe bronze = RECIPES.find(BuildingType.FURNACE, VanillaItems.BRONZE_ORE).orElseThrow();
        List<Recipe> recipes = List.of(iron, bronze);
        int totalLineCount = 5; // 3 header lines (label/status/recipe summary) + 2 recipe rows
        int recipeSectionStart = totalLineCount - recipes.size();

        float panelX = InspectionPanelLayout.panelX(screenW);
        float panelH = InspectionPanelLayout.panelHeight(totalLineCount);
        float panelY = InspectionPanelLayout.panelY(screenH, totalLineCount);
        // A point on the glyphs of the SECOND recipe row (index recipeSectionStart + 1).
        //
        // This used to be measured from the panel's top edge, which is where the bands USED to
        // start — and that was the bug, not the fixture's convenience: rows are drawn
        // FIRST_ROW_TOP lower than that, so a point picked this way sat in the row above the one
        // the reader of this test would expect. The assertions below never changed; only where
        // the click actually is did.
        float rowHudY = panelY + panelH - InspectionPanelLayout.FIRST_ROW_TOP
                - (recipeSectionStart + 1) * InspectionPanelLayout.LINE_HEIGHT - 3f;
        float rowScreenX = panelX + 10f;
        float rowScreenY = screenH - rowHudY;

        assertEquals(Optional.of(bronze),
                InspectionPanelLayout.hitTestRecipe(rowScreenX, rowScreenY, screenW, screenH, totalLineCount, recipes));

        // A point on the first header line (row 0), above the recipe section entirely.
        float headerHudY = panelY + panelH - InspectionPanelLayout.FIRST_ROW_TOP - 3f;
        assertEquals(Optional.empty(),
                InspectionPanelLayout.hitTestRecipe(rowScreenX, screenH - headerHudY, screenW, screenH, totalLineCount, recipes));

        // No recipes at all (a Chest, say) — never a hit, regardless of where the click lands.
        assertEquals(Optional.empty(),
                InspectionPanelLayout.hitTestRecipe(rowScreenX, rowScreenY, screenW, screenH, totalLineCount, List.of()));
    }

    @Test
    void isOverPanelIsTrueInsideAndFalseOutsideAndWhenNothingIsOpen() {
        int screenW = 1280;
        int screenH = 800;
        int lineCount = 4;
        float panelX = InspectionPanelLayout.panelX(screenW);
        float panelH = InspectionPanelLayout.panelHeight(lineCount);
        float panelY = InspectionPanelLayout.panelY(screenH, lineCount);
        float midHudY = panelY + panelH / 2f;
        float insideScreenY = screenH - midHudY;

        assertTrue(InspectionPanelLayout.isOverPanel(panelX + 10, insideScreenY, screenW, screenH, lineCount));
        assertFalse(InspectionPanelLayout.isOverPanel(panelX - 10, insideScreenY, screenW, screenH, lineCount), "left of the panel entirely");
        assertFalse(InspectionPanelLayout.isOverPanel(panelX + 10, insideScreenY, screenW, screenH, 0), "no panel open right now");
    }
}
