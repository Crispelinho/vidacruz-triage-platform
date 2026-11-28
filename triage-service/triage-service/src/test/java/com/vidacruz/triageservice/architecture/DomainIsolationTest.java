package com.vidacruz.triageservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * The triage domain is a package layer, not a Gradle module, so the compiler cannot enforce its boundary. These rules
 * make it explicit and fail the build when it is crossed.
 *
 * <p>The domain is an allow-list, not a deny-list: it may depend only on the JDK, on Reactor (the reactive types the
 * WebFlux brief requires) and on itself. Spring, Jackson, Micrometer and any adapter code stay outside. A deny-list
 * would silently accept the next framework that someone imports.
 */
class DomainIsolationTest {

	private static final String DOMAIN = "com.vidacruz.triageservice.domain..";
	private static final String EVENT_DOMAIN = "com.vidacruz.triageservice.event.domain..";

	private static JavaClasses production;

	@BeforeAll
	static void importProductionClasses() {
		production = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.vidacruz.triageservice");
	}

	@Test
	void domainDependsOnlyOnTheJdkReactorAndItself() {
		classes()
				.that().resideInAnyPackage(DOMAIN, EVENT_DOMAIN)
				.should().onlyDependOnClassesThat().resideInAnyPackage("java..", "reactor..", DOMAIN, EVENT_DOMAIN)
				.as("Domain classes may depend only on the JDK, Reactor and the domain itself")
				.check(production);
	}

	@Test
	void domainDoesNotDependOnAdaptersOrEntryPoints() {
		noClasses()
				.that().resideInAnyPackage(DOMAIN, EVENT_DOMAIN)
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.vidacruz.triageservice.application..",
						"com.vidacruz.triageservice.infrastructure..",
						"com.vidacruz.triageservice.event.infrastructure..")
				.as("Domain classes must not depend on adapters or entry points")
				.check(production);
	}

	@Test
	void domainPortsAreInterfaces() {
		classes()
				.that().resideInAPackage("com.vidacruz.triageservice.domain.port..")
				.should().beInterfaces()
				.as("Domain ports are interfaces implemented by infrastructure adapters")
				.check(production);
	}
}
