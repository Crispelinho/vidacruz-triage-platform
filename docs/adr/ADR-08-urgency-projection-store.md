# ADR-08: Urgency projection store for the Clinical Assistant

- **Status:** Proposed. Not accepted. Option A is the proposed route; G-11, G-24 and the API-05 error code remain open.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** C-07, RF-04-B and API-05. This ADR chooses the store and update model for the urgency projection. It does not change the event envelope (ADR-02), the projection consumer technology (ADR-02), or the retention decision OD-3.
- **Related:** RF-04-B, API-05, DEC-10, DER-03, DER-09, NFR-01, NFR-02, NFR-19, R-08, G-07, G-11, G-24, OD-3, OD-5, ADR-02, TST-PRJ in [clinical-assistant-service.md](../services/clinical-assistant-service.md), [capabilities-map.md](../capabilities-map.md) and [adr-roadmap.md](../adr-roadmap.md)

## Context

API-05 returns the count of patients not in `ALTA`, grouped by level. RF-04-B requires that it be built only from `triaje.eventos`, with no synchronous call to Triage (NFR-04). DEC-10 reserves Redis for the Assistant projection, and the source of truth for patients remains Triage. The projection is rebuildable, not authoritative.

D1 §11 says "Hash per level", but the exact layout is ambiguous (G-11). Section 8.2 of the service analysis proposes one count hash with five level fields, one hash per patient holding `nivel`, `estado`, `ultimaSecuencia` and `contado`, and one expiring key per `eventId` for dedupe. Section 8.3 defines the atomic Lua rules: set `nivel` once, apply state only when `pacienteSequence` advances, treat `ALTA` as terminal, and recompute `contado` as an exact delta. A Redis set cannot give per-member TTL, so the processed-event set is implemented as one expiring key per event.

Rebuild uses the retained topic and, for full reconstruction, the durable `EventoHistorico` in Triage (DER-09, OD-3). No snapshot is required for correctness. OD-5, the retention period of `EventoHistorico`, is still open and blocks the production retention policy. This ADR proposes the dedupe TTL and Redis persistence mode; ADR-02 records the acknowledgement-order consequence but does not decide them.

## Decision drivers

- Atomic counts under duplicate and out-of-order events (R-08, DER-03).
- No Triage API call on the request path (NFR-04, RF-04-B).
- Correct reconstruction after the projection is emptied (TST-PRJ, OD-3).
- Non-blocking Redis access (NFR-01).
- Low-cardinality metrics: no patient ID or free text in labels (NFR-19).
- A defined 503 when the projection is unavailable (API-05, G-07).

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Redis with atomic Lua updates, per-patient hashes and expiring event markers** | One count hash, one hash per patient and one `SET NX PX` marker per event; all rules run in one Lua script | Matches DEC-10 and the reactive skeleton; atomic read/update; rebuildable; dedupe has a natural TTL | Durability and TTL values are still open; multi-key scripts across Redis Cluster slots fail; patient keys grow with every patient ever seen | **Proposed**, not rejected. Acceptance depends on G-11 and G-24 |
| **B. PostgreSQL projection table in the Assistant database with R2DBC** | Store counts and per-patient state in relational tables; dedupe in an indexed table with TTL cleanup | Durable, familiar semantics; no Redis Cluster constraint; transactional updates | Adds R2DBC writes to the consumer path and more load on the Assistant database; TTL cleanup becomes a job; loses the purpose of the Redis reservation in DEC-10 | Rejected: DEC-10 reserves Redis for this projection and the skeleton already provides reactive Redis |
| **C. In-memory map with periodic snapshot** | Keep counts in process memory and persist a snapshot | Simplest implementation; no external store dependency | Projection lost on restart unless the snapshot is current; no dedupe across instances; snapshot and replay leave a correctness gap | Rejected: it does not meet the rebuild and idempotency requirements |

## Decision

**Proposed: Option A.** Keep `EstadoUrgenciaProyeccion` in Redis as a projection, not a source of truth. Adopt the section 8.2 key layout and the section 8.3 Lua update rules, including `pacienteSequence` ordering and `eventId` dedupe. Use a single Redis node in the three-day scope; if a cluster is used instead, all keys touched by one script must share a hash tag. Two rebuild modes are distinguished: replay from the earliest retained Kafka offset for a normal reset, and full reconstruction from `EventoHistorico` under OD-3 when the retained window is not enough. The full reconstruction does not depend on the Kafka retention window.

Redis persistence is proposed as AOF, with the fsync policy set from the measured loss tolerance; `no` persistence is rejected. The `ProcessedEvent` TTL is proposed as the effective Kafka retention plus a safety margin; the numeric margin is pending explicit confirmation under G-11. ADR-02 depends on this durability level for its acknowledgement order and does not decide it. The API-05 503 error code is pending G-07. This is a proposal, not an accepted decision.

## Consequences

- Positive: counts are atomic under duplicates and out-of-order events, and Triage is never called from the request path.
- Negative: correctness after a Redis restart depends on a durability policy that is not yet chosen; Redis Cluster imposes a hash-tag constraint.
- C-07 inherits the rebuild procedure and its dependency on DER-09, OD-3 and OD-5.
- ADR-02 owns the consumer path, the DLT rules and the acknowledgement order; this ADR owns only the store and update rules.
- C-11 must expose projection errors as metrics without patient identifiers (NFR-19).

## Risks and mitigations

- **R-08 (duplicates and out-of-order events):** Lua rules use `eventId` markers and `pacienteSequence`; the worked examples in section 8.3 are test cases.
- **G-11 (layout, persistence and TTL):** adopt section 8.2/8.3, AOF persistence and the retention-plus-margin TTL; confirm the numeric margin explicitly before acceptance.
- **G-24 (rebuild completeness):** replay from `EventoHistorico` rather than relying only on Kafka retention; the production retention period remains OD-5.
- **G-07 (API-05 error contract):** define the error code and detection rule before the client contract is frozen.
- **NFR-01:** use reactive Redis; no `.block()` on the consumer or request path.

## Verification

- **TST-PRJ replay:** clearing the projection and replaying from the earliest retained offset restores the same counts as live consumption; full reconstruction from `EventoHistorico` restores the same counts without relying on the Kafka window.
- **TST-PRJ edge cases:** duplicate events, out-of-order `pacienteSequence`, EVT-04 before EVT-01, and a gap in state sequence follow section 8.3 with no negative counts.
- **TST-PRJ unavailability:** Redis timeout on API-05 returns 503 with the code agreed under G-07.
- **NFR-19 evidence:** metric labels contain no patient ID and no free text.
- The evidence belongs in the `assistant-urgency-projection` change of `implementation-roadmap.md` (T-16, T-17).
