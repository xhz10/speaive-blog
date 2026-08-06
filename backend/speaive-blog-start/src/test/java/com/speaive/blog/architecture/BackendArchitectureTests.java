package com.speaive.blog.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class BackendArchitectureTests {
    private static final String DOMAIN = "..domain..";
    private static final String APPLICATION = "..application..";
    private static final String APPLICATION_INBOUND_PORT = "..application.port.in..";
    private static final String APPLICATION_OUTBOUND_PORT = "..application.port.out..";
    private static final String APPLICATION_SERVICE = "..application.service..";
    private static final String WEB = "..interfaces..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String START = "..start..";

    private static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.speaive.blog");

    @Test
    void productionClassesBelongToAKnownLayer() {
        classes()
                .should().resideInAnyPackage(
                        DOMAIN,
                        APPLICATION,
                        WEB,
                        INFRASTRUCTURE,
                        START,
                        "com.speaive.blog")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void moduleLayersRespectTheAllowedDependencyDirection() {
        layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("Domain").definedBy(DOMAIN)
                .layer("Application").definedBy(APPLICATION)
                .layer("Web").definedBy(WEB)
                .layer("Infrastructure").definedBy(INFRASTRUCTURE)
                .layer("Start").definedBy("com.speaive.blog", START)
                .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Start")
                .whereLayer("Application").mayOnlyBeAccessedByLayers("Web", "Infrastructure", "Start")
                .whereLayer("Web").mayOnlyBeAccessedByLayers("Start")
                .whereLayer("Infrastructure").mayOnlyBeAccessedByLayers("Start")
                .whereLayer("Start").mayNotBeAccessedByAnyLayer()
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainIsFrameworkIndependentAndDoesNotDependOnOuterLayers() {
        noClasses()
                .that().resideInAPackage(DOMAIN)
                .should().dependOnClassesThat().resideInAnyPackage(
                        APPLICATION,
                        WEB,
                        INFRASTRUCTURE,
                        START,
                        "org.springframework..",
                        "org.mapstruct..",
                        "org.apache.ibatis..",
                        "com.baomidou.mybatisplus..",
                        "jakarta.persistence..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationDoesNotDependOnAdaptersOrTheCompositionRoot() {
        noClasses()
                .that().resideInAPackage(APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(WEB, INFRASTRUCTURE, START)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void webInboundAdaptersUseOnlyApplicationInboundContracts() {
        noClasses()
                .that().resideInAPackage(WEB)
                .should().dependOnClassesThat().resideInAnyPackage(
                        DOMAIN,
                        INFRASTRUCTURE,
                        APPLICATION_OUTBOUND_PORT,
                        APPLICATION_SERVICE,
                        START)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void controllersStayInsideTheWebInboundPackage() {
        classes()
                .that().haveSimpleNameEndingWith("Controller")
                .should().resideInAPackage(WEB)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void restControllersStayInsideTheWebInboundPackage() {
        classes()
                .that().areAnnotatedWith("org.springframework.web.bind.annotation.RestController")
                .should().resideInAPackage(WEB)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void webFrameworksStayOutOfCoreAndOutboundAdapters() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN, APPLICATION, INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..",
                        "org.springframework.security..",
                        "jakarta.servlet..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void infrastructureRemainsAnOutboundAdapter() {
        noClasses()
                .that().resideInAPackage(INFRASTRUCTURE)
                .should().dependOnClassesThat().resideInAnyPackage(
                        WEB,
                        APPLICATION_INBOUND_PORT,
                        APPLICATION_SERVICE,
                        START)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void controllersDoNotReachPersistenceTypes() {
        noClasses()
                .that().haveSimpleNameEndingWith("Controller")
                .should().dependOnClassesThat().resideInAnyPackage(
                        INFRASTRUCTURE,
                        APPLICATION_OUTBOUND_PORT,
                        "..repository..",
                        "org.apache.ibatis..",
                        "com.baomidou.mybatisplus..")
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        slices()
                .matching("com.speaive.blog.(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }
}
