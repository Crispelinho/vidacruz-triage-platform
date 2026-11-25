# ADR-03: Transactional Outbox Pattern for Event Publication

- **Status:** Proposed. Not accepted. The claiming protocol and the broker-outage posture are specified below; the outage posture is accepted (developer, 2026-11-25) as backoff only with no circuit breaker. The remaining verification gates still apply before this ADR and ADR-02 can be accepted.
- **Date:** 2026-11-25
- **Deciders:** developer (broker-outage posture decided 2026-11-25; ADR acceptance pending)
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
3. **Broker outage behavior:** If Kafka is unreachable for hours, what does the publisher do? Keep retrying with backoff (simpler, unbounded retries), or open a circuit breaker after N failures (bounds retries and load, but adds state)? **Closed 2026-11-25: backoff only, no breaker.**

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **A. Short lease claim + backoff only (accepted outage posture, 2026-11-25)** | Claim rows in a short transaction that stamps `claimado_por` and `claim_expira_en`; no DB lock or transaction is open during the Kafka ACK. `FOR UPDATE SKIP LOCKED` is used only inside the short claim transaction. Backoff bounds retry latency per row. | An expired lease can let another replica resend a row; duplicates are handled by ADR-02. Lease duration is configuration. |
| **B. Short lease claim + circuit breaker (Resilience4j)** | Same claim protocol, plus a breaker around the send; bounds broker load during a prolonged outage. | Adds a state machine (closed, open, half-open) and explicit failure classification so a bad row does not open the breaker. **Rejected for this scope (2026-11-25):** no evidence yet that the extra state is needed; may be revisited in a new ADR. |
| **C. `FOR UPDATE SKIP LOCKED` held until ACK** | No lease columns; one transaction covers claim, send and mark. | Holds row locks during network I/O, serializes replicas on hot rows and increases contention during a broker outage. **Rejected.** |
| **D. Polling without claiming (no mutual exclusion)** | Simplest: publisher just polls and sends. | Multiple replicas send the same row; Kafka receives duplicates; consumers must handle them (DER-03). Higher broker load. Only viable if the broker can absorb the extra load. |

---

## Decision

**Proposed: Option A.** Claim with a short lease transaction, send outside any database transaction, then mark published in a separate guarded transaction. Exponential backoff applies to transient failures. The broker-outage posture is also decided: backoff only, no circuit breaker (developer, 2026-11-25); it is recorded below.

**Row claiming (short transaction):**
- Each publisher cycle claims a bounded batch by atomically stamping a lease. The claim transaction commits before any Kafka call:
  ```sql
  UPDATE outbox_evento
     SET claimado_por = :publisherId,
         claimado_en = CURRENT_TIMESTAMP,
         claim_expira_en = CURRENT_TIMESTAMP + (:leaseSeconds * INTERVAL '1 second'),
         actualizado_en = CURRENT_TIMESTAMP
   WHERE id IN (
       SELECT id
         FROM outbox_evento
        WHERE publicado_en IS NULL
          AND requiere_revision = FALSE
          AND (claim_expira_en IS NULL OR claim_expira_en <= CURRENT_TIMESTAMP)
        ORDER BY id
        LIMIT :batchSize
        FOR UPDATE SKIP LOCKED
   )
  RETURNING id, event_id, event_type, schema_version, paciente_id,
            paciente_sequence, occurred_at, correlation_id, payload;
  ```
- `FOR UPDATE SKIP LOCKED` is used only inside this short claim transaction to make the lease stamp atomic across replicas; the row lock is released on commit.
- A row becomes eligible for reclaim when its lease expires (`claim_expira_en <= CURRENT_TIMESTAMP`). A crash or an expired lease can cause a duplicate send; ADR-02 deduplicates by `eventId`.

**Publication (no database transaction during ACK):**
- Send each claimed row with the non-blocking client (reactor-kafka or adapter; G-T25) and wait reactively for the broker acknowledgement. No R2DBC transaction and no row lock are open while waiting.
- On success, mark the row published in a separate short transaction, guarded by ownership:
  ```sql
  UPDATE outbox_evento
     SET publicado_en = CURRENT_TIMESTAMP,
         publicado_por = :publisherId,
         claimado_por = NULL,
         claimado_en = NULL,
         claim_expira_en = NULL,
         actualizado_en = CURRENT_TIMESTAMP
   WHERE id = :id
     AND publicado_en IS NULL
     AND claimado_por = :publisherId;
  ```
  If the update affects 0 rows, the lease expired or another publisher won; the event may already be in Kafka and the duplicate is handled by ADR-02.
- On failure, release the claim only if this publisher still owns it, then apply failure tracking:
  ```sql
  UPDATE outbox_evento
     SET claimado_por = NULL,
         claimado_en = NULL,
         claim_expira_en = NULL,
         actualizado_en = CURRENT_TIMESTAMP
   WHERE id = :id
     AND claimado_por = :publisherId;
  ```

**Failure handling:**
- **Transient failure (network timeout, broker slow):** Increment `intentos`, set `ultimo_fallo_en` and `ultimo_error`, release the claim and continue with the next row in the batch.
- **Persistent failure (bad row):** After the configured threshold and window (e.g., 10 retries within 24 hours), set `requiere_revision = TRUE`, release the claim, log and alert. Rows with this flag are skipped in future cycles until an operator clears them.
- **Broker outage:** Rows stay unpublished with released or expired leases and are reclaimed on later cycles with exponential backoff. No row is lost; no circuit breaker is used (accepted posture, 2026-11-25).

**Configuration (illustrative, not decided):**
```yaml
outbox:
  publisher:
    batch-size: 100
    send-timeout-ms: 10000
    lease-seconds: 30          # must exceed send-timeout-ms
    retry-base-delay-ms: 5000
    retry-max-delay-ms: 60000
    retry-multiplier: 1.5
    failure-threshold: 10
    failure-window-hours: 24
```

**Publisher loop (pseudocode):**
1. Claim a batch of unpublished rows (short lease transaction, then commit).
2. For each row, outside any database transaction:
   - Send to Kafka with the non-blocking client and wait reactively for acknowledgement.
   - On success: mark published in a separate guarded transaction.
   - On failure: release the claim and apply failure logic (§3.4 in Spec C-05).
3. Repeat at a configurable interval (e.g., every 5 seconds).

---

## Broker Outage: Backoff Only (Accepted 2026-11-25)

**Decision (developer): backoff only against the same broker; no circuit breaker and no second broker.** The outbox keeps every event while Kafka is unavailable, and the request path stays unaffected because registration only writes to PostgreSQL (NFR-04). Exponential backoff with a configurable cap bounds the rate of send attempts when the broker returns, avoiding a retry storm. A circuit breaker would add states, thresholds and metrics for a single dependency without evidence that it is needed; NFR-03 requires a breaker only for LLM calls (ADR-09). A second broker or alternate cluster is not introduced by this decision.

**Rejected for this scope:** a Resilience4j breaker around the send. It would need explicit failure classification so a row-level fault does not open the breaker, and it offers no request-path benefit. It may be proposed later in a new ADR if production metrics show unacceptable backlog or broker load.

**Future enhancement, not an open gate:** configuration keeps the failure policy separate so a breaker can be added without rewriting the publisher. This is an evidence-conditioned option, not a pending decision that blocks this ADR.

---

## Consequences

### Positive
- Short lease claiming is deterministic: `FOR UPDATE SKIP LOCKED` prevents two replicas from holding a valid lease on the same row at the same time.
- No database transaction or row lock is held while waiting for a Kafka acknowledgement, so a slow broker cannot block other writers.
- Backoff keeps retries bounded per row; no infinite loops.
- Failure tracking (`intentos`, `ultimo_fallo_en`, `requiere_revision`) makes debugging easier.
- Request path remains non-blocking (NFR-01, NFR-04).
- DER-02 (transactional guarantee) is preserved: state change and event are atomic.

### Negative
- **On a long broker outage:** rows backlog in the outbox. The publisher keeps reclaiming and retrying with backoff until the broker recovers or the row reaches the failure threshold.
  - **Mitigation:** Kafka retention (OD-3: 30 days) must be verified as configuration before this ADR is accepted. If the broker is down longer than retention, events are lost from Kafka and cannot be recovered from it; full reconstruction uses the accepted durable record `EventoHistorico` (OPEN-09/10 closed as Option A on 2026-11-25). The retained-outbox alternative was rejected.
- **Operator intervention required:** Rows flagged with `requiere_revision = TRUE` must be cleared manually or via a dashoard. No automatic recovery.
  - **Mitigation:** Alerting on threshold (NFR-07) so operators are notified immediately.

---

## Risks

- **R-07 (dual writes):** If the publisher sends the event but crashes before marking it published, the same row is sent twice on restart. DER-03 (idempotent consumers via `eventId` and `pacienteSequence`) mitigates this.
  - **Probability:** Low (crash after Kafka acknowledgement but before `publicado_en` is updated is rare).
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
- [ ] Short lease claim is tested under concurrent publisher replicas: no row has two valid leases at the same time.
- [ ] Slow-broker test proves no R2DBC transaction is open and no publisher row lock is held while waiting for the Kafka acknowledgement.
- [ ] Lease-expiry test proves a publisher killed after send but before `publicado_en` is updated can be reclaimed by another replica; ADR-02 deduplicates the resulting Kafka duplicate.
- [ ] Failure handling (transient vs persistent) is tested: a row that fails N times within the window is flagged; a successful send after failures resets the failure counter.
- [ ] Backoff is tested: a row with exponential backoff eventually succeeds after broker recovery, and the attempt rate stays bounded by the configured cap (no retry storm) with no circuit breaker in the publisher.
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

- **OD-3 (decided in documents, developer, 2026-11-24):** Kafka retains `triaje.eventos` for 30 days; `EventoHistorico` is the accepted durable source for full reconstruction (OPEN-09/OPEN-10 closed as Option A on 2026-11-25). G-T29 is closed at 7 days.
- **G-T25 (open):** Reactive Kafka client library (reactor-kafka or explicit adapter). spring-kafka is blocking and does not meet NFR-01.
- **Broker outage posture (closed 2026-11-25):** backoff only, no circuit breaker and no second broker; see Spec C-05 §3.3 and the decision recorded above. A future breaker remains conditional on operational evidence and a new ADR.
