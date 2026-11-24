# VidaCruz — Implementation Roadmap (D6)

Status: **draft for review.** Plan only: nothing here is implemented. Each task becomes a row of the change's `tasks.md` when its OpenSpec change is opened.
Sources: `docs/openspec-roadmap.md` (D4, changes 1…12), `docs/traceability-matrix.md` (D5, requirement rows), `docs/requirements.md` (D1), `docs/adr-roadmap.md` (D3), `deliverables-plan.md` §3 (D6 content plan).

Branch context: part of the D3–D6 planning set proposed in PR #3 (https://github.com/Crispelinho/vidacruz-triage-platform/pull/3). It is not integrated into `develop`.

## 1. Open blocker: the time window

`deliverables-plan.md` §1 (D-4) records that the brief is dated 2026-10-29 with a 3-day window, and that today is 2026-11-24. The developer has not confirmed whether the three days start now. This roadmap is written as three working days (Day 1 to Day 3). If the window has already closed, the P2 tasks and some P1 tasks must be dropped or moved, and the developer must decide which. No task is removed here without that decision.

## 2. Priorities

- **P0**: required by the brief. Without it the delivery is incomplete.
- **P1**: robustness. Required for the quality of the delivery, cut only if P0 is at risk.
- **P2**: extras. Done only if P0 and P1 are complete.

Each task has an ID (T-xx), the change it belongs to (D4 number), the requirement IDs it satisfies, and a verifiable result. A task is done only when its result is shown by a test or by evidence.

## 2.1 Decision gates (ADRs accepted before the tasks that depend on them)

Architecture decisions are prerequisites, not a Day 3 task. Each ADR is written and accepted (status `Accepted`, with two alternatives and a verification step) before the **change** that requires it starts, which is the first task of that change in D4 §3. The gates are listed in the exact acceptance order of `adr-roadmap.md` §3.3. Writing the ADRs is the developer's responsibility and happens in this order, before the gate it unlocks; it is not scheduled on any day.

| Gate | ADR (D3 §3.3 order) | Must be accepted before | Note |
|---|---|---|---|
| G-01 | ADR-01 service decomposition | T-01 (change 1) | First ADR of the set |
| G-02 | ADR-10 Boot 3.5.16 and Java 21 | T-01 (change 1) | Official support evidence in §9 (R-02) |
| G-03 | ADR-14 one PostgreSQL per service | T-01 (change 1) | Required by change 1, not by T-03 |
| G-04 | ADR-03 transactional outbox | T-04 (change 2) | |
| G-05 | ADR-02 Kafka and envelope | T-04 (change 2) | |
| G-06 | ADR-13 SLA evaluation mechanism | T-11 (change 5) | |
| G-07 | ADR-07 authentication | T-05 (change 3) | API key proposed in DEC-05 |
| G-08 | ADR-12 Idempotency-Key replay | T-05 (change 3) | Required by change 3, not by T-07 |
| G-09 | ADR-04, ADR-05, ADR-09 vector store, LLM provider, circuit breaker | T-14 (change 8) | ADR-05 compares against official Spring AI and LangChain4j docs |
| G-10 | ADR-08 projection store | T-16 (change 9) | Linked to OD-3 |
| G-11 | ADR-06 notification channel | T-18 (change 7) | Confirm the mock receiver in docker-compose is acceptable |
| G-12 | ADR-11 Azure target | T-22 (change 12) | Blocked by R-06 if access is unavailable |

A gate that is not met blocks the whole change, not only its first task. The blocker is recorded in the README (T-25), not worked around.

## 3. Day 1: foundation, ingestion and the Triage chain

| ID | Pri | Change | Task | Components | Requirements | Verifiable result |
|---|---|---|---|---|---|---|
| T-01 | P0 | 1 | Set the baseline in both services: Spring Boot 3.5.16, Java 21, Gradle 8.14.8, Spring Cloud 2025.0.3, Spring AI 1.1.1 (assistant). Make the Gradle wrappers executable and pin them. | both `build.gradle.kts`, wrappers | TC-01, TC-09, TC-11, NFR-15, AMB-13 | `./gradlew build` passes on Java 21 in both services (CI log) |
| T-02 | P0 | 1 | Create Testcontainers configuration for PostgreSQL (with pgvector in the assistant) and Kafka | both test sources | NFR-17, NFR-15 | An integration test starts both containers and passes |
| T-03 | P0 | 1 | Create the Triage schema with Flyway (patients, audit, state history, outbox, idempotency key store) and make the audit table reject UPDATE and DELETE | triage persistence | TC-12, DER-04, RN-04 | Integration test shows UPDATE and DELETE on audit are rejected |
| T-04 | P0 | 2 | Implement the outbox writer and the publisher; define the envelope and EVT-01…EVT-04 payloads | triage (outbox), shared contract | EVT-01…EVT-04, DER-02, TC-04, TC-08, NFR-02, NFR-04 | An outbox row exists if and only if its state change committed (forced rollback test). With the Assistant stopped, registration, transitions and escalation still succeed, and Triage makes no synchronous HTTP call to the Assistant (NFR-04 test) |
| T-05 | P0 | 3 | Implement `POST /triage/pacientes` (WebFlux, R2DBC) with VAL-01…VAL-11, RN-01 levels and RN-02-A SLA | triage | RF-01, RN-01, RN-02-A, VAL-01…VAL-11, API-01, NFR-01, NFR-11, DER-05 | E1…E6 classify correctly; each VAL rule has a test |
| T-06 | P0 | 3 | Add the registration audit (RN-04-R) and the EVT-01 outbox row in the same transaction | triage | RN-04-R, EVT-01, DER-02 | One transaction test: patient, audit and outbox row commit together or not at all |
| T-07 | P0 | 3 | Implement `Idempotency-Key` replay (24 h TTL) and the 409 on a reused key with a different body | triage | DEC-15, API-01 | Replay returns the original response; a different body returns 409 |
| T-08 | P0 | 3 | Standard error body `{code, message, field?, correlationId}` for all endpoints | triage | NFR-20, DER-08 | Test for 400, 404, 409 responses shows the body |

Day 1 entry: gates G-01, G-02 and G-03 are accepted before T-01 starts; G-04 and G-05 before T-04; G-06 before T-11; G-07 and G-08 before T-05. The ADR writing is not a task of any day (see §2.1).

Day 1 exit: T-01 to T-08 pass in CI.

## 4. Day 2: Triage lifecycle, SLA, reporting, Clinical Assistant

| ID | Pri | Change | Task | Components | Requirements | Verifiable result |
|---|---|---|---|---|---|---|
| T-09 | P0 | 4 | Implement `PATCH /triage/pacientes/{id}/estado` with the three valid transitions, 404 for unknown ID and 409 for any other change | triage | RF-02, AMB-06, API-02 | `EN_CONSULTA` transition emits exactly one EVT-03; `ALTA` transition emits exactly one EVT-04; an invalid transition emits no event. State, history, audit and outbox row commit or roll back together |
| T-10 | P0 | 4 | Implement `GET /triage/pacientes/{id}` with `tiempoEnEstadoMinutos` | triage | RF-02, API-03 | Value matches a fixture |
| T-11 | P0 | 5 | Implement the SLA evaluator as a scheduled job with a conditional update (`escalado = false AND estado = 'REGISTRADO'`); the interval and SLA values are configuration | triage | RF-03, RN-02-B, RN-03, NFR-08, DER-01, DEC-04, AMB-04 | Overdue patient escalated once; second evaluation is a no-op; two instances produce one escalation |
| T-12 | P0 | 5 | Write the escalation audit row and EVT-02 in the same transaction as the escalation | triage | RF-03, EVT-02 | Transaction test shows both or neither |
| T-13 | P0 | 6 | Implement `GET /triage/reportes/operacion` in SQL (hourly UTC buckets, average and p95 per AMB-05 and DEC-11) with the indexes of D1 §11 | triage | RF-05, API-06, AMB-05, DEC-11 | Fixture matches hand computation, including a patient with no consultation; execution plan uses the indexes; `desde` later than `hasta` returns 400 |
| T-14 | P0 | 8 | Implement the RAG flow: corpus (PROTO-001, PROTO-002, PROTO-003), retrieval with a relevance threshold, LLM answer with validated citations, fixed notice when no evidence exists | assistant | RF-04-A, RN-05, API-04, TC-05, TC-06, TC-07, AMB-08, AMB-16 | PROTO queries return validated citations; diabetes query returns the notice with `fuentes: []` |
| T-15 | P0 | 8 | Put the Resilience4j circuit breaker on the LLM call with a 503 fallback; keep the JDBC vector store off the event loop | assistant | NFR-03, NFR-01, DER-06, TC-05 | Test with LLM stubbed to fail: breaker opens and endpoint returns 503 |
| T-16 | P0 | 9 | Implement the consumer of EVT-01…EVT-04 and the emergency projection with the idempotent guard (`pacienteSequence` and eventId) | assistant | RF-04-B, API-05, DEC-06, NFR-02, DER-03, AMB-14 | Replay does not double count; a request makes no call to Triage (Triage unreachable test) |
| T-17 | P0 | 9 | Implement `GET /asistente/estado-urgencias` | assistant | API-05 | Counts match a replayed event sequence |
| T-18 | P0 | 7 | Implement the alert consumer of EVT-02 with the delivery attempts table and the webhook channel (chosen in ADR-06) | triage | RF-06, AMB-10, DEC-09, DER-03 | Duplicate EVT-02 produces one notification; failed delivery retried with backoff |

Day 2 exit: T-09 to T-18 pass; the full acceptance cases E1…E6 and the PROTO queries are green.

## 5. Day 3: security, observability, delivery, documentation

| ID | Pri | Change | Task | Components | Requirements | Verifiable result |
|---|---|---|---|---|---|---|
| T-19 | P0 | 10 | Implement API key authentication on `/triage/**` and `/asistente/**` (ADR-07), key from environment variable | both | NFR-10, NFR-09, AMB-09, DEC-05 | Unauthenticated request rejected on every protected endpoint (test per endpoint) |
| T-20 | P0 | 11 | Health, readiness and liveness probes; readiness rules of C-11 (LLM not in readiness; broker outage is degraded) | both | NFR-05, NFR-06, DER-07 | Readiness fails when DB is down; stays up when broker or LLM is down |
| T-21 | P0 | 11 | Micrometer metrics: classification counts per level, escalations, LLM latency, DB and Kafka metrics; no high-cardinality labels | both | NFR-07, NFR-19 | Metric endpoint shows each metric; no patient ID in labels |
| T-22 | P0 | 12 | `Dockerfile` per service and `docker-compose.yml` that starts both services with PostgreSQL, Kafka, Redis and a webhook mock | both | NFR-12 | `docker compose up` starts all services; health checks pass |
| T-23 | P0 | 12 | Kubernetes manifests: Deployment, Service, probes, ConfigMap, Secret | both | NFR-13 | Manifests validate with a schema check (evidence in the change) |
| T-24 | P0 | 12 | Azure deployment (AKS or Container Apps, ADR-11) or a documented blocker | infra | NFR-14, AMB-11, R-06 | Deployment URL, or a blocker record that names R-06 |
| T-25 | P0 | 12 | README with run instructions, decisions index (ADRs), and the blocker list | docs | NFR-18 (process), D3 | README instructions run on a clean checkout |

Day 3 exit: P0 complete or each blocked item recorded with its reason.

## 6. P1: robustness (only after P0 passes)

| ID | Change | Task | Requirements | Verifiable result |
|---|---|---|---|---|
| T-27 | 2, 5 | Outbox retry and publisher backoff on broker outage | EVT-01…EVT-04, DER-02 | Broker stopped: events delayed, none lost (test) |
| T-28 | 10 | Secrets scan on the repository and image | NFR-09 | Scan report with no secret found |
| T-29 | 11 | Correlation ID propagated via HTTP and Kafka headers | NFR-06, DER-07 | Trace or log line carries the same ID from request to event |
| T-30 | 1 | Startup validation of configuration (SLA values, interval, LLM key presence) | DER-01, NFR-08, NFR-09 | Application refuses to start with a missing key (test) |

## 7. P2: extras (only after P0 and P1)

| ID | Change | Task | Requirements | Verifiable result |
|---|---|---|---|---|
| T-31 | 11 | OpenTelemetry tracing | NFR-16 (bonus) | A trace spans HTTP request and EVT publication |
| T-32 | 12 | Infrastructure as code for the Azure target | NFR-14 (extra) | IaC plan runs without error |

## 8. Scope cut order if time runs short

Cut in this order, first cut first:
1. P2 (T-31, T-32)
2. P1 tasks that no P0 acceptance test needs: T-29, then T-27 and T-28
3. T-21 is reduced to the metrics named in NFR-07 (no extra metrics); T-20 keeps its readiness rules

P0 tasks are not cut. If a P0 task is blocked, its blocker is recorded in T-24 or in the README, with the reason.

## 9. Risks that affect the plan

- **R-06 / AMB-11:** Azure access may be unavailable. T-24 records the blocker instead of claiming a deployment.
- **R-02 / AMB-12:** Verified against the official Spring announcement (https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/, dated 2026-06-25): Spring Boot 3.5.16 is the last open-source release of the 3.5.x line, and Spring says continued OSS support requires moving to 4.0.x or 4.1.x. The baseline is fixed by the brief (Boot 3.x), so the project runs on a line whose OSS support has ended. The developer must explicitly accept this risk in ADR-10 (G-02). Until ADR-10 is accepted and records the support limitation and its consequences, G-02 remains unmet and T-01 is blocked. The risk is not removed by the decision. Commercial support is a paid option and is not assumed.
- **D-4:** the time window is unconfirmed (see §1).
- **OD-2:** requirement numbering in `prompt-init.txt` (RF-01…RF-09) differs from `requirements.md` (RF-01…RF-06). The tasks follow `requirements.md`.
- **OD-3 and OD-4:** event retention and report range limit are not decided. T-13 needs OD-4 before implementation; the rebuild test of T-16 needs OD-3.

## 10. Completion criteria for this deliverable

1. Every P0 requirement of `requirements.md` (RF, RN, VAL, API, EVT, NFR marked M) has at least one task in §3 to §5.
2. Every task has an ID, a change number (D4), requirement IDs and a verifiable result.
3. Tasks in each day depend only on earlier tasks or on the change listed.
4. No task is marked done in this draft.
