# OpenSpec Change 1: Platform Foundation — Tasks

**Change ID:** `platform-foundation`  
**Component:** Both services  
**Date:** 2026-11-25  
**Estimated Time:** 4–6 hours  
**Status:** Ready to start

---

## Task Checklist

### Phase 1: Gradle Configuration (1–2 hours)

**T1.1: Update Triage Service Gradle Build**
- [ ] Edit `triage-service/build.gradle.kts`
- [ ] Set `org.springframework.boot` plugin to version `3.5.16`
- [ ] Update `spring-cloud-dependencies` to `2025.0.3`
- [ ] Update all Spring dependencies to latest 3.5.x line
- [ ] Set Java toolchain to 21
- [ ] Add R2DBC PostgreSQL driver: `org.postgresql:r2dbc-postgresql:1.0.5.RELEASE`
- [ ] Add Flyway for PostgreSQL: `org.flywaydb:flyway-core:9.22.3` and `flyway-database-postgresql:9.22.3`
- [ ] Add Spring Kafka: `org.springframework.kafka:spring-kafka`
- [ ] Add Testcontainers (test scope): `postgresql:1.19.8`, `kafka:1.19.8`
- **Verification:** `./gradlew build` from `triage-service/` succeeds without errors or deprecation warnings.

**T1.2: Update Assistant Service Gradle Build**
- [ ] Edit `clinical-assistant-service/build.gradle.kts`
- [ ] Repeat T1.1 steps (same Boot/Cloud versions)
- [ ] Add Spring AI: `org.springframework.ai:spring-ai-spring-boot-starter:1.1.1`
- [ ] Add pgvector starter: `org.springframework.ai:spring-ai-pgvector-store-spring-boot-starter:1.1.1`
- [ ] Add PostgreSQL JDBC driver: `org.postgresql:postgresql:42.7.1`
- [ ] Add Redis: `org.springframework.data:spring-data-redis`, `io.lettuce:lettuce-core:6.3.2.RELEASE`
- **Verification:** `./gradlew build` from `clinical-assistant-service/` succeeds.

**T1.3: Create Root Gradle Build**
- [ ] Create `build.gradle.kts` at repo root
- [ ] Configure Java 21 toolchain at root level
- [ ] Configure subprojects block with shared repositories and dependencies
- [ ] Ensure both services reference the shared configuration
- **Verification:** `./gradlew projects` lists both services.

---

### Phase 2: Database Schema and Migrations (1 hour)

**T2.1: Create Triage Service Flyway Migrations**
- [ ] Create `triage-service/src/main/resources/db/migration/`
- [ ] Create `V1__init.sql` with:
  - `AuditoriaClinica` table (audit-only, no UPDATE/DELETE)
  - `OutboxEvento` table (for change 2)
  - `IdempotencyKey` table (for change 3)
  - Indexes for query performance
  - RBAC grants (audit table readonly for app user)
- **Verification:** Schema loads without errors; `SELECT * FROM AuditoriaClinica` works; `UPDATE AuditoriaClinica` fails.

**T2.2: Create Assistant Service Flyway Migrations**
- [ ] Create `clinical-assistant-service/src/main/resources/db/migration/`
- [ ] Create `V1__init.sql` with:
  - `DocumentoClinico` table (protocol metadata)
  - `FragmentoDocumental` table (chunks + embeddings)
  - pgvector extension load
  - IVFFLAT index on embeddings
- **Verification:** Schema loads; pgvector extension is active; embedding column accepts vectors.

---

### Phase 3: Application Configuration (30 minutes)

**T3.1: Triage Application Properties**
- [ ] Create `triage-service/src/main/resources/application.yml`
- [ ] Configure Spring WebFlux base path: `/api`
- [ ] Configure R2DBC (PostgreSQL connection pool)
- [ ] Configure Kafka bootstrap servers
- [ ] Configure Flyway
- [ ] Configure Actuator endpoints (health, readiness, liveness)
- [ ] Configure SLA defaults (CRITICO=5, URGENTE=10, URGENTE_2=30, NO_URGENTE=60, NO_URGENTE_2=120)
- **Verification:** `spring.webflux.base-path` is `/api`; health endpoint responds.

**T3.2: Assistant Application Properties**
- [ ] Create `clinical-assistant-service/src/main/resources/application.yml`
- [ ] Same as T3.1, plus:
  - Spring AI configuration (provider, Ollama base URL)
  - Redis configuration
  - R2DBC (separate database URL)
- **Verification:** Redis connection is configured; AI provider is set to Ollama.

**T3.3: Create docker-compose.yml**
- [ ] At repo root, create `docker-compose.yml` with five services:
  - `triage-service` (image built from `triage-service/Dockerfile`)
  - `clinical-assistant-service` (image built from `clinical-assistant-service/Dockerfile`)
  - `triage-db` (PostgreSQL)
  - `assistant-db` (PostgreSQL, separate)
  - `kafka` (Kafka broker)
  - `zookeeper` (Kafka dependency)
  - `redis` (Redis)
  - `alert-receiver` (mock webhook endpoint)
- [ ] Environment variables passed to services
- [ ] Health checks for each service
- [ ] Volumes for data persistence
- **Verification:** `docker-compose up -d` succeeds; all services reach `healthy`.

---

### Phase 4: Container Images (1 hour)

**T4.1: Create Dockerfile for Triage Service**
- [ ] Create `triage-service/Dockerfile`
- [ ] Multi-stage build (builder → runtime)
- [ ] Builder stage: compile with `./gradlew build`
- [ ] Runtime stage: copy JAR, set JVM flags (`-XX:+UseG1GC -Xmx512m`)
- [ ] Expose port 8081
- [ ] Health check command
- **Verification:** `docker build -t triage-service . ` succeeds; container starts and responds to health probe.

**T4.2: Create Dockerfile for Assistant Service**
- [ ] Create `clinical-assistant-service/Dockerfile`
- [ ] Same as T4.1
- [ ] Expose port 8082
- **Verification:** `docker build -t clinical-assistant-service .` succeeds.

**T4.3: Create Mock Alert Receiver**
- [ ] Create `test-support/alert-receiver/` directory
- [ ] Create a simple Spring Boot app (or Express.js) that:
  - Listens on `POST /webhooks/escalation`
  - Logs the payload
  - Responds with 200 OK
- [ ] Create `test-support/alert-receiver/Dockerfile`
- [ ] Image pulls from `docker-compose.yml`
- **Verification:** `curl -X POST http://alert-receiver:8080/webhooks/escalation -H "Content-Type: application/json" -d '{}' ` returns 200.

---

### Phase 5: CI/CD Workflow (30 minutes)

**T5.1: Create GitHub Actions Workflow**
- [ ] Create `.github/workflows/ci.yml`
- [ ] Trigger on PR to `develop` and push to `develop`
- [ ] Steps:
  1. Checkout code
  2. Set up JDK 21 (Temurin)
  3. Build Triage: `cd triage-service && ./gradlew build`
  4. Build Assistant: `cd clinical-assistant-service && ./gradlew build`
  5. Start docker-compose: `docker-compose up -d`
  6. Wait for health: `sleep 30 && curl -f http://localhost:8081/health/ready && curl -f http://localhost:8082/health/ready`
  7. Run integration tests (triage and assistant)
  8. Publish logs if failed
- **Verification:** Trigger a PR; workflow runs and shows build/test results.

---

### Phase 6: Integration Tests (1–2 hours)

**T6.1: Triage Service Integration Test**
- [ ] Create `triage-service/src/test/java/.../DatabaseConnectivityTest.java`
- [ ] Use `@Testcontainers` with PostgreSQL container
- [ ] Test:
  - R2DBC connection pool creation
  - Flyway migration execution
  - SELECT from `AuditoriaClinica`
  - Confirm UPDATE/DELETE fail (RBAC)
- **Verification:** Test passes; audit table is append-only.

**T6.2: Triage Service Kafka Integration Test**
- [ ] Create `triage-service/src/test/java/.../KafkaConnectivityTest.java`
- [ ] Use `@Testcontainers` with Kafka container
- [ ] Test:
  - Kafka producer sends a test message to `triaje.eventos`
  - Message is received by a test consumer
- **Verification:** Test passes; Kafka is functional.

**T6.3: Assistant Service Integration Test**
- [ ] Create `clinical-assistant-service/src/test/java/.../DatabaseConnectivityTest.java`
- [ ] Use `@Testcontainers` with PostgreSQL container
- [ ] Test:
  - R2DBC connection
  - Flyway migration (pgvector extension load)
  - CREATE and SELECT on `FragmentoDocumental`
- **Verification:** Test passes; pgvector is loaded.

~~**T6.4: Assistant Service Redis Integration Test**~~ **[REMOVED — belongs to change 9: emergency-state-projection]**
- Redis testing is out of scope for platform-foundation.
- Redis is used only by the Assistant for urgency projection (change 9).
- T6.4 will be implemented in change 9 when the projection feature is built.

**T6.5: BlockHound Setup**
- [ ] Add BlockHound dependency (test scope)
- [ ] Create a test base class that calls `BlockHound.install()`
- [ ] Extend base class in all integration tests
- **Verification:** Tests run with BlockHound enabled; no blocking I/O detected.

---

### Phase 7: Documentation and Verification (30 minutes)

**T7.1: Create README**
- [ ] Create `README.md` at repo root with:
  - Project overview (VidaCruz triage platform)
  - Quick start: `docker-compose up -d`
  - Architecture diagram
  - Technologies used (Boot 3.5, Kafka, pgvector, etc.)
  - Known issues (Boot 3.5 EOL, documented in ADR-10)
  - Development workflow (fork, local run, tests, PR)
- **Verification:** README is clear and helpful; someone new can follow quick start and run services.

**T7.2: Verify All Acceptance Criteria**
- [ ] Run `./gradlew build` from each service → passes
- [ ] Run `docker-compose up -d` → all services healthy within 30s
- [ ] `curl http://localhost:8081/health/ready` → 200 UP
- [ ] `curl http://localhost:8082/health/ready` → 200 UP
- [ ] PostgreSQL check: audit table exists and rejects UPDATE/DELETE
- [ ] GitHub Actions workflow on a test PR → passes build and tests
- **Verification:** All acceptance criteria pass.

**T7.3: Create Change Summary**
- [ ] Update `openspec/changes/platform-foundation/DONE.md` with:
  - Date completed
  - Commits hash
  - Files changed
  - Verification results
  - Blockers (if any)
- **Verification:** Summary is clear and links to commits.

**T7.4: Verify Production Restriction in README**
- [ ] Edit `README.md` to document:
  - **Clear warning:** "This baseline is for development and evaluation only. Production deployments require either VMware Spring Support contract for Boot 3.5.x or migration to Boot 4.1.x+ (open-source support)."
  - Scope restrictions explicitly stated
  - Production path options documented
  - Reference to ADR-10 for details
- **Verification:** README explicitly states production restriction and both path options (commercial support for 3.5.x OR migration to 4.1.x).

---

## Task Dependencies

```
T1.1 (Triage Gradle) ──┐
                       ├─→ T2.1 (Triage migrations) ──┐
T1.2 (Assistant Gradle) ┤                             ├─→ T3.1/T3.2 (Config) ──→ T4.1/T4.2 (Dockerfile) ──┐
                       └─→ T2.2 (Assistant migrations)─┘                                                     ├─→ T5.1 (CI/CD)
T1.3 (Root Gradle) ────────────────────────────────────────────────────────────────────────────────────────┘
                                                                                                      ↓
                                                                                              T6.1/T6.2/T6.3/T6.4
                                                                                                      ↓
                                                                                              T7.1/T7.2/T7.3
```

**Critical path:** 7 hours (longest chain).  
**Parallelizable:** T1.1 and T1.2, T2.1 and T2.2, T4.1 and T4.2, T6.1 and T6.3 can run in parallel.  
**Note:** T6.4 (Redis test) removed; it belongs to change 9 (emergency-state-projection). Task count revised from 15 to 14.

---

## Success Criteria

✅ All acceptance criteria from `proposal.md` are met:
1. Both services build on Java 21 with Boot 3.5.16.
2. `docker-compose up` starts all services; all reach healthy within 30s.
3. Health probes respond 200 UP.
4. Audit table rejects UPDATE/DELETE.
5. GitHub Actions workflow passes.

✅ No blocking I/O detected by BlockHound.

✅ README is clear and helpful.

✅ At least one integration test per service passes.

---

## Known Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Java 25 / Gradle 9.x incompatibility with Boot 3.5 | Use Java 21 (LTS until 2031); Gradle 8 is proven. Tested and working. |
| Boot 3.5 EOL (June 2026 passed) | Documented in ADR-10; plan Boot 4.1 migration post-delivery. |
| Docker image size | Multi-stage build; lightweight base image (openjdk:21-slim). |
| Kafka Zookeeper coupling | Standard for Confluent 7.5; KRaft mode is optional future upgrade. |
| Local port conflicts | docker-compose uses isolated network; ports 8081, 8082, 5432, 5433, 29092, 6379 must be free locally. |

