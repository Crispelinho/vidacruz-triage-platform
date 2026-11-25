# VidaCruz — Capabilities Map (D2)

Status: **draft for review.** Analysis only: no code, no OpenSpec change folders, no commits to `develop` or `main`.
Sources: `docs/requirements.md` (D1, master specification), `docs/analysis.md` (D0), `deliverables-plan.md`, `prompt-init.txt` §11.2.

Requirement IDs (RF, RN, VAL, API, EVT, DER, AMB, DEC, NFR, TST, R) are defined in `requirements.md` and are referenced here, not restated.

## 1. Purpose

A capability is a unit of behavior that one service owns, that can be specified and tested on its own, and that changes for one reason. This map assigns every **traceability unit** to exactly one owning capability, shows the dependencies between capabilities, and names the interfaces and persistence each one touches. D4 (`openspec-roadmap.md`) turns these capabilities into ordered changes.

A traceability unit is either a whole requirement or one part of a requirement that has independent behavior. Units are named with a suffix: `RF-04-A`, `RF-04-B`, `RN-02-A`, `RN-02-B`, `RN-04-R`, `RN-04-T`. Requirement IDs do not change; the suffix only splits them for traceability.

## 2. Capabilities and owning services

| ID | Capability | Service | Owns (traceability units) | Type |
|---|---|---|---|---|
| C-01 | `platform-foundation` | Both | Build, runtime, container and delivery baseline; definition of the Triage audit table and its append-only constraint, shared by C-02 and C-03 inside Triage (NFR, TC sections of D1) | DERIVED |
| C-02 | `triage-classification` | Triage | RF-01, RN-01, RN-02-A (SLA value at registration), RN-04-R (audit of registration), VAL-01…VAL-11, API-01, DEC-15 | EXPLICIT + DERIVED |
| C-03 | `patient-lifecycle-audit` | Triage | RF-02, RN-04-T (audit of transitions and state history), API-02, API-03 | EXPLICIT + DERIVED |
| C-04 | `triage-sla-escalation` | Triage | RF-03, RN-02-B (SLA evaluation), RN-03, DEC-04, AMB-04 | EXPLICIT |
| C-05 | `event-driven-integration` | Both | EVT-01…EVT-04, DER-02 (transactional outbox and publisher), consumer idempotency | EXPLICIT + DERIVED |
| C-06 | `clinical-assistant-rag` | Clinical Assistant | RF-04-A, RN-05, API-04, protocol corpus (§12 of D1), NFR-03 (503 fallback) | EXPLICIT |
| C-07 | `emergency-state-projection` | Clinical Assistant | RF-04-B, API-05, DEC-10, ADR-08 | EXPLICIT |
| C-08 | `operational-reporting` | Triage | RF-05, API-06, AMB-05, indexes in §11 of D1 | EXPLICIT |
| C-09 | `medical-escalation-alerts` | Triage (+ channel) | RF-06, DER-03, AMB-10, DEC-09 | EXPLICIT (channel open) |
| C-10 | `security` | Both | Authentication, authorization, data protection, secrets handling and access control (NFR security sections of D1) | DERIVED + PROPOSED |
| C-11 | `observability` | Both | Health probes, structured logs, metrics, tracing and operational diagnostics (NFR observability sections of D1) | DERIVED |

Eleven capabilities. Security and observability are separate (OD-1 resolved). Delivery (CI, containers, Kubernetes and Azure manifests) stays in C-01 (OD-1b).

## 3. Requirement coverage

Each traceability unit has exactly one owner. A requirement without parts has a single unit with its own ID.

| Unit | Owner | Also touched by (not owners) |
|---|---|---|
| RF-01 | C-02 | C-05 (publishes EVT-01) |
| RF-02 | C-03 | C-05 (publishes EVT-03, EVT-04) |
| RF-03 | C-04 | C-05 (publishes EVT-02) |
| RF-04-A | C-06 | — |
| RF-04-B | C-07 | C-05 (consumes EVT-01…EVT-04) |
| RF-05 | C-08 | C-03 (source timestamps) |
| RF-06 | C-09 | C-05 (consumes EVT-02) |
| RN-01 | C-02 | — |
| RN-02-A | C-02 | — |
| RN-02-B | C-04 | — |
| RN-03 | C-04 | — |
| RN-04-R (registration audit) | C-02 | C-01 (Triage audit table) |
| RN-04-T (transition audit) | C-03 | C-01 (Triage audit table) |
| RN-05 | C-06 | — |

RN-04 is split into two units because the registration and the transitions are written by different capabilities. The audit table and its append-only constraint belong to C-01 as Triage persistence, shared by C-02 and C-03 inside Triage only. Each capability writes its own rows; no capability reads or depends on another capability to write audit.

Coverage check: every RF-01…RF-06 and RN-01…RN-05 has at least one unit in this table, and each unit has one owner. Done criterion in §5.

## 4. Capability detail

Each block lists what the capability needs to exist. Specs and tests belong to D4 and the OpenSpec changes, not here.

### C-01 `platform-foundation`
- **Services:** both.
- **Goal:** both services build, run and start in containers with the pinned baseline (Spring Boot 3.5.16, Java 21, Gradle 8.14.8, Spring Cloud 2025.0.3, Spring AI 1.1.1 for the assistant). It owns the delivery path (CI pipeline, Docker images, docker-compose, Kubernetes and Azure manifests) and the definition of the Triage audit table with its append-only constraint.
- **Interfaces:** none beyond build and deployment definitions.
- **Persistence:** the audit table belongs to Triage persistence (append-only, no update or delete grant). It is shared by C-02 and C-03 inside Triage. It is not a table shared between the two services.
- **Depends on:** none. It is the first capability.
- **Tests:** build succeeds on Java 21; containers start; CI runs build and tests on each pull request; manifests validate; the audit table rejects update and delete.

### C-05 `event-driven-integration`
- **Services:** both. Triage publishes EVT-01…EVT-04. Clinical Assistant consumes the events required by C-07. Triage also consumes EVT-02 for C-09 notification processing. Publishing and consuming are separate responsibilities, even inside the same service.
- **Goal:** every successful state change creates exactly one logical outbox event, written in the same transaction as the change. The publisher provides at-least-once delivery. Consumers process duplicate deliveries idempotently, so each event produces only one business effect.
- **Interfaces:** envelope and payloads of EVT-01…EVT-04 (DEC-03); topic `triaje.eventos`. The outbox writer is used by C-02, C-03 and C-04.
- **Persistence:** outbox table in Triage (DEC-14); durable event history `EventoHistorico` in Triage (OD-3, DER-09); processed-event record in Clinical Assistant.
- **Depends on:** C-01 only. It is business-agnostic: it knows the envelope, not the patient rules. This is what keeps the graph acyclic.
- **Tests:** an outbox row exists if and only if the state change committed; a broker outage delays publication and loses nothing; a duplicate delivery produces no second effect; per-patient order is preserved through `pacienteSequence`.

### C-02 `triage-classification`
- **Service:** Triage.
- **Goal:** register a patient with vital signs, validate the input (VAL rules), assign a level by RN-01, compute the SLA by RN-02-A, write the registration audit entry (RN-04-R) and the outbox event (EVT-01) in one transaction. If `Idempotency-Key` is present, return the original response on replay (DEC-15).
- **Interfaces:** `POST /triage/pacientes` (API-01).
- **Persistence:** patient record (level, SLA, state, `escalado` flag, registration timestamp); idempotency key store (24 h TTL).
- **Depends on:** C-01 (audit table), C-05 (outbox writer). It does not depend on C-03.
- **Tests:** E1…E6 classification; every VAL rule; registration writes patient, audit and outbox row together; idempotent replay; 409 on a reused key with a different body.

### C-03 `patient-lifecycle-audit`
- **Service:** Triage.
- **Goal:** enforce the state flow `REGISTRADO → EN_CONSULTA`, `REGISTRADO → ALTA`, `EN_CONSULTA → ALTA`; reject everything else with 409; record each transition in the state history and the audit trail (RN-04-T).
- **Interfaces:** `PATCH /triage/pacientes/{id}/estado` (API-02), `GET /triage/pacientes/{id}` (API-03).
- **Persistence:** state history table; transition audit rows in the shared audit table.
- **Depends on:** C-01 (audit table), C-02 (the patient must exist), C-05 (outbox writer).
- **Tests:** each valid transition writes state, history, audit and outbox row together; each invalid transition changes nothing; `tiempoEnEstadoMinutos` is correct.

### C-04 `triage-sla-escalation`
- **Service:** Triage.
- **Goal:** on each evaluation interval, escalate each overdue `REGISTRADO` patient exactly once (RN-03). Patients in `EN_CONSULTA` or `ALTA` are never escalated.
- **Concurrency:** the escalation is a conditional update (`escalado = false AND estado = 'REGISTRADO'`). Only the evaluator instance whose update changes a row writes the audit entry and the outbox event (EVT-02). With several instances, one wins per patient (DEC-04).
- **Interfaces:** scheduled evaluator (no HTTP); emits EVT-02 through C-05.
- **Persistence:** `escalado` flag; audit row; outbox row, all in one transaction.
- **Depends on:** C-02, C-03 (state and history), C-05 (outbox writer).
- **Tests:** an overdue patient is escalated once; a second evaluation is a no-op; two concurrent evaluators produce one escalation; boundary behavior per AMB-04; escalation is testable in seconds with a short configured SLA.

### C-06 `clinical-assistant-rag`
- **Service:** Clinical Assistant.
- **Goal:** answer only from retrieved and validated evidence from the protocol corpus, with citations. The clinical content of an answer is limited to text that was retrieved from the corpus and whose citation was validated against it (DEC-07). If no relevant evidence exists, return the fixed notice (RN-05). If the LLM is unavailable, return 503 (NFR-03).
- **Interfaces:** `POST /asistente/consulta` (API-04). Calls the vector store and the LLM only.
- **Persistence:** vector store with the corpus (PROTO-001, PROTO-002, PROTO-003, §12 of D1).
- **Depends on:** C-01. It has no synchronous call to Triage (RF-04-B and the brief).
- **Tests:** PROTO queries return citations that validate against the corpus; a question with no relevant evidence returns the notice with `fuentes: []`; the circuit breaker opens and returns 503; no answer contains a claim without a validated citation.

### C-07 `emergency-state-projection`
- **Service:** Clinical Assistant.
- **Goal:** keep a projection of patients not in `ALTA`, grouped by level, built only from events.
- **Interfaces:** `GET /asistente/estado-urgencias` (API-05); consumes EVT-01…EVT-04 through C-05.
- **Persistence:** projection store (DEC-10 / ADR-08, Redis or equivalent). Rebuild procedure in §4.1 below.
- **Depends on:** C-01, C-05 (event contracts and consumer idempotency).
- **Tests:** counts match a replayed event sequence; a replayed event does not double count; a request makes no call to Triage; after the projection store is emptied, a replay restores the same counts.

**Rebuild after losing the projection store.** The projection is rebuilt by replaying `triaje.eventos` from the earliest retained offset into a new consumer group, applying events in `pacienteSequence` order per patient. Kafka keeps `triaje.eventos` for 30 days. The full rebuild does not depend on that window: it reads the durable history `EventoHistorico` in Triage (OD-3, decided), republished by a reconstruction job with the original `eventId` and `pacienteSequence`. The rebuild is verified by deleting the projection and comparing the rebuilt counts with the counts before deletion.

### C-08 `operational-reporting`
- **Service:** Triage.
- **Goal:** report per hour (UTC, AMB-05): total registered, count per level, and the average and p95 minutes from registration to first `EN_CONSULTA`. Aggregation runs in PostgreSQL.
- **Interfaces:** `GET /triage/reportes/operacion?desde=&hasta=` (API-06).
- **Persistence:** reads from the patient and state-history tables; indexes defined in §11 of D1.
- **Depends on:** C-02, C-03.
- **Edge cases (DEC-11):** patients with no first `EN_CONSULTA` (including direct `ALTA`) are counted in the total and excluded from the average and p95. The range requires an offset or `Z`, and `desde` must be earlier than `hasta`. The maximum span is 90 calendar days, inclusive, in UTC (OD-4, decided).
- **Tests:** results match a hand-computed fixture, including a patient with no consultation; the query uses the planned indexes; a range with `desde` later than `hasta` returns 400.

### C-09 `medical-escalation-alerts`
- **Service:** Triage, with an external channel.
- **Goal:** notify the medical team of each escalation. The business fact (the patient was escalated) and the external effect (the notification was sent) are kept apart.
- **Single integration flow:** C-04 emits EVT-02 through C-05. C-09 consumes EVT-02 from `triaje.eventos`. It stores each delivery attempt and its result in its own table. A retry of a notification never produces a new escalation.
- **Guarantee:** EVT-02 is delivered at least once; the notification effect is idempotent, keyed by the escalation event identifier, within the limits of the chosen channel (DEC-09 webhook).
- **Interfaces:** consumes EVT-02; the channel is chosen in ADR-06 (see `adr-roadmap.md`).
- **Persistence:** delivery attempts per escalation event, with status and retry count.
- **Depends on:** C-04 (the escalation fact), C-05 (contract and delivery).
- **Tests:** a failed delivery is retried with backoff; a duplicate EVT-02 produces one notification; a retry never emits an escalation.

### C-10 `security`
- **Services:** both.
- **Goal:** authentication on `/triage/**` and `/asistente/**` (mechanism and scope decided in ADR-07), authorization, protection of patient data in logs and responses, and secrets read only from environment variables or Kubernetes Secrets. Functional validation of request data is not part of this capability; it belongs to C-02 (VAL rules).
- **Interfaces:** the authentication mechanism and the rejection responses it produces.
- **Persistence:** none of its own.
- **Depends on:** C-01. Cross-cutting: its tests run against every protected endpoint.
- **Tests:** an unauthenticated request to a protected endpoint is rejected; a secret is never present in the repository or the image; logs do not contain patient data beyond the agreed fields.

### C-11 `observability`
- **Services:** both.
- **Goal:** health and readiness probes, structured logs, metrics, traces and the operational signals needed to diagnose a failure.
- **Readiness rules:** Triage is ready when its database is reachable and migrations have run. A broker outage is reported as degraded, not as a readiness failure, because the outbox keeps events until publication succeeds. Clinical Assistant is ready when its vector store and projection store are reachable. The LLM is not part of readiness: its state (including the circuit breaker) is reported as a health detail, so a provider outage does not remove the service from rotation for queries that can still be answered.
- **Interfaces:** `/actuator` health endpoints and the metrics endpoint.
- **Persistence:** none of its own.
- **Depends on:** C-01. Cross-cutting: it observes every other capability.
- **Tests:** readiness fails when the database is down; readiness stays up when the broker or the LLM is down and the health detail shows it; a metric increments for each outbox event published; a trace links a request to its event.

## 5. Dependency order

Dependencies, by capability (each arrow reads "needs"):

```
C-01  platform-foundation              (none)
C-05  event-driven-integration         -> C-01
C-02  triage-classification            -> C-01, C-05
C-03  patient-lifecycle-audit          -> C-01, C-02, C-05
C-04  triage-sla-escalation            -> C-02, C-03, C-05
C-08  operational-reporting            -> C-02, C-03
C-09  medical-escalation-alerts        -> C-04, C-05
C-06  clinical-assistant-rag           -> C-01
C-07  emergency-state-projection       -> C-01, C-05
C-10  security                         -> C-01   (cross-cutting)
C-11  observability                    -> C-01   (cross-cutting)
```

Topological order: C-01, C-05, C-02, C-03, C-04, C-08, C-09, then C-06 and C-07 in parallel with the Triage chain, then C-10 and C-11 across all. C-06 and C-07 do not wait for the Triage capabilities, except that C-07 needs the event contracts from C-05.

No cycle exists: C-05 depends only on C-01, so the outbox is not a dependency of the business capabilities that use it.

Completion criteria for this deliverable:
1. Every traceability unit in §3 has exactly one owner, and every RF-01…RF-06 and RN-01…RN-05 has at least one unit.
2. Every capability in §2 names a service, a goal, interfaces, persistence, dependencies and tests.
3. The dependency list in §5 has no cycle, checked by a topological order that exists.
4. No requirement is restated: each is referenced by ID or unit ID.

## 6. Open decisions

- **OD-1 — Split of security and observability.** Resolved: C-10 and C-11 are separate.
- **OD-1b — Delivery placement.** Resolved for now: delivery stays in C-01. A separate `delivery` capability remains possible later; not proposed.
- **OD-2 — Naming of the requirement set.** `prompt-init.txt` §5 numbers functional requirements RF-01…RF-09, while `requirements.md` uses RF-01…RF-06. This map follows `requirements.md`, which is the canonical source. Before D5, the prompt's references are checked against D1 and corrected; no requirement is renumbered silently. Owner: developer.
- **OD-3 — Event retention and reconstruction. Decided (developer, 2026-11-24):** Kafka retains `triaje.eventos` for 30 days; the durable history `EventoHistorico` in Triage is the source for full reconstruction. See `requirements.md` §10, §11 and DER-09.
- **OD-5 — Retention period of `EventoHistorico`.** New, not decided. No time-based purge until the developer sets the clinical data retention period. Owner: developer.
- **OD-4 — Maximum span of an operational report range. Decided (developer, 2026-11-24):** 90 calendar days, inclusive, in UTC; filter `timestamp >= desde` and `timestamp < día_siguiente(hasta)`; error body per NFR-20 (no RFC 9457 claim).
