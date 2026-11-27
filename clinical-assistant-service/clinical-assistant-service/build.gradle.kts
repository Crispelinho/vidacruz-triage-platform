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

extra["springAiVersion"] = "1.1.1"
extra["springCloudVersion"] = "2025.0.3"

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	// Micrometer Observation to OpenTelemetry bridge; OTLP export is enabled only where the endpoint is configured.
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")
	implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
	implementation("org.springframework.kafka:spring-kafka")
	implementation("io.projectreactor.kafka:reactor-kafka")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-webflux")
	implementation("org.springframework.ai:spring-ai-starter-vector-store-pgvector")
	implementation("org.springframework.cloud:spring-cloud-starter-circuitbreaker-reactor-resilience4j")
	implementation("org.flywaydb:flyway-core")
	implementation("org.flywaydb:flyway-database-postgresql")
	developmentOnly("org.springframework.boot:spring-boot-devtools")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")
	runtimeOnly("org.postgresql:postgresql")
	runtimeOnly("org.postgresql:r2dbc-postgresql")
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("com.tngtech.archunit:archunit:1.4.1")
	testImplementation("io.projectreactor:reactor-test")
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
		mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:${property("springCloudVersion")}")
	}
}

tasks.withType<Test> {
	useJUnitPlatform()
	// BlockHound instruments JDK classes by adding methods (JDK 13+).
	jvmArgs("-XX:+AllowRedefinitionToAddDeleteMethods")
	// Keep CI's Linux entropy source non-blocking (/dev/urandom); Windows uses its native provider.
	if (!System.getProperty("os.name").lowercase().contains("win")) {
		systemProperty("java.security.egd", "file:/dev/./urandom")
	}
}
