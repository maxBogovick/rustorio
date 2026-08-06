package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.registry.Registry;
import com.rustorio.domain.FluidType;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a modder gets back for a {@code content/fluids/*.json} file — including, above all, what
 * they get back when they get it wrong. A loader that reads a fluid correctly is only half of the
 * contract; the other half is that a typo names the file and the field, because a modder never sees
 * the engine's stack trace and cannot read its sources to find out what it wanted.
 *
 * <p>The happy path is already exercised, indirectly, by {@code VanillaAsModParityTest} loading the
 * vanilla water and steam. The failures below were exercised by nothing at all.
 */
class FluidJsonLoaderTest {

    @TempDir
    Path tempDir;

    private final ModId modId = new ModId("testmod");

    @Test
    void aFluidIsReadUnderTheOwningModsNamespace() throws IOException {
        Registry<FluidType> fluids = new Registry<>();
        write("acid.json", """
                { "path": "acid", "label": "Acid", "colorRgb": "#33CC44" }
                """);

        FluidJsonLoader.loadInto(tempDir, modId, fluids);

        FluidType acid = fluids.peek(ContentId.of("testmod:acid")).orElseThrow();
        assertEquals("Acid", acid.label());
        assertEquals(0x33CC44, acid.colorRgb(), "the #RRGGBB text becomes the packed int the renderer reads");
    }

    /**
     * A fluid's label takes the same {@code {"en": ..., "ru": ...}} object an item's does. Worth
     * pinning because the two loaders are separate classes: nothing but a test stops one of them
     * from quietly accepting a shape the other rejects.
     */
    @Test
    void aLocalizedLabelIsAcceptedTheSameWayAnItemsIs() throws IOException {
        Registry<FluidType> fluids = new Registry<>();
        write("acid.json", """
                { "path": "acid", "label": { "en": "Acid", "ru": "Кислота" }, "colorRgb": "#33CC44" }
                """);

        FluidJsonLoader.loadInto(tempDir, modId, fluids);

        assertTrue(fluids.peek(ContentId.of("testmod:acid")).isPresent(), "an object label is a label, not an error");
    }

    @Test
    void aColorThatIsNotHashRrggbbNamesTheFieldAndTheFile() throws IOException {
        Registry<FluidType> fluids = new Registry<>();
        write("acid.json", """
                { "path": "acid", "label": "Acid", "colorRgb": "green" }
                """);

        ModLoadException thrown =
                assertThrows(ModLoadException.class, () -> FluidJsonLoader.loadInto(tempDir, modId, fluids));
        assertTrue(thrown.getMessage().contains("colorRgb"), "the message has to name the field: " + thrown.getMessage());
        assertTrue(thrown.getMessage().contains("acid.json"), "and the file: " + thrown.getMessage());
    }

    /** Right length and leading '#', wrong alphabet — the case a length check alone would wave through. */
    @Test
    void aColorOfTheRightShapeButNotHexIsStillRejected() throws IOException {
        Registry<FluidType> fluids = new Registry<>();
        write("acid.json", """
                { "path": "acid", "label": "Acid", "colorRgb": "#GGHHII" }
                """);

        ModLoadException thrown =
                assertThrows(ModLoadException.class, () -> FluidJsonLoader.loadInto(tempDir, modId, fluids));
        assertTrue(thrown.getMessage().contains("colorRgb"), thrown.getMessage());
    }

    @Test
    void aMissingRequiredFieldNamesItRatherThanFailingOnSomethingElse() throws IOException {
        Registry<FluidType> fluids = new Registry<>();
        write("acid.json", """
                { "path": "acid", "colorRgb": "#33CC44" }
                """);

        ModLoadException thrown =
                assertThrows(ModLoadException.class, () -> FluidJsonLoader.loadInto(tempDir, modId, fluids));
        assertTrue(thrown.getMessage().contains("label"), thrown.getMessage());
    }

    /** A mod with no fluids at all is the ordinary case, not an error — the directory simply isn't there. */
    @Test
    void anAbsentFluidsDirectoryRegistersNothingAndDoesNotThrow() {
        Registry<FluidType> fluids = new Registry<>();

        FluidJsonLoader.loadInto(tempDir.resolve("nope"), modId, fluids);

        fluids.freeze();
        assertEquals(0, fluids.size());
    }

    private void write(String fileName, String content) throws IOException {
        Files.writeString(tempDir.resolve(fileName), content);
    }
}
