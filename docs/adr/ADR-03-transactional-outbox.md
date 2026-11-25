# ADR-03: Transactional Outbox Pattern for Event Publication

- **Status:** Proposed. Not accepted. The publisher strategy (claiming, batch claiming, failure handling) and the broker-outage behavior (backoff only vs circuit breaker) are open and pending this ADR before ADR-02 (consumer) can be accepted.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** The Triage service outbox publisher: responsibility, claiming strategy, batch semantics, failure handling, and behavior during broker outages. It does NOT decide the outbox schema (ADR-14, Spec C-05 §2.1) or the consumer deduplication (ADR-02). ADR-03 sits between application writes (DER-02) and Kafka delivery.
- **Related:** DER-02 (transactional guarantee), DER-03 (idempotent consumer), DER-09 (durable event history), R-07 (dual writes), R-08 (duplicate/out-of-order), ADR-01 (service boundary), ADR-02 (consumer envelope and dedup), ADR-08 (store backend), ADR-14 (database ownership), Spec C-05 (outbox schema, publisher semantics, failure scenarios), `docs/services/triage-service.md` §4.3, `requirements.md` §10 (DER-02, DER-03, DER-09)

---

## Context

The brief requires the Triage service to publish patient lifecycle events (EVT-01…EVT-04) to Kafka without data loss, even if the broker is temporarily unavailable (R-07, R-08, NFR-04). DER-02 mandates that event publication and the state change be atomic: if the transaction rolls back, neither the row nor the event persist. DER-03 requires idempotent consumers: duplicates must not cause side effects.

The transactional outbox pattern meets this by:
1. Writing the event and the state change in a single R2DBC transaction (§2.2 in Spec C-05).
2. A reactive publisher drains the outbox asynchronously, keeping the request path non-blocking (NFR-01).
3. The publisher sends events to Kafka and marks them published only after broker acknowledgement.
4. Consumers are idempotent (ADR-02, DER-03), so duplicate delivery is safe.

The question is not whether to use the outbox pattern (DER-02 settles that), but how the publisher claims rows, retries on failure, and behaves during a prolonged broker outage.

---

## Problem

A reactive publisher faces three decisions:

1. **Row claiming under concurrency:** If multiple publisher replicas run, how do they avoid claiming and sending the same row twice at the same time?
2. **Failure classification and retry:** A send failure can be transient (network timeout, broker temporarily slow) or permanent (bad payload, bad row). How does the publisher distinguish and retry?
3. **Broker outage behavior:** If Kafka is unreachable for hours, what does the publisher do? Keep retrying with backoff (simpler, unbounded retries), or open a circuit breaker after N failures (bounds retries and load, but adds state)?

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **A. FOR UPDATE SKIP LOCKED + backoff only (recommended)** | Rows are claimed atomically within R2DBC; multiple replicas never claim the same row twice. Backoff bounds latency per row; simple to reason about. | Unbounded retry window on broker outage. No circuit breaker means repeated attempts can overload a recovering broker. |
| **B. FOR UPDATE SKIP LOCKED + circuit breaker (Resilience4j)** | Backoff + circuit breaker bounds both retry latency and broker load. Faster recovery signal (circuit is open, no sends attempted). | Adds state machine (closed, open, half-open). Requires explicit failure classification to avoid opening breaker on bad rows. |
| **C. Lease column (alternative claiming)** | No `FOR UPDATE` lock — a column (`lease_expires_at`) marks the row as claimed until timeout. Useful if `FOR UPDATE` is not available. | More complex state: expired leases must be cleaned up. Lock-free semantics require care on concurrent claims. |
| **D. Polling without claiming (no mutual exclusion)** | Simplest: publisher just polls and sends. | Multiple replicas send the same row; Kafka receives duplicates; consumers must handle (but DER-03 says they will). Higher broker load. Only viable if broker can absorb the extra load. |

---

## Decision

**Proposed: Option A.** Claiming via `FOR UPDATE SKIP LOCKED` in R2DBC, exponential backoff on transient failures, row-level error tracking and manual operator intervention for persistent failures.

**Row claiming:**
- Each publisher cycle, claim a bounded batch (e.g., 100 rows) with:
  ```sql
  SELECT id, ... FROM outbox_evento 
  WHERE publicado_en IS NULL AND requiere_revision = FALSE
  ORDER BY id
  FOR UPDATE SKIP LOCKED
  LIMIT :batch_size
  ```
- Multiple replicas running in parallel will never claim the same row: `FOR UPDATE` serializes them atomically.
- Lock is held while the row is sent to Kafka and marked published, within the same transaction.

**Failure handling:**
- **Transient failure (network timeout, broker slow):** Increment `intentos`, set `ultimo_fallo_en` and `ultimo_error`, release the lock and continue with the next row in the batch.
- **Persistent failure (bad row):** After the row reaches a threshold (e.g., 10 retries within 24 hours), set `requiere_revision = TRUE`, log and alert. Rows with this flag are skipped in future cycles until an operator clears it.
- **Broker outage:** Rows remain unpublished and backlog in the outbox. When the broker recovers, the publisher resumes draining. No circuit breaker; backoff caps retry latency per row.

**Configuration (illustrative, not decided):**
```yaml
outbox:
  publisher:
    batch-size: 100
    send-timeout-ms: 10000
    retry-base-delay-ms: 5000
    retry-max-delay-ms: 60000
    retry-multiplier: 1.5
    failure-threshold: 10
    failure-window-hours: 24
```

**Publisher loop (pseudocode):**
1. Claim a batch of unpublished rows (FOR UPDATE SKIP LOCKED).
2. For each row:
   - Send to Kafka with non-blocking client (reactor-kafka or adapter; G-T25).
   - Wait reactively for broker acknowledgement.
   - On success: mark published (`publicado_en`, `publicado_por`), release lock.
   - On failure: apply failure logic (§3.4 in Spec C-05), skip row, continue.
3. Repeat at a configurable interval (e.g., every 5 seconds).

---

## Broker Outage: Why Not Circuit Breaker?

The brief specifies only one circuit breaker requirement: LLM calls (NFR-03, ADR-09). Kafka is treated as reliable but potentially slow.

**Option A (backoff only):**
- Simpler: no additional state machine.
- Request path is unaffected: `POST /triage/pacientes` returns immediately regardless of publisher backlog (NFR-04).
- Broker load on recovery is bounded only by the backoff cap (e.g., max delay 60s between retries).

**Option B (circuit breaker):**
- Adds Resilience4j or equivalent: state transitions (closed → open → half-open), thresholds, metrics.
- Faster recovery signal: circuit opens after N failures, no sends are attempted until half-open succeeds.
- Requires failure classification: a bad row must not open the breaker (the row, not the broker, is at fault).
- Request path is still unaffected (NFR-04).

**Recommendation for Proposed:** Option A is simpler and sufficient for this scope. Circuit breaker can be added in ADR-09-2 (future enhancement) if broker recovery times become problematic.

---

## Open Decision: Circuit Breaker Post-Delivery

If production observability shows that broker outages cause unacceptable publisher backlog, a future ADR can add a circuit breaker. The decision is not whether to add it now, but whether to leave the door open: configuration should use a strategy pattern so the breaker can be plugged in without rewriting the publisher.

---

## Consequences

### Positive
- R2DBC claiming (`FOR UPDATE SKIP LOCKED`) is deterministic and prevents duplicate sends at the same time.
- Backoff keeps retries bounded per row; no infinite loops.
- Failure tracking (`intentos`, `ultimo_fallo_en`, `requiere_revision`) makes debugging easier.
- Request path remains non-blocking (NFR-01, NFR-04).
- DER-02 (transactional guarantee) is preserved: state change and event are atomic.

### Negative
- **On a long broker outage:** rows backlog in the outbox. The publisher keeps retrying with backoff until the broker recovers or the row reaches the failure threshold.
  - **Mitigation:** Kafka retention (OD-3: 30 days) must be verified as configuration before this ADR is accepted. If the broker is down longer than retention, events are lost and cannot be recovered; ADR-14 and ADR-08 must handle this scenario (rebuild projections from `EventoHistorico` or retained outbox; OPEN-09/10).
- **Operator intervention required:** Rows flagged with `requiere_revision = TRUE` must be cleared manually or via a dashoard. No automatic recovery.
  - **Mitigation:** Alerting on threshold (NFR-07) so operators are notified immediately.

---

## Risks

- **R-07 (dual writes):** If the publisher sends the event but crashes before marking it published, the same row is sent twice on restart. DER-03 (idempotent consumers via `eventId` and `pacienteSequence`) mitigates this.
  - **Probability:** Low (crash after Kafka acknowledgement but before transaction commit is rare).
  - **Impact:** Duplicate event in Kafka; consumer deduplicates it.

- **R-08 (out-of-order delivery):** Publisher sends rows in `id` order per batch, but multiple batches or restarts can reorder events across batches. The topic partition key (`pacienteId`) ensures per-patient order in Kafka, and the consumer applies `pacienteSequence` ordering (ADR-02, DER-03).
  - **Probability:** Medium (expected on restarts or multiple replicas).
  - **Impact:** Consumer applies order guard; no business impact if implemented correctly.

- **Failure classification ambiguity:** A timeout can be transient (retry) or permanent (bad row takes too long to serialize). The threshold-based approach (failure window + count) is a heuristic, not a guarantee.
  - **Mitigation:** Alerting on threshold and operator review before auto-flagging.

---

## Verification

The following must pass before this ADR transitions from Proposed to Accepted:

- [ ] Outbox schema (ADR-14, Spec C-05 §2.1) exists in both Flyway migrations with failure tracking columns (`intentos`, `primer_fallo_en`, `ultimo_fallo_en`, `ultimo_error`, `requiere_revision`).
- [ ] Publisher claiming query (`FOR UPDATE SKIP LOCKED`) is tested under concurrent publisher replicas; no row is claimed twice at the same time.
- [ ] Failure handling (transient vs persistent) is tested: a row that fails N times within the window is flagged; a successful send after failures resets the failure counter.
- [ ] Backoff is tested: a row with exponential backoff eventually succeeds after broker recovery.
- [ ] Kafka retention (OD-3: 30 days) is verified as configuration in the deployment pipeline before this ADR is accepted.
- [ ] Alerting is configured: when a row reaches the failure threshold, an alert is raised (NFR-07).
- [ ] ADR-02 (consumer) acceptance is not blocked by this ADR once claiming, failure handling and retry logic are in place.

---

## References

- Brief: TC-03 (R2DBC reactive), TC-12 (Flyway migrations)
- Requirements: DER-02 (atomic write + publish), DER-03 (idempotent consumer), DER-09 (durable history), NFR-01 (non-blocking request path), NFR-04 (Triage independent of Assistant), NFR-07 (observability)
- Risk register: R-07 (dual writes), R-08 (duplicate/out-of-order)
- Decision register: OD-3 (Kafka retention: 30 days), DEC-14 (outbox pattern decision, yes)
- Related ADRs: ADR-01 (service boundary), ADR-02 (consumer), ADR-08 (store backend), ADR-14 (database ownership), ADR-09 (circuit breaker for LLM only)
- Specifications: Spec C-05 (outbox schema §2.1, publisher §3, failure scenarios §6.1)
- Service analysis: `docs/services/triage-service.md` §4.3 (publisher, claiming, cleanup)

---

## Linked Open Decisions

- **OD-3 (decided in documents, developer, 2026-11-24):** Kafka retains `triaje.eventos` for 30 days; `EventoHistorico` in Triage is the durable source for full reconstruction.
- **G-T25 (open):** Reactive Kafka client library (reactor-kafka or explicit adapter). spring-kafka is blocking and does not meet NFR-01.
