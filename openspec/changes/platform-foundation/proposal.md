# OpenSpec Change 1: Platform Foundation

**Change ID:** `platform-foundation`  
**Capability:** C-01 (Platform Foundation)  
**Component:** Both services (Triage + Clinical Assistant)  
**Priority:** P0 (blocking; every other change depends on this)  
**Status:** Proposed  
**Date:** 2026-11-25

---

## Summary

Establish the development and deployment baseline for both microservices: upgrade to Spring Boot 3.5.16, Spring Cloud 2025.0.3, Java 21, and Gradle 8.14.8. Set up local docker-compose for development (PostgreSQL, Kafka, Redis), CI/CD pipeline on GitHub Actions, and integration test infrastructure (Testcontainers, BlockHound for reactive compliance).

---

## Scope

### In Scope
- Update `triage-service` and `clinical-assistant-service` skeletons to Boot 3.5.16 (from 4.2.0).
- Align Spring Cloud BOM to 2025.0.3 (from 2026.0.0-M1).
- Set Java toolchain to 21 (from 25).
- Create `docker-compose.yml` with:
  - PostgreSQL 15 for both services (separate databases).
  - Kafka 3.x (topic `triaje.eventos`).
  - Redis 7.x (for Assistant projection).
  - Mock alert receiver (webhook endpoint for testing).
- Add Gradle plugins for Spring Boot, R2DBC drivers, and Testcontainers.
- Set up GitHub Actions workflow: build, unit tests, integration tests on each PR to `develop`.
- Create the audit table schema (empty for now; writes in change 3; constraint tested in this change).
- Document versioning decisions and known risks (ADR-10).

### Out of Scope
- Source code implementation (no Triage or Assistant logic yet).
- Cloud deployment (Azure is change 12).
- Kubernetes manifests (Azure is change 12).
- Observability (metrics, tracing, logs) — that is change 11.
- Authentication filter implementation — that is change 10.
- Load balancing or horizontal scaling.

---

## Dependencies

**Requires formal acceptance of:**
- ADR-10 (Spring Boot 3.5.16 baseline, development/evaluation scope)
- ADR-14 (One PostgreSQL database per service)
- ADR-07 (API key authentication)
- ADR-05 (Spring AI 1.1.x GA for LLM)
- ADR-06 (Webhook notifications with outbox retry)

**Current status:** All five ADRs are Proposed. This change is conditional on their acceptance. If any ADR is rejected or significantly modified, platform-foundation must be revisited.

## Motivation

The brief specifies Spring Boot 3.x; the provided skeletons default to Boot 4.x. ADR-10 accepts Boot 3.5.x for development/evaluation (with production options documented). This change:
1. Aligns with the brief requirement for the evaluation window.
2. Avoids Java 25 + Gradle 9.x compatibility risk.
3. Establishes the platform so other changes can begin in parallel.
4. Proves the CI pipeline works before feature development.

---

## Requirements

| Requirement ID | Type | Description |
|---|---|---|
| NFR-01 | Reactive programming | Both services use Spring WebFlux; no blocking I/O on request paths. |
| NFR-05 | Health and readiness probes | Spring Boot Actuator endpoints: `/health`, `/health/ready`, `/health/live`. |
| NFR-12 | docker-compose | One command starts all services and dependencies. |
| NFR-18 | Progressive commits | Commits work at each step (build, run, test). |
| TC-01 | Spring Boot 3.x | Use Spring Boot 3.5.16 (latest 3.5 patch). |
| TC-02 | Spring WebFlux | Use `@RestController` with `Mono` and `Flux`. |
| TC-03 | R2DBC driver | Use `spring-boot-starter-data-r2dbc` for PostgreSQL. |
| TC-04 | Kafka | Topic `triaje.eventos` for event streaming. |
| DER-01 | Startup validation | Properties for SLAs, Kafka, LLM key; fail fast if missing. |
| DER-04 | Audit append-only | Create `AuditoriaClinica` table with no UPDATE/DELETE grants; test it. |

---

## Dependencies

**Blocks:** All changes (1–12). Nothing can be implemented until the platform is set up.

**Requires:**
- ADR-01 (Service decomposition).
- ADR-10 (Spring Boot 3.5 and Java 21).
- ADR-14 (One database per service).
- Access to GitHub (for Actions) and Docker Hub (for base images).

---

## Done Criterion

✅ **Platform builds and runs locally:**
1. Both services compile on Java 21 with Gradle 8.14.8: `./gradlew build` from each service root returns SUCCESS (exit code 0, no errors).
2. `docker-compose up -d` from repo root completes; `docker-compose ps` shows all services with status "healthy" (not "up" or "starting") within 30 seconds.
3. CI workflow triggers on PR to `develop`: GitHub Actions workflow completes successfully (all steps pass); build and unit tests pass.

✅ **Data layer is ready:**
1. After `docker-compose up -d` and successful startup, verify: `psql -h localhost -U postgres -d vidacruz_triage -c "SELECT COUNT(*) FROM AuditoriaClinica"` returns 0 rows (table created). Repeat for `vidacruz_assistant` with `SELECT COUNT(*) FROM FragmentoDocumental`.
2. `AuditoriaClinica` table exists with columns (verify via schema query): `id` UUID, `timestamp` TIMESTAMP, `pacienteId` UUID, `estadoAnterior` VARCHAR, `estadoNuevo` VARCHAR, `tipoEvento` VARCHAR.
3. Integration test T6.1 verifies RBAC: connects via R2DBC (application user), executes INSERT → SUCCESS; executes UPDATE → ERROR "permission denied"; executes DELETE → ERROR "permission denied". Test runs with app user from R2DBC pool, not postgres superuser.

✅ **Containers are ready:**
1. `docker-compose up` starts:
   - `triage-service` on `http://localhost:8081/health`.
   - `clinical-assistant-service` on `http://localhost:8082/health`.
   - PostgreSQL 15 (separate databases).
   - Kafka 3.x.
   - Redis 7.x.
   - Alert receiver mock (HTTP POST endpoint).
2. All services reach readiness within 30 seconds.

✅ **Testing infrastructure:**
1. Testcontainers is configured for PostgreSQL and Kafka integration tests.
2. BlockHound is enabled in tests to detect blocking I/O on the event loop.
3. At least one integration test passes for each service (e.g., database connectivity).

---

## Acceptance

| Scenario | Expected Result |
|---|---|
| `./gradlew build` from each service root | Build succeeds on Java 21 without errors or deprecation warnings from Boot 3.5. |
| `docker-compose up` from the repo root | All services start and reach readiness within 30 seconds. |
| `curl http://localhost:8081/health/ready` (Triage) | HTTP 200 `{ "status": "UP" }`. |
| `curl http://localhost:8082/health/ready` (Assistant) | HTTP 200 `{ "status": "UP" }`. |
| PostgreSQL check: `psql -h localhost -U postgres -d vidacruz_triage -c "SELECT * FROM information_schema.tables"` | `AuditoriaClinica` table is listed. |
| Integration test: Try `UPDATE AuditoriaClinica SET ...` | Fails (RBAC or constraint). |
| GitHub Actions workflow on a PR | Runs build and unit tests; passes or lists failures. |

---

## Deliverables

- Both skeletons updated: `triage-service/build.gradle.kts` and `clinical-assistant-service/build.gradle.kts`.
- `docker-compose.yml` with five services (two apps + PostgreSQL + Kafka + Redis).
- `.github/workflows/ci.yml` (GitHub Actions workflow).
- `Dockerfile` for both services (Spring Boot layered JAR, Java 21 base image).
- Database migration files (`db/migration/V1__init.sql`).
- README with setup and run instructions.
- One sample integration test per service (database connectivity, event production).

---

## Risks

- **R-02 (EOL):** Boot 3.5.x has no free security patches after June 2026. Documented in ADR-10; plan Boot 4.x migration post-delivery.
- **R-05 (Spring AI drift):** Spring AI 1.1.x GA must be verified on spring.io at build time. Build fails with a clear error if not found.
- **Environment variable collision:** If the user has local `KAFKA_BOOTSTRAP_SERVERS` or `DATABASE_URL` set, docker-compose may fail. Document override in README.

---

## Implementation Notes

1. **Gradle version:** Use Gradle wrapper (`./ gradlew`); do not require local Gradle installation.
2. **Docker images:** Use official images (Spring Boot 3.5-compatible, openjdk:21-slim for Java base).
3. **Flyway:** Migrations run at startup (outside the request path, using JDBC startup hook).
4. **Health probes:** Use Spring Boot Actuator; configure readiness to check database connectivity.
5. **Logs:** Use SLF4J + Logback (default for Spring Boot); enable JSON logging for production (optional for now).

---

## Next Step

Change 2 (`event-driven-integration`) requires this change to be done: CI pipeline passes, docker-compose runs, services are ready.
