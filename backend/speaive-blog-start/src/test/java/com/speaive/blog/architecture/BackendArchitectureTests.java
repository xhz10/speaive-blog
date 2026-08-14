package com.speaive.blog.architecture;

import com.speaive.blog.application.port.in.markdown.MarkdownUseCase;
import com.speaive.blog.application.port.in.media.MediaUseCase;
import com.speaive.blog.application.port.in.post.PostUseCase;
import com.speaive.blog.application.port.in.importing.MarkdownInboxUseCase;
import com.speaive.blog.application.service.MarkdownApplicationService;
import com.speaive.blog.application.service.MediaApplicationService;
import com.speaive.blog.application.service.PostApplicationService;
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
    private static final String APPLICATION_COMMAND = "..application.command..";
    private static final String APPLICATION_RESULT = "..application.result..";
    private static final String APPLICATION_PERSISTENCE_PORT = "..application.port.out.persistence..";
    private static final String WEB = "..interfaces..";
    private static final String INFRASTRUCTURE = "..infrastructure..";
    private static final String START = "..start..";
    private static final String PERSISTENCE_PO = "..persistence.po";
    private static final String PERSISTENCE_MAPPER = "..persistence.mapper";
    private static final String PERSISTENCE_MAPPING = "..persistence.mapping";
    private static final String PERSISTENCE_REPOSITORY = "..persistence.repository";

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
    void applicationBoundaryTypesStayInTheirDedicatedPackages() {
        classes()
                .that().haveSimpleNameEndingWith("Command")
                .should().resideInAPackage(APPLICATION_COMMAND)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleNameEndingWith("Result")
                .should().resideInAPackage(APPLICATION_RESULT)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleNameEndingWith("UseCase")
                .should().resideInAPackage(APPLICATION_INBOUND_PORT)
                .andShould().beInterfaces()
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleNameEndingWith("ApplicationService")
                .should().resideInAPackage(APPLICATION_SERVICE)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().resideInAPackage(APPLICATION)
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage(APPLICATION_PERSISTENCE_PORT)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().resideInAPackage(APPLICATION)
                .and().haveSimpleNameEndingWith("Port")
                .should().resideInAPackage(APPLICATION_OUTBOUND_PORT)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void applicationServicesImplementTheirNarrowInboundPorts() {
        classes()
                .that().haveSimpleName(PostApplicationService.class.getSimpleName())
                .should().beAssignableTo(PostUseCase.class)
                .andShould().notBeAssignableTo(MediaUseCase.class)
                .andShould().notBeAssignableTo(MarkdownUseCase.class)
                .andShould().notBeAssignableTo(MarkdownInboxUseCase.class)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleName(MediaApplicationService.class.getSimpleName())
                .should().beAssignableTo(MediaUseCase.class)
                .andShould().notBeAssignableTo(PostUseCase.class)
                .andShould().notBeAssignableTo(MarkdownUseCase.class)
                .andShould().notBeAssignableTo(MarkdownInboxUseCase.class)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleName(MarkdownApplicationService.class.getSimpleName())
                .should().beAssignableTo(MarkdownUseCase.class)
                .andShould().beAssignableTo(MarkdownInboxUseCase.class)
                .andShould().notBeAssignableTo(PostUseCase.class)
                .andShould().notBeAssignableTo(MediaUseCase.class)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void inboundPortsAndResultsDoNotExposeDomainTypes() {
        noClasses()
                .that().resideInAnyPackage(
                        APPLICATION_INBOUND_PORT,
                        APPLICATION_COMMAND,
                        APPLICATION_RESULT)
                .should().dependOnClassesThat().resideInAPackage(DOMAIN)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainProductionTypesStayInApprovedBusinessPackages() {
        classes()
                .that().resideInAPackage(DOMAIN)
                .and().doNotHaveSimpleName("package-info")
                .should().resideInAnyPackage(
                        "..domain.author..",
                        "..domain.error..",
                        "..domain.post..")
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
    void persistenceTypesStayInTheirDedicatedPackages() {
        classes()
                .that().haveSimpleNameEndingWith("Po")
                .should().resideInAPackage(PERSISTENCE_PO)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleNameEndingWith("DatabaseMapper")
                .should().resideInAPackage(PERSISTENCE_MAPPER)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().resideInAPackage(INFRASTRUCTURE)
                .and().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage(PERSISTENCE_REPOSITORY)
                .check(PRODUCTION_CLASSES);
        classes()
                .that().haveSimpleNameEndingWith("MapStructMapper")
                .should().resideInAPackage(PERSISTENCE_MAPPING)
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        slices()
                .matching("com.speaive.blog.(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }

    @Test
    void domainAndApplicationSubpackagesAreFreeOfCycles() {
        slices()
                .matching("com.speaive.blog.domain.(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
        slices()
                .matching("com.speaive.blog.application.(*)..")
                .should().beFreeOfCycles()
                .check(PRODUCTION_CLASSES);
    }
}
