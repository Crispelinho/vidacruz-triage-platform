# OpenSpec Change 1: Platform Foundation — Technical Design

**Change ID:** `platform-foundation`  
**Component:** Both services  
**Date:** 2026-11-25

---

## Architecture Decisions

### 1. Build System: Gradle with Wrapper

**Decision:** Use Gradle 8.14.8 with the wrapper (`./ gradlew`) to avoid local version dependencies.

**Rationale:**
- Spring Boot 3.5 is compatible with Gradle 8.x (not 9.x, which requires Java 25).
- Gradle wrapper ensures CI and local builds use the same version.
- Multi-project build (root `build.gradle.kts` + subprojects).

**Trade-offs:**
- Maven is simpler for small projects but less flexible for multi-module builds.
- Gradle has a steeper learning curve but scales better.

---

### 2. Database: PostgreSQL per Service with Flyway Migrations

**Decision:** Each service has its own PostgreSQL database (separate containers). Flyway runs at startup (JDBC hook outside the request path).

**Rationale:**
- ADR-14: One database per service (Triage + Assistant separate).
- Flyway ensures schema consistency across deployments.
- JDBC hook for startup avoids blocking the request path.

**Trade-offs:**
- Schema evolution must be coordinated per service (not cross-service).
- No distributed transactions between services.

---

### 3. Event Stream: Kafka with 30-day Retention

**Decision:** Kafka topic `triaje.eventos` retains events for 30 days. Topic is auto-created on startup.

**Rationale:**
- Durable event log for event-driven architecture (DEC-03).
- 30-day retention allows rebuilding the Assistant projection (OD-3, change 9).
- Auto-create simplifies local setup.

**Trade-offs:**
- Zookeeper is a heavy dependency; Kafka KRaft mode is not yet stable in Confluent's images.

---

### 4. Data Storage: Redis for Projection (Assistant Only)

**Decision:** Redis stores the urgency projection (counts per level, last sequence per patient).

**Rationale:**
- Fast reads for `/asistente/estado-urgencias` (RF-04-B).
- Simple key-value store; no complex queries.
- TTL-based deduplication for idempotent consumers.

**Trade-offs:**
- Projection is eventually consistent (lags Kafka by seconds).
- Data loss on restart; rebuild from Kafka events (DER-09).

---

### 5. Health and Readiness Probes

**Decision:** Use Spring Boot Actuator with `/health/ready` checking database and Kafka connectivity.

**Rationale:**
- Standard for Kubernetes (liveness, readiness).
- Database and Kafka health determine readiness (services should not advertise "ready" if critical dependencies are down).
- Health probe is public (no authentication required).

**Configuration:**
```yaml
management.endpoints.web.exposure.include: health,readiness,liveness
management.endpoint.health.show-details: when-authorized
management.health.livenessState.enabled: true
management.health.readinessState.enabled: true
```

---

### 6. R2DBC for Async Database Access

**Decision:** Use Spring Data R2DBC for async (non-blocking) database access on the request path. Flyway uses JDBC (startup hook, outside request path).

**Rationale:**
- NFR-01: No blocking I/O on the event loop.
- R2DBC driver for PostgreSQL is production-ready.
- Flyway migrations run once at startup (JDBC, acceptable because it's outside the request path).

**Trade-offs:**
- pgvector starter (Spring AI) uses JDBC; must isolate on bounded elastic scheduler (DER-06, change 8).

---

### 7. Configuration via Environment Variables

**Decision:** All secrets and deployment-specific settings are injected via environment variables (12-factor app).

**Rationale:**
- Never commit secrets to git.
- Same image runs in all environments (local, staging, production).
- Docker Compose and Kubernetes both support environment injection.

**Key variables:**
- `SPRING_DATASOURCE_URL`, `_USERNAME`, `_PASSWORD`
- `SPRING_KAFKA_BOOTSTRAP_SERVERS`
- `SPRING_REDIS_HOST`, `_PORT`
- `TRIAGE_API_KEY`, `ASSISTANT_API_KEY`
- `SPRING_AI_PROVIDER`, `OLLAMA_BASE_URL`

---

### 8. CI/CD: GitHub Actions on Each PR

**Decision:** GitHub Actions runs `./gradlew build` and integration tests on every PR to `develop`.

**Rationale:**
- Catch regressions early.
- Prove platform changes (Gradle, Boot, Java version) work in CI before feature development.
- Fast feedback loop (5–10 minutes per PR).

**Workflow:**
1. Checkout code.
2. Set up JDK 21.
3. Run `gradlew build` for each service (unit tests included).
4. Start `docker-compose`.
5. Run integration tests (Testcontainers, Kafka, database).
6. Publish build logs if failed.

---

## Component Diagram

```
┌─────────────────────────────────────────────────────────────┐
│ Local Development / CI                                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐         │
│  │ Triage Service       │  │ Assistant Service    │         │
│  │ (Spring WebFlux)     │  │ (Spring WebFlux)     │         │
│  │ Port 8081            │  │ Port 8082            │         │
│  └──────────┬───────────┘  └──────────┬───────────┘         │
│             │                         │                     │
│      ┌──────┴──────────────────┬──────┘                     │
│      │                         │                            │
│  ┌───▼─────────────────────┐  │   ┌─────────────────────┐  │
│  │ vidacruz_triage         │  │   │ vidacruz_assistant  │  │
│  │ PostgreSQL              │  │   │ PostgreSQL + pgvec  │  │
│  │ Port 5432               │  │   │ Port 5433           │  │
│  └────────────────────────-┘  └──┬┘                     │  │
│                                  │                      │  │
│                                  │  ┌──────────────────┘  │
│                                  │  │                      │
│      ┌───────────────────────┐  │  │  ┌─────────────────┐ │
│      │ Kafka Cluster         │◄─┴──┤  │ Redis           │ │
│      │ Topic: triaje.eventos │     │  │ Port 6379       │ │
│      │ Port 29092            │     │  └─────────────────┘ │
│      └───────────────────────┘     │                      │
│                                    └──────────────────────┘│
└─────────────────────────────────────────────────────────────┘
```

---

## Deployment Layers

```
Application Layer
├── Triage Service (REST + WebFlux)
├── Clinical Assistant Service (REST + WebFlux)
└── Health/Readiness Probes

Data Access Layer
├── R2DBC (async requests)
├── JDBC (startup migrations via Flyway)
└── Kafka client (event producer/consumer)

Infrastructure Layer
├── PostgreSQL 15 (Triage DB)
├── PostgreSQL 15 (Assistant DB)
├── Kafka 3.x + Zookeeper
└── Redis 7.x
```

---

## Reactive Compliance (BlockHound)

**Decision:** Enable BlockHound in tests to detect blocking I/O on the event loop.

```java
// In test setup
@BeforeAll
static void setupBlockHound() {
    BlockHound.install();
}
```

**Benefit:** Catches accidental blocking calls (e.g., `.block()` on Mono, `Thread.sleep()`) during development.

---

## Startup Sequence

1. **Spring Boot initializes:**
   - Load `application.yml` and environment variables.
   - Validate configuration (fail fast if critical properties are missing).

2. **Flyway runs:**
   - Connect via JDBC (startup hook, outside request path).
   - Execute migrations in `db/migration/` folder.
   - Create tables (`AuditoriaClinica`, `OutboxEvento`, etc.).

3. **R2DBC connection pool initialized:**
   - Establish async connection pool to PostgreSQL.

4. **Kafka client initializes:**
   - Connect to Kafka broker.
   - Optionally subscribe to topics (for consumers in future changes).

5. **Redis client initializes:**
   - Connect to Redis (Assistant only).

6. **Health checks:**
   - Readiness probe checks database, Kafka, Redis connectivity.
   - Readiness returns 200 UP if all checks pass.

7. **Service listens:**
   - Triage on `http://localhost:8081`.
   - Assistant on `http://localhost:8082`.

---

## Error Handling

### Startup Validation

```java
@Component
public class StartupValidator {
    @PostConstruct
    public void validate() {
        // Fail fast if required properties are missing
        if (env.getProperty("triage.api-key") == null) {
            throw new IllegalArgumentException("triage.api-key is required");
        }
        if (env.getProperty("spring.kafka.bootstrap-servers") == null) {
            throw new IllegalArgumentException("Kafka bootstrap servers not configured");
        }
    }
}
```

### Request-Level Errors

```java
@RestController
@RequestMapping("/triage/pacientes")
public class TriageController {
    @PostMapping
    public Mono<ResponseEntity<?>> registerPatient(@RequestBody Mono<RegisterPatientRequest> req) {
        return req
            .flatMap(this::validateAndRegister)
            .onErrorResume(e -> handleError(e));
    }
    
    private Mono<ResponseEntity<?>> handleError(Throwable e) {
        if (e instanceof ValidationException) {
            return Mono.just(ResponseEntity.badRequest()
                .body(new ErrorResponse("VALIDATION_ERROR", e.getMessage())));
        }
        return Mono.just(ResponseEntity.status(500)
            .body(new ErrorResponse("INTERNAL_ERROR", "An unexpected error occurred")));
    }
}
```

---

## Security Considerations

### Secrets Management

- **Never commit secrets** to git (use `.gitignore`).
- **Environment variables** for all sensitive values.
- **No logging of secrets** (API keys, database passwords).
- **Scan images** for hardcoded credentials (e.g., `hadolint`).

### API Key Validation

- Implemented in change 10 (security).
- For now, `triage.api-key` and `assistant.api-key` are configuration properties.

### Database Access

- Flyway runs at startup with elevated privileges (migration user).
- Application user has limited permissions (SELECT, INSERT, UPDATE on specific tables; no DROP, no ALTER).
- Audit table rejects UPDATE and DELETE at the database level.

---

## Performance Baseline

No performance testing yet; targets are set for future changes:
- Request latency: < 500ms (p99).
- Throughput: > 100 requests/sec per service.
- Database query time: < 100ms (p99).

---

## Monitoring and Observability (Change 11)

For now, only basic health checks. Later changes add:
- Structured logging (JSON, correlation ID).
- Micrometer metrics (classification counts, escalations, LLM latency).
- OpenTelemetry tracing (optional, bonus).

