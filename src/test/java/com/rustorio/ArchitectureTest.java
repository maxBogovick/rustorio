package com.rustorio;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Архитектурные инварианты как обычные тесты (ArchUnit). Раньше «правильность»
 * архитектуры доказывалась разовым анализом графа импортов — теперь эти же
 * свойства проверяются на КАЖДОЙ сборке и падают при первом нарушении.
 *
 * <p>Анализируем только основной код (без тестов). Логика игры живёт в корне
 * {@code com.rustorio} (пакеты {@code core}, {@code model}, {@code sim},
 * {@code game}); графический движок — в отдельном корне {@code com.graphics}
 * (пакеты {@code render}, {@code input}, {@code screen}).
 */
@AnalyzeClasses(
        packages = {"com.rustorio", "com.graphics"},
        importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** [1] Ацикличность: между пакетами не должно быть циклов (граф — DAG). */
    @ArchTest
    static final ArchRule packages_are_free_of_cycles =
            slices().matching("com.*.(*)..")
                    .should().beFreeOfCycles();

    /** [2] Изоляция домена от движка: ядро логики не знает про libGDX. */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_libgdx =
            noClasses().that()
                    .resideInAnyPackage("..core..", "..model..", "..sim..", "..game..")
                    .should().dependOnClassesThat().resideInAPackage("com.badlogic..")
                    .because("ядро логики должно быть тестируемо и переносимо без движка");

    /**
     * [3] Правило зависимостей: логика игры ({@code com.rustorio}) не знает про
     * графический движок ({@code com.graphics}). Зависимость идёт только вниз:
     * движок читает домен, домен про движок — нет.
     */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_engine_layer =
            noClasses().that().resideInAPackage("com.rustorio..")
                    .should().dependOnClassesThat().resideInAPackage("com.graphics..")
                    .because("зависимости идут вниз: политика не знает про детали");

    /** [4] core — нижний слой: не зависит ни от одного другого пакета проекта. */
    @ArchTest
    static final ArchRule core_is_the_base_layer =
            noClasses().that().resideInAPackage("..core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..model..", "..sim..", "..game..",
                            "..render..", "..input..", "..screen..")
                    .because("core — фундамент домена, зависит только от себя и JDK");

    /**
     * [5] {@code Config} — только неизменяемые константы.
     *
     * <p>В спринте 0 баланс уехал из {@code Config} в {@code Balance}: константы времени
     * компиляции нельзя менять, а прогрессия обязана менять числа во время игры. Это
     * правило стережёт, чтобы через полгода, когда все забудут почему, изменяемое поле не
     * приползло обратно в {@code Config}.
     */
    @ArchTest
    static final ArchRule config_holds_only_constants =
            fields().that().areDeclaredInClassesThat().haveSimpleName("Config")
                    .should().beStatic().andShould().beFinal()
                    .because("изменяемый баланс живёт в Balance, а не в Config");

    /**
     * [6] Симуляция ДЕТЕРМИНИРОВАНА: никакой случайности и никаких «настенных часов».
     *
     * <p>Одинаковые действия игрока обязаны давать одинаковый мир. Без этого не будет ни
     * сохранений (загруженная фабрика поедет иначе, чем сохранённая), ни надёжных тестов
     * (падает через раз — ищи потом причину).
     *
     * <p>Отрисовки правило не касается: там {@code System.nanoTime()} и анимации законны —
     * они не меняют мир.
     */
    @ArchTest
    static final ArchRule simulation_is_deterministic =
            noClasses().that().resideInAnyPackage("..model..", "..sim..", "..game..")
                    .should().callMethod(Math.class, "random")
                    .orShould().callMethod(System.class, "currentTimeMillis")
                    .orShould().callMethod(System.class, "nanoTime")
                    .because("симуляция должна быть воспроизводимой: без этого не будет "
                            + "ни сохранений, ни надёжных тестов");
}
