# Design: platform-foundation

## Baseline

| Item | Version | Source |
|---|---|---|
| Spring Boot | 3.5.16 | DEC-12, TC-01, AMB-12, ADR-10 |
| Java | 21 | DEC-13, AMB-13 |
| Gradle | 8.14.8 (wrapper, SHA-256 pinned) | scaffold audit |
| Spring Cloud | 2025.0.3 | DEC-12 |
| Spring AI | 1.1.1 (assistant) | TC-11, DER-06 |

The Boot 3.5.16 line is the last open-source release of 3.5.x (spring.io announcement, 2026-06-25). The brief requires Boot 3.x, so the end of OSS support is accepted only through ADR-10. Until then, the risk is recorded and the gate stays open.

## Structure

- Two independent Gradle projects, `triage-service` and `clinical-assistant-service`, one per microservice (TC-09, ADR-01 gate).
- Packages `com.vidacruz.triageservice` and `com.vidacruz.clinicalassistantservice` (renamed from underscore names; see Tasks).
- Gradle wrapper committed with `gradle-wrapper.jar`; `gradlew` executable in git.

## Persistence

- PostgreSQL per service (ADR-14 gate). Triage owns the audit table and its append-only grants (DER-04).
- Flyway migrations run at startup through JDBC, outside the request path (TC-12).
- Assistant: R2DBC for requests, a separate JDBC `DataSource` for pgvector (DER-06). Wiring is an open point of the scaffold audit and is a task below.

## Test strategy

- Context tests use Testcontainers (PostgreSQL with pgvector, Kafka, Redis) through a shared `TestcontainersConfiguration` per service.
- A test that needs the containers fails when Docker is not available; it is not skipped silently.

## Open points carried to later changes

- Dockerfile and image build: change 12 (`platform-delivery`).
- Kubernetes and Azure: change 12 (NFR-13, NFR-14, ADR-11).
- Support end of Boot 3.5: ADR-10 decision.
