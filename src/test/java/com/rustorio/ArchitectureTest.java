package com.rustorio;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Архитектурные инварианты как обычные тесты (ArchUnit). Раньше «правильность»
 * архитектуры доказывалась разовым анализом графа импортов — теперь эти же
 * свойства проверяются на КАЖДОЙ сборке и падают при первом нарушении.
 *
 * <p>Анализируем только основной код (без тестов), пакеты домена — это
 * {@code core}, {@code model}, {@code sim}, {@code game}; слой движка —
 * {@code render}, {@code input}, {@code screen}.
 */
@AnalyzeClasses(packages = "com.rustorio", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    /** [1] Ацикличность: между пакетами не должно быть циклов (граф — DAG). */
    @ArchTest
    static final ArchRule packages_are_free_of_cycles =
            slices().matching("com.rustorio.(*)..")
                    .should().beFreeOfCycles();

    /** [2] Изоляция домена от движка: ядро логики не знает про libGDX. */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_libgdx =
            noClasses().that()
                    .resideInAnyPackage("..core..", "..model..", "..sim..", "..game..")
                    .should().dependOnClassesThat().resideInAPackage("com.badlogic..")
                    .because("ядро логики должно быть тестируемо и переносимо без движка");

    /** [3] Правило зависимостей: домен не зависит от слоёв отрисовки/ввода/экранов. */
    @ArchTest
    static final ArchRule domain_does_not_depend_on_engine_layer =
            noClasses().that()
                    .resideInAnyPackage("..core..", "..model..", "..sim..", "..game..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..render..", "..input..", "..screen..")
                    .because("зависимости идут вниз: политика не знает про детали");

    /** [4] core — нижний слой: не зависит ни от одного другого пакета проекта. */
    @ArchTest
    static final ArchRule core_is_the_base_layer =
            noClasses().that().resideInAPackage("..core..")
                    .should().dependOnClassesThat()
                    .resideInAnyPackage("..model..", "..sim..", "..game..",
                            "..render..", "..input..", "..screen..")
                    .because("core — фундамент домена, зависит только от себя и JDK");
}
