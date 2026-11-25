# ADR-02: Kafka consumer model for the Clinical Assistant

- **Status:** Proposed. Not accepted. Option B is the proposed route. The sequence/duplicate/future-event protocol is fixed below; the open items in the "Open items" section, including the numeric Redis dedupe TTL and gap limits, remain unresolved pending explicit confirmation.
- **Scope:** Clinical Assistant consumer of `triaje.eventos`. The envelope is defined by DEC-03 (Proposed) and is not reopened here. This ADR does not present it as an accepted decision.
- **Related:** DEC-03, TC-04, NFR-01, CA-G-10, CA-G-11, CA-G-13, CA-G-18, CA-G-24, and CA-G-09 (to confirm) in [clinical-assistant-service.md](../services/clinical-assistant-service.md)

## Context

The Assistant builds its projection (Part B of RF-04) from events only. It must not call the Triage API. The projection writes to Redis, and Redis access is reactive. NFR-01 requires a non-blocking request path. The consumer is not on the HTTP request path, but it still must not block event-loop threads or call `.block()` on Redis operations.

The skeleton declares `spring-kafka` and uses `@KafkaListener` in its default style, which is a blocking consumer model. That mismatch is what this ADR proposes to resolve.

## Options

| Option | How it works | Pros | Cons |
|---|---|---|---|
| **A. `@KafkaListener` (blocking)** | Standard listener on a bounded thread pool; reactive Redis calls would need `.block()` | Matches the skeleton; simplest; best-documented path | Forces `.block()` inside the consumer for Redis, which conflicts with the reactive design and makes a BlockHound test fail on the consumer path |
| **B. `ReactiveKafkaConsumerTemplate` (spring-kafka + reactor-kafka)** | Reactive consumer with a `Flux` of records; bounded prefetch gives backpressure | Native fit with reactive Redis; explicit backpressure; no `.block()` | Requires reactor-kafka as an extra dependency. Its maintenance status is a risk. The claim that Spring Cloud Stream deprecated its Reactor Kafka binder in 4.3.0 is not verified against a source in this document |
| **C. Custom consumer loop** | Own poll loop around `KafkaConsumer` | Full control | Reimplements offset management, rebalancing and retries; not justified for this scope |

## Decision

**Proposed: Option B**, with the conditions below. Not accepted.

1. **Consumer technology:** `ReactiveKafkaConsumerTemplate` with reactor-kafka.
2. **Prefetch and backpressure:** bounded and configurable. The default value is set in configuration, not in code.
3. **Non-blocking processing:** no `.block()` anywhere in the consumer path. BlockHound covers this path in tests.
4. **Commit order:** the atomic Redis update (Lua, section 8.3 of the service analysis) must succeed and reach the durability level defined in "Redis durability" below before the offset is committed.
5. **Idempotency:** deduplication by `eventId` through the `ProcessedEvent` record.
6. **Order control:** `pacienteSequence` is compared per patient as defined in section 8.3 of the service analysis.
7. **Metrics:** consumer lag, processing errors, retry count, and number of messages sent to the DLT.

**Explicit exclusion:** `@KafkaListener` is not an automatic fallback. If reactor-kafka stops being viable, the options are (a) another reactive alternative, evaluated in a new ADR, or (b) a formal, explicit exception to NFR-01 approved by the user. Neither is decided here.

## Retry policy (consumer only)

- **Transient Redis errors** (timeouts, connection loss, failover): retry a limited number of times with backoff, without committing the offset during the retry window. The maximum attempt count and backoff values are configuration, not decided here.
- **Permanent processing errors** (malformed event, unknown `schemaVersion`, invariant violation): do not retry. Send to the DLT. A permanent error must never enter an infinite retry cycle.
- When the retry limit is reached for a transient error, the record is treated as failed and follows the DLT rule below.
- This policy is independent of the LLM retry policy, which is defined by CA-G-05.

## Failure handling

| Situation | Behavior |
|---|---|
| Unknown `schemaVersion` | Send to DLT (permanent, no retry) |
| Unknown `eventType` with a known `schemaVersion` | Discard in a controlled way; log and metric |
| Malformed event | Send to DLT (permanent, no retry) |
| Transient Redis error | Retry within the limit, without committing the offset |
| Duplicate event (`eventId` already processed) | Do not repeat the update; commit the offset after recognizing the duplicate |

**DLT rule:** the DLT publication must be confirmed before the original offset is acknowledged. The DLT record keeps the original message, topic, partition, offset, and rejection reason. The DLT record also carries the `eventId`, so DLT consumers can deduplicate after a redelivery.

**DLT failure rule:** if the DLT publication fails, the original offset is not acknowledged. The message stays unacknowledged and is redelivered. Losing the message is not acceptable; the cost is that the consumer stalls on that partition until the DLT is available. That stall must raise an alert through the lag metric.

**Partition recovery (to verify in the spike):**
- Only the affected partition is paused. The other partitions keep flowing, so one failing DLT write does not block the whole consumer. This requires per-partition pipelines; whether reactor-kafka can pause one partition without affecting the others is **not verified** and must be checked in the spike.
- While paused, the DLT publication is retried with backoff. The retry limit and backoff are configuration, not decided here.
- When the DLT accepts the record, the partition resumes from the failed record. No offset past that record is committed while it is pending.
- If the partition is reassigned during the pause (rebalance), the new owner starts from the last committed offset and reprocesses the record. The `eventId` deduplicates the DLT record if it was already published.

**Delivery guarantee:** the pipeline is at-least-once. A crash between the DLT publication and the offset acknowledgement produces a duplicate DLT record. The `eventId` makes that duplicate detectable.

## Sequence, duplicate and future-event protocol

The projection store keeps `lastApplied`, the highest `pacienteSequence` applied for each patient. The consumer applies this contract (Spec C-05 §4.1 C2 and §5.1):

1. `pacienteSequence <= lastApplied`: duplicate or stale event. No projection change, acknowledge the offset and increment the duplicate/stale metric.
2. `pacienteSequence = lastApplied + 1`: apply the event atomically with its `eventId` marker, then acknowledge. Continue with contiguous entries already pending for that patient.
3. `pacienteSequence > lastApplied + 1`: future event. Keep the record in a bounded, in-memory `pendingByPatient` map; do not apply it and do not acknowledge it. Continue consuming other patients and partitions.
4. When the missing contiguous sequences arrive, apply them in order and acknowledge each one.
5. If a pending entry exceeds the configured gap timeout or a new future event would exceed the pending-map limit, publish that event (the oldest pending entry if the map is full) to `triaje.eventos.dlq` with `dlqReason = "GAP_UNRESOLVED"`; acknowledge the original offset only after the DLT write is confirmed. No projection change is invented and the error metric/alert is the trace.
6. On restart, the pending map is rebuilt from unacknowledged redeliveries. No gap decision is persisted in the projection.

The numeric gap timeout and map size are configuration pending explicit confirmation under CA-G-11/CA-G-24; they do not change this protocol.

## Redis durability

"Durable" in this ADR means: the Redis write has been acknowledged under the persistence and replication policy that the team has chosen. The offset is not committed before that level is reached.

The concrete policy is **not decided here** (Redis durability, gap identifier to be confirmed). It must define:
- The persistence mode (for example AOF) and its fsync policy. With a policy that allows up to about one second of loss on crash, an acknowledged write can disappear after a restart, even though the offset was committed. That case is covered by rebuilding from Kafka retention when the window is sufficient, and by full reconstruction from Triage's durable record under OD-3 when it is not (`EventoHistorico` or retained outbox; OPEN-09/OPEN-10 is open, ADR-08), so the decisions depend on each other.
- Whether replication is used and what acknowledgement level the write must reach before it counts as durable.

## ProcessedEvent TTL (proposal, not accepted)

**Proposal:** `TTL ≥ effective Kafka retention + safety margin`, counted from the time the event is processed.

- The reason: once the `eventId` marker expires, a late redelivery is no longer caught by the marker. The sequence and level rules can still reject it, but the sequence rule depends on G-T06 (`pacienteSequence` generation and storage, registered in the Draft Triage service analysis), which is not decided. That protection is therefore not treated as a closed guarantee. The TTL must cover at least the retention period during which Kafka can still redeliver the record.
- The safety margin is a numeric value that is **pending**. It is not decided here.
- The 30-day retention of OD-3 is recorded as a decision in documents on `develop` (PR #6), but no executable Kafka retention configuration is verified in this branch.
- This proposal does not accept Option B, and it does not close CA-G-09, CA-G-11, CA-G-24, OD-3 or OD-5.
- CA-G-11 remains **Open** until the user confirms the TTL explicitly.

## Event list (resolved by the order protocol)

Proposed: the Assistant consumes **EVT-01** (patient registered), **EVT-02** (escalated), **EVT-03** (in consultation) and **EVT-04** (discharged). EVT-01/03/04 change projection state and counts; EVT-02 advances `pacienteSequence` and dedupe state without changing counts.

Status: CA-G-10 is resolved in favour of all four events. Skipping EVT-02 would create a false sequence gap under §5.1; consuming it preserves contiguity. D0 §8 already described all four, and D1 §10 / D0 §3.2 are updated accordingly.

## Test requirements for the DLT path

These tests are required before the consumer is considered complete. They are not written yet.

1. DLT publication succeeds, then the offset is acknowledged.
2. DLT is unavailable: the offset is not acknowledged, the message is redelivered, and the lag metric raises an alert.
3. Crash between the DLT publication and the offset acknowledgement: the original message is redelivered, a duplicate DLT record is produced, and the `eventId` identifies it.
4. Redelivery of the original message after a DLT record exists: the consumer does not corrupt the projection, and the second DLT record carries the same `eventId`.
5. Permanent error: no retry occurs; the message goes to the DLT once.

## Open items (unresolved)

- **CA-G-18:** version and documentation consistency: end of open-source support for Spring Boot 3.5 (AMB-12, R-02), compatibility of Spring Boot 3.5.16, Spring Cloud 2025.0.x and Spring AI 1.1.1, and the Java 21 versus Java 25 mismatch between D1 and the skeletons, to verify before the first build. Sub-point, pending verification: compatibility of reactor-kafka with those versions and its maintenance status. G-18 does not cover reactor-kafka by itself.
- **CA-G-11:** Redis dedupe TTL (`ProcessedEvent`) and its relation to Kafka retention (rebuild completeness: CA-G-24). **Pending.** The TTL value is not decided and is not accepted until the user confirms it explicitly.
- **Redis durability** (gap identifier to be confirmed): concrete configuration (persistence mode, fsync policy, replication, acknowledgement level). The gap table covers persistence and the TTL in one entry, so the identifier must be confirmed before it is cited.
- **Gap timeout and pending-map size** (CA-G-11/CA-G-24): numeric values pending confirmation. The protocol above is fixed; only these limits remain open.
- **DLT name and maximum retry count** (CA-G-09): `triaje.eventos.dlq` is the proposed name in Spec C-05 §6; the retry count is not set. Neither is accepted.
- **Test requirements above and in Spec C-05 §7:** not implemented in this branch.

## Consequences

- Under the proposed Option B, the consumer would be fully reactive, and BlockHound could cover it in tests. Not accepted.
- Under the proposed design, the offset would be acknowledged only after the projection is durable under the chosen policy. A crash before acknowledgement causes redelivery; the `ProcessedEvent` dedupe would make redelivery safe. Not accepted.
- The DLT is a second write target. Its availability is part of the consumer's failure path, and it has explicit test requirements.
- A weak Redis durability policy can lose acknowledged state after a crash. Under a proposed policy, this risk would be acceptable only together with a rebuild path from Kafka retention (CA-G-24). Not accepted.
