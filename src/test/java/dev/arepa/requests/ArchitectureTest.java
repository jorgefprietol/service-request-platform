package dev.arepa.requests;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages="dev.arepa.requests", importOptions=ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {
    @ArchTest static final ArchRule domainIsIndependent = noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideOutsideOfPackages("java..", "dev.arepa.requests.domain..");
    @ArchTest static final ArchRule applicationPointsInward = noClasses().that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideOutsideOfPackages("java..", "dev.arepa.requests.domain..", "dev.arepa.requests.application..");
    @ArchTest static final ArchRule httpDoesNotAccessPersistence = noClasses().that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..", "org.springframework.jdbc..", "java.sql..");
}
