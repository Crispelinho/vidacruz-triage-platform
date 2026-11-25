# VidaCruz — ADR Roadmap (D3)

Status: **draft for review.** Plan only: the ADRs are not written yet, and no decision here is accepted until the developer approves it.
Sources: `docs/requirements.md` (D1, decision register DEC-01…DEC-15, risks R-01…R-10), `docs/analysis.md` (D0 §16, ADR candidates), `docs/capabilities-map.md` (D2, capabilities C-01…C-11), `deliverables-plan.md` §3 (D3 content plan).

Branch context: the D3–D6 planning set was merged into `develop` through PR #3 (https://github.com/Crispelinho/vidacruz-triage-platform/pull/3). This revision fixes the open findings of the read-only audit and is branched from `develop`.

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
| DEC-07 | RAG relevance gate and circuit breaker | ADR-09 (circuit breaker and timeout only) | Resilience mechanism. The relevance gate and citation validation are rules of C-06 (RN-05, R-10), not an ADR |
| DEC-08 | API conventions and status codes | No | Contract rule; lives in API-01…API-06 |
| DEC-09 | Outbox-driven webhook for alerts | ADR-06 | Channel choice |
| DEC-10 | One database per service; Redis only for the projection | ADR-14 (new), ADR-08 | Data ownership changes the deployment and storage design |
| DEC-11 | Report time zone and exclusion rule | No | Report rule; lives in RF-05 and AMB-05 |
| DEC-12 | Version pins (Boot 3.5, Spring Cloud 2025.0.x, Spring AI 1.1.x) | ADR-10 | Platform baseline |
| DEC-13 | Java 21 target | ADR-10 | Part of the platform baseline |
| DEC-14 | Transactional outbox | ADR-03 | Consistency mechanism |
| DEC-15 | Idempotency-Key replay | ADR-12 | Replay storage and semantics |

The original analysis (D0 §16) listed twelve candidates. This roadmap adds two: ADR-13 for the scheduling mechanism (DEC-04) and ADR-14 for database ownership (DEC-10). `docs/analysis.md` §16 now lists fourteen candidates (PR #4), so this point is closed.

## 3. ADR list

| ADR | Decision | Capabilities (D2) | Requirements | Status | Must be accepted before |
|---|---|---|---|---|---|
| ADR-01 | Service decomposition: Triage and Clinical Assistant only | C-01, all | TC-09, §3 of D0 | Proposed | Any change (first ADR) |
| ADR-02 | Kafka messaging and event envelope | C-05 | EVT-01…EVT-04, DEC-03, R-08 | Proposed | C-05, C-07 |
| ADR-03 | Transactional outbox | C-05, C-02, C-03 | DEC-14, R-07 | Proposed | C-05 |
| ADR-04 | Vector store (pgvector) and JDBC isolation | C-06 | DER-06, TC-11, R-03 (no DEC covers pgvector; DEC-06 is Spring AI only) | Proposed | C-06 |
| ADR-05 | LLM provider and Spring AI versus LangChain4j | C-06 | DEC-06, RF-04-A | Proposed | C-06 |
| ADR-06 | RF-06 notification channel (webhook) | C-09 | RF-06, DEC-09, AMB-10 | Proposed | C-09 |
| ADR-07 | Authentication: API key versus JWT | C-10 | DEC-05, NFR security | Proposed | C-10, and any protected endpoint |
| ADR-08 | Projection store for urgency state (Redis) | C-07 | RF-04-B, DEC-10 | Proposed | C-07 |
| ADR-09 | Degraded mode and circuit breaker (relevance gate and citation validation stay in C-06) | C-06 | DEC-07, NFR-03, R-04, R-10 | Proposed | C-06 |
| ADR-10 | Spring Boot 3.x line and end-of-support risk; Java 21 | C-01 | DEC-12, DEC-13, R-02, R-05 | Proposed (Boot 3.x chosen by the user) | C-01 |
| ADR-11 | Azure target: Container Apps or AKS | C-01 (Azure manifests only) | §14 of D0, R-06 | Proposed | Azure manifests in C-01. Dockerfiles, docker-compose and CI do not wait for it |
| ADR-12 | Idempotency-Key replay for registration | C-02 | DEC-15, R-09 | Proposed (accepted as scope by the user) | C-02 |
| ADR-13 | SLA evaluation mechanism (scheduled job, conditional update) | C-04 | RF-03, DEC-04 | Proposed (new) | C-04 |
| ADR-14 | Database ownership: one PostgreSQL per service | C-01, C-02, C-07 | DEC-10 | Proposed (new) | C-01 |

### 3.1 Coherence with the capabilities map (D2)

- **ADR-06 and C-09.** ADR-06 decides only the channel that carries the notification effect. The flow is fixed by C-09 and is not an ADR decision: Triage consumes EVT-02, records each delivery attempt, and a retry never produces an escalation. The chosen channel must support at-least-once delivery with an idempotent effect keyed by the escalation event identifier. If the channel cannot offer that, ADR-06 must record the limit.
- **ADR-07 and C-10.** ADR-07 decides the authentication mechanism and the scope of protected endpoints (`/triage/**` and `/asistente/**`). Authorization rules and data protection stay in C-10. Functional validation of request data stays in C-02 (VAL rules). ADR-07 must not decide validation.

### 3.2 Open decisions linked to ADRs

These are open decisions from D2 §6. OD-3 was decided in PR #6 (2026-11-24). OD-4 is **not approved**. Each one is resolved only when the linked ADR or design is accepted.

- **OD-3 — Retention of `triaje.eventos`.** Linked to ADR-02 (retention is part of the messaging contract) and ADR-08 (rebuilding the projection after losing its store, including the option of a snapshot plus replay). Decided (2026-11-24): Kafka retains 30 days; full reconstruction uses the durable history EventoHistorico in Triage (DER-09), so ADR-08 does not need a snapshot for correctness. The open item is OD-5, the retention period of EventoHistorico.
- **OD-4 — Maximum span of an operational report range.** Not an architectural decision, so no ADR. It is resolved in the design of RF-05 (C-08) before the report is implemented.

### 3.3 Order of acceptance

ADR-01, then ADR-10, ADR-14, ADR-03, ADR-02, ADR-13, ADR-07, ADR-12, then the Assistant decisions ADR-04, ADR-05, ADR-09, ADR-08, and last ADR-06 and ADR-11. The reason: foundation and data ownership first, because every capability depends on them; delivery-related choices last, because they depend on Azure access (R-06).

ADR-11 stays last. C-01 is first in §5 of the capabilities map, so its local delivery part (Dockerfiles, docker-compose, CI) starts before ADR-11; only its Azure manifests wait for it.

### 3.4 Alternatives still missing

The ADR template (§5) requires at least two alternatives. These decisions have only one alternative in the decision register, so a second one must be added before their ADR is drafted:

- ADR-05 and ADR-04: a provider alternative is missing (ADR-05 title says «LLM provider», but DEC-06 only compares Spring AI with LangChain4j).
- ADR-07 (DEC-05): only «JWT with external IdP».
- ADR-14 and ADR-08 (DEC-10): only «Shared database».
- ADR-10 (DEC-12, DEC-13): only «Boot 4.x» and «Java 25».
- ADR-03 (DEC-14): only «Direct publish after commit».
- ADR-12 (DEC-15): only «No idempotency».

## 4. Decisions that need the developer before writing

These are choices, not analysis. Each one is blocking for its ADR.

- **ADR-05 LLM provider.** The comparison must be made against official documentation of Spring AI and LangChain4j. No equivalence of features is assumed.
- **ADR-06 channel.** DEC-09 proposes a webhook. The developer must confirm that a mock receiver in docker-compose is acceptable for the evaluation.
- **ADR-07 authentication.** DEC-05 proposes an API key in `X-API-Key`. Confirm, or choose JWT with its extra cost.
- **ADR-11 Azure target.** Container Apps or AKS. Blocked by R-06 (subscription and quota). Record the blocker if access is not available.
- **ADR-10 support risk.** R-02 is verified: the official Spring announcement of 2026-06-25 (https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/) states that Spring Boot 3.5.16 is the last open-source release of the 3.5.x line, and that continued OSS support requires 4.0.x or 4.1.x. The brief requires Boot 3.x, so the developer must accept the end of OSS support in ADR-10 and record the link; the alternative (a supported line) is excluded by the brief.

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
