# VidaCruz — ADR Roadmap (D3)

Status: **draft for review.** Plan only: the ADRs are not written yet, and no decision here is accepted until the developer approves it.
Sources: `docs/requirements.md` (D1, decision register DEC-01…DEC-15, risks R-01…R-10), `docs/analysis.md` (D0 §16, ADR candidates), `docs/capabilities-map.md` (D2, capabilities C-01…C-11), `deliverables-plan.md` §3 (D3 content plan).

Branch context: this deliverable is stacked on `feature/d2-capabilities-map`, which is stacked on `feature/docs-requirements-analysis`. It is not integrated into `develop`.

## 1. Purpose

An ADR records one architectural decision: the context, the alternatives, the decision, and its consequences. This roadmap lists which decisions need an ADR, in what order they must be accepted, and which requirements and capabilities each one affects. It does not repeat the ADR contents.

## 2. Which decisions become ADRs

Not every decision in D1 is architectural. The rule used here: a decision becomes an ADR when it changes a technology, a boundary, a data flow, a security mechanism, or a deployment target. Business rules and API conventions stay in `requirements.md`.

| DEC | Decision (short) | Becomes ADR | Reason |
|---|---|---|---|
| DEC-01 | Classification order and complaint matching | No | Business rule; lives in RN-01 |
| DEC-02 | Escalation state model (`escalado` flag, audit row) | No | Domain model; lives in RF-03 and RN-03 |
| DEC-03 | Event envelope and JSON format | ADR-02 | Messaging contract |
| DEC-04 | SLA evaluation by scheduled job | ADR-13 (new) | Scheduling mechanism changes the runtime design |
| DEC-05 | API key authentication | ADR-07 | Security mechanism |
| DEC-06 | Spring AI behind an interface | ADR-05 | Framework and provider choice |
| DEC-07 | RAG relevance gate and circuit breaker | ADR-09 | Resilience mechanism |
| DEC-08 | API conventions and status codes | No | Contract rule; lives in API-01…API-06 |
| DEC-09 | Outbox-driven webhook for alerts | ADR-06 | Channel choice |
| DEC-10 | One database per service; Redis only for the projection | ADR-14 (new), ADR-08 | Data ownership changes the deployment and storage design |
| DEC-11 | Report time zone and exclusion rule | No | Report rule; lives in RF-05 and AMB-05 |
| DEC-12 | Version pins (Boot 3.5, Spring Cloud 2025.0.x, Spring AI 1.1.x) | ADR-10 | Platform baseline |
| DEC-13 | Java 21 target | ADR-10 | Part of the platform baseline |
| DEC-14 | Transactional outbox | ADR-03 | Consistency mechanism |
| DEC-15 | Idempotency-Key replay | ADR-12 | Replay storage and semantics |

The original analysis (D0 §16) listed twelve candidates. This roadmap adds two: ADR-13 for the scheduling mechanism (DEC-04) and ADR-14 for database ownership (DEC-10). D0 should be updated to fourteen when this roadmap is approved.

## 3. ADR list

| ADR | Decision | Capabilities (D2) | Requirements | Status | Must be accepted before |
|---|---|---|---|---|---|
| ADR-01 | Service decomposition: Triage and Clinical Assistant only | C-01, all | TC-09, §3 of D0 | Proposed | Any change (first ADR) |
| ADR-02 | Kafka messaging and event envelope | C-05 | EVT-01…EVT-04, DEC-03, R-08 | Proposed | C-05, C-07 |
| ADR-03 | Transactional outbox | C-05, C-02, C-03 | DEC-14, R-07 | Proposed | C-05 |
| ADR-04 | Vector store (pgvector) and JDBC isolation | C-06 | DEC-06, R-03 | Proposed | C-06 |
| ADR-05 | LLM provider and Spring AI versus LangChain4j | C-06 | DEC-06, RF-04-A | Proposed | C-06 |
| ADR-06 | RF-06 notification channel (webhook) | C-09 | RF-06, DEC-09, AMB-10 | Proposed | C-09 |
| ADR-07 | Authentication: API key versus JWT | C-10 | DEC-05, NFR security | Proposed | C-10, and any protected endpoint |
| ADR-08 | Projection store for urgency state (Redis) | C-07 | RF-04-B, DEC-10 | Proposed | C-07 |
| ADR-09 | Degraded mode and circuit breaker | C-06 | DEC-07, NFR-03, R-04, R-10 | Proposed | C-06 |
| ADR-10 | Spring Boot 3.x line and end-of-support risk; Java 21 | C-01 | DEC-12, DEC-13, R-02, R-05 | Proposed (Boot 3.x chosen by the user) | C-01 |
| ADR-11 | Azure target: Container Apps or AKS | C-01 | §14 of D0, R-06 | Proposed | C-01 delivery work |
| ADR-12 | Idempotency-Key replay for registration | C-02 | DEC-15, R-09 | Proposed (accepted as scope by the user) | C-02 |
| ADR-13 | SLA evaluation mechanism (scheduled job, conditional update) | C-04 | RF-03, DEC-04 | Proposed (new) | C-04 |
| ADR-14 | Database ownership: one PostgreSQL per service | C-01, C-02, C-07 | DEC-10 | Proposed (new) | C-01 |

### 3.1 Coherence with the capabilities map (D2)

- **ADR-06 and C-09.** ADR-06 decides only the channel that carries the notification effect. The flow is fixed by C-09 and is not an ADR decision: Triage consumes EVT-02, records each delivery attempt, and a retry never produces an escalation. The chosen channel must support at-least-once delivery with an idempotent effect keyed by the escalation event identifier. If the channel cannot offer that, ADR-06 must record the limit.
- **ADR-07 and C-10.** ADR-07 decides the authentication mechanism and the scope of protected endpoints (`/triage/**` and `/asistente/**`). Authorization rules and data protection stay in C-10. Functional validation of request data stays in C-02 (VAL rules). ADR-07 must not decide validation.

### 3.2 Open decisions linked to ADRs

These are open decisions from D2 §6. They are **not approved**. Each one is resolved only when the linked ADR or design is accepted.

- **OD-3 — Retention of `triaje.eventos`.** Linked to ADR-02 (retention is part of the messaging contract) and ADR-08 (rebuilding the projection after losing its store, including the option of a snapshot plus replay). The retention value is not decided here.
- **OD-4 — Maximum span of an operational report range.** Not an architectural decision, so no ADR. It is resolved in the design of RF-05 (C-08) before the report is implemented.

### 3.3 Order of acceptance

ADR-01, then ADR-10, ADR-14, ADR-03, ADR-02, ADR-13, ADR-12, ADR-07, then the Assistant decisions ADR-04, ADR-05, ADR-09, ADR-08, and last ADR-06 and ADR-11. The reason: foundation and data ownership first, because every capability depends on them; delivery-related choices last, because they depend on Azure access (R-06).

## 4. Decisions that need the developer before writing

These are choices, not analysis. Each one is blocking for its ADR.

- **ADR-05 LLM provider.** The comparison must be made against official documentation of Spring AI and LangChain4j. No equivalence of features is assumed.
- **ADR-06 channel.** DEC-09 proposes a webhook. The developer must confirm that a mock receiver in docker-compose is acceptable for the evaluation.
- **ADR-07 authentication.** DEC-05 proposes an API key in `X-API-Key`. Confirm, or choose JWT with its extra cost.
- **ADR-11 Azure target.** Container Apps or AKS. Blocked by R-06 (subscription and quota). Record the blocker if access is not available.
- **ADR-10 support risk.** R-02: Boot 3.5 open-source support has ended according to one source that is not fully verified. The developer must accept the risk, or choose a supported line, which the brief does not allow.

## 5. ADR template

Every ADR file is written as `docs/adr/ADR-NN-<slug>.md` with this structure:

```
# ADR-NN: <decision title>

Status: Proposed | Accepted | Superseded by ADR-MM
Date: YYYY-MM-DD
Deciders: <developer>

## Context
Forces and constraints, with requirement IDs (RF, RN, NFR, DEC, R).

## Decision drivers
Measurable criteria the decision must satisfy (latency, failure behavior, delivery deadline).

## Alternatives considered
At least two. For each one: description, pros, cons, and why it was rejected.

## Decision
The chosen option, in one paragraph.

## Consequences
Positive, negative, and what it forces on other capabilities (C-xx).

## Risks and mitigations
Risk IDs (R-xx) and how they are reduced.

## Verification
The test or evidence that shows the decision holds (TST or NFR IDs).
```

Completion criterion for an ADR: it has at least two alternatives, names the affected requirement and capability IDs, and states a verification step. An ADR without a verification step is not accepted.

## 6. Completion criteria for this deliverable

1. Every DEC-01…DEC-15 is classified in §2 as ADR or not, with a reason.
2. Every ADR in §3 names at least one capability from `capabilities-map.md` and at least one requirement or risk ID from `requirements.md`.
3. Every ADR in §3 appears in the order in §3.3 without a forward dependency.
4. The difference from D0 §16 (twelve to fourteen) is recorded in §2.
5. Every open decision in §3.2 is linked to the ADR or design that resolves it, and none is marked approved.
6. No ADR content is written in this file; the template in §5 is the only structure defined here.
