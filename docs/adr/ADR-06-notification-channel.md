# ADR-06: RF-06 notification channel (webhook)

- **Status:** Proposed. Not accepted. A webhook mock receiver in docker-compose is acceptable only with explicit developer confirmation, and the delivery guarantee conflict remains unresolved until ADR-06 is accepted.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** Decides the external notification channel for RF-06 escalation alerts: an outbox-driven webhook to a configured endpoint with retries and backoff. It does not redefine escalation detection (C-04), the event envelope or Kafka contract (ADR-02/ADR-13), the outbox mechanism (ADR-03), or projection behavior (ADR-08), which ignores EVT-02 for counts.
- **Related:** C-09, RF-06, DER-03, AMB-10, DEC-09, G-T09, G-T10, G-T11, EVT-02, T-18, T-22, [requirements.md](../requirements.md), [analysis.md](../analysis.md), [capabilities-map.md](../capabilities-map.md), [triage-service.md](../services/triage-service.md), [implementation-roadmap.md](../implementation-roadmap.md), [openspec-roadmap.md](../openspec-roadmap.md), [adr-roadmap.md](../adr-roadmap.md), ADR-02 and ADR-03 (proposed in PR #17), [ADR-13](ADR-13-sla-evaluation.md)

## Context

RF-06 requires alerting the medical team when a patient is escalated, but the channel, format, and technology were left open by the brief (AMB-10, RF-06). D1 proposes DEC-09: an outbox-driven webhook to a configured endpoint with retries and backoff, with a mock receiver in docker-compose. C-09 fixes the integration flow: C-04 emits EVT-02 through C-05; C-09 consumes EVT-02 from `triaje.eventos`; each delivery attempt is stored in its own table; and a notification retry never produces a new escalation. The chosen channel must support at-least-once delivery with an idempotent effect keyed by the escalation `eventId`, with at most one logical alert.

The Triage service analysis records two unresolved design tensions. G-T09 notes the dispatch contradiction between “outbox-driven” in DEC-09 and “alerts channel consumes EVT-02” in the earlier analysis; the ADR must choose between a Triage Kafka consumer group reading EVT-02 with its own dedupe table, or a dedicated alert outbox table filled in the escalation transaction. G-T10 records the delivery guarantee conflict: RF-06/DER-03 ask for at most one alert per escalation, while the channel can realistically provide at-least-once delivery; the proposed resolution is at-least-once delivery with `eventId` as webhook idempotency key and receiver deduplication, making the effect “at most one logical alert”. G-T11 leaves retry limit, backoff parameters, and terminal `FALLIDA` state pending. ADR-02 explicitly ignores EVT-02 for Assistant projection behavior, while ADR-06 consumes EVT-02 for notification; ADR-08 also ignores EVT-02 for urgency counts.

## Decision drivers

- RF-06, C-09, DER-03: every escalation must produce an alert effect with at-least-once delivery and no duplicate logical alert.
- DEC-09, AMB-10: the channel must be testable in docker-compose without external accounts, subject to developer confirmation of the mock receiver.
- G-T09: dispatch ownership must be explicit and consistent with the outbox/Kafka architecture.
- G-T10, G-T11: retries, idempotency, and terminal failure visibility must be defined without claiming stronger guarantees than the channel provides.
- NFR-04, ADR-02, ADR-08: notification handling must not create synchronous coupling to the Assistant and must not change EVT-02 projection semantics.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Outbox-driven webhook to a configured endpoint** | Persist alert intent/delivery state from the escalation flow and dispatch an HTTP webhook with retries/backoff; use escalation `eventId` as idempotency key; exercise with a mock receiver in docker-compose. | Matches DEC-09; easy to test locally; supports at-least-once delivery with idempotent receiver effect; no external provider account required. | Requires receiver deduplication contract; retry limit/backoff/terminal state remain pending (G-T11); HTTP endpoint availability becomes an external dependency. | **Proposed**, pending developer confirmation of the docker-compose mock receiver. |
| **B. Email / SMTP** | Send escalation alerts through an SMTP server/provider. | Familiar operational channel; human-readable delivery. | Harder to test end to end in evaluation; needs SMTP configuration/test server; weaker fit for deterministic idempotent integration tests. | Rejected for now per AMB-10/DEC-09; could be reconsidered if developer requires human inbox delivery. |
| **C. Queue consumer / dedicated downstream queue integration** | Publish alerts to a queue and require a separate consumer/team-owned worker to deliver them. | Decouples delivery from HTTP endpoint availability; fits async messaging patterns. | Adds another consumer/contract to operate; still needs final delivery channel; increases scope for three-day evaluation. | Not selected; DEC-09 prefers direct webhook. |
| **D. SMS** | Send escalation alerts as SMS messages through an external provider. | Direct mobile reach. | Needs paid/external provider; harder local testing; idempotency and cost controls add scope. | Rejected for evaluation scope per AMB-10/DEC-09. |
| **E. Dispatch via Triage Kafka consumer group reading EVT-02** | A Triage consumer group reads EVT-02 from `triaje.eventos` and stores delivery attempts in its own dedupe table before calling the webhook. | Aligns with “alerts channel consumes EVT-02”; reuses Kafka delivery semantics; separates escalation commit from webhook call. | Contradicts the “outbox-driven” wording unless carefully reconciled; duplicates delivery state outside escalation transaction boundary; requires consumer lag/offset operational handling. | Viable alternative recorded by G-T09; not selected pending ADR acceptance because dispatch ownership is still unresolved. |
| **F. Dedicated alert outbox table filled in escalation transaction** | Write an alert-specific outbox row in the same escalation transaction, then a publisher drains it to the webhook endpoint. | Strongly matches “outbox-driven”; keeps alert intent tied to escalation transaction; clear retry/audit state in Triage DB. | Needs additional outbox table/publisher behavior; still requires idempotent webhook effect and reconciliation with EVT-02 consumer model. | Viable alternative recorded by G-T09; not selected pending ADR acceptance. |

## Decision

**Proposed: Option A, with dispatch mechanism still to be finalized.** RF-06 escalation alerts will use an outbox-driven webhook to a configured endpoint, with retries and exponential backoff, as proposed by DEC-09. Deliveries are at-least-once; the escalation `eventId` is the idempotency key, and receivers must deduplicate so each escalation produces at most one logical alert. A mock webhook receiver in docker-compose is acceptable for evaluation only if the developer explicitly confirms it. The implementation must record each delivery attempt, retry count, and terminal outcome; `FALLIDA` is the proposed terminal state name from G-T11, but max attempts, backoff schedule, and mock contract remain pending confirmation. The G-T09 dispatch choice between a Triage Kafka consumer group reading EVT-02 and a dedicated alert outbox table filled in the escalation transaction must be resolved before this ADR is accepted.

## Consequences

- Positive: provides a locally testable RF-06 channel without external accounts and aligns with DEC-09.
- Positive: makes retry/idempotency behavior explicit through `eventId`, delivery attempts, and terminal failure visibility.
- Negative: webhook availability and receiver deduplication become part of the operational contract; at-least-once delivery cannot honestly be presented as exactly-once delivery.
- C-09 owns delivery attempts and notification effects; C-04 continues to own escalation facts; C-05/ADR-02 continue to own event transport contract.
- ADR-02 and ADR-08 remain unchanged: Assistant projection ignores EVT-02 for counts, while C-09 consumes EVT-02 for alerts.

## Risks and mitigations

- **R-07 / DER-03 / G-T10 (duplicate or conflicting delivery semantics):** send `eventId` as idempotency key; require receiver deduplication; test duplicate EVT-02 produces one logical notification.
- **G-T09 (dispatch ambiguity):** explicitly choose and document Kafka-consumer-group dispatch or dedicated alert outbox before acceptance; do not implement both ambiguously.
- **G-T11 (retry exhaustion undefined):** define configurable max attempts/backoff and terminal `FALLIDA` state; expose failures in logs/metrics.
- **AMB-10 / implementation-roadmap.md:36 (mock confirmation pending):** keep docker-compose mock receiver as provisional until developer confirms.
- **External endpoint outage:** retries with backoff and terminal failure visibility reduce silent loss; operator runbook/metrics needed after acceptance.

## Verification

- **T-18 / implementation-roadmap.md:71:** duplicate EVT-02 produces one notification; failed delivery is retried with backoff; a retry never emits an escalation.
- **T-22 / implementation-roadmap.md:82:** docker-compose includes the webhook mock receiver and health checks pass, once the developer confirms the mock is acceptable.
- **openspec-roadmap.md:32 / triage-service.md:457:** E2E alert test proves escalation leads to alert delivery through the chosen channel; delivery attempts are stored per escalation event.
- **triage-service.md:501-503:** implementation evidence shows at-least-once delivery, `eventId` idempotency key usage, and terminal `FALLIDA` behavior after configured retries.
