# VidaCruz — Requirements Analysis (D0)

Status: **Draft for review.** Companion to `requirements.md` (D1). IDs in this document refer to D1. No code, no commits.

## 1. Executive summary

VidaCruz needs a digital triage system with a clinical assistant, built as at least two reactive Java microservices (Triage and Clinical Assistant), integrated by Kafka, with resilience to LLM failure and operational observability. The brief is explicit about business rules (levels, SLAs, idempotency, audit, anti-hallucination) and about technology (WebFlux, R2DBC, Kafka, Resilience4j, Spring AI or LangChain4j, Docker, Kubernetes, Azure).

Main analysis findings:
- The clinical rules are deterministic and testable. The brief gives six patient examples (E1–E6) that serve as acceptance cases.
- The architecture is largely fixed by the brief, but several details are open (RF-06 channel, authentication, response shape for missing protocols). Each is resolved as a documented decision (DEC-xx in D1).
- Three technical risks need design attention: JDBC-based pieces in a reactive stack (Flyway, Spring AI's pgvector store), dual writes between database and Kafka (solved with an outbox), and the Spring Boot version constraint (Boot 3.x, end of open-source support, R-02).
- One clinical observation to record, not change: patients with heart rate 40–89 and no other criteria fall to NO_URGENTE (AMB-01).

## 2. Requirements inventory

Counts from D1: 6 functional (RF), 5 business rules (RN), 11 validations (VAL), 20 non-functional (NFR), 12 technical constraints (TC), 8 derived (DER), 7 deliverables (DL), 16 ambiguities (AMB), 15 decisions (DEC).

| Type | Items |
|---|---|
| EXPLICIT | RF-01…RF-06, RN-01…RN-05, VAL-01…VAL-10, NFR-01…NFR-18 (mixed mandatory/bonus), TC-01…TC-10, DL-01…DL-05 |
| DERIVED | VAL-11, NFR-19, NFR-20, TC-11, TC-12, DER-01…DER-08, DL-07 |
| PROPOSED | DEC-01…DEC-15 (recommendations; DEC-13 and DEC-15 accepted by the user, the rest proposed) |
| AMBIGUOUS | AMB-01…AMB-16 |

Each requirement's acceptance criterion and test type are in D1 §4–§15 and in D5 (traceability matrix, to be written).

## 3. Requirements per microservice

### 3.1 Triage Service
- **Responsibility:** patient registration, classification, state flow, SLA evaluation, escalation, audit, operational report, publication of EVT-01…EVT-04.
- **Use cases:** register patient (RF-01), change state (RF-02), read patient (RF-02), evaluate SLA (RF-03), produce report (RF-05).
- **APIs:** API-01, API-02, API-03, API-06.
- **Events produced:** EVT-01, EVT-02, EVT-03, EVT-04.
- **Events consumed:** none required. The service must not consume its own events for state.
- **Data:** Paciente, Clasificacion, AuditoriaClinica, OutboxEvento (D1 §11).
- **External dependencies:** PostgreSQL, Kafka. Notification channel for RF-06 (DEC-09).
- **Errors:** 400 validation, 404 unknown ID, 409 invalid transition, 500 unexpected (no details leaked).
- **Concurrency:** escalation and state changes are conditional updates; the evaluator is safe with several replicas (DEC-04).
- **Idempotency:** escalation (RN-03). Registration accepts an optional `Idempotency-Key` header with replay semantics (DEC-15, R-09). Without the header, each POST creates a patient, as the brief implies.
- **Must not:** call the Assistant (NFR-04); expose audit deletion (RN-04); compute the operations report in memory (RF-05).
- **Testing:** unit tests for RN-01, RN-02, RN-03 and validation; integration tests with PostgreSQL and Kafka (Testcontainers recommended, NFR-17).

### 3.2 Clinical Assistant Service
- **Responsibility:** RAG over the protocol corpus with citation validation (RN-05); maintain the urgency projection from `triaje.eventos` (RF-04 Part B).
- **Use cases:** answer a clinical question (RF-04A), report urgency state (RF-04B), ingest the corpus.
- **APIs:** API-04, API-05.
- **Events produced:** none.
- **Events consumed:** EVT-01…EVT-04. EVT-02 advances `pacienteSequence` for contiguity without changing counts (ADR-02/ADR-08, Spec C-05 §5.1).
- **Data:** DocumentoClinico, FragmentoDocumental (pgvector), EstadoUrgenciaProyeccion and ProcessedEvent (Redis) (D1 §11).
- **External dependencies:** LLM provider (API key from environment), pgvector database, Redis, Kafka.
- **Errors:** 400 empty question, 503 when LLM unavailable (NFR-03), 503 when projection unavailable (API-05).
- **Concurrency:** projection updates use per-patient sequence checks (DER-03); duplicates ignored via ProcessedEvent.
- **Must not:** call the Triage API to rebuild state (RF-04B); produce clinical content without a valid citation (RN-05).
- **Testing:** RAG tests with mocked LLM; projection rebuild from a fixture event stream; circuit breaker tests.

### 3.3 Responsibilities that must not move between services
- Classification and SLA logic stay in Triage. The Assistant only reads events.
- Protocol retrieval and citation validation stay in the Assistant. Triage never calls the LLM.
- Audit lives only in Triage.

## 4. API contracts

| ID | Method | Path | Service | Success | Errors | Side effects |
|---|---|---|---|---|---|---|
| API-01 | POST | /triage/pacientes | Triage | 201 | 400, 409 | audit, EVT-01 |
| API-02 | PATCH | /triage/pacientes/{id}/estado | Triage | 200 | 400, 404, 409 | audit, EVT-03 or EVT-04 |
| API-03 | GET | /triage/pacientes/{id} | Triage | 200 | 404 | none |
| API-04 | POST | /asistente/consulta | Assistant | 200 | 400, 503 | none |
| API-05 | GET | /asistente/estado-urgencias | Assistant | 200 | 503 | none |
| API-06 | GET | /triage/reportes/operacion | Triage | 200 | 400 | none |

Full request and response examples are in D1 §9. Authentication applies to all six (DEC-05). The brief is silent on pagination for API-06; it returns hourly buckets over the requested range, which is bounded by DEC-11 (range limit to be fixed in the ADR).

## 5. Business rules and validations

- **RN-01:** five-level decision table, first match wins, level 5 refines level 4 (D1 §5). Six acceptance cases E1–E6 verified by hand against the table: E1 CRITICO, E2 URGENTE, E3 URGENTE_2, E4 NO_URGENTE, E5 NO_URGENTE_2, E6 CRITICO.
- **RN-02:** SLA per level, measured from `fechaRegistro` to first `EN_CONSULTA`, with `ALTA` also ending evaluation.
- **RN-03:** escalate once; never escalate `EN_CONSULTA` or `ALTA`.
- **RN-04:** audit row for every state or flag change; no deletion endpoint; DB-level append-only (DER-04).
- **RN-05:** citation required; invalid citation rejected; no-match returns the fixed notice; retrieval gate before the LLM call.
- **Validations:** VAL-01…VAL-11 (D1 §6). Cross-field rule VAL-04 (diastolic < systolic) is easy to miss and must be tested.

**Edge matrix (summary):**

| Input | Expected |
|---|---|
| SpO2 = 90 | URGENTE (inclusive boundary of level 2, not CRITICO) |
| Temperature = 38.5 | URGENTE (level 2 starts at 38.5) |
| Temperature = 38.0 | URGENTE_2 |
| FC = 120 | URGENTE_2 (level 1 requires > 120) |
| FC = 40 | NO_URGENTE: FC 40–89 matches no level alone (AMB-01) |
| Elapsed = SLA exactly | Not escalated (AMB-04) |
| `ALTA → EN_CONSULTA` | 409 |
| Same state, e.g. `EN_CONSULTA → EN_CONSULTA` | 409 (DEC-08) |
| Admin complaint with different case or accents | Level 5 (DEC-01) |

## 6. Reactive architecture and concurrency

**Must be non-blocking:** HTTP handlers, R2DBC queries, Kafka producer and consumer, Redis projection reads and writes, scheduler work that touches the database, LLM HTTP calls (use the reactive client or an async adapter).

**Blocking risks identified:**

| Risk | Where | Mitigation |
|---|---|---|
| Flyway runs on JDBC | Startup of both services | Run migrations at startup only, outside the request path (TC-12) |
| Spring AI pgvector vector store uses JDBC | Assistant retrieval | Isolate on bounded elastic, or use an R2DBC-based store with pgvector SQL; decide in ADR (DER-06, R-03) |
| Spring AI ChatClient is blocking by default | LLM call | Wrap in `Mono.fromCallable(...).subscribeOn(boundedElastic())` or use the reactive model API if available; measure |
| Kafka reactive client | Publishing and consuming | Use Reactor Kafka or spring-kafka with reactive adapter; no blocking `send().get()` |
| Scheduler | SLA evaluator | Reactive pipeline with `Flux.interval`-style or scheduler that delegates to R2DBC |

Backpressure: consumers use bounded prefetch; the SLA evaluator processes candidates in bounded batches.
Timeouts: LLM call timeout (configured), Kafka send timeout, R2DBC acquisition timeout.
Cancellation: client disconnect cancels the LLM call; `Mono` cancellation must propagate.
Background execution: the SLA evaluator and the outbox publisher are background tasks; both must be restartable and idempotent.

**Testing:** BlockHound or an equivalent check on request threads (TST-RXN). Tests must fail when a blocking call runs on the event loop.

Note: using WebFlux does not make the application reactive by itself. Every adapter must be checked.

## 7. Data model

Summary of D1 §11:
- **Triage (PostgreSQL):** Paciente, Clasificacion, AuditoriaClinica (append-only), OutboxEvento, IdempotencyKey (DEC-15).
- **Assistant (PostgreSQL + pgvector):** DocumentoClinico, FragmentoDocumental.
- **Assistant (Redis):** EstadoUrgenciaProyeccion, ProcessedEvent (TTL).

Key indexes: `Paciente(estado, escalado)` for the evaluator; `AuditoriaClinica(pacienteId, timestamp)` and `(tipoEvento, timestamp)` for the report; `OutboxEvento(publicadoEn)` partial for the publisher.

Retention: audit is kept as long as the system exists (no deletion). Outbox rows are deleted after publication with a configurable retention. ProcessedEvent TTL must be at least the Kafka retention for the topic (DER-03).

Separation of stores: one PostgreSQL database per service (DEC-10). Redis is not used for the source of truth.

## 8. Kafka contracts

- Topic `triaje.eventos`, key `pacienteId`, at-least-once delivery.
- Envelope and four event types (EVT-01…EVT-04) in D1 §10 (DEC-03).
- Producer: Triage only. Consumers: Assistant (all four), alerts channel (EVT-02).
- Publication timing: after the state change is committed, through the outbox (DEC-14).
- Consumer idempotency: `eventId` set plus `pacienteSequence` comparison (DER-03).
- Retry policy: consumer retries with backoff, then dead-letter topic (proposed, DEC-03 extension).
- Schema evolution: additive within `schemaVersion`; breaking changes bump the version.

The Assistant can rebuild its state from events alone: EVT-01 gives level and registration, EVT-03 and EVT-04 give state changes. Count of active patients = registered minus discharged, grouped by level. This satisfies the requirement that the Assistant does not call the Triage API.

## 9. RAG design

Pipeline:
1. **Ingestion:** read protocol files at startup or on demand; extract text; chunk by section (`protocolo`, `seccion` as metadata).
2. **Embedding:** provider chosen by configuration (DEC-06).
3. **Storage:** pgvector, one row per chunk, metadata filter on `protocolo`.
4. **Retrieval:** top-k similarity search with a threshold (DEC-07). Threshold calibrated against the three acceptance questions.
5. **Relevance gate:** if no chunk passes the threshold, return the fixed notice. The LLM is not called.
6. **Context build:** only retrieved chunks are included in the prompt.
7. **Generation:** LLM call with timeout, wrapped by the circuit breaker.
8. **Citation validation:** every cited protocol must exist in the corpus and in the retrieved set; otherwise reject and return the fixed notice (RN-05).
9. **Response:** `respuesta` plus `fuentes` built from validated citations only.

**Responsibilities:**
- Spring AI or LangChain4j provides: embedding and chat clients, vector store abstraction, prompt templates.
- Our business logic provides: relevance gate, citation validation, fixed notice, circuit breaker and fallback, corpus ingestion rules, and the decision about when to call the LLM at all.

**Acceptance cases:**

| Question | Expected |
|---|---|
| Medidas iniciales crisis hipertensiva | Content from PROTO-001, `fuentes` contains PROTO-001 |
| Primer antibiótico en sepsis | Content from PROTO-002, `fuentes` contains PROTO-002 |
| Tratamiento diabetes tipo 2 | Fixed notice, `fuentes: []`, LLM not called |

## 10. SLA and notifications

**Evaluator (RF-03):**
- Scheduled job at a configurable interval (NFR-08).
- Selects `REGISTRADO` patients with `escalado = false` whose elapsed time exceeds their SLA.
- For each: conditional update sets `escalado = true`; if one row changed, write audit and outbox event EVT-02 in the same transaction.
- Replicas: the conditional update guarantees one winner per patient.
- Failures: a failed evaluation run is retried at the next interval; no partial escalation survives (transactional).

**Notification (RF-06):**
- Options considered: email (SMTP), webhook, message queue consumer, SMS.
- Recommended: outbox-driven webhook to a configured endpoint with retries and backoff (DEC-09). For a three-day test it can be exercised end to end with a mock receiver in docker-compose, without external accounts.
- Email is a valid alternative but needs a test SMTP server and adds configuration; SMS needs a paid provider.
- Failure handling: retries with exponential backoff, then alert is marked failed in the outbox and visible in logs and metrics.
- Each escalation produces at most one notification (DER-03).

## 11. Reports and SQL

RF-05 query outline (PostgreSQL):
1. Filter `AuditoriaClinica` for `tipoEvento = 'REGISTRO'` and `tipoEvento = 'ENTRADA_CONSULTA'` in the range, in UTC.
2. Compute first consultation entry per patient with `ROW_NUMBER() OVER (PARTITION BY pacienteId ORDER BY timestamp)` and keep row 1.
3. Join to the registration time; compute wait in minutes.
4. Group by `date_trunc('hour', registro)`; aggregate with `COUNT(*) FILTER (WHERE nivel = ...)` for each level.
5. Compute `AVG(wait)` and `percentile_cont(0.95) WITHIN GROUP (ORDER BY wait)` per hour.
6. Patients without consultation entry are excluded from wait metrics and included in the total (DEC-11).

Performance: indexes listed in §7. Check with `EXPLAIN ANALYZE` on fixture data (TST-RPT). Range limit to bound cost (proposed: 31 days, DEC-11 extension).

No in-memory processing of all records (brief).

## 12. Security and resilience

**Mandatory:**
- Authentication on `/triage/**` and `/asistente/**` (NFR-10). Recommended: API key header (DEC-05).
- No hardcoded credentials; LLM key from environment; application fails fast at startup if missing (DER-01).
- Input validation on all endpoints (NFR-11).
- Errors do not leak stack traces or SQL (DER-08).
- Logs must not contain clinical data beyond what is needed; no free text in metrics (NFR-19).
- Audit append-only (DER-04).

**Resilience:**
- Circuit breaker around LLM calls with timeout; fallback returns 503 `LLM_UNAVAILABLE` (NFR-03).
- Triage never depends on Assistant (NFR-04): no synchronous calls; events only.
- Retries with backoff on Kafka publication (through the outbox publisher).
- Timeouts on all outbound calls.
- Isolation: separate databases and separate containers per service (DEC-10).

**Recommended beyond mandatory:** rate limiting on the assistant endpoint; secret store in Azure Key Vault (documented, not required).

## 13. Observability

**Probes:** liveness and readiness through Actuator; readiness includes database, Kafka and Redis checks where relevant. LLM status should not make the Assistant unready (its fallback is intentional); report it as a metric instead.

**Logs:** structured JSON; correlation ID from header `X-Correlation-Id` or generated; propagated to Kafka headers (DER-07).

**Metrics (Micrometer):**
- `vidacruz.triage.classified` counter, tag `nivel` (5 values).
- `vidacruz.triage.escalations` counter.
- `vidacruz.llm.latency` timer, tag `outcome` (success, timeout, fallback).
- Kafka and database metrics from Micrometer binders.

**Cardinality rules:** no patient ID, no free text, no protocol section in tags. Protocol ID as a tag is acceptable only because the corpus is small (bounded).

**Tracing:** OpenTelemetry as bonus (NFR-16).

## 14. Infrastructure and deployment

**Local (docker-compose, one command):** PostgreSQL (two databases or two containers), Redis, Kafka (KRaft), Triage, Assistant, optional mock webhook receiver for RF-06.

**Images:** multi-stage Dockerfiles per service; non-root user; health check.

**Kubernetes:** Deployment, Service, ConfigMap, Secret (API key), liveness and readiness probes per service. Stateful dependencies (PostgreSQL, Kafka, Redis) may use managed services in Azure; the manifests must document which.

**Azure:** AKS or Azure Container Apps (NFR-14). Container Apps is simpler for a three-day window; AKS demonstrates Kubernetes directly. Decision in ADR (to add to D3).

**IaC:** optional (bonus). If added, Bicep or Terraform; documented as complementary.

Risk: Azure access and quotas at delivery time (R-06). Manifests are delivered regardless; the URL depends on access.

## 15. Test strategy

| Layer | Scope | Tools | Mandatory cases |
|---|---|---|---|
| Unit | RN-01, RN-02, RN-03, validations, citation validation, relevance gate | JUnit 5, StepVerifier | E1–E6, boundaries, transitions |
| Integration (persistence) | Repositories, audit append-only, indexes | Testcontainers PostgreSQL | Audit not updatable, conditional escalation update |
| Integration (Kafka) | Outbox publishing, consumer idempotency | Testcontainers Kafka | One event per transition, duplicate ignored |
| API | Contracts and errors | WebTestClient | 400, 404, 409, 503, 401 |
| Security | API key enforcement, no secrets in config | WebTestClient | Missing or wrong key → 401 |
| RAG | Three acceptance questions, invalid citation, no-match | Mock LLM | No LLM call on no-match |
| Resilience | Circuit breaker, fallback 503, triage independence | Mock LLM failures | Triage still responds with Assistant down |
| Concurrency | Escalation single winner | Parallel test | One event for N concurrent evaluations |
| Reactive | No blocking calls | BlockHound | Request path |
| End-to-end | Full flow across both services | docker-compose | Register → escalate → alert; register → query assistant |

Testcontainers is recommended (NFR-17). Coverage target is a team decision; not set by the brief.

## 16. ADR candidates

| ADR | Decision | Source |
|---|---|---|
| ADR-01 | Service decomposition (Triage and Assistant, no further split) | TC-09, §3 |
| ADR-02 | Kafka messaging and event envelope | DEC-03, TC-04 |
| ADR-03 | Transactional outbox | DEC-14, R-07 |
| ADR-04 | Vector store choice (pgvector) and JDBC isolation | DER-06, TC-11, R-03 (no DEC covers pgvector) |
| ADR-05 | LLM provider and Spring AI vs LangChain4j | DEC-06 |
| ADR-06 | RF-06 notification channel | DEC-09 |
| ADR-07 | Authentication (API key vs JWT) | DEC-05 |
| ADR-08 | Projection store for urgency state (Redis) | DEC-10 |
| ADR-09 | Degraded mode and circuit breaker | DEC-07, NFR-03 |
| ADR-10 | Spring Boot 3.x line and end-of-support risk | DEC-12, R-02 |
| ADR-11 | Azure target: Container Apps or AKS | §14 |
| ADR-12 | Idempotency-Key replay for registration | DEC-15, R-09 |
| ADR-13 | SLA evaluation mechanism (scheduled job, conditional update) | DEC-04 |
| ADR-14 | Database ownership: one PostgreSQL per service | DEC-10 |

The brief requires at least two; this plan has fourteen candidates (ADR-13 and ADR-14 added after the D3 review). Full content goes in D3.

## 17. Traceability summary

Chain: source section → requirement ID → capability → OpenSpec change → task → code → test → evidence. Full matrix is D5. Coverage rule: every EXPLICIT requirement appears in at least one change and one test before completion.

## 18. Ambiguities and risks

**Ambiguities:** AMB-01…AMB-16 (D1 §14). Each has a documented decision.

**Clinical risk to record:** AMB-01 (heart rate 40–89 with no other criteria → NO_URGENTE). The rule is kept as written. Escalation is a clinical question for the hospital, not for this system to decide.

**Technical risks:**

| ID | Risk | Impact | Probability | Mitigation |
|---|---|---|---|---|
| R-01 | FC 40–89 gap | Clinical | Medium | Document |
| R-02 | Boot 3.5 open-source support ended | Security | High | Plan upgrade path; ADR-10 |
| R-03 | JDBC pieces (Flyway, pgvector) in reactive path | Performance | Medium | Isolate; measure; ADR-04 |
| R-04 | LLM latency or cost during evaluation | Demo reliability | Medium | Timeouts, circuit breaker, mock |
| R-05 | Spring AI 1.x version drift | Build | Low | Pin GA in build |
| R-06 | Azure access at delivery | Deployment | Medium | Manifests first |
| R-07 | Dual write DB and Kafka | Consistency | High without outbox | Outbox (DEC-14) |
| R-08 | Duplicate or out-of-order Kafka messages | Projection errors | Medium | Sequence and eventId dedupe |
| R-09 | Duplicate patients on client retry (no key in brief) | Duplicate patients | Low | Optional Idempotency-Key with replay (DEC-15) |
| R-10 | Anti-hallucination failure in LLM output | Patient safety | Medium | Citation validation; no-match gate |

## 19. Three-day backlog (summary)

Full backlog is D6. Summary:

- **Day 1 (P0 foundation):** repositories on Boot 3.x, Docker Compose skeleton, Triage classification and registration with validations and Idempotency-Key, E1–E6 tests, state machine with audit, outbox and EVT-01/EVT-03/EVT-04.
- **Day 2 (P0 core):** SLA evaluator and escalation (EVT-02), Assistant ingestion and RAG with gate, citation validation and circuit breaker, projection consumer and API-05, report SQL (API-06).
- **Day 3 (P0 close + P1):** RF-06 channel with mock receiver, API key security, metrics and probes, README, ADRs, Kubernetes manifests, Azure deployment attempt, final test run, commit history.

P2 (OpenTelemetry, IaC) only if time remains.

## 20. Final compliance checklist

- [ ] RF-01 to RF-06 covered by requirements, tests and endpoints.
- [ ] RN-01 to RN-05 formalized and tested, including E1–E6.
- [ ] All VAL rules enforced with 400 and field name.
- [ ] Four Kafka events defined and published through outbox.
- [ ] Triage works with Assistant down (NFR-04 test).
- [ ] RAG: three acceptance questions pass; no-match never calls LLM.
- [ ] Circuit breaker: 503 fallback tested.
- [ ] Report uses window functions and aggregates in SQL.
- [ ] Authentication on protected endpoints; no hardcoded secrets.
- [ ] Probes, structured logs, correlation ID, Micrometer metrics with bounded cardinality.
- [ ] docker-compose runs everything with one command.
- [ ] Kubernetes manifests and Azure target documented; deployment status stated honestly.
- [ ] At least two ADRs (plan lists twelve candidates).
- [ ] README with architecture diagram and decisions.
- [ ] Every mandatory requirement has an acceptance criterion and a test (D5).
- [ ] No mandatory requirement is marked done without evidence.
