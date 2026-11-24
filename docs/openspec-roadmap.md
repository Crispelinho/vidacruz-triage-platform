# VidaCruz — OpenSpec Roadmap (D4)

Status: **draft for review.** Plan only: no OpenSpec change folders exist yet, and no change is approved until the developer accepts this roadmap.
Sources: `docs/capabilities-map.md` (D2, capabilities C-01…C-11 and §5 dependencies), `docs/adr-roadmap.md` (D3, ADR-01…ADR-14 and §3.3 acceptance order), `docs/requirements.md` (D1, requirement IDs), `deliverables-plan.md` §3 (D4 content plan).

Branch context: part of the D3–D6 planning set proposed in PR #3 (https://github.com/Crispelinho/vidacruz-triage-platform/pull/3). It is not integrated into `develop`.

## 1. Purpose

An OpenSpec change is the unit of specification and delivery: one change, one capability slice, one verifiable result. This roadmap orders the changes so that each one depends only on changes already done. It does not contain the specs, which are written inside each change (proposal, spec deltas, design, tasks), and it does not restate requirements (they are referenced by ID).

## 2. Rules for every change

- A change is named in kebab-case and maps to one capability (C-xx) or to the delivery part of C-01.
- A change does not start until the ADRs listed in its "Requires ADR" line are accepted (D3 §3).
- A change is done only when its done-criterion is met by a test or by evidence. An opinion is not a done-criterion.
- Each change follows the lifecycle in `docs/process/workflow.md` (SDD for the criteria, OpenSpec for the change folder, TDD red → green → refactor for each task).
- A change touches only the files of its own service unless the change is C-01 or C-05, which define shared contracts.

## 3. Ordered changes

The order follows the topological order of `capabilities-map.md` §5 and the ADR acceptance order of `adr-roadmap.md` §3.3. Each change depends only on changes above it.

| # | Change | Capability | Requirements | Depends on (changes) | Requires ADR | Done criterion |
|---|---|---|---|---|---|---|
| 1 | `platform-foundation` | C-01 | NFR build and runtime, TC | none | ADR-01, ADR-10, ADR-14 | Both services build on Java 21 with Spring Boot 3.5.16, Spring Cloud 2025.0.3 and Gradle 8.14.8; containers start; CI runs build and tests on each pull request; the audit table (created here, see change 3 for its writes) rejects UPDATE and DELETE in an integration test |
| 2 | `event-driven-integration` | C-05 | EVT-01…EVT-04, DER-02 | 1 | ADR-03, ADR-02 | An outbox row exists if and only if its state change committed (test with forced rollback); a broker outage delays publication and loses nothing (test with broker stopped); a duplicate delivery produces no second effect (consumer test); per-patient order is kept through `pacienteSequence`; architecture check for NFR-04: Triage has no synchronous HTTP client to the Assistant, and events are delivered with the Assistant not running (the functional NFR-04 test is in change 5) |
| 3 | `triage-classification` | C-02 | RF-01, RN-01, RN-02-A, RN-04-R, VAL-01…VAL-11, API-01, DEC-15 | 1, 2 | ADR-03, ADR-07, ADR-12 | Examples E1…E6 classify as specified; every VAL rule has a test; registration writes patient, audit row and outbox row in one transaction; a repeated `Idempotency-Key` returns the original response; a reused key with a different body returns 409 |
| 4 | `patient-lifecycle-audit` | C-03 | RF-02, RN-04-T, API-02, API-03 | 1, 2, 3 | ADR-03 | A transition to `EN_CONSULTA` emits exactly one EVT-03; a transition to `ALTA` emits exactly one EVT-04; state, history, audit and outbox row commit or roll back together; each invalid transition returns 409, changes nothing and emits no event; `tiempoEnEstadoMinutos` matches a fixture |
| 5 | `triage-sla-escalation` | C-04 | RF-03, RN-02-B, RN-03, DEC-04 | 2, 3, 4 | ADR-13, ADR-02 | An overdue `REGISTRADO` patient is escalated once; a second evaluation is a no-op; two concurrent evaluators produce one escalation (test with two instances); boundary behavior follows AMB-04; the test runs in seconds with a short SLA; final NFR-04 integration test: with the Assistant stopped, registration, transitions and escalation all succeed |
| 6 | `operational-reporting` | C-08 | RF-05, API-06, AMB-05, DEC-11 | 3, 4 | ADR-01 | Results match a hand-computed fixture, including a patient with no first consultation; the query uses the indexes of D1 §11 (evidence: execution plan); `desde` later than `hasta` returns 400; OD-4 is resolved in this change's design before implementation |
| 7 | `medical-escalation-alerts` | C-09 | RF-06, DER-03, AMB-10, DEC-09 | 2, 5 | ADR-06 | A failed delivery is retried with backoff (test with failing receiver); a duplicate EVT-02 produces one notification; a retry never emits an escalation; delivery attempts are stored per escalation event |
| 8 | `clinical-assistant-rag` | C-06 | RF-04-A, RN-05, API-04, NFR-03 | 1 | ADR-04, ADR-05, ADR-09 | PROTO-001, PROTO-002 and the diabetes query return the behavior required by RN-05 (test with the corpus); every citation validates against the corpus; no answer contains a claim without a validated citation; the circuit breaker opens and the endpoint returns 503 (test with the LLM stubbed to fail) |
| 9 | `emergency-state-projection` | C-07 | RF-04-B, API-05, DEC-06 | 1, 2 | ADR-08, ADR-02 | Counts match a replayed event sequence; a replayed event does not double count; a request makes no call to Triage (test with Triage unreachable); after the projection store is emptied, a replay restores the same counts |
| 10 | `security` | C-10 | Security NFR of D1 | 1, and every protected endpoint (changes 3, 4, 6, 8, 9: `GET /asistente/estado-urgencias`) | ADR-07 | An unauthenticated request to `/triage/**` and `/asistente/**` is rejected (test for each endpoint); no secret is in the repository or the image (scan evidence); logs carry no patient data beyond the agreed fields |
| 11 | `observability` | C-11 | Observability NFR of D1 | 1, 2 | none | Readiness fails when the database is down; readiness stays up when the broker or the LLM is down and the health detail shows it; a metric increments per outbox event published; a trace links a request to its event |
| 12 | `platform-delivery` | C-01 (delivery part) | DEC-12, DEC-13, ADR-11 | 1…11 | ADR-11 | Docker images build; docker-compose starts both services with their dependencies; Kubernetes manifests validate; Azure deployment is either demonstrated or recorded as blocked by R-06, with the blocker written in the change |

Notes on the order:
- Changes 8 (`clinical-assistant-rag`) can start as soon as change 1 is done, because it does not wait for Triage. The table keeps it after change 7 to keep one writer at a time (see §5). Two developers could run 8 in parallel with 2 to 7.
- ADR-07 (authentication) must be accepted before change 3, because `POST /triage/pacientes` is the first protected endpoint. Change 10 then applies the authentication to every protected endpoint and tests each one.
- Change 12 is last because it needs the Azure target (ADR-11), which depends on access (R-06).

## 4. Traceability to D2 and D3

- Every capability C-01…C-11 appears in at least one change. C-01 appears in changes 1 and 12.
- Every ADR marked "Must be accepted before" in `adr-roadmap.md` §3 is listed under "Requires ADR" in the first change that needs it.
- The requirement IDs in this table are the ones in `capabilities-map.md` §3. D5 (`traceability-matrix.md`) maps each change to its tasks, code paths, tests and evidence.

## 5. Concurrency rules

- One writer per service at a time. Changes 3 to 7 write to the Triage service; they run in sequence.
- Changes 8 and 9 write to the Clinical Assistant service; they run after change 2 and do not touch Triage.
- Shared contracts (the envelope and EVT payloads, change 2) are changed only by change 2. A later change that needs a different payload opens a new change and does not edit change 2.

## 6. Open decisions that block a change

- **OD-2 — Requirement numbering.** `prompt-init.txt` §5 numbers RF-01…RF-09; `requirements.md` uses RF-01…RF-06. This roadmap follows `requirements.md`. The numbering must be aligned before D5 is written. Owner: developer. The prompt is not edited here.
- **OD-3 — Retention of `triaje.eventos`.** Blocks the rebuild test of change 9. Linked to ADR-02 and ADR-08. Not decided.
- **OD-4 — Maximum span of a report range.** Blocks the design of change 6. Not decided.

None of these open decisions is marked approved in this document.

## 7. Completion criteria for this deliverable

1. Every change in §3 names a capability, requirement IDs, dependencies, required ADRs and a done-criterion.
2. Every dependency points to a change above it in the table (no forward dependency).
3. Every ADR listed in `adr-roadmap.md` §3 appears under "Requires ADR" of at least one change, or is marked as a design-only decision.
4. Every open decision in §6 is linked to the change it blocks and none is marked approved.
