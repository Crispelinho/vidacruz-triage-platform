package com.vidacruz.clinical.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The clinical domain is a package layer, not a Gradle module, so the compiler cannot enforce its boundary. These
 * rules make it explicit and fail the build when it is crossed.
 *
 * <p>The domain is an allow-list, not a deny-list: it may depend only on the JDK, on Reactor (the reactive types the
 * ports return, required by the WebFlux brief) and on itself. Spring, Jackson, Spring AI, Resilience4j, Micrometer and
 * any adapter code stay outside. A deny-list would silently accept the next framework someone imports.
 */
class DomainIsolationTest {

	private static final String DOMAIN = "com.vidacruz.clinical.domain..";

	private static JavaClasses production;

	@BeforeAll
	static void importProductionClasses() {
		production = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.vidacruz.clinical", "com.vidacruz.clinicalassistantservice");
	}

	@Test
	void domainDependsOnlyOnTheJdkReactorAndItself() {
		classes()
				.that().resideInAPackage(DOMAIN)
				.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "reactor..", DOMAIN)
				.because("the domain must stay framework-free (0 Spring imports) so it can become its own module")
				.check(production);
	}

	@Test
	void domainDoesNotDependOnAdaptersOrEntryPoints() {
		noClasses()
				.that().resideInAPackage(DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.vidacruz.clinical.application..",
						"com.vidacruz.clinical.infrastructure..",
						"com.vidacruz.clinicalassistantservice..")
				.because("dependencies point inward: adapters implement domain ports, never the reverse")
				.check(production);
	}

	@Test
	void domainPortsAreInterfaces() {
		classes()
				.that().resideInAPackage("com.vidacruz.clinical.domain.ports..")
				.should().beInterfaces()
				.because("a port is a contract; its implementation belongs to an adapter")
				.check(production);
	}

}
