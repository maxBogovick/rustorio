package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.api.content.model.AuthoredMap;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.api.content.vanilla.VanillaFluids;
import com.rustorio.architecture.BuildOutputs;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.BuildingType;
import com.rustorio.domain.Direction;
import com.rustorio.domain.building.Building;
import com.rustorio.domain.building.FluidPort;
import com.rustorio.domain.building.InspectableBuilding;
import com.rustorio.domain.world.World;
import com.rustorio.game.GameBootstrap;
import com.rustorio.persistence.JsonSaveRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Educational lightbulb mod: craft chain lands in vanilla machines, placed lamp burns only on
 * power, and the on-sprite is what the player sees as "горит".
 *
 * <p>Jar is rebuilt from {@code javasrc/} into a temp mods tree (same bargain as petrochem): the
 * committed {@code lightbulb.jar} is what a player loads, so a second test keeps its class list
 * aligned with sources.
 */
class LightbulbAcceptanceTest {

    private static final Path MODS_ROOT = Path.of("resources", "mods");
    private static final Path LIGHTBULB = MODS_ROOT.resolve("lightbulb");
    private static final String ENTRY_POINT = "com.lightbulb.LightbulbMod";

    private static final ContentId LAMP = ContentId.of("lightbulb:lamp");
    private static final ContentId WORKSHOP = ContentId.of("lightbulb:bulb_workshop");
    private static final ContentId SPRITE_ON = ContentId.of("lightbulb:lamp_on");
    private static final ContentId SPRITE_OFF = ContentId.of("lightbulb:lamp_off");

    @Test
    void theCraftChainTurnsSandAndPlatesIntoALightbulb(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);
        ItemType sand = content.items().get(ContentId.of("lightbulb:sand"));
        ItemType glass = content.items().get(ContentId.of("lightbulb:glass"));
        ItemType wire = content.items().get(ContentId.of("lightbulb:wire"));
        ItemType filament = content.items().get(ContentId.of("lightbulb:filament"));
        ItemType bulbBase = content.items().get(ContentId.of("lightbulb:bulb_base"));
        ItemType bronzePlate = content.items().get(ContentId.of("rustorio:bronze_plate"));
        ItemType ironPlate = content.items().get(ContentId.of("rustorio:iron_plate"));
        ItemType coal = content.items().get(ContentId.of("rustorio:coal"));
        ItemType lightbulb = content.items().get(ContentId.of("lightbulb:lightbulb"));

        assertTrue(world.place(BuildingType.FURNACE, 10, 10, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 11, 10, Direction.UP));
        assertTrue(world.place(ContentId.of("lightbulb:wire_drawer"), 13, 10, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 14, 10, Direction.UP));
        assertTrue(world.place(BuildingType.PRESS, 16, 10, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 17, 10, Direction.UP));
        assertTrue(world.place(ContentId.of("lightbulb:base_former"), 10, 14, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 11, 14, Direction.UP));
        assertTrue(world.place(BuildingType.ASSEMBLER, 13, 14, Direction.RIGHT));
        assertTrue(world.place(BuildingType.CHEST, 15, 14, Direction.UP));

        Building furnace = world.peek(10, 10).orElseThrow();
        Building wireDrawer = world.peek(13, 10).orElseThrow();
        Building filamentPress = world.peek(16, 10).orElseThrow();
        Building baseFormer = world.peek(10, 14).orElseThrow();
        Building bulbAssembler = world.peek(13, 14).orElseThrow();

        for (int tick = 0; tick < 500; tick++) {
            furnace.accept(world, sand);
            furnace.accept(world, coal);
            wireDrawer.accept(world, bronzePlate);
            filamentPress.accept(world, wire);
            baseFormer.accept(world, ironPlate);
            bulbAssembler.accept(world, glass);
            bulbAssembler.accept(world, filament);
            bulbAssembler.accept(world, bulbBase);
            world.tick();
        }

        assertTrue(world.stats().total(glass) > 0, "glass from sand: " + world.stats().total(glass));
        assertTrue(world.stats().total(filament) > 0, "filament from wire: " + world.stats().total(filament));
        assertTrue(world.stats().total(bulbBase) > 0, "bulb base: " + world.stats().total(bulbBase));
        assertTrue(world.stats().total(lightbulb) > 0, "assembled lightbulb: " + world.stats().total(lightbulb));
    }

    @Test
    void theLampStaysDarkWithoutPower(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);

        assertTrue(world.place(LAMP, 10, 10, Direction.UP));
        world.tick();

        Building lamp = world.peek(10, 10).orElseThrow();
        assertEquals(BuildingStatus.NO_POWER, lamp.status());
        assertEquals(SPRITE_OFF, lamp.appearance().sprite());
        assertEquals("No power — need a pole and a generator", details(world, lamp, 10, 10).get(0));
    }

    @Test
    void theLampBurnsWhenPoweredAndShowsTheOnSprite(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);

        assertTrue(world.place(LAMP, 5, 5, Direction.UP));
        assertTrue(world.place(BuildingType.POLE, 7, 5));
        assertTrue(world.place(BuildingType.GENERATOR, 8, 5, Direction.RIGHT));
        assertTrue(world.place(BuildingType.TANK, 9, 5));
        portInto(world, 9, 5).insert(VanillaFluids.STEAM, 2000);

        world.tick();

        Building lamp = world.peek(5, 5).orElseThrow();
        assertEquals(BuildingStatus.WORKING, lamp.status());
        assertEquals(SPRITE_ON, lamp.appearance().sprite(), "burning is the on-sprite, not a render glow");
        assertEquals("Lit", details(world, lamp, 5, 5).get(0));
    }

    @Test
    void theLampStateSurvivesSaveAndLoad(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World saved = worldOn(content);
        assertTrue(saved.place(LAMP, 10, 10, Direction.LEFT));

        JsonSaveRepository repository = GameBootstrap.saves(content, dir.resolve("save.json"));
        repository.save(saved);
        World loaded = worldOn(content);
        repository.load(loaded);

        Building restored = loaded.peek(10, 10).orElseThrow();
        assertEquals(LAMP, restored.prototypeId());
        assertEquals(Direction.LEFT, restored.outputDirection().orElseThrow(),
                "codec must round-trip facing — otherwise save stores a dead field");
        loaded.tick();
        assertEquals(BuildingStatus.NO_POWER, restored.status(), "still unpowered after reload");
    }

    @Test
    void placingALampCostsOneCraftedLightbulb(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);
        ItemType lightbulb = content.items().get(ContentId.of("lightbulb:lightbulb"));

        world.creditItem(lightbulb, 1);
        assertTrue(world.trySpendBuildingCost(LAMP), "one crafted bulb pays for one lamp");
        assertEquals(0, world.inventory().amount(lightbulb));
        assertTrue(!world.trySpendBuildingCost(LAMP), "second lamp needs another bulb");
    }

    @Test
    void theWorkshopMapHasEveryResourceTheLessonNeeds(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        AuthoredOreLayout layout = AuthoredOreLayout.from(content.maps().get(WORKSHOP));
        ItemType sand = content.items().get(ContentId.of("lightbulb:sand"));
        ItemType iron = content.items().get(ContentId.of("rustorio:iron_ore"));
        ItemType bronze = content.items().get(ContentId.of("rustorio:bronze_ore"));
        ItemType coal = content.items().get(ContentId.of("rustorio:coal"));
        ItemType water = content.items().get(ContentId.of("rustorio:water"));

        assertEquals(sand, layout.oreAt(16, 20).orElseThrow(), "sand for glass");
        assertEquals(iron, layout.oreAt(28, 14).orElseThrow(), "iron for plates and bulb base");
        assertEquals(bronze, layout.oreAt(40, 22).orElseThrow(), "bronze for wire");
        assertEquals(coal, layout.oreAt(22, 32).orElseThrow(), "coal for furnace and boiler");
        assertEquals(water, layout.terrainAt(42, 34).orElseThrow(), "water shore for the pump");
    }

    @Test
    void theJarInTheRepositoryMatchesItsSources(@TempDir Path dir) {
        Path fresh = dir.resolve("fresh.jar");
        TestModJarBuilder.build(fresh, sourcesFromJavasrc(),
                Map.of("com.rustorio.api.mod.RustorioMod", ENTRY_POINT));

        assertEquals(BuildOutputs.classNamesIn(fresh).stream().sorted().toList(),
                BuildOutputs.classNamesIn(LIGHTBULB.resolve("lightbulb.jar")).stream().sorted().toList(),
                "lightbulb.jar drifted from javasrc/ — rebuild it (see modding-guide javac+jar recipe)");
    }

    private static World worldOn(LoadedGame content) {
        AuthoredMap map = content.maps().get(WORKSHOP);
        return GameBootstrap.createWorld(content, AuthoredOreLayout.from(map), 64, 64);
    }

    private static List<String> details(World world, Building building, int x, int y) {
        return ((InspectableBuilding) building).inspectionDetails(world, x, y);
    }

    private static FluidPort portInto(World world, int x, int y) {
        Optional<FluidPort> port = world.fluidPort(x - 1, y, Direction.RIGHT);
        assertTrue(port.isPresent(), "expected a fluid tile at (" + x + ", " + y + ")");
        return port.orElseThrow();
    }

    private static LoadedGame loadWithFreshlyBuiltJar(Path dir) {
        Path mods = dir.resolve("mods");
        copyTree(MODS_ROOT, mods);
        TestModJarBuilder.build(mods.resolve("lightbulb").resolve("lightbulb.jar"), sourcesFromJavasrc(),
                Map.of("com.rustorio.api.mod.RustorioMod", ENTRY_POINT));
        return ModLoader.loadAll(ModDirectories.discover(mods));
    }

    private static Map<String, String> sourcesFromJavasrc() {
        Map<String, String> sources = new LinkedHashMap<>();
        Path javasrc = LIGHTBULB.resolve("javasrc");
        try (Stream<Path> files = Files.list(javasrc)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".java")).sorted().toList()) {
                String name = file.getFileName().toString();
                sources.put("com.lightbulb." + name.substring(0, name.length() - ".java".length()),
                        Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read lightbulb javasrc in " + javasrc, e);
        }
        assertTrue(sources.containsKey(ENTRY_POINT), "entry point missing in javasrc/: " + sources.keySet());
        return sources;
    }

    private static void copyTree(Path from, Path to) {
        try (Stream<Path> entries = Files.walk(from)) {
            for (Path source : entries.toList()) {
                Path target = to.resolve(from.relativize(source).toString());
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(source, target);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot copy mods from " + from, e);
        }
    }
}
