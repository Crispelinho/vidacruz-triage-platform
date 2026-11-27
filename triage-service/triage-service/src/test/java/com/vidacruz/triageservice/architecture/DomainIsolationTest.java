package com.vidacruz.triageservice.architecture;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

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

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.constructors;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The triage domain is a package layer, not a Gradle module, so the compiler cannot enforce its boundary. These rules
 * make the boundary explicit: Spring DI never enters the domain, and the remaining framework imports are a shrinking
 * allow-list instead of an invisible convention.
 */
class DomainIsolationTest {

	private static final String[] DOMAIN_PACKAGES = {
			"com.vidacruz.triageservice.domain..",
			"com.vidacruz.triageservice.event.domain.."
	};

	private static final String TRANSACTIONAL_OPERATOR =
			"org.springframework.transaction.reactive.TransactionalOperator";
	private static final String DATABASE_CLIENT = "org.springframework.r2dbc.core.DatabaseClient";

	/** Documented exceptions from the intermediate cleanup step; remove entries as each adapter is extracted. */
	private static final Map<String, Set<String>> ALLOWED_SPRING_DEPENDENCIES = Map.of(
			"com.vidacruz.triageservice.domain.service.PatientLifecycleService", Set.of(TRANSACTIONAL_OPERATOR),
			"com.vidacruz.triageservice.domain.service.SlaEvaluator", Set.of(TRANSACTIONAL_OPERATOR),
			"com.vidacruz.triageservice.domain.service.TriageRegistrationService", Set.of(TRANSACTIONAL_OPERATOR),
			"com.vidacruz.triageservice.domain.service.ReportService", Set.of(DATABASE_CLIENT,
					"org.springframework.r2dbc.core.DatabaseClient$GenericExecuteSpec",
					"org.springframework.r2dbc.core.RowsFetchSpec"),
			"com.vidacruz.triageservice.event.domain.OutboxEvento", Set.of(
					"org.springframework.data.annotation.Id",
					"org.springframework.data.annotation.Transient",
					"org.springframework.data.domain.Persistable",
					"org.springframework.data.relational.core.mapping.Column",
					"org.springframework.data.relational.core.mapping.Table"));

	private static JavaClasses production;

	@BeforeAll
	static void importProductionClasses() {
		production = new ClassFileImporter()
				.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
				.importPackages("com.vidacruz.triageservice");
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

	@Test
	void springDependenciesAreRatcheted() {
		classes()
				.that().resideInAnyPackage(DOMAIN_PACKAGES)
				.should(new SpringDependencyRatchet())
				.as("Domain Spring dependencies stay inside the documented allow-list").check(production);
	}

	@Test
	void allowListOnlyMentionsDomainClasses() {
		assertThat(ALLOWED_SPRING_DEPENDENCIES.keySet()).allSatisfy(name ->
				assertThat(name.startsWith("com.vidacruz.triageservice.domain.")
						|| name.startsWith("com.vidacruz.triageservice.event.domain.")).isTrue());
	}

	private static final class SpringDependencyRatchet extends ArchCondition<JavaClass> {

		private SpringDependencyRatchet() {
			super("use only the documented Spring types");
		}

		@Override
		public void check(JavaClass item, ConditionEvents events) {
			Set<String> actual = item.getDirectDependenciesFromSelf().stream()
					.map(dependency -> dependency.getTargetClass().getName())
					.filter(name -> name.startsWith("org.springframework."))
					.collect(Collectors.toCollection(TreeSet::new));
			Set<String> allowed = ALLOWED_SPRING_DEPENDENCIES.getOrDefault(item.getName(), Set.of());
			for (String target : actual) {
				if (!allowed.contains(target)) {
					events.add(SimpleConditionEvent.violated(item,
							item.getName() + " depends on a new Spring type: " + target));
				}
			}
			for (String expected : allowed) {
				if (!actual.contains(expected)) {
					events.add(SimpleConditionEvent.violated(item,
							item.getName() + " dropped ratcheted dependency " + expected
									+ "; remove it from the allow-list"));
				}
			}
		}
	}
}
