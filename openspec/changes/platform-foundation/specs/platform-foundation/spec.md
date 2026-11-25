# Spec: platform-foundation

Requirement IDs are defined in `docs/requirements.md`. Each scenario is verified by one automated test or by recorded evidence.

## Requirement: pinned baseline (TC-01, TC-09, TC-10, TC-11, AMB-13)

Both services build with Spring Boot 3.5.16, Java 21, Gradle 8.14.8 and Spring Cloud 2025.0.3. The assistant also uses Spring AI 1.1.1.

- Scenario: `./gradlew build` succeeds on a clean machine with JDK 21 in both services.
- Scenario: the Gradle wrapper distribution checksum is verified (SHA-256 pinned in `gradle-wrapper.properties`).
- Scenario: no dependency is resolved from the Boot 4 line (`spring-boot-starter-*` versions and the Spring AI 2.x BOM are absent).

## Requirement: reactive and JDBC separation (TC-02, TC-03, TC-12, DER-01)

Request paths use WebFlux and R2DBC. Flyway runs at startup with a JDBC driver, outside the request path.

- Scenario: the application context starts with R2DBC configured and Flyway migrations applied.
- Scenario: the assistant exposes a JDBC `DataSource` used only by the vector store (pgvector), and R2DBC remains the request-path driver.

## Requirement: append-only audit table (DER-04, RN-04)

The Triage audit table rejects UPDATE and DELETE for the application role.

- Scenario: an UPDATE on the audit table fails with a permission error.
- Scenario: a DELETE on the audit table fails with a permission error.

## Requirement: test infrastructure (NFR-15, NFR-17)

Integration tests start PostgreSQL (pgvector in the assistant), Kafka and Redis with Testcontainers.

- Scenario: each service's context test starts its containers and passes.
- Scenario: no test depends on a manually started database or broker.

## Requirement: packaging and build hygiene (NFR-12 build part)

- Scenario: package names are lowercase, without underscores, under `com.vidacruz`.
- Scenario: `gradlew` is executable in git (mode 100755).
- Scenario: secrets files (`.env`, local profiles) are ignored by git, and the Gradle wrapper jar is tracked.

## Requirement: configuration validation (DER-01, NFR-08, NFR-09)

- Scenario: the application refuses to start when the required configuration is missing (for example the LLM key in the assistant).
- Scenario: no credential appears in the repository or in the build files.
