package com.rustorio.mod;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.rustorio.api.content.ContentId;
import com.rustorio.architecture.BuildOutputs;
import com.rustorio.api.content.model.AuthoredMap;
import com.rustorio.domain.AuthoredOreLayout;
import com.rustorio.domain.BuildingStatus;
import com.rustorio.domain.Direction;
import com.rustorio.api.content.model.ItemType;
import com.rustorio.domain.PatchOreLayout;
import com.rustorio.domain.building.Building;
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
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Держит честным то, что обещает руководство по моддингу на примере мода {@code petrochem}.
 *
 * <p>Документ утверждает конкретные вещи: цепочка «помпа → НПЗ → генератор → столб → электробур»
 * запускается с нуля, здание из jar встаёт без тока в {@code NO_POWER}, его состояние переживает
 * сейв, подписка на событие доходит до панели осмотра, а перекрытые пятна карты достаются первому
 * по порядку. Проза устаревает молча; этот тест — единственное, что делает её проверяемой.
 *
 * <p><b>Jar собирается здесь из {@code javasrc/}</b>, а не берётся с диска: собранный руками
 * {@code petrochem.jar} лежит в репозитории и легко расходится с исходниками — так уже было. Мод
 * копируется во временный каталог вместе с остальными, и загрузчик видит свежескомпилированную
 * половину. Тест заодно сверяет, что лежащий в репозитории jar содержит те же классы, — иначе он
 * протух, и игрок получит не то, что проверено здесь.
 */
class PetrochemGuideAcceptanceTest {

    private static final Path MODS_ROOT = Path.of("resources", "mods");
    private static final Path PETROCHEM = MODS_ROOT.resolve("petrochem");
    private static final String ENTRY_POINT = "com.petrochem.PetrochemMod";

    private static final ContentId CRACKER = ContentId.of("petrochem:electro_cracker");
    private static final ContentId OIL_FIELD = ContentId.of("petrochem:oil_field");

    @Test
    void theOilChainStartsFromNothingAndFeedsTheElectricMiner(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);
        ItemType coal = content.items().get(ContentId.of("rustorio:coal"));
        ItemType oilSand = content.items().get(ContentId.of("petrochem:oil_sand"));

        placeChain(world);
        assertTrue(world.place(ContentId.of("petrochem:oil_miner"), 29, 30, Direction.LEFT), "бур на руде");
        assertTrue(world.place(ContentId.of("rustorio:chest"), 28, 30, Direction.UP), "приёмник за буром");

        Building refinery = world.peek(32, 30).orElseThrow();
        for (int tick = 0; tick < 300; tick++) {
            refinery.accept(world, coal); // уголь приезжает лентой; здесь — руками, ленты не про этот тест
            world.tick();
        }

        assertEquals(BuildingStatus.WORKING, world.peek(29, 30).orElseThrow().status(),
                "электробур получает ток от газового генератора, а не стоит в NO_POWER");
        assertTrue(world.stats().total(oilSand) > 0, "добытый песок: " + world.stats().total(oilSand));
    }

    @Test
    void theJarBuildingRunsOnThatSamePowerAndShowsWhatTheEventCounted(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);
        ItemType coal = content.items().get(ContentId.of("rustorio:coal"));
        ItemType oilSand = content.items().get(ContentId.of("petrochem:oil_sand"));
        ItemType plastic = content.items().get(ContentId.of("petrochem:plastic"));

        placeChain(world);
        assertTrue(world.place(CRACKER, 31, 33, Direction.RIGHT), "крекер в радиусе столба");
        assertTrue(world.place(ContentId.of("rustorio:chest"), 32, 33, Direction.UP), "приёмник за крекером");

        Building refinery = world.peek(32, 30).orElseThrow();
        Building cracker = world.peek(31, 33).orElseThrow();
        assertEquals(CRACKER, cracker.prototypeId(), "здание из jar докладывает свой прототип, а не одолженный");
        for (int tick = 0; tick < 300; tick++) {
            refinery.accept(world, coal);
            cracker.accept(world, oilSand);
            world.tick();
        }

        long made = world.stats().total(plastic);
        assertTrue(made > 0, "сваренный пластик: " + made);
        assertEquals("Пластика за сессию: " + made, details(world, cracker, 31, 33).get(2),
                "эту строку наполняет подписка на ItemProducedEvent — если она молчит, счётчик нулевой");
    }

    @Test
    void theJarBuildingStopsWithoutPower(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);

        assertTrue(world.place(CRACKER, 10, 10, Direction.RIGHT));
        Building cracker = world.peek(10, 10).orElseThrow();
        cracker.accept(world, content.items().get(ContentId.of("petrochem:oil_sand")));
        world.tick();

        assertEquals(BuildingStatus.NO_POWER, cracker.status(),
                "поведение, которого данными не выразить: demand спрашивает только бур");
    }

    @Test
    void theJarBuildingsStateSurvivesSaveAndLoad(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World saved = worldOn(content);
        ItemType oilSand = content.items().get(ContentId.of("petrochem:oil_sand"));

        assertTrue(saved.place(CRACKER, 10, 10, Direction.RIGHT));
        Building cracker = saved.peek(10, 10).orElseThrow();
        cracker.accept(saved, oilSand);
        cracker.accept(saved, oilSand);

        JsonSaveRepository repository = GameBootstrap.saves(content, dir.resolve("save.json"));
        repository.save(saved);
        World loaded = worldOn(content);
        repository.load(loaded);

        Building restored = loaded.peek(10, 10).orElseThrow();
        assertEquals(CRACKER, restored.prototypeId(), "прототип мода восстановлен, а не потерян как неизвестный контент");
        assertEquals("Сырьё в буфере: 2/5", details(loaded, restored, 10, 10).get(0),
                "буфер пережил сейв через собственный Codec мода");
    }

    /**
     * Вторая производственная ветка гайда, не завязанная на энергосеть: {@code plastic_plant}
     * (уголь + песок, общий пул {@code chemistry}) и {@code polymer_works} (пластик + шестерня,
     * свой приватный пул — здание не объявляет {@code "kind"}, поэтому пул назван его же id).
     * Ни один из существующих тестов класса эту ветку не трогает, хотя она описана в
     * {@code docs/modding-guide.md} наравне с электрокрекером.
     */
    @Test
    void theChemistryBranchTurnsSandIntoAPolymerPack(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);
        ItemType coal = content.items().get(ContentId.of("rustorio:coal"));
        ItemType gear = content.items().get(ContentId.of("rustorio:gear"));
        ItemType oilSand = content.items().get(ContentId.of("petrochem:oil_sand"));
        ItemType plastic = content.items().get(ContentId.of("petrochem:plastic"));
        ItemType polymerPack = content.items().get(ContentId.of("petrochem:polymer_pack"));

        assertTrue(world.place(ContentId.of("petrochem:plastic_plant"), 10, 10, Direction.RIGHT), "завод пластика");
        assertTrue(world.place(ContentId.of("petrochem:polymer_works"), 15, 10, Direction.RIGHT), "завод полимеров");
        Building plasticPlant = world.peek(10, 10).orElseThrow();
        Building polymerWorks = world.peek(15, 10).orElseThrow();

        for (int tick = 0; tick < 400; tick++) {
            plasticPlant.accept(world, oilSand);
            plasticPlant.accept(world, coal);
            polymerWorks.accept(world, plastic);
            polymerWorks.accept(world, gear);
            world.tick();
        }

        assertTrue(world.stats().total(plastic) > 0, "сваренный пластик: " + world.stats().total(plastic));
        assertTrue(world.stats().total(polymerPack) > 0,
                "собранный полимерный набор: " + world.stats().total(polymerPack));
    }

    /**
     * {@code oil_processing} — единственная технология мода — раньше открывалась без единого
     * эффекта: JSON описывает только узел дерева. Теперь она наполовину сокращает партию крекера
     * (см. {@code ElectroCracker.effectiveCrackTicks}); это то, чем гайд обязан подтвердить обещание,
     * а не оставить прозой.
     *
     * <p>Читает время партии через панель осмотра, а не гоняет крекер тиками до готовой партии:
     * тому мешала бы разгонка всей энергосети (насос → НПЗ → генератор), которая одинакова что с
     * технологией, что без неё, и только зашумила бы разницу, которую проверяет этот тест.
     */
    @Test
    void unlockingOilProcessingSpeedsUpTheCracker(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        World world = worldOn(content);
        assertTrue(world.place(CRACKER, 10, 10, Direction.RIGHT));
        Building cracker = world.peek(10, 10).orElseThrow();

        assertEquals("До партии: 20 тиков", details(world, cracker, 10, 10).get(1),
                "без технологии крекер варит партию за исходные 20 тиков");

        world.addResearchPoints(80);
        assertTrue(world.tryUnlockTech(ContentId.of("rustorio:fast_mining")), "предок технологии должен открыться первым");
        world.addResearchPoints(200);
        assertTrue(world.tryUnlockTech(ContentId.of("petrochem:oil_processing")),
                "открытие должно состояться — очков и предка хватает");

        assertEquals("До партии: 10 тиков", details(world, cracker, 10, 10).get(1),
                "открытая технология вдвое сокращает партию — тот же эффект, что у FAST_MINING в движке");
    }

    /**
     * {@code oil_pump} reuses the vanilla {@code PUMP} archetype with {@code placement:
     * "NEEDS_ORE"} instead of {@code ADJACENT_TO_WATER} — before this test, {@code Pump} never
     * touched the {@link com.rustorio.domain.OreLayout} it stands on at all, so the oil sand under
     * it was, in effect, an inexhaustible source. Reads the same {@link
     * com.rustorio.domain.OreLayout#depletionSnapshot()} the save format already relies on, rather
     * than mining until the (6000-call) reserve actually runs dry — this only has to prove the
     * pump SPENDS a call each cycle, not walk the whole curve {@code OreDepletion} already covers.
     */
    @Test
    void theOilPumpSpendsTheSandReserveItStandsOn(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        AuthoredOreLayout oreLayout = AuthoredOreLayout.from(content.maps().get(OIL_FIELD));
        World world = GameBootstrap.createWorld(content, oreLayout, 48, 48);
        assertTrue(world.place(ContentId.of("petrochem:oil_pump"), 30, 30, Direction.RIGHT), "помпа на нефтяном поле");

        for (int tick = 1; tick <= 25; tick++) { // two full pump cycles plus a partial third (PUMP_INTERVAL == 10)
            world.tick();
        }

        int index = 30 * PatchOreLayout.STANDARD_WIDTH + 30;
        assertEquals(2, oreLayout.depletionSnapshot().get(index),
                "две откачки за 25 тиков должны были дважды позвать extract() по тому же контракту, что у Miner");
    }

    @Test
    void overlappingOrePatchesBelongToTheFirstOneDeclared(@TempDir Path dir) {
        LoadedGame content = loadWithFreshlyBuiltJar(dir);
        AuthoredOreLayout layout = AuthoredOreLayout.from(content.maps().get(OIL_FIELD));

        // (33, 31) накрыто обоими нефтяными пятнами карты — правило «первое по порядку забирает клетку».
        assertEquals(content.items().get(ContentId.of("petrochem:oil_sand")), layout.oreAt(33, 31).orElseThrow(),
                "перекрытие достаётся первому объявленному пятну");
        assertTrue(layout.oreAt(300, 300).isEmpty(), "сетка карты 256x256, за её краем руды нет");
    }

    /**
     * Gradle {@code petrochemModJar} must stay aligned with {@code javasrc/} — same class list
     * {@link TestModJarBuilder} would produce in a test fixture.
     */
    @Test
    void theGradleBuiltJarMatchesItsSources(@TempDir Path dir) {
        Path committed = PETROCHEM.resolve("petrochem.jar");
        assertTrue(Files.isRegularFile(committed),
                "petrochem.jar missing — run ./gradlew modJars (or ./gradlew build) first");

        Path fresh = dir.resolve("fresh.jar");
        TestModJarBuilder.build(fresh, sourcesFromJavasrc(),
                Map.of("com.rustorio.api.mod.RustorioMod", ENTRY_POINT));

        assertEquals(BuildOutputs.classNamesIn(fresh).stream().sorted().toList(),
                BuildOutputs.classNamesIn(committed).stream().sorted().toList(),
                "petrochem.jar drifted from javasrc/ — ./gradlew petrochemModJar");
    }

    /** Помпа на руде → труба → НПЗ → труба → генератор, плюс столб, накрывающий всё это и потребителей рядом. */
    private static void placeChain(World world) {
        assertTrue(world.place(ContentId.of("petrochem:oil_pump"), 30, 30, Direction.RIGHT), "помпа на нефтяном поле");
        assertTrue(world.place(ContentId.of("rustorio:pipe"), 31, 30, Direction.RIGHT));
        assertTrue(world.place(ContentId.of("petrochem:refinery"), 32, 30, Direction.RIGHT));
        assertTrue(world.place(ContentId.of("rustorio:pipe"), 33, 30, Direction.RIGHT));
        assertTrue(world.place(ContentId.of("petrochem:gas_generator"), 34, 30, Direction.LEFT));
        assertTrue(world.place(ContentId.of("rustorio:pole"), 32, 32, Direction.UP));
    }

    private static World worldOn(LoadedGame content) {
        AuthoredMap map = content.maps().get(OIL_FIELD);
        return GameBootstrap.createWorld(content, AuthoredOreLayout.from(map), 48, 48);
    }

    private static List<String> details(World world, Building building, int x, int y) {
        return ((InspectableBuilding) building).inspectionDetails(world, x, y);
    }

    /**
     * Копия установленных модов во временном каталоге. Jar петрохима уже собран задачей
     * {@code petrochemModJar} до прогона тестов — см. {@code modJars} в {@code build.gradle}.
     */
    private static LoadedGame loadWithFreshlyBuiltJar(Path dir) {
        Path mods = dir.resolve("mods");
        copyTree(MODS_ROOT, mods);
        return ModLoader.loadAll(ModDirectories.discover(mods));
    }

    /** Полное имя класса → текст файла: {@code javasrc/Foo.java} лежит в пакете {@code com.petrochem}. */
    private static Map<String, String> sourcesFromJavasrc() {
        Map<String, String> sources = new LinkedHashMap<>(); // порядок компиляции стабилен между запусками
        Path javasrc = PETROCHEM.resolve("javasrc");
        try (Stream<Path> files = Files.list(javasrc)) {
            for (Path file : files.filter(f -> f.getFileName().toString().endsWith(".java")).sorted().toList()) {
                String name = file.getFileName().toString();
                sources.put("com.petrochem." + name.substring(0, name.length() - ".java".length()),
                        Files.readString(file));
            }
        } catch (IOException e) {
            throw new UncheckedIOException("не прочитать исходники мода в " + javasrc, e);
        }
        assertTrue(sources.containsKey(ENTRY_POINT), "точка входа мода должна лежать в javasrc/: " + sources.keySet());
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
            throw new UncheckedIOException("не скопировать моды из " + from, e);
        }
    }
}
