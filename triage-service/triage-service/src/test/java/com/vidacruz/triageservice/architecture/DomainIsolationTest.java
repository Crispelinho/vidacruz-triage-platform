package com.vidacruz.triageservice.architecture;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * The triage domain is a package layer, not a Gradle module, so the compiler cannot enforce its boundary. These rules
 * make it explicit and fail the build when it is crossed.
 *
 * <p>The domain is an allow-list, not a deny-list: it may depend only on the JDK, on Reactor (the reactive types the
 * ports return, required by the WebFlux brief), on the SLF4J logging facade and on itself. Spring, Jackson, R2DBC and
 * any adapter code stay outside. A deny-list would silently accept the next framework someone imports.
 */
class DomainIsolationTest {

	private static final String[] DOMAIN_PACKAGES = {
			"com.vidacruz.triageservice.domain..",
			"com.vidacruz.triageservice.event.domain.."
	};

	private static JavaClasses production;

	@BeforeAll
	static void importProductionClasses() {
		production = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.vidacruz.triageservice");
	}

	@Test
	void domainDependsOnlyOnTheJdkReactorSlf4jAndItself() {
		classes()
				.that().resideInAnyPackage(DOMAIN_PACKAGES)
				.should().onlyDependOnClassesThat().resideInAnyPackage(
						"java..",
						"reactor..",
						"org.slf4j..",
						"com.vidacruz.triageservice.domain..",
						"com.vidacruz.triageservice.event.domain..")
				.because("the domain must stay framework-free (0 Spring imports) so it can become its own module")
				.check(production);
	}

	@Test
	void domainDoesNotDependOnAdaptersOrEntryPoints() {
		noClasses()
				.that().resideInAnyPackage(DOMAIN_PACKAGES)
				.should().dependOnClassesThat().resideInAnyPackage(
						"com.vidacruz.triageservice.application..",
						"com.vidacruz.triageservice.infrastructure..",
						"com.vidacruz.triageservice.event.infrastructure..")
				.because("dependencies point inward: adapters implement domain ports, never the reverse")
				.check(production);
	}

	@Test
	void domainClassesHaveNoSpringDiAnnotations() {
		classes()
				.that().resideInAnyPackage(DOMAIN_PACKAGES)
				.should().notBeAnnotatedWith(Service.class)
				.andShould().notBeAnnotatedWith(Component.class)
				.andShould().notBeAnnotatedWith(Repository.class)
				.andShould().notBeAnnotatedWith(Controller.class)
				.andShould().notBeAnnotatedWith(RestController.class)
				.andShould().notBeAnnotatedWith(Configuration.class)
				.andShould().notBeAnnotatedWith(ComponentScan.class)
				.andShould().notBeAnnotatedWith(ConfigurationProperties.class)
				.as("Domain classes have no Spring DI annotations").check(production);
	}

	@Test
	void domainFieldsHaveNoSpringDiAnnotations() {
		fields()
				.that().areDeclaredInClassesThat().resideInAnyPackage(DOMAIN_PACKAGES)
				.should().notBeAnnotatedWith(Autowired.class)
				.andShould().notBeAnnotatedWith(Value.class)
				.andShould().notBeAnnotatedWith(Qualifier.class)
				.andShould().notBeAnnotatedWith(Bean.class)
				.as("Domain fields have no Spring DI annotations").check(production);
	}

	@Test
	void domainMethodsHaveNoSpringDiAnnotations() {
		methods()
				.that().areDeclaredInClassesThat().resideInAnyPackage(DOMAIN_PACKAGES)
				.should().notBeAnnotatedWith(Autowired.class)
				.andShould().notBeAnnotatedWith(Value.class)
				.andShould().notBeAnnotatedWith(Qualifier.class)
				.andShould().notBeAnnotatedWith(Bean.class)
				.as("Domain methods have no Spring DI annotations").check(production);
	}

	@Test
	void domainConstructorsHaveNoSpringDiAnnotations() {
		constructors()
				.that().areDeclaredInClassesThat().resideInAnyPackage(DOMAIN_PACKAGES)
				.should().notBeAnnotatedWith(Autowired.class)
				.andShould().notBeAnnotatedWith(Value.class)
				.andShould().notBeAnnotatedWith(Qualifier.class)
				.as("Domain constructors have no Spring DI annotations").check(production);
	}
}
