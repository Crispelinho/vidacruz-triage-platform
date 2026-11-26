# ADR-03 Gap Resolutions: Transactional Outbox Specification

**Date:** 2026-11-25  
**Status:** Gap Analysis for ADR-03 Acceptance  
**Linked to:** DEC-14 (transactional outbox), R-07 (dual writes), OPEN-09/10 (outbox durability)

---

## Summary

ADR-03 (Transactional Outbox) requires three precision specifications to close gaps and enable Change 2 (event-driven-integration) acceptance:

1. **Lease budget configuration** — explicit timeouts and backoff limits
2. **Flyway schema for failure tracking** — columns for durability and observability
3. **Error classification logic** — transient vs. row-level error handling

---

## Gap 1: Lease Budget Configuration

### Current State
ADR-03 §DEC-14 proposes the outbox pattern but does not specify concrete timeout values, risking publisher misconfiguration.

### Specification

**Publisher retry configuration:**

| Parameter | Value | Rationale |
|-----------|-------|-----------|
| `send-timeout` | 10 seconds | Kafka producer timeout; network RT + jitter |
| `max-backoff` | 32 seconds | Exponential backoff cap (2^5); prevents long waits |
| `lease-seconds` | **90 seconds** | Must satisfy: `lease-seconds > send-timeout + max-backoff + margin(10s)` = 52s + 10s = 62s minimum; use 90s for safety |
| `initial-backoff` | 100 milliseconds | First retry delay |
| `backoff-multiplier` | 2.0 | Exponential growth factor |
| `max-retries` | 10 | Attempts before marking `requiere_revision=true` |

**Rationale:**
- `lease-seconds` must exceed the worst-case retry window to prevent concurrent publishers claiming the same event
- 90s ensures at least one full backoff cycle completes before lease expiry
- Configuration must be overridable via environment variable `TRIAGE_OUTBOX_LEASE_SECONDS` for testing (e.g., 30s in integration tests)

**Verification:**
- Integration test: publish with Kafka down; verify event stays in outbox; resume Kafka; verify publication completes within lease window
- Unit test: verify retry backoff never exceeds max-backoff; cumulative time ≤ lease-seconds

---

## Gap 2: Flyway Schema for Failure Tracking

### Current State
ADR-03 specifies the outbox pattern but does not name the columns needed to track failed deliveries and trigger manual intervention.

### Specification

**Table: `outbox_pending_events` (Triage database)**

```sql
CREATE TABLE outbox_pending_events (
  event_id UUID PRIMARY KEY,
  event_type VARCHAR(50) NOT NULL,
  schema_version INT NOT NULL DEFAULT 1,
  paciente_id UUID NOT NULL,
  paciente_sequence INT NOT NULL,
  occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
  correlation_id VARCHAR(255) NOT NULL,
  payload JSONB NOT NULL,
  
  -- Publication tracking
  created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
  publicado_en TIMESTAMP WITH TIME ZONE,
  
  -- Failure tracking (new columns for Gap 2)
  intentos INT NOT NULL DEFAULT 0,
  primer_fallo_en TIMESTAMP WITH TIME ZONE,
  ultimo_fallo_en TIMESTAMP WITH TIME ZONE,
  ultimo_error TEXT,
  requiere_revision BOOLEAN NOT NULL DEFAULT false,
  
  -- Indexes
  INDEX idx_publicado_null (publicado_en) WHERE publicado_en IS NULL,
  INDEX idx_requiere_revision (requiere_revision),
  INDEX idx_paciente_sequence (paciente_id, paciente_sequence),
  UNIQUE(paciente_id, paciente_sequence)
);
```

**Column descriptions:**

| Column | Type | Purpose | Lifecycle |
|--------|------|---------|-----------|
| `intentos` | INT | Retry attempt count | Incremented by publisher on each send attempt |
| `primer_fallo_en` | TIMESTAMP | First failure timestamp | Set on first failed attempt; never updated |
| `ultimo_fallo_en` | TIMESTAMP | Most recent failure timestamp | Updated on each failed attempt |
| `ultimo_error` | TEXT | Last error message/code | Truncated to 500 chars; updated on each failed attempt |
| `requiere_revision` | BOOLEAN | Requires manual review | Set to true when `intentos >= max_retries` |

**Rationale:**
- `intentos`: Enables publisher to enforce max-retry limit without querying; visible in monitoring
- `primer_fallo_en` / `ultimo_fallo_en`: Distinguish transient issues (many attempts within hours) from persistent ones (days)
- `ultimo_error`: Operational debugging (Kafka auth error? broker down? full partition?)
- `requiere_revision`: Triggers alerting; prevents silent loss of events

**Verification:**
- Integration test: insert outbox row; force publish to fail; verify `intentos` incremented, `ultimo_fallo_en` updated
- Schema validation test: Flyway migration applies without errors; constraints enforced
- Query test: SELECT outbox rows WHERE `requiere_revision=true` returns only rows that exceeded retry limit

---

## Gap 3: Error Classification Logic (retryWhen)

### Current State
ADR-03 describes at-least-once delivery but does not specify which Kafka errors are transient (retry) vs. persistent (log and skip).

### Specification

**Error classification for publisher retryWhen logic:**

### Transient Errors (RETRY with backoff)
*Errors indicating temporary unavailability; retrying is expected to succeed.*

| Error Class | Examples | Kafka Exception | Backoff | Max Attempts |
|-------------|----------|---|---------|---|
| Broker unavailable | No brokers available; broker timeout | `org.apache.kafka.errors.RetriableException` | Exponential 100ms→32s | 10 |
| Network I/O | Connection reset; socket timeout | `IOException` subclass | Exponential | 10 |
| Quota exceeded | Producer quota; throttling | `QuotaExceededException` | Exponential + jitter | 10 |
| Request timeout | Send timed out; request queued too long | `TimeoutException`, `RequestTimedOutException` | Exponential | 10 |
| Coordinator not ready | Rebalancing; leader election | `NotCoordinatorException`, `NotControllerException` | Exponential | 10 |

### Non-Transient Errors (LOG & SKIP, no retry)
*Errors indicating a misconfiguration or bad data; retrying will fail identically.*

| Error Class | Examples | Kafka Exception | Action |
|-------------|----------|---|---|
| Authorization | Invalid credentials; not authorized to topic | `AuthorizationException` | Log at ERROR; set `requiere_revision=true` |
| Invalid message | Serialization failed; bad schema | `SerializationException` | Log at ERROR; set `requiere_revision=true` |
| Topic/partition errors | Topic does not exist; partition offline | `UnknownTopicOrPartException`, `OffsetOutOfRangeException` | Log at ERROR; set `requiere_revision=true` |
| Producer stopped | Service shutting down | `ProducerFenced` | Log at WARN; mark event unfinished (attempt next startup) |

### Decision Flowchart (Publisher logic)

```
Kafka send fails with exception E
├─ Is E instanceof RetriableException?
│  ├─ YES: incrementar intentos; if intentos < max_retries → exponential backoff → retry
│  └─ NO: continue
├─ Is E TimeoutException or RequestTimedOutException?
│  ├─ YES: same as transient → exponential backoff → retry
│  └─ NO: continue
├─ Is E AuthorizationException or SerializationException?
│  ├─ YES: set requiere_revision=true; log ERROR; skip to next event
│  └─ NO: continue
└─ Otherwise: log at WARN with exception details; set requiere_revision=true; skip

After max_retries exceeded (intentos >= 10):
├─ Set requiere_revision=true
├─ Emit metric: outbox.event.max_retries_exceeded
├─ Alert: operational dashboard flag
└─ Next startup: skip events with requiere_revision=true (no auto-recovery; requires manual review)
```

### Thresholds

| Decision | Value | Rationale |
|----------|-------|-----------|
| `max-retries` | 10 | Exceeds 90s lease window; allows ~1 retry per 9s over backoff cap |
| Auto-mark `requiere_revision` | At `intentos >= 10` | Signal human operator; persistent errors surfaced within seconds (not hours) |
| Alerting threshold | Any event with `requiere_revision=true` for > 1 hour | Signals operational issue (misconfiguration or infrastructure problem) |

**Verification Checklist:**

- [ ] Transient Kafka errors (broker down, timeout) result in retry with backoff
- [ ] Non-transient errors (auth, serialization) set `requiere_revision=true` immediately
- [ ] `max-retries=10` enforced before marking `requiere_revision=true`
- [ ] Publisher emits metric `outbox.event.max_retries_exceeded` per error class
- [ ] Rows with `requiere_revision=true` are queryable for operational alerts
- [ ] A forced Kafka failure + recovery cycle completes publishing of all events
- [ ] A permanent auth failure marks the event `requiere_revision=true` without infinite retry

---

## Implementation Impact on Change 2

These specifications enable:

1. **Outbox Publisher design** — knows concrete timeouts, backoff limits, and when to give up
2. **Flyway migration** — creates the failure-tracking table from the first deployment
3. **Consumer integration test** — can mock transient vs. non-transient errors and verify handling
4. **Operational observability** — alerts on `requiere_revision=true` events before they age
5. **ADR-03 acceptance criterion** — fulfills DEC-14 (transactional outbox) with explicit, verifiable behavior

---

## Traceability

- **Requirement:** DEC-14 (transactional outbox), R-07 (dual writes), NFR-02 (Kafka integration)
- **Capability:** C-05 (event-driven-integration); blocks C-02, C-03, C-04 (all Triage changes depend on outbox)
- **Decision:** ADR-03 (Transactional Outbox)
- **Testing:** TST-OTX (outbox transactionality), TST-RET (retry behavior), TST-RES (resilience to broker outage)

---

## Next Steps for Change 2 Acceptance

1. ✅ Integrate Gap 1 (lease budget) into ADR-03 §Configuration
2. ✅ Integrate Gap 2 (Flyway schema) into ADR-03 §Persistence with CREATE TABLE DDL
3. ✅ Integrate Gap 3 (error classification) into ADR-03 §Verification with the flowchart and checklist
4. 🔄 Change 2 spec review: validate values, timeouts, and error handling logic
5. 🔄 ADR-03 acceptance: Stakeholder verdicts confirm the three gaps are resolved
6. 🔄 Change 2 implementation: Flyway migration + publisher + tests per checklist

---

**Document prepared for:** ADR-03 Gap Resolution Review  
**Status:** Ready for integration into ADR-03 §4 (Configuration, Persistence, Verification)
