# VidaCruz — Capabilities Map (D2)

Status: **draft for review.** Analysis only: no code, no OpenSpec change folders, no commits to `develop` or `main`.
Sources: `docs/requirements.md` (D1, master specification), `docs/analysis.md` (D0), `deliverables-plan.md`, `prompt-init.txt` §11.2.

Requirement IDs (RF, RN, VAL, API, EVT, DER, AMB, DEC, NFR, TST) are defined in `requirements.md` and are referenced here, not restated.

## 1. Purpose

A capability is a unit of behavior that one service owns, that can be specified and tested on its own, and that changes for one reason. This map assigns every requirement to exactly one owning capability, shows the dependencies between capabilities, and names the interfaces and persistence each one touches. D4 (`openspec-roadmap.md`) turns these capabilities into ordered changes.

## 2. Capabilities and owning services

| ID | Capability | Service | Requirements (owner) | Type |
|---|---|---|---|---|
| C-01 | `platform-foundation` | Both | Build, runtime, container and delivery baseline: Gradle, Java 21, Docker and docker-compose, CI pipeline, Kubernetes and Azure manifests (NFR, TC sections of D1) | DERIVED |
| C-02 | `triage-classification` | Triage | RF-01, RN-01, RN-02 (SLA value at registration), VAL-01…VAL-11, API-01, DEC-15 (Idempotency-Key) | EXPLICIT + DERIVED |
| C-03 | `patient-lifecycle-audit` | Triage | RF-02, RN-04, API-02, API-03 | EXPLICIT + DERIVED |
| C-04 | `triage-sla-escalation` | Triage | RF-03, RN-02 (evaluation), RN-03, DEC-04, AMB-04 | EXPLICIT |
| C-05 | `event-driven-integration` | Both | EVT-01…EVT-04, DER-02 (transactional outbox), Kafka consumer idempotency | EXPLICIT + DERIVED |
| C-06 | `clinical-assistant-rag` | Clinical Assistant | RF-04 Part A, RN-05, API-04, protocol corpus (§12 of D1), NFR-03 (503 fallback) | EXPLICIT |
| C-07 | `emergency-state-projection` | Clinical Assistant | RF-04 Part B, API-05, DEC-06 | EXPLICIT |
| C-08 | `operational-reporting` | Triage | RF-05, API-06, AMB-05, indexes in §11 of D1 | EXPLICIT |
| C-09 | `medical-escalation-alerts` | Triage (+ channel) | RF-06, DER-03, AMB-10, DEC-09 | EXPLICIT (channel open) |
| C-10 | `security` | Both | Authentication, authorization, input validation, data protection, secrets handling and access control (NFR security sections of D1) | DERIVED + PROPOSED |
| C-11 | `observability` | Both | Health probes, structured logs, metrics, tracing and operational diagnostics (NFR observability sections of D1) | DERIVED |

OD-1 is resolved (see §6): the former `security-observability-delivery` is split into C-10 and C-11. Delivery (CI pipeline, containers, Kubernetes and Azure manifests) moves to C-01, which already owns the build and container baseline. This keeps the count at eleven capabilities. The split of delivery into its own capability is the developer's call; see §6.

## 3. Requirement coverage

Each EXPLICIT requirement has exactly one owner. Business rules that shape behavior belong to the capability that enforces them.

| Requirement | Owner capability | Also touched by |
|---|---|---|
| RF-01 | C-02 | C-05 (publishes EVT-01), C-03 (writes audit) |
| RF-02 | C-03 | C-05 (publishes EVT-03, EVT-04) |
| RF-03 | C-04 | C-05 (publishes EVT-02), C-09 (alert) |
| RF-04 | C-06 (Part A), C-07 (Part B) | C-05 (consumes events for C-07) |
| RF-05 | C-08 | C-03 (source timestamps) |
| RF-06 | C-09 | C-05 (event source) |
| RN-01 | C-02 | — |
| RN-02 | C-02 (value at registration), C-04 (evaluation) | — |
| RN-03 | C-04 | — |
| RN-04 | C-03 | every capability that writes state (C-02, C-04) |
| RN-05 | C-06 | — |

Coverage check: every RF-01…RF-06 and RN-01…RN-05 appears in §2 with an owner. Done criterion in §5.

## 4. Capability detail

Each block lists what the capability needs to exist. Specs and tests belong to D4 and the OpenSpec changes, not here.

### C-01 `platform-foundation`
- **Services:** both.
- **Goal:** both services build, run and start in containers with the pinned baseline (Spring Boot 3.5.16, Java 21, Gradle 8.14.8, Spring Cloud 2025.0.3, Spring AI 1.1.1 for the assistant). It also owns the delivery path: CI pipeline, Docker images, docker-compose, Kubernetes and Azure manifests.
- **Interfaces:** none beyond the build and deployment definitions.
- **Persistence:** none by itself.
- **Depends on:** none. It is the first capability.
- **Tests:** build succeeds on Java 21; containers start; the CI pipeline runs build and tests on each pull request; manifests validate.

### C-02 `triage-classification`
- **Service:** Triage.
- **Goal:** register a patient with vital signs, validate the input, assign a level by RN-01, compute the SLA by RN-02, and return the idempotent response when `Idempotency-Key` is present (DEC-15).
- **Interfaces:** `POST /triage/pacientes` (API-01); emits `paciente_registrado` through C-05.
- **Persistence:** patient record with level, SLA, state, `escalado` flag, registration timestamp; idempotency key store.
- **Depends on:** C-01, C-03 (audit write is part of registration).
- **Tests:** E1…E6 classification; every VAL rule; idempotent replay; 409 on reused key with a different body.

### C-03 `patient-lifecycle-audit`
- **Service:** Triage.
- **Goal:** enforce the state flow `REGISTRADO → EN_CONSULTA`, `REGISTRADO → ALTA`, `EN_CONSULTA → ALTA`; reject everything else with 409; keep an append-only audit trail.
- **Interfaces:** `PATCH /triage/pacientes/{id}/estado` (API-02), `GET /triage/pacientes/{id}` (API-03).
- **Persistence:** state change history and append-only audit table. No update or delete path exists for audit rows.
- **Depends on:** C-02 (patient exists).
- **Tests:** each valid transition persists state, audit and event together; each invalid transition changes nothing; `tiempoEnEstadoMinutos` is correct.

### C-04 `triage-sla-escalation`
- **Service:** Triage.
- **Goal:** on each evaluation interval, escalate each overdue `REGISTRADO` patient exactly once (RN-03), and never escalate patients in `EN_CONSULTA` or `ALTA`.
- **Interfaces:** scheduled evaluator (no HTTP); emits `paciente_escalado` through C-05.
- **Persistence:** `escalado` flag set atomically with the audit row.
- **Depends on:** C-02, C-03.
- **Tests:** overdue patient escalated once; second evaluation is a no-op; boundary behavior per AMB-04; escalation testable in seconds with a short configured SLA.

### C-05 `event-driven-integration`
- **Services:** both. Triage publishes; Clinical Assistant consumes.
- **Goal:** publish the four events to `triaje.eventos` through a transactional outbox, so that a state change and its event are never split; consumers process each event idempotently.
- **Interfaces:** envelope and payloads of EVT-01…EVT-04; topic `triaje.eventos`.
- **Persistence:** outbox table in Triage; consumer offset or processed-event record in Clinical Assistant.
- **Depends on:** C-02, C-03 (events are written in the same transaction as the state change).
- **Tests:** event published exactly once per successful state change; a broker outage does not lose events; duplicate delivery produces no second effect.

### C-06 `clinical-assistant-rag`
- **Service:** Clinical Assistant.
- **Goal:** answer clinical questions only from the protocol corpus, with citations. If no relevant protocol exists, return the fixed notice (RN-05). If the LLM is unavailable, return 503 (NFR-03).
- **Interfaces:** `POST /asistente/consulta` (API-04). Calls the LLM and the vector store only.
- **Persistence:** vector store with the corpus (PROTO-001, PROTO-002, PROTO-003, §12 of D1).
- **Depends on:** C-01. It does not depend on the Triage Service: no synchronous call to Triage (RF-04 Part B and the brief).
- **Tests:** PROTO queries return citations; a question with no protocol returns the notice; circuit breaker opens and returns 503.

### C-07 `emergency-state-projection`
- **Service:** Clinical Assistant.
- **Goal:** keep a projection of patients not in `ALTA`, grouped by level, built only from events.
- **Interfaces:** `GET /asistente/estado-urgencias` (API-05); consumes EVT-01…EVT-04 through C-05.
- **Persistence:** projection store (DEC-06, Redis or equivalent).
- **Depends on:** C-05, C-01.
- **Tests:** counts match a replayed event sequence; a replayed event does not double count; no call to Triage during a request.

### C-08 `operational-reporting`
- **Service:** Triage.
- **Goal:** report per hour (UTC, AMB-05): total, count per level, average and p95 minutes from registration to first `EN_CONSULTA`. Aggregation runs in PostgreSQL.
- **Interfaces:** `GET /triage/reportes/operacion?desde=&hasta=` (API-06).
- **Persistence:** reads from the patient and state-history tables; indexes defined in §11 of D1.
- **Depends on:** C-02, C-03.
- **Tests:** results match the hand-computed fixture; the query uses the planned indexes.

### C-09 `medical-escalation-alerts`
- **Service:** Triage, with an external channel.
- **Goal:** alert the medical team on escalation, at least once, with retries, and at most one alert per escalation (DER-03).
- **Interfaces:** consumes `paciente_escalado` from its own outbox or from C-04's event. The channel is chosen in an ADR (see `adr-roadmap.md`, D3).
- **Persistence:** alert delivery record per escalation.
- **Depends on:** C-04, C-05.
- **Tests:** a failed delivery is retried; a duplicate escalation event produces one alert.

### C-10 `security`
- **Services:** both.
- **Goal:** authentication on `/triage/**` and `/asistente/**` (mechanism and scope decided in ADR-07), authorization, input validation, protection of patient data in logs and responses, and secrets read only from environment variables or Kubernetes Secrets.
- **Interfaces:** the authentication mechanism and the rejection responses it produces.
- **Persistence:** none of its own.
- **Depends on:** C-01. Cross-cutting: its tests run against every protected endpoint.
- **Tests:** an unauthenticated request to a protected endpoint is rejected; a secret is never present in the repository or the image; logs do not contain patient identifiers beyond the agreed field.

### C-11 `observability`
- **Services:** both.
- **Goal:** health and readiness probes, structured logs, metrics, traces and the operational signals needed to diagnose a failure (for example, circuit breaker state and event lag).
- **Interfaces:** `/actuator` health endpoints and the metrics endpoint.
- **Persistence:** none of its own.
- **Depends on:** C-01. Cross-cutting: it observes every other capability.
- **Tests:** probes report the real state of the database, broker and LLM dependency; a metric increments for each published event; a trace links a request to its event.

## 5. Dependency order

```
C-01 platform-foundation
 ├── C-02 triage-classification
 │    └── C-03 patient-lifecycle-audit
 │         ├── C-04 triage-sla-escalation ──┐
 │         ├── C-08 operational-reporting   │
 │         └── C-05 event-driven-integration ┤
 │                                          ├── C-09 medical-escalation-alerts
 │                                          │
 ├── C-06 clinical-assistant-rag            │
 └── C-07 emergency-state-projection ◄──── C-05
C-10 security and C-11 observability (cross-cutting, apply to all capabilities)
```

The dependency graph has no cycles. C-06 is independent of the Triage capabilities, so it can be developed in parallel with them.

Completion criteria for this deliverable:
1. Every RF-01…RF-06 and RN-01…RN-05 has exactly one owner in §2 and §3.
2. Every capability in §2 names a service, a goal, interfaces, persistence, dependencies and tests.
3. The dependency graph in §5 has no cycle (checked by reading the edges).
4. No requirement is restated: each is referenced by ID only.

## 6. Open decisions

- **OD-1 — Split of security and observability.** Resolved: C-10 `security` and C-11 `observability` are separate capabilities, as approved in review.
- **OD-1b — Delivery placement.** Delivery (CI, containers, Kubernetes and Azure manifests) is placed in C-01 `platform-foundation`. Trade-off: fewer capabilities, but C-01 grows. Alternative: a separate `delivery` capability (twelve capabilities). Owner: developer.
- **OD-2 — Naming of the requirement set.** `prompt-init.txt` §5 numbers functional requirements RF-01…RF-09, while `requirements.md` uses RF-01…RF-06 and §5 of the prompt has no capability list anymore. This map follows `requirements.md`. Recommended: align the prompt's §5 numbering with D1 before D5 (traceability), so both documents use one numbering. Owner: developer.
