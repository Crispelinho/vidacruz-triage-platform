# OPEN-09 / OPEN-10: Outbox durability and projection recovery source

- **Status:** Open. Not decided. Decide before ADR-02 and ADR-03 are accepted.
- **Date:** 2026-11-25
- **Owner:** developer
- **Type:** Design decision (verification after the decision)
- **Blocks:** acceptance of ADR-02 (Kafka consumer, dedupe TTL, rebuild source) and ADR-03 (transactional outbox, published-row retention). It also feeds ADR-08 (rebuild of the projection) and ADR-14 (what lives in the Triage database).
- **Related:** DEC-14, DER-02, DER-03, DER-09, OD-3, OD-5, G-T06, G-T29, R-07, R-08, C-05, C-07, [requirements.md](../requirements.md) §10 and §11, [capabilities-map.md](../capabilities-map.md) C-05 and C-07, [triage-service.md](../services/triage-service.md) §4.3, [ADR-02](../adr/ADR-02-assistant-kafka-consumer.md), [ADR-08](../adr/ADR-08-urgency-projection-store.md), [ADR-14](../adr/ADR-14-database-ownership-one-db-per-service.md), `openspec/changes/event-driven-integration/spec.md`

## Origin of the identifiers

OPEN-09 and OPEN-10 come from the open-items register on the branch `analysis-clinical-assistant` (`docs/adr/open-items-register.md`), which is not part of `develop`. There, OPEN-09 is the contradiction about the rebuild source of the projection (service analysis section 8.5 versus OD-3) and OPEN-10 is the implementation of `EventoHistorico` in Triage. This record answers both with one question. The register itself is not imported here, so these identifiers are not yet defined anywhere on `develop`.

## Question

Triage writes each event twice in the business transaction: an `OutboxEvento` row for delivery and, under OD-3, an `EventoHistorico` row for reconstruction. Which of the two is the durable record?

- **Option A:** the outbox is temporary (published rows are cleaned up after a retention window) and `EventoHistorico` is the durable history.
- **Option B:** the outbox is never cleaned up and serves as the durable history, with no separate `EventoHistorico`.

## What the documents on `develop` already say

- `requirements.md` §10 and §11 (OD-3, decided by the developer on 2026-11-24): Triage keeps every published event in `EventoHistorico`, written in the same transaction as the state change and its outbox row; Kafka keeps `triaje.eventos` for 30 days; a full rebuild reads `EventoHistorico` and republishes with the original `eventId`, `pacienteSequence` and payload; `EventoHistorico` is append-only and is not purged by time until OD-5 is decided.
- `triage-service.md` §4.3: published outbox rows are deleted after a configurable retention; the value is not set (G-T29, proposal: 7 days).
- The draft of `spec.md` for C-05 said "24 hours minimum" for outbox cleanup and linked it to OD-3. That value is not decided; OD-3 concerns Kafka retention and the history, not the outbox cleanup.
- The 30-day Kafka retention is recorded in documents only. No executable Kafka configuration is verified (ADR-02).

Option A is therefore what the current documents describe. This record stays Open because the outbox cleanup value (G-T29), the retention of `EventoHistorico` (OD-5) and the effect on ADR-02 and ADR-03 are not decided, and because OPEN-09 is a contradiction that is still listed as open in the register.

## Options

| | A. Temporary outbox + `EventoHistorico` | B. Outbox kept indefinitely |
|---|---|---|
| Durable record | `EventoHistorico` (append-only) | `OutboxEvento` itself |
| Outbox cleanup | Published rows deleted after a configurable window (value pending: G-T29 proposes 7 days; 24 hours was also suggested) | None |
| Writes per event | Two rows in one transaction (outbox and history) | One row |
| Append-only enforcement | Possible on `EventoHistorico` (no UPDATE or DELETE grants). The outbox needs UPDATE to set `publicadoEn` | Not possible without breaking publication: the same row must be updated when published, so it cannot be append-only |
| Size of the hot table | Outbox stays small; the pending-row partial index stays effective | Table grows with every event, including the rows the publisher scans; vacuum and bloat pressure on the polling query |
| Consistency with current documents | Matches OD-3, DER-09 and `requirements.md` §11 | Contradicts OD-3 and removes `EventoHistorico` and DER-09 from the persistence model |
| Retention and privacy | One decision on `EventoHistorico` (OD-5); the outbox is operational and short-lived | The operational queue and the clinical history share one retention decision (OD-5) and one erasure problem |
| Rebuild beyond the 30-day Kafka window | Reads `EventoHistorico`, republishes in `pacienteSequence` order | Reads the outbox and republishes |
| Main risk | Two writes can drift if the transaction is not shared; needs a forced-failure test | Operational and audit concerns mixed in one table; growth; no append-only guarantee |

## Decision criteria

1. **Projection recovery (C-07):** after the projection store is emptied, the rebuild restores the same counts, including events older than the Kafka retention window, without any call from the Assistant to Triage.
2. **Idempotency (C-05):** republished events keep their original `eventId` and `pacienteSequence`, so consumers ignore duplicates. The `ProcessedEvent` TTL must cover the effective Kafka retention plus a margin (ADR-02, ADR-08); republishing old events must not reopen a window in which the dedupe marker has already expired.
3. **Kafka retention:** the 30-day value of OD-3 must exist as verified configuration before the rebuild design relies on it.
4. **Append-only and audit:** whichever table is the durable record must be enforceable as append-only at database level (DER-04 style grants).
5. **Growth and retention:** the table that holds the durable record needs a retention policy (OD-5). The cleanup of the operational outbox needs its own value (G-T29).
6. **Write cost and atomicity:** the extra write in Option A must stay inside the same R2DBC transaction as the state change (DER-02, R-07).

## What this record does not decide

- The numeric value of the outbox cleanup window (G-T29).
- The retention period of `EventoHistorico` (OD-5), which needs a clinical data retention and privacy review.
- The assignment strategy of `pacienteSequence` (G-T06); the rebuild order depends on it.
- The backend of consumer deduplication (ADR-08, Proposed).
- The republication target topic and the cut point between replayed and live events.

## Closing conditions

- The developer chooses A or B and records the choice here, with the date.
- If A: the outbox cleanup value is set (G-T29) and the C-05 spec states it as configuration; `EventoHistorico` stays as documented; OD-5 remains tracked separately.
- If B: `requirements.md` §10, §11 and §16 (OD-3, DER-09, `EventoHistorico`), `capabilities-map.md` and ADR-02, ADR-08 and ADR-14 need coordinated changes. They are not made here.
- A transactional test is defined that forces a failure between the outbox write and the history write and shows that neither row persists.
- A rebuild test is defined that restores the projection counts from the durable record, with events older than the Kafka window.
- ADR-02 and ADR-03 are updated to cite the closed decision before they move to Accepted.
