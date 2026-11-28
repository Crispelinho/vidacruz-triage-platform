plugins {
	java
	id("org.springframework.boot") version "3.5.16"
	id("io.spring.dependency-management") version "1.1.7"
}

group = "com.vidacruz"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
}

extra["springCloudVersion"] = "2025.0.3"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	// Micrometer Observation to OpenTelemetry bridge; OTLP export is enabled only where the endpoint is configured.
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.flywaydb:flyway-core")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	// springdoc 2.x targets Spring Boot 3.x and serves /v3/api-docs plus the Swagger UI behind the public /docs routes.
	implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:2.10.1")
	implementation("org.flywaydb:flyway-database-postgresql")
	implementation("org.springframework:spring-jdbc")
	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("org.postgresql:postgresql")
	implementation("org.postgresql:r2dbc-postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("com.tngtech.archunit:archunit:1.4.1")
	testImplementation("io.projectreactor:reactor-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:kafka")
	testImplementation("org.testcontainers:postgresql")
	testImplementation("org.testcontainers:r2dbc")
	// TST-RXN: BlockHound detects blocking calls on reactor threads (ADR-04/ADR-05).
	testImplementation("io.projectreactor.tools:blockhound:1.0.10.RELEASE")
	testImplementation("io.projectreactor.tools:blockhound-junit-platform:1.0.10.RELEASE")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	jvmArgs = listOf(
		"-Dfile.encoding=UTF-8",
		"-Dsun.jnu.encoding=UTF-8",
		"-Duser.country=US",
		"-Duser.language=en",
		// BlockHound instruments JDK classes by adding methods (JDK 13+).
		"-XX:+AllowRedefinitionToAddDeleteMethods"
	)
	// Keep CI's Linux entropy source non-blocking (/dev/urandom); Windows uses its native provider.
	if (!System.getProperty("os.name").lowercase().contains("win")) {
		systemProperty("java.security.egd", "file:/dev/./urandom")
	}
}
