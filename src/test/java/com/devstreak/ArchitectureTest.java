package com.devstreak;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.devstreak", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule controllersMustNotUseRepositories =
            noClasses().that().resideInAPackage("..controllers..")
                    .should().dependOnClassesThat().resideInAPackage("..repositories..");

    @ArchTest
    static final ArchRule controllersMustNotUseEntities =
            noClasses().that().resideInAPackage("..controllers..")
                    .should().dependOnClassesThat().resideInAPackage("..entities..");

    @ArchTest
    static final ArchRule servicesMustNotUseControllers =
            noClasses().that().resideInAPackage("..services..")
                    .should().dependOnClassesThat().resideInAPackage("..controllers..");

    @ArchTest
    static final ArchRule entitiesMustStayIndependent =
            noClasses().that().resideInAPackage("..entities..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..services..");

    @ArchTest
    static final ArchRule repositoriesMustOnlyUseEntitiesAndEnums =
            noClasses().that().resideInAPackage("..repositories..")
                    .should().dependOnClassesThat().resideInAnyPackage("..controllers..", "..services..");
}
