# ADR-01: Service decomposition: Triage and Clinical Assistant only

- **Status:** Proposed. Not accepted. `requirements.md` §2 and TC-09 say "minimum two services" and "at least two microservices". The stricter "only two" comes from `adr-roadmap.md` and `analysis.md` §16 and still needs developer acceptance (gate G-01).
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** The application service boundary for Triage and Clinical Assistant: responsibilities, data ownership, the integration channel and the ban on synchronous calls between the two services. It excludes technology pins (ADR-10), database layout in detail (ADR-14), the Kafka envelope and consumer model (ADR-02) and outbox mechanics (ADR-03).
- **Related:** TC-09, TC-10, NFR-01, NFR-04, RF-04-B, DEC-10, DER-02, DER-03, R-07, R-08, C-01…C-11, G-01 in [requirements.md](../requirements.md), [analysis.md](../analysis.md), [capabilities-map.md](../capabilities-map.md), [adr-roadmap.md](../adr-roadmap.md), [implementation-roadmap.md](../implementation-roadmap.md), [openspec-roadmap.md](../openspec-roadmap.md), [triage-service.md](../services/triage-service.md), [clinical-assistant-service.md](../services/clinical-assistant-service.md), [ADR-02](ADR-02-assistant-kafka-consumer.md), [ADR-04](ADR-04-pgvector-jdbc-isolation.md), [ADR-08](ADR-08-urgency-projection-store.md), [ADR-09](ADR-09-circuit-breaker-degraded-mode.md), [ADR-11](ADR-11-azure-target.md)

## Context

The brief requires "Triage Service and Clinical Assistant Service (minimum two services)" (`requirements.md` §2) and TC-09 states "At least two microservices: Triage and Clinical Assistant". TC-10 leaves the architecture style free but requires it to be justified. The stricter title "only" appears in `adr-roadmap.md` (ADR-01 row) and in `analysis.md` §16; this ADR makes that stricter reading explicit instead of treating it as already accepted.

`analysis.md` §3.1 and §3.2 assign patient registration, classification, state flow, SLA evaluation, escalation, audit, operational reporting and publication of EVT-01…EVT-04 to Triage, and RAG with citation validation plus the urgency projection to the Assistant. `analysis.md` §3.3 lists the responsibilities that must not move between services: classification and SLA logic and audit stay in Triage; protocol retrieval and citation validation stay in the Assistant. The service analyses agree. `triage-service.md` forbids Triage to call the Assistant, synchronously or otherwise, and to share a database with it. `clinical-assistant-service.md` states that neither service has a synchronous dependency on the other. NFR-01 requires non-blocking request paths and NFR-04 requires Triage availability to be independent of the Assistant. RF-04-B requires the urgency state to be built only from `triaje.eventos`, without calls to the Triage API.

All eleven capabilities C-01…C-11 are allocated to these two services, or to both, in `capabilities-map.md`. ADR-02, ADR-04, ADR-08, ADR-09 and ADR-11 already write their scopes against this boundary, so changing it would invalidate their assumptions. Gate G-01 requires this decision to be accepted before T-01 (change 1) in `implementation-roadmap.md`, and change 1 is gated again in `openspec-roadmap.md`.

## Decision drivers

- Satisfy TC-09 and TC-10 with a justified architecture style while preserving the stricter roadmap reading of exactly two services.
- Keep every capability C-01…C-11 owned by Triage, the Assistant, or both, with no capability requiring a third application service.
- Preserve NFR-04 and RF-04-B: no synchronous HTTP or SDK dependency in either direction; integration is event-driven through C-05.
- Preserve NFR-01: both services remain reactive on their request paths.
- Keep the boundary assumptions of ADR-02, ADR-04, ADR-08, ADR-09 and ADR-11 stable.
- Make G-01 an accepted gate before T-01; no implementation task starts on an unaccepted boundary.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Exactly two reactive services: Triage and Clinical Assistant** | Triage owns patient, state, SLA, audit, report and events; the Assistant owns RAG, citations and the urgency projection. Integration is event-only through Kafka | Matches the roadmap title and `analysis.md` §16; covers all capabilities without a new boundary; preserves NFR-04 and the assumptions of the drafted ADRs | Triage carries several capabilities and the projection is eventually consistent | **Proposed**, not accepted. This is the recommended option and requires G-01 before T-01 |
| **B. Single service or monolith** | Merge Triage and the Assistant into one deployable service | Removes the inter-service boundary and the event delivery path | Violates TC-09 and the "minimum two services" scope; loses independent failure under NFR-04 | Rejected: mandatory constraint violation |
| **C. Additional service splits beyond the two named services** | Add a separate projection service, alert service, reporting service or another domain service | Smaller individual components and independent scaling | Conflicts with "only" in `adr-roadmap.md` and `analysis.md`; adds boundaries, deployment units and likely synchronous dependencies; invalidates the drafted ADR scopes | Rejected: not justified for this scope |

## Decision

**Proposed: Option A.** The system has exactly two reactive application services. This fixes the boundary only and is not an accepted decision until the developer accepts it under G-01.

**Responsibilities.**
- **Triage Service:** patient registration and state, classification, SLA evaluation and escalation, clinical audit, the operational report, and production of EVT-01…EVT-04.
- **Clinical Assistant Service:** RAG over the protocol corpus, citation validation, and the urgency projection built from events.
- Classification, SLA logic and audit never move to the Assistant; retrieval, citation validation and the LLM never move to Triage.

**Data ownership.** Each service owns its data; no table, database or cache is shared (DEC-10). Triage owns the patient, audit, outbox and durable event-record data (whether the durable record is `EventoHistorico` or the retained outbox is open: OPEN-09/OPEN-10). The Assistant owns the protocol corpus metadata, the embeddings and the projection state. The database layout is decided in ADR-14 and is not repeated here.

**Integration through Kafka.** The only runtime channel between the services is the topic `triaje.eventos`: Triage publishes, the Assistant consumes. Publication goes through the transactional outbox (DER-02; ADR-03) and consumers are idempotent (DER-03; ADR-02). The envelope and consumer model are decided in ADR-02, not here.

**No synchronous calls.** There is no synchronous call from Triage to the Assistant (NFR-04), and no synchronous call from the Assistant to Triage (RF-04-B). Triage keeps serving with the Assistant stopped. A future proposal to add a synchronous client in either direction requires a new ADR. Alerting that Triage itself delivers for EVT-02 (C-09) is not a call to the Assistant; its delivery mechanism is a separate open decision (G-T09).

## Consequences

- Positive: TC-09, TC-10, NFR-01, NFR-04 and RF-04-B have one explicit boundary; all capabilities C-01…C-11 have an owner.
- C-01 remains in both services; C-02, C-03, C-04, C-08 and C-09 remain in Triage; C-05 spans both; C-06 and C-07 remain in the Assistant; C-10 and C-11 apply to both.
- Negative: Triage has a wide responsibility set and the Assistant projection is eventually consistent; neither is moved to a third service by this ADR.
- ADR-02, ADR-04, ADR-08, ADR-09 and ADR-11 keep their current service scopes without amendment.
- ADR-03 depends on this boundary: the outbox publisher must deliver with the Assistant absent. ADR-14 depends on it for database ownership.
- G-T08 (the EVT-03 "Triage metrics" consumer conflict) and CA-G-10 (the Assistant consumed-event list) remain tracked in their service analyses; this ADR fixes the service boundary, not those gaps.

## Risks and mitigations

- **TC-09 ambiguity:** the brief says "minimum two" while the roadmap and analysis say "only two"; the caveat stays in the status and the developer resolves it at G-01.
- **R-07 (dual writes):** the split makes database/Kafka consistency a boundary risk; ADR-03 addresses it with the transactional outbox.
- **R-08 (duplicate or out-of-order messages):** event-only integration shifts correctness to idempotent consumers under DER-03; ADR-02 and ADR-08 carry that mitigation.
- **NFR-04 dependency creep:** verify that neither service adds a synchronous client to the other; any proposal to do so requires a new ADR.
- **G-01 gate:** T-01 and change 1 do not start until this ADR is accepted (`implementation-roadmap.md`, `openspec-roadmap.md`).

## Verification

- Before T-01, record G-01 as accepted in `implementation-roadmap.md`; an unaccepted status blocks change 1.
- Change 1 done check (`openspec-roadmap.md`): exactly two deployable service projects exist, Triage and Clinical Assistant, and both start under the criteria of that change.
- NFR-04 test: stop the Assistant and confirm registration, transitions and escalation still succeed, and assert that Triage makes no outbound HTTP call to the Assistant (`triage-service.md` test plan, `openspec-roadmap.md` change 5).
- RF-04-B test: with Triage unreachable, `GET /asistente/estado-urgencias` still answers from the projection (`openspec-roadmap.md` change 9).
- Architecture check: neither service declares a client, SDK or SQL connection to the other; the only inter-service channel is `triaje.eventos`.
- TST-RXN: BlockHound or equivalent shows no blocking call on either service's request threads (`requirements.md` §13).
