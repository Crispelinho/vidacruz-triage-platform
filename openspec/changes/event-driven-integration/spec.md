# Spec: event-driven-integration (C-05)

Requirement IDs are defined in `docs/requirements.md`. Decision documentation lives in `docs/adr/` (ADR-02 and ADR-08 are on `develop`; ADR-01, ADR-03, ADR-10 and ADR-14 are in the consolidation branch and not yet on `develop`) and is summarized in `docs/adr-roadmap.md`. **Every ADR cited here is Proposed, not Accepted.** The durable-record decision (OPEN-09/OPEN-10: Option A; G-T29: 7 days), the publisher client (G-T25), the `pacienteSequence` contract (G-T06) and the broker-outage posture are accepted; OD-5 remains open. This spec therefore states contracts and criteria, and it names the remaining decisions it depends on instead of settling them. Each scenario is verified by one automated test or by recorded evidence.

Risk IDs cite only the risk register in `docs/requirements.md` §17 (R-01…R-10). Open decisions cite `OD-5` (`docs/capabilities-map.md`); the closed record `docs/decisions/OPEN-09-10-outbox-durability-recovery.md`, `G-T29` (7 days), `G-T25` (publisher only), `G-T06` (sequence contract) and the backoff-only posture are cited as accepted decisions, not open ones.

**Decisions this spec depends on and does not settle:**

| Topic | Where it is decided | Status |
|---|---|---|
| Consumer deduplication backend (PostgreSQL or Redis) | ADR-08 (projection store), ADR-02 (consumer) | Proposed |
| Durable record and outbox published-row retention | `OPEN-09-10-outbox-durability-recovery.md` (Option A), G-T29 (7 days) | Accepted (2026-11-25) |
| Retention period of `EventoHistorico` | OD-5 | Open |
| `pacienteSequence` assignment mechanism | G-T06 (`docs/services/triage-service.md`, §5.1) | Accepted (2026-11-25): per-patient counter in the same transaction |
| Reactive Kafka client library for the C-05 publisher | G-T25 (`docs/services/triage-service.md`, ADR-03) | Accepted (2026-11-25): spring-kafka async adapter; consumer not included |
| Publisher behavior during a long broker outage | ADR-03 (backoff only, no circuit breaker) | Accepted (2026-11-25) |

---

## 1. Event Contracts (EVT-01, EVT-02, EVT-03, EVT-04, DEC-03)

### 1.1 Topic and partitioning

- **Topic name:** `triaje.eventos` (single topic for all event types from Triage).
- **Partitioning key:** `pacienteId` (extracted from the event payload).
- **Goal:** keep the events of one patient in one partition, so Kafka preserves the order in which the broker accepted them.
- **Scenario:** two events for the same `pacienteId` published by one publisher instance, in outbox order, appear in the partition in that order.
- **Limit of this guarantee:** the topic order can differ from the outbox order when more than one publisher instance runs, when a send is retried, or when events are republished from the durable record (OPEN-09/OPEN-10). Order per patient is therefore protected by `pacienteSequence` at the consumer (§5.1), not by the publisher.

### 1.2 Event envelope structure

The envelope is **Proposed (DEC-03)**. Every event in `triaje.eventos` follows this JSON schema:

```json
{
  "eventId": "uuid-v4-generated-at-outbox-write",
  "eventType": "paciente_registrado | paciente_escalado | paciente_en_consulta | paciente_alta",
  "schemaVersion": 1,
  "pacienteId": "patient-uuid",
  "pacienteSequence": 1,
  "occurredAt": "ISO-8601-with-offset",
  "correlationId": "string-inherited-or-generated",
  "payload": { }
}
```

> **Aligned with `docs/requirements.md` §10 (canonical; DEC-03 is Proposed).** `schemaVersion` is the JSON number `1` (an integer, not the string `"1"`), and the payload of each event is the one in the requirements event table (§1.3). An earlier draft of this spec used a different payload (`escalado`, `tiempoEnEstadoMinutos`) and a string `schemaVersion`; both are removed. If `requirements.md` changes DEC-03, this section follows it.

**Field descriptions:**

| Field | Type | Required | Source | Purpose |
|-------|------|----------|--------|---------|
| `eventId` | UUID v4 string | Yes | Generated (java.util.UUID) when the outbox row is written | Unique per event across all services; the deduplication identity (§4.1); never null or empty |
| `eventType` | enum string | Yes | Outbox record `event_type` column | One of EVT-01…EVT-04; used for routing and filtering |
| `schemaVersion` | integer (`1`) | Yes | Constant | Forward compatibility; additive changes keep the value, breaking changes increment it (DEC-03) |
| `pacienteId` | UUID string | Yes | Outbox record `paciente_id` | Kafka partition key; groups the events of one patient in one partition |
| `pacienteSequence` | integer (JSON number) | Yes | Outbox record `paciente_sequence` column, assigned per patient by Triage in the same transaction (§5.1, G-T06) | Order and duplicate guard at the consumer |
| `occurredAt` | ISO-8601 with offset | Yes | Current timestamp (UTC) | Server time when the event was persisted; enables ordering diagnostics and replay |
| `correlationId` | string | Yes | Inherited from the request header or generated (a UUID v4 when generated) | Links related events across services; `requirements.md` §10 types it as a string, so an inherited value is not required to be a UUID |
| `payload` | object | Yes | Outbox record `payload` column (JSON) | Event-specific data; structure depends on `eventType` |

### 1.3 Event type payloads

Payload fields come from `docs/requirements.md` §10 and agree with `docs/services/triage-service.md` §4.2. Payloads carry no patient name and no free text (data minimization). EVT-03 and EVT-04 carry no `nivel`: under the fixed order protocol of §5.1, EVT-01 (which carries `nivel`) is applied before them, so the projection already knows the level. If EVT-03 or EVT-04 arrives first it is a future event, stays pending and is dead-lettered on timeout; it never decrements a count with an unknown level (ADR-02, ADR-08).

#### EVT-01: `paciente_registrado` (from RF-01, RN-04)

Triggered when: `POST /triage/pacientes` completes successfully.

```json
{
  "eventType": "paciente_registrado",
  "payload": {
    "nivel": "CRITICO | URGENTE | URGENTE_2 | NO_URGENTE | NO_URGENTE_2",
    "slaMinutos": "<integer, RN-02>",
    "estado": "REGISTRADO",
    "fechaRegistro": "<ISO-8601 with offset>"
  }
}
```

**Scenario:** when a valid patient registration completes, an EVT-01 event is written to the outbox atomically with the patient row insert (DER-02).

#### EVT-02: `paciente_escalado` (from RF-03, RN-03)

Triggered when: SLA evaluation detects an overdue patient and escalates it.

```json
{
  "eventType": "paciente_escalado",
  "payload": {
    "nivel": "<classification>",
    "slaMinutos": "<integer, RN-02>",
    "fechaEscalamiento": "<ISO-8601 with offset>"
  }
}
```

**Scenario:** a patient overdue by one second is escalated exactly once; a second evaluation attempt publishes no new event.

#### EVT-03: `paciente_en_consulta` (from RF-02, RN-04)

Triggered when: `PATCH /triage/pacientes/{id}/estado` transitions a patient to `EN_CONSULTA`.

```json
{
  "eventType": "paciente_en_consulta",
  "payload": {
    "estadoAnterior": "REGISTRADO",
    "fechaEntradaConsulta": "<ISO-8601 with offset>"
  }
}
```

**Scenario:** a valid transition from `REGISTRADO` to `EN_CONSULTA` writes an audit row and publishes EVT-03 (`estadoAnterior = REGISTRADO`) atomically (DER-02).

#### EVT-04: `paciente_alta` (from RF-02, RN-04)

Triggered when: `PATCH /triage/pacientes/{id}/estado` transitions a patient to `ALTA` (discharge).

```json
{
  "eventType": "paciente_alta",
  "payload": {
    "estadoAnterior": "REGISTRADO | EN_CONSULTA",
    "fechaAlta": "<ISO-8601 with offset>"
  }
}
```

**Scenario:** both direct transitions (`REGISTRADO → ALTA`) and state-flow transitions (`EN_CONSULTA → ALTA`) publish EVT-04 atomically with the state change; `estadoAnterior` records which one occurred.

### 1.4 Scenario: envelope validation

- **Scenario:** an event with a missing `eventId` is rejected at producer time (validation fails before the outbox row is inserted).
- **Scenario:** an event with `schemaVersion` other than the integer `1` is accepted by the outbox, but consumers are configured to reject it on receipt with a dead-letter record.
- **Scenario:** `schemaVersion` is serialized as a JSON number; the string `"1"` is not a valid value.
- **Scenario:** `pacienteSequence` for a new patient starts at 1 and increases by exactly 1 for each subsequent event of that patient (DEC-03).

---

## 2. Persistence — Outbox Pattern (DEC-14, ADR-03)

### 2.1 Outbox table schema

**Table name:** `OutboxEvento` (Triage database only; one database per service, ADR-14 and DEC-10).

The DDL below is illustrative. The Flyway migration of Triage is the source of the final schema.

```sql
CREATE TABLE outbox_evento (
  id BIGSERIAL PRIMARY KEY,
  event_id UUID NOT NULL UNIQUE,
  event_type VARCHAR(50) NOT NULL,
  schema_version INTEGER NOT NULL DEFAULT 1,
  paciente_id UUID NOT NULL,
  paciente_sequence BIGINT NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  correlation_id TEXT NOT NULL,
  payload JSONB NOT NULL,

  -- Publication tracking
  publicado_en TIMESTAMPTZ,
  publicado_por UUID,

  -- Short lease claim (ADR-03; no lock is held during the Kafka ACK)
  claimado_por UUID,
  claimado_en TIMESTAMPTZ,
  claim_expira_en TIMESTAMPTZ,

  -- Failure tracking (used by §3.4 and §6.1)
  intentos INTEGER NOT NULL DEFAULT 0,
  primer_fallo_en TIMESTAMPTZ,
  ultimo_fallo_en TIMESTAMPTZ,
  ultimo_error TEXT,
  requiere_revision BOOLEAN NOT NULL DEFAULT FALSE,

  -- Audit
  creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
  actualizado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- Constraints
  CONSTRAINT outbox_evento_paciente_sequence
    UNIQUE (paciente_id, paciente_sequence)
);

CREATE INDEX idx_outbox_evento_publicado_en
  ON outbox_evento (publicado_en NULLS FIRST)
  WHERE publicado_en IS NULL;

CREATE INDEX idx_outbox_evento_claim
  ON outbox_evento (claim_expira_en, id)
  WHERE publicado_en IS NULL AND requiere_revision = FALSE;

CREATE INDEX idx_outbox_evento_paciente_id
  ON outbox_evento (paciente_id);

CREATE INDEX idx_outbox_evento_creado_en
  ON outbox_evento (creado_en);
```

**Field descriptions:**

| Column | Type | Null | Purpose |
|--------|------|------|---------|
| `id` | BIGSERIAL | No | Auto-increment internal ID (not used externally); gives the publisher a stable claim order |
| `event_id` | UUID | No | Deduplication identity; unique across all events |
| `event_type` | VARCHAR | No | One of EVT-01…EVT-04; indexed for filtering |
| `schema_version` | INTEGER | No | `1` for current; bumped on breaking changes |
| `paciente_id` | UUID | No | Patient the event belongs to; enables replay per patient |
| `paciente_sequence` | BIGINT | No | Per-patient counter; unique with `paciente_id`; see §5.1 for the assignment contract |
| `occurred_at` | TIMESTAMPTZ | No | Timestamp when the event logically occurred |
| `correlation_id` | TEXT | No | Correlation ID carried in logs, traces and Kafka headers (DER-07) |
| `payload` | JSONB | No | Event-specific data; schema varies by `event_type` |
| `publicado_en` | TIMESTAMPTZ | Yes | Null until the broker acknowledges the send; UTC |
| `publicado_por` | UUID | Yes | Publisher instance ID that completed publication (for observability) |
| `claimado_por` | UUID | Yes | Publisher instance ID holding the current short lease; cleared after publish or failure |
| `claimado_en` | TIMESTAMPTZ | Yes | Time the current lease was stamped |
| `claim_expira_en` | TIMESTAMPTZ | Yes | Time after which another replica may reclaim the row; configured in §3.5 |
| `intentos` | INTEGER | No | Count of failed publication attempts for this row |
| `primer_fallo_en` | TIMESTAMPTZ | Yes | Time of the first failure in the current failure series; opens the failure window of §3.4 |
| `ultimo_fallo_en` | TIMESTAMPTZ | Yes | Time of the most recent failure |
| `ultimo_error` | TEXT | Yes | Last error message; must not contain patient identifiers or free text from the request |
| `requiere_revision` | BOOLEAN | No | Set when the failure threshold of §3.4 is reached; the publisher skips flagged rows until an operator clears the flag |
| `creado_en` | TIMESTAMPTZ | No | Row insert timestamp (for auditing) |
| `actualizado_en` | TIMESTAMPTZ | No | Row update timestamp |

### 2.2 Transactional guarantee (DER-02, ADR-03)

**Scenario:** when a patient is registered (RF-01), the following happen in a **single R2DBC transaction** (no Kafka call inside it, R-07):
1. Insert the patient row into the patient table with `estado = 'REGISTRADO'`.
2. Insert one outbox row into `outbox_evento` with `event_type = 'paciente_registrado'` and `publicado_en = NULL`.
3. Insert one audit row into the audit table (RN-04).
4. Insert one `EventoHistorico` row for the same event. `EventoHistorico` is the accepted durable record (OPEN-09/OPEN-10 closed as Option A on 2026-11-25; OD-3 and DER-09 in `docs/requirements.md` §10 and §11). The retained-outbox alternative was rejected.

If the transaction commits, all rows exist. If it rolls back, none exist. **Scenario:** if the outbox insert fails, the entire transaction rolls back; the patient is NOT registered.

### 2.3 Outbox cleanup policy

**Status:** G-T29 is closed: the retention value is **7 days**, configurable (`docs/decisions/OPEN-09-10-outbox-durability-recovery.md`). `docs/services/triage-service.md` §4.3 proposed the value; an earlier draft of this spec said 24 hours and is superseded. The rules below are fixed; the 7-day value is the accepted default of the configurable retention property.

- Rows with `publicado_en IS NULL` are **never** deleted, whatever their age.
- Rows with `requiere_revision = TRUE` are **never** deleted by the cleanup job.
- A published row becomes eligible for deletion when `publicado_en` is older than the configured retention. The cleanup job is optional, reactive (R2DBC), and runs in batches.
- **Accepted Option A:** the cleanup of the operational outbox does not remove `EventoHistorico` rows. The retention of `EventoHistorico` is OD-5 and remains open; it has no time-based purge until the developer decides it.
- The retained-outbox alternative (Option B) was rejected; no branch of this cleanup job exists for it.
- The rules above (never delete unpublished or flagged rows) hold for the accepted option; a deployment enables the cleanup job with the configured 7-day retention.

**Scenario (accepted Option A):** a row published longer ago than the configured retention is deleted by the cleanup job; a row published more recently is kept; a row with `publicado_en IS NULL` is kept regardless of age.

**Scenario:** if the publisher is down for longer than any configured retention, all unpublished rows remain in the outbox. When the publisher restarts, it drains the entire backlog without data loss.

---

## 3. Publisher: Triage → Kafka (ADR-02, ADR-03)

### 3.1 Publisher responsibility

The **Outbox Publisher** is a reactive pipeline on R2DBC and a non-blocking Kafka client. It is **not** a blocking `@Scheduled` method, it never calls `.block()`, and it never uses JDBC on an event-loop thread (NFR-01, TC-03). **G-T25 is accepted (2026-11-25) for the C-05 publisher:** keep the maintained Spring Kafka `KafkaTemplate` as the Kafka client and use Project Reactor as the composition layer through `Mono.defer(() -> Mono.fromFuture(template.send(record)))` with `suppressCancel = false`. `KafkaTemplate.send(...)` returns `CompletableFuture<SendResult>`; this adapter does not block the event loop. `reactor-kafka` is excluded because it is discontinued and archived, so no new Kafka dependency is introduced. This decision closes only the publisher; the Assistant consumer model stays in ADR-02 and is not decided here. The send starts on subscription, so backpressure is imposed upstream by the claim batch size and a bounded number of in-flight sends, not by the Kafka client. TST-RXN (BlockHound or equivalent) applies to this publisher path.

On each cycle, triggered by a reactive timer at a configurable interval, it does the following:
1. **Claim** a bounded batch of pending rows (`publicado_en IS NULL AND requiere_revision = FALSE`) in a short R2DBC transaction that stamps `claimado_por`, `claimado_en` and `claim_expira_en`. The claim query uses `FOR UPDATE SKIP LOCKED` only inside that transaction, then commits (ADR-03). Several replicas can run the publisher without holding a valid lease on the same row at the same time.
2. **Send** each claimed row to `triaje.eventos` with the non-blocking client, in `id` order, and wait reactively for the broker acknowledgement. No database transaction and no row lock are open during this wait.
3. **Mark** the row published (`publicado_en`, `publicado_por`) in a separate short transaction after the acknowledgement, guarded by `id` and `claimado_por`. If the update affects 0 rows, the lease expired or another replica won; the possible duplicate is handled by §4.
4. **Release** the claim on failure (`claimado_por`, `claimado_en`, `claim_expira_en` set to NULL) only if this publisher still owns the row, then **record failures** (§3.4) and continue with the next row.

A row with an expired lease (`claim_expira_en <= CURRENT_TIMESTAMP`) becomes claimable again. At-least-once delivery means a crash or a genuinely expired lease can produce a duplicate; ADR-02 deduplicates by `eventId`. **Lease invariant (accepted with G-T25):** a live publisher must not lose its claim while a row is in flight. The lease either covers the **full publication budget** — all send attempts, accumulated backoff, ACK confirmation and the publish-mark transaction — or it is renewed before expiry while the row remains in flight. `lease-seconds > send-timeout-ms` alone is not sufficient. No other replica may reclaim a row that still has an in-flight send. Cancellation or timeout is not proof that Kafka rejected the send: the row is never marked published without a confirmed ACK, and a later duplicate delivery is safe because `eventId` is the idempotence key.

### 3.2 Delivery guarantee

**Semantic:** at-least-once delivery. An event may appear in Kafka multiple times if:
- The publisher sends the event and crashes before updating `publicado_en`.
- The network connection is interrupted mid-send, requiring a retry.

**Scenario:** if the publisher publishes an event and crashes before marking it as published, the event appears twice in Kafka when the publisher restarts. Consumers must handle this through the idempotence contract (§4).

### 3.3 Non-blocking behavior and behavior during a broker outage

**Scenario (request path):** the publisher runs off the request path and does not block it. A `POST /triage/pacientes` returns to the client within the SLA even if the publisher is backlogged or Kafka is unavailable, because the request only writes to PostgreSQL (NFR-04).

**Decision (developer, 2026-11-25): backoff only, no circuit breaker.** The publisher retries the same broker with exponential backoff (configurable base delay, cap and multiplier); retries continue while the broker is down, and a row that keeps failing is handled by §3.4. The posture is recorded in ADR-03. Earlier drafts of this spec contradicted themselves (§3.3 said no circuit breaker; §6.4 opened one after a fixed time); that contradiction is resolved by this decision. A second broker or an alternate cluster is not introduced by this decision.

**Why this posture:**
- The outbox keeps every event while Kafka is unavailable; no row is lost and the request path is unaffected (NFR-04).
- The backoff cap bounds the rate of send attempts when the broker recovers, avoiding a retry storm.
- A circuit breaker would add states, thresholds and metrics for a single dependency without evidence that it is needed. NFR-03 requires a breaker only for LLM calls (ADR-09), not for Kafka.
- A breaker may be added later through a new ADR if operational metrics show unacceptable backlog or load. The publisher keeps the failure policy configurable so this does not require rewriting it.

**Scenarios that hold under the accepted posture:**
- If the Kafka broker is down, the publisher logs the error without patient identifiers, keeps the row unpublished and retries with backoff; no row is lost.
- `retryWhen` retries **only transient Kafka errors**. Serialization failures, record-construction failures, invalid payloads and synchronous exceptions thrown when invoking `send()` are row-level errors (§3.4); they do not enter the infrastructure retry loop and do not block other rows.
- A cancelled or timed-out send is indeterminate: Kafka may still have received it. The row stays unpublished until an ACK is confirmed; a later re-send can produce a duplicate, which `eventId` makes harmless. A timeout is never evidence that the broker did not receive the event.

### 3.4 Publish failure handling

**Scenario:** if a single event fails to publish (for example, a serialization error), the publisher increments `intentos`, sets `primer_fallo_en` if it was empty, sets `ultimo_fallo_en` and `ultimo_error`, and continues with the next claimed row.

**Scenario:** when the same row reaches the configured number of consecutive failures within the configured window (`intentos` ≥ threshold and `ultimo_fallo_en - primer_fallo_en` within the window), the publisher sets `requiere_revision = TRUE`, logs it and raises a metric. Flagged rows are skipped until an operator clears the flag. Moving the row to a dead-letter topic is an alternative to be decided with §3.3; the default is flag, log and alert.

**Scenario:** a successful publication of a row resets nothing on other rows, and a row cleared by an operator restarts its failure series (`intentos = 0`, `primer_fallo_en = NULL`).

### 3.5 Configuration

The names and values below are **illustrative** and not decided, except `published-retention: 7d` (G-T29, accepted 2026-11-25). The publisher uses the accepted spring-kafka async adapter (G-T25) and backoff only (§3.3); no circuit-breaker setting exists for Kafka.

```yaml
triage:
  outbox:
    publisher:
      interval-seconds: 5          # reactive timer interval
      batch-size: 100              # rows claimed per cycle
      max-in-flight-sends: 20      # illustrative; upper bound for concurrent sends (G-T25)
      send-timeout-ms: 10000       # bound on one reactive send wait
      lease-seconds: 30            # illustrative; must cover the full publication budget or be renewed (§3.1)
      # Backoff on transient Kafka errors only; row-level errors follow §3.4
      retry-base-delay-ms: 5000
      retry-max-delay-ms: 60000
      retry-multiplier: 1.5
      # Row-level failure handling (§3.4)
      failure-threshold: 10
      failure-window-hours: 24
    cleanup:
      enabled: false
      published-retention: 7d         # accepted default (G-T29, 2026-11-25); configurable
```

**Scenario:** when `publisher.interval-seconds` is set to 1, the publisher cycles every second; when set to 60, events may take up to 60 seconds to appear in Kafka.

---

## 4. Consumer Idempotence (ADR-02, ADR-08)

### 4.1 Contract

The contract below is independent of the storage technology. The Clinical Assistant (or any other consumer) must meet it.

| # | Rule |
|---|------|
| C1 | **Identity.** `eventId` identifies an event. A second delivery of the same `eventId` produces no business effect. |
| C2 | **Order guard and gap handling.** For each patient the consumer keeps the highest `pacienteSequence` applied in the projection store. `sequence <= lastApplied` is a duplicate/stale event: no effect and the offset is acknowledged. `sequence = lastApplied + 1` is applied. `sequence > lastApplied + 1` is a future event: it is kept in a bounded pending map, is NOT applied and is NOT acknowledged; it is applied only when the missing contiguous sequences arrive, or goes to the DLT with `dlqReason = "GAP_UNRESOLVED"` when the configured limit expires (see §5.1). |
| C3 | **Atomicity.** Recording that an event was processed and applying its effect must be atomic in the chosen store, or the effect itself must be idempotent. A crash between the two must never lose the effect or apply it twice. |
| C4 | **Commit order.** The Kafka offset is acknowledged only after the effect and the processed marker have reached the durability level defined by ADR-02 (Redis durability or the PostgreSQL commit). |
| C5 | **Marker retention.** A processed marker is kept for at least the effective Kafka retention of `triaje.eventos` plus a safety margin, counted from the time of processing. The margin is pending (ADR-02, CA-G-11). The retention is **not a fixed 24 hours**: with a 24-hour window, a redelivery after the marker expired would no longer be caught by `eventId`. OD-3 records the Kafka retention as 30 days, in documents only (not verified as configuration), so the marker retention derives from that value and follows it if it changes. |
| C6 | **Duplicate outcome.** A duplicate is recognized, no effect is repeated, the offset is acknowledged and a metric counts it (no patient identifier in labels, NFR-19). |
| C7 | **Store failure.** If the store is unavailable, the event is retried within the limits of ADR-02 without acknowledging the offset. A store failure is not treated as a duplicate. |

**Scenario:** if the same event (same `eventId`) is received twice, the second receipt is discarded; no duplicate side effect occurs.

### 4.2 Idempotence store

The backend of the processed-event record and of the per-patient sequence guard is **not fixed by this spec**. ADR-08 (Proposed) decides the projection store and ADR-02 (Proposed) decides the consumer; this spec follows whichever they settle. `docs/requirements.md` §11 currently places `ProcessedEvent` and `EstadoUrgenciaProyeccion` in Redis and DEC-10 reserves Redis for the Assistant projection, but DEC-10 and ADR-08 are Proposed.

| Criterion | PostgreSQL (Assistant database, R2DBC) | Redis (reactive client) |
|---|---|---|
| Atomicity with the projection effect (C3) | A single transaction when the projection is also in PostgreSQL; not atomic with a Redis projection | A single Lua script when the projection is also in Redis; not atomic with a PostgreSQL projection |
| Expiry of old markers (C5) | A cleanup job by `received_at` | Native TTL per key (one key per `eventId`; a Redis set has no per-member TTL) |
| Durability (C4) | Transaction commit | Depends on the persistence mode (AOF, fsync) chosen in ADR-02/ADR-08 |
| Growth | Table grows until cleanup | Memory grows with the marker retention |
| Fit with decisions | Adds R2DBC writes to the consumer path | Matches DEC-10 and `requirements.md` §11, both Proposed (Redis only for the Assistant projection and processed events); ADR-14 does not accept it |

**Dependency:** this spec cannot be implemented until ADR-08 and ADR-02 are Accepted. Whichever backend is chosen, the contract of §4.1 and the tests of §7.3 and §7.4 apply unchanged.

**Scenario:** when two consumer instances receive the same event concurrently, exactly one applies it (C1, C3); the other recognizes the duplicate and skips it.

### 4.3 Validation: second delivery has no effect

**Scenario:** a consumer receives `paciente_registrado` event #1 for patient P123 and updates the projection (count = 1 for level CRITICO). The consumer crashes and restarts. The same event is re-delivered. The consumer recognizes it (C1/C2) and skips it. The projection count remains 1 (not 2).

**Scenario:** a delivery that arrives after a restart, and within the marker retention (C5), is recognized by `eventId`. A delivery that arrives after the marker retention is still rejected by the sequence guard (C2), provided the per-patient sequence record survives. The assignment contract is closed (G-T06, 2026-11-25), but its implementation and runtime verification are still pending; until the tests exist, this protection is not treated as executed evidence.

---

## 5. Order and Correlation

### 5.1 Per-patient order: partition key and `pacienteSequence` (DER-03)

Order per patient relies on two layers.

1. **Partition key.** All events of one patient use the key `pacienteId`, so they go to one partition and Kafka preserves the order in which the broker accepted them (§1.1).
2. **`pacienteSequence`.** Because delivery is at-least-once, several publisher instances may run, sends may be retried and the durable record may be republished, the topic order is not a sufficient guarantee. The consumer applies the guard C2 of §4.1.

**Assignment contract for `pacienteSequence`:**
- It starts at 1 for the first event of a patient and increases by exactly 1 for each event of that patient.
- It is assigned by Triage in the **same transaction** as the state change and the outbox row.
- Two committed events of one patient never share a sequence: the unique constraint `(paciente_id, paciente_sequence)` is the safety net. A rolled-back transaction leaves no gap among committed events.
- Concurrent changes to one patient (for example, an escalation and a state transition) are serialized, so each gets a distinct sequence.
- A republished event keeps its original `eventId` and `pacienteSequence`.

**Ordering rules.**
- Order per patient is defined **only** by `pacienteSequence`. The outbox `id` gives the publisher a claim order and is not an ordering contract: rows of different patients interleave in it, and `id` order is not commit order. `occurred_at` is a clock timestamp and is not used for ordering.
- Within one patient, a higher `pacienteSequence` means a later event. The unique constraint `(paciente_id, paciente_sequence)` guarantees no ties.
- The mechanism must not leave gaps among committed events. A database `SEQUENCE` per patient would, because sequence values are not rolled back; this is one reason the counter-on-row proposal below is preferred.

**Gaps and future events at the consumer (protocol).** The sequence is contiguous per patient, so `sequence > lastApplied + 1` means earlier events have not been applied yet. The consumer keeps a bounded, in-memory `pendingByPatient` map keyed by `pacienteSequence`; entries are unacknowledged Kafka records and are not projection state. Processing rules:

1. `sequence <= lastApplied`: duplicate or stale event; no effect, acknowledge the offset and increment the duplicate/stale metric.
2. `sequence = lastApplied + 1`: apply the event atomically with its `eventId` marker, then acknowledge. Continue draining contiguous entries already in `pendingByPatient`.
3. `sequence > lastApplied + 1`: store the record in `pendingByPatient`; do not apply it and do not acknowledge it. Keep consuming other patients and partitions.
4. When a pending entry exceeds the configured gap timeout, or a new future event would exceed the configured map size, publish that event (the oldest pending entry if the map is full) to `triaje.eventos.dlq` with `dlqReason = "GAP_UNRESOLVED"`; acknowledge the original offset only after the DLT write is confirmed. The projection is not silently marked consistent; the DLT record and alert are the trace.
5. On restart the pending map is rebuilt from unacknowledged redeliveries. No gap decision is persisted in the projection.

The numeric gap timeout and map size remain configuration pending explicit confirmation under ADR-02/ADR-08; the protocol itself is fixed and is tested in §7.9.

**Accepted mechanism (G-T06, developer, 2026-11-25):** a per-patient counter column `paciente.ultima_secuencia BIGINT NOT NULL DEFAULT 0`. Triage's application service assigns the sequence in the **same R2DBC transaction** that persists the state change, the counter, the outbox row, the audit row and the `EventoHistorico` row. Registration inserts the patient with `ultima_secuencia = 1` and EVT-01 with `pacienteSequence = 1`. Later events use `UPDATE ... SET ultima_secuencia = ultima_secuencia + 1 ... RETURNING ultima_secuencia`; the returned value is the assigned sequence. A PostgreSQL `SEQUENCE` is rejected because its increments are not rolled back with the business transaction and would leave gaps among committed events.

**Transaction and result handling:**
- All writes share the same R2DBC transactional context (`TransactionalOperator`); being in the same reactive flow is not enough. If the outbox, audit or history write fails, the counter increment is rolled back too (criterion §7.11).
- One returned row: the sequence was obtained; continue the operation.
- No returned row: the state/escalation guard was not met; follow the G-T24 conflict path. Do not allocate a sequence in this case.
- Integrity or database error: classify it; never invent a sequence outside the transaction.
- On a transient database failure, roll back and re-execute the **complete transactional operation**. Do not retry only the event insert after losing the assignment context.

**Concurrency, duplicates and pending publications:** the row lock taken by the guarded `UPDATE` serializes concurrent writers of the same patient; under `READ COMMITTED` a waiting writer re-evaluates its guard against the committed row. `UNIQUE (paciente_id, paciente_sequence)` is a safety net, not the normal allocator. A publisher retry or a G-T25 timeout reuses the original `eventId` and sequence; no renumbering occurs. Committed sequences are contiguous by construction; gaps observed by a consumer are delivery gaps, handled by the pending map and DLT rule above. G-T03 (request idempotency), G-T24 (conflict response) and the ADR-02 consumer model remain separate decisions.

**Scenario:** for patient P123, three events occur in this order:
1. `paciente_registrado` (time 10:00:00, `pacienteSequence` = 1, eventId = UUID-A)
2. `paciente_escalado` (time 10:05:30, `pacienteSequence` = 2, eventId = UUID-B)
3. `paciente_en_consulta` (time 10:06:00, `pacienteSequence` = 3, eventId = UUID-C)

All use the partition key `pacienteId = P123`, so they appear in one partition in the order the broker accepted them. A consumer that processes partitions in parallel handles different patients concurrently, and the events of one patient sequentially.

**Scenario:** the consumer fails transiently on UUID-B; it retries UUID-B before processing UUID-C and does not acknowledge UUID-C first. Permanent errors follow the dead-letter rule of ADR-02 instead of blocking the partition indefinitely.

**Scenario:** an event of P123 with `pacienteSequence` not greater than the last applied sequence is not applied (C2), whatever its `eventId`.

### 5.2 Correlation tracing

Every event carries a `correlationId` (a string; a UUID v4 when generated by Triage). When a request triggers multiple events, they all share the same `correlationId`.

**Scenario:** `POST /triage/pacientes` with an `X-Correlation-ID` (or `X-Request-ID`) header writes that value as `correlationId` into the outbox row (DER-07). If the header is absent, one is generated. The publisher copies it to a Kafka header, and the consumer puts it in its log context.

**Scenario:** traces and logs of the Triage request, the outbox publication and the consumer processing are linked by `correlationId`. Tracing uses **Micrometer Tracing** (the Spring Boot 3.x tracing support, with the OpenTelemetry bridge for NFR-16). Spring Cloud Sleuth is not used: it is not part of the Spring Boot 3.x line this project fixes (TC-01, ADR-10).

---

## 6. Error Handling and Dead-Letter Queue

> **Governing decision.** ADR-02 (Proposed) governs consumer retries, backoff and the dead-letter rule, and states that attempt counts and backoff values are configuration, not decided. The numbers and the topic name in this section are illustrative defaults. ADR-02 uses the term DLT; `triaje.eventos.dlq` is the name proposed here.

### 6.1 Publisher errors

**Scenario:** when publishing an event to Kafka fails:
- If the error is transient (network timeout or broker unreachable), the publisher retries with exponential backoff and no circuit breaker (accepted posture, §3.3).
- If the broker stays down, the publisher keeps the row unpublished and retries later with the configured backoff cap (§3.3).
- If the error is specific to the row (for example, a corrupted outbox record or a JSON serialization error), the publisher records the failure in `intentos`, `ultimo_fallo_en` and `ultimo_error`, logs the row ID, applies §3.4 and continues with other rows.

### 6.2 Consumer errors

**Scenario A — Permanent error:** a consumer receives an event with an invalid JSON payload or an unknown `schemaVersion` (any value other than `1`). The consumer:
1. Logs the event ID, type and error.
2. Publishes the event to the dead-letter topic `triaje.eventos.dlq`, and waits for that publication to be confirmed.
3. Only then acknowledges the original offset; the event is NOT retried.
4. Continues with the next event.

**Scenario B — Transient processing error:** a consumer receives a valid event but cannot update the projection store (for example, a lost connection). The consumer:
1. Does NOT acknowledge the offset.
2. Retries with backoff, within a configurable limit (ADR-02).
3. When the limit is reached, treats the record as failed and applies the dead-letter rule of ADR-02.

### 6.3 Dead-letter topic

**Topic name (proposed):** `triaje.eventos.dlq` (single dead-letter topic for all Triage events).

**Schema:** same envelope structure as `triaje.eventos`, plus metadata:
```json
{
  "originalTopic": "triaje.eventos",
  "originalPartition": 3,
  "originalOffset": 12345,
  "originalTimestamp": "ISO-8601",
  "dlqReason": "SCHEMA_VALIDATION_ERROR | PROCESSING_ERROR_THRESHOLD",
  "errorMessage": "Human-readable error description",
  "attemptCount": 1
}
```

**Scenario:** an event with an unknown `schemaVersion` is a permanent error. It is published to the dead-letter topic once, with `dlqReason = "SCHEMA_VALIDATION_ERROR"` and `attemptCount = 1`, and it carries its `eventId` so a duplicate dead-letter record can be recognized.

**Scenario:** a valid event that keeps failing for a transient reason reaches the dead-letter topic with `dlqReason = "PROCESSING_ERROR_THRESHOLD"` and `attemptCount` equal to the configured limit. Monitoring consumes the topic and alerts operators.

**Scenario:** if the dead-letter publication fails, the original offset is not acknowledged and the record is redelivered (ADR-02).

### 6.4 Publisher behavior during a broker outage

See §3.3. The accepted posture is backoff only with no circuit breaker (ADR-03). Under that posture:

**Scenario:** while the broker is unreachable, new requests (`POST /triage/pacientes`) still insert their outbox row and return success; the rows wait for the publisher, and no latency is added to the request path.

---

## 7. Test Strategy

### 7.1 Integration tests: Testcontainers (NFR-15, NFR-17)

Both services use Testcontainers to run PostgreSQL and Kafka (and Redis if ADR-08 selects it) in Docker during test execution. No manual setup is required.

**Scenario:** an integration test boots the Triage service with PostgreSQL and Kafka containers (`testcontainers-kafka` and `testcontainers-postgresql`). The test:
1. Calls `POST /triage/pacientes` with a valid patient.
2. Waits for the publisher to drain the outbox (observes `publicado_en` updated).
3. Consumes from Kafka and verifies the event envelope.

**Scenario:** an integration test boots the Clinical Assistant consumer with a Kafka container and the projection store chosen by ADR-08. The test:
1. Publishes a manually crafted `paciente_registrado` event to Kafka.
2. Waits for the consumer to process and update the projection.
3. Queries the projection and verifies the count increased.

### 7.2 Failure scenario: Kafka broker down

**Scenario:** a test stops the Kafka container (or causes a network disconnect) and calls `POST /triage/pacientes`. The test verifies:
1. The patient is inserted successfully (request returns 201).
2. The outbox row is inserted with `publicado_en = NULL`.
3. The publisher records the failure and schedules a retry with exponential backoff and no circuit breaker (accepted posture, §3.3).
4. Send attempts while the broker is down are spaced by the configured backoff cap; the test asserts there is no retry storm.
5. When the broker is back online, the outbox is drained (eventual consistency) and no row is lost.

**Verification:** outbox row state, recorded failure columns, the observed attempt spacing against the configured backoff cap, and the absence of a breaker state in publisher metrics.

### 7.3 Duplicate delivery: second publish has no effect

**Scenario:** a test publishes the same event twice to Kafka (simulating publisher crash/retry):
1. Consumer receives event #1 (eventId = UUID-X), updates projection count to 1.
2. Consumer receives event #2 (same UUID-X), recognizes it as a duplicate and skips it.
3. Projection count remains 1 (no duplicate side effect).

**Verification:** query the projection and assert count = 1, not 2. The test does not depend on the idempotence backend (§4.2).

### 7.4 Per-patient order enforcement

**Scenario:** a test publishes, for the same patient (P123), events with `pacienteSequence` 1, 2 and 3 (`paciente_registrado`, `paciente_escalado`, `paciente_en_consulta`) and checks the projection: the last applied sequence for P123 is 3 and the per-level count reflects a patient in consultation (the exact projection fields are decided in ADR-08). Escalation does not change `estado` (it stays `REGISTRADO` until the consultation, DEC-02).

**Scenario:** after those three events, the test delivers a stale event of P123 (sequence 2 again, with a new `eventId`). The projection does not change (C2).

**Verification:** the projection is the same before and after the stale delivery. Gap and future-event handling follow the fixed protocol in §5.1 and are verified by §7.9.

### 7.5 Outbox cleanup and retention

**Scenario:** a test sets the retention to R, inserts outbox rows published R + 1 hour ago, R − 1 hour ago, and an unpublished row older than R, then runs the cleanup job. The test verifies:
1. The row published R + 1 hour ago is deleted.
2. The row published R − 1 hour ago is kept.
3. The unpublished row is kept, and so is any row with `requiere_revision = TRUE`.
4. `EventoHistorico` rows are untouched (accepted Option A; no retained-outbox branch exists).

**Verification:** query `outbox_evento` before and after the cleanup. The test runs for two values of R, so it proves the retention is configuration and not a constant.

### 7.6 Dead-letter queue integration

**Scenario:** a test publishes an event with an unsupported `schemaVersion` (for example the integer `99`). The consumer:
1. Logs the error.
2. Publishes to the dead-letter topic and waits for confirmation.
3. Acknowledges the original offset only afterward.
4. The test consumes from the dead-letter topic and verifies the event is there with metadata.

**Verification:** `triaje.eventos.dlq` contains the event with `dlqReason = "SCHEMA_VALIDATION_ERROR"`.

### 7.7 Failure columns and atomicity

**Scenario:** a test forces a serialization failure for one outbox row. It verifies `intentos` is incremented, `ultimo_error` is set, other rows are still published, and after the configured threshold the row has `requiere_revision = TRUE` and is skipped.

**Scenario:** a test forces a failure between the outbox write and the `EventoHistorico` write in the registration transaction. It verifies that neither row, nor the patient row, persists (DER-02).

### 7.8 Test command and coverage

**Command:**
```bash
./gradlew test -DincludeTags=integration --info
# Runs all integration tests marked with @Tag("integration")
# Starts Testcontainers automatically; no manual setup needed
```

**Current status in this PR:** no executable implementation or Testcontainers integration tests for §7.1–§7.9 exist in the scaffold branch. Only context-load and reactive database round-trip tests are present. These scenarios are acceptance definitions for T-02, T-04, T-16, T-27 and TST-PRJ; they must not be reported as validated until those tasks produce evidence.

**Coverage target (proposed, not a requirement of `requirements.md`):** 80% line coverage for publisher and consumer code. NFR-15 requires unit and integration tests and does not set a percentage.

### 7.9 Claim lease, no lock during ACK, and future events

**Scenario (lease):** two publisher replicas claim batches from the same outbox. Assert no row has two valid leases at the same time, and a row whose `claim_expira_en` has passed is reclaimed by the next cycle.

**Scenario (no lock during ACK):** with a deliberately slow Kafka broker, start a send and query `pg_locks` for the claimed outbox row. Assert the claim transaction has committed and no publisher row lock is held while the acknowledgement is pending. After the ACK, assert `publicado_en` and `publicado_por` are set and the claim columns are NULL.

**Scenario (future event):** deliver sequence 1, then 3, for the same patient. Assert 3 is not applied and its offset is not acknowledged. Deliver sequence 2; assert 2 and then 3 are applied in order and acknowledged. Deliver sequence 2 again with a new `eventId`; assert no projection change.

**Scenario (unresolved gap):** deliver sequence 1 then 5 and let the configured gap timeout expire. Assert the record is in `triaje.eventos.dlq` with `dlqReason = "GAP_UNRESOLVED"` and the original offset is acknowledged only after the DLT write is confirmed.

**Verification:** DB lock query, outbox row state, Kafka offsets, DLT record and projection counts.

### 7.10 Reactive publisher client (G-T25, accepted 2026-11-25)

Acceptance criteria for the implementation; all unchecked here.

- [ ] A claimed row's `send(...)` is not invoked before subscription to the returned `Mono`.
- [ ] Each transient retry resubscribes to the deferred `Mono` and invokes `KafkaTemplate.send(...)` again; a row-level error invokes no infrastructure retry.
- [ ] Cancellation with `suppressCancel = false` does not set `publicado_en`; a late ACK is discarded and the claim is released without marking the row.
- [ ] A timeout may produce a duplicate delivery, but no logical loss: the row stays unpublished until ACK and `eventId` deduplicates the duplicate.
- [ ] In-flight sends are bounded by `max-in-flight-sends`; the publisher does not claim more rows while at the cap.
- [ ] The lease covers the full publication budget or is renewed while the row is in flight; no second replica obtains a valid lease during retries.
- [ ] Row-level failures (serialization, record construction, invalid payload, synchronous `send()` exceptions) update failure columns and never block other rows.
- [ ] BlockHound (or equivalent) reports no blocking call on the reactive publisher path.

**Verification:** spy on `KafkaTemplate.send(...)`, cancellation/timeout tests, bounded-concurrency test, lease assertions, outbox row state and a BlockHound run. These are definitions, not executed evidence.

### 7.11 `pacienteSequence` assignment (G-T06, accepted 2026-11-25)

Acceptance criteria for the implementation; all unchecked here.

- [ ] Two concurrent writers of the same patient obtain distinct, contiguous sequences; no `(paciente_id, paciente_sequence)` pair repeats.
- [ ] A failed transaction leaves the patient counter at its previous value: no increment without the outbox, audit and history writes.
- [ ] If any associated write fails, the counter remains unchanged and no event is emitted.
- [ ] A zero-row `UPDATE RETURNING` follows the G-T24 conflict path and does not allocate a sequence.
- [ ] A transient database failure re-executes the complete transactional operation, not only the event insert.
- [ ] A re-send keeps the original `eventId` and sequence; reconstruction from `EventoHistorico` preserves order and original values.
- [ ] Stale and future sequences follow C2 and §5.1; EVT-03/EVT-04 are not applied with an unknown level.

**Verification:** concurrent-transaction test, forced failure between writes, spied `UPDATE ... RETURNING`, outbox and history row assertions. These are definitions, not executed evidence.

---

## 8. Acceptance Criteria

**Status:** specification draft. The event contract is aligned with `docs/requirements.md` §10. The durable record (Option A), the 7-day outbox retention, the broker-outage posture, the G-T25 publisher client and the G-T06 sequence contract are accepted; the ADRs listed below remain Proposed.

**Gates:** implementation does not begin until these are **Accepted** (all are Proposed today):
- ADR-02 (Kafka envelope, at-least-once, consumer model) and ADR-03 (transactional outbox). The broker-outage posture, G-T25 and G-T06 sub-decisions are closed, but ADR-03 itself is still Proposed. ADR-02's consumer model is a separate decision and is not closed by G-T25 or G-T06.
- ADR-08, for the idempotence backend (§4.2).
- The decision record `OPEN-09-10-outbox-durability-recovery.md` and G-T29, for the outbox retention (§2.3): **Accepted** as Option A with 7 days on 2026-11-25; no longer a gate. OD-5 stays open but does not gate C-05 implementation.

**Accepted, not gates:** the publisher posture of §3.3 (backoff only, no circuit breaker; developer, 2026-11-25, recorded in ADR-03), G-T25 (spring-kafka async adapter for the publisher only, §3.1 and §7.10) and G-T06 (per-patient counter in the same transaction, §5.1 and §7.11).

**Completion criteria for C-05 (unchecked until verified by the evidence below):**

1. [ ] Event envelope (EVT-01…EVT-04) is defined in schema and matches `requirements.md` §10 (including `schemaVersion` as a number).
2. [ ] Outbox table exists in Triage PostgreSQL with the schema, failure columns and indexes of §2.1.
3. [ ] State change, audit row, outbox row and `EventoHistorico` row occur in a single R2DBC transaction (accepted Option A; DER-02 verified).
4. [ ] The publisher is reactive through the accepted G-T25 adapter (no blocking `@Scheduled`, no `.block()`, bounded in-flight sends), drains unpublished rows to Kafka with at-least-once delivery, and marks rows only after broker acknowledgement.
5. [ ] Per-patient order is protected by the partition key and by `pacienteSequence` assigned under the accepted G-T06 contract (§5.1, §7.11).
6. [ ] Consumer idempotence meets the contract C1–C7 of §4.1; a second delivery has no effect.
7. [ ] A broker outage is handled by backoff only with no circuit breaker (accepted posture, §3.3), without blocking the request path and without a retry storm on recovery.
8. [ ] The dead-letter topic captures permanent and exhausted-transient errors, and the offset is acknowledged only after the dead-letter publication is confirmed.
9. [ ] All integration tests pass with Testcontainers (no manual setup).
10. [ ] Outbox cleanup is configurable with the accepted 7-day retention (G-T29, closed), never deletes unpublished or flagged rows, and never touches `EventoHistorico`.
11. [ ] Marker retention of the consumer derives from the Kafka retention plus a margin (C5), not from a fixed 24 hours.
12. [ ] The publisher holds no database transaction or row lock while awaiting a Kafka acknowledgement; the claim-lease and future-event tests of §7.9 pass.

All boxes are unchecked in this PR: the scenarios above are acceptance definitions, not executed evidence.

**Verification evidence:**

| Requirement | Evidence |
|-------------|----------|
| EVT-01…EVT-04 contract | Integration test: `POST /triage/pacientes` → event in Kafka with correct envelope |
| DER-02 transaction | Integration test: patient insert fails → no outbox row; under Option A, forced failure between outbox and history writes → neither persists |
| At-least-once delivery | Integration test: publisher crash → event re-published on restart; consumer contract prevents a second effect |
| Per-patient order | Integration test (§7.4): sequences 1, 2, 3 produce the expected state; a stale event changes nothing. Assignment checklist (§7.11) is not executed in this spec |
| Consumer idempotence | Integration test (§7.3): same event twice → projection count increments only once |
| Marker retention (C5) | Configuration test: marker retention is at least the configured Kafka retention plus margin |
| Broker outage | Integration test (§7.2): Kafka down → `POST /triage/pacientes` returns 201; outbox waits; drains when the broker returns |
| Failure columns | Integration test (§7.7) |
| Claim lease and no lock during ACK | Integration test (§7.9): `pg_locks`/transaction check during a slow ACK; expired lease reclaimed; duplicate deduplicated |
| Reactive publisher client (G-T25) | Acceptance checklist (§7.10) on a spied `KafkaTemplate`; not executed in this spec |
| Future events and unresolved gap | Integration test (§7.9): sequence 1 → 3 → 2 applies in order; sequence 1 → 5 ends in the DLT with `GAP_UNRESOLVED` |
| Dead-letter topic | Integration test (§7.6): invalid `schemaVersion` → event in the dead-letter topic with metadata |
| Outbox cleanup | Integration test (§7.5) with two retention values |
| Test infrastructure | All tests use Testcontainers; no `.env` or manual setup required |

---

## 9. Traceability

**Requirements satisfied by this change:**

- **EVT-01, EVT-02, EVT-03, EVT-04** (from RF-01, RF-02, RF-03): Event definitions and publishing.
- **DEC-03**: Event envelope format and JSON structure (Proposed).
- **DEC-14**: Transactional outbox pattern (ADR-03, Proposed).
- **DER-02**: Atomicity of state change and event publication.
- **DER-03**: Idempotent consumers and escalation guard (contract of §4.1 and §5.1).
- **DER-07**: Correlation ID propagated through HTTP and Kafka headers (§5.2).
- **DER-09**: Durable event history `EventoHistorico` written in the same transaction (§2.2), as documented and accepted under OPEN-09/OPEN-10 Option A.
- **RN-04**: Audit row written in the same transaction as the outbox row.
- **NFR-01**: Non-blocking publisher and consumer.
- **NFR-02**: Kafka integration with documented message format and idempotent consumers.
- **NFR-04**: Triage never depends on Assistant availability; the request path does not wait for Kafka.
- **NFR-06, NFR-16**: Correlation ID in logs; Micrometer Tracing with OpenTelemetry bridge.
- **NFR-15, NFR-17**: Unit and integration tests with Testcontainers.
- **TC-03, TC-04**: R2DBC for PostgreSQL; Kafka topic `triaje.eventos`.
- **R-07** (dual writes): mitigated by the transactional outbox (§2.2).
- **R-08** (duplicate or out-of-order Kafka messages): mitigated by `pacienteSequence` and `eventId` deduplication (§4.1, §5.1).

No other Risk ID is cited. `docs/requirements.md` §17 has no entry for outbox retention or publisher outage behavior; those topics are tracked as open decisions above, not as risks.

**ADRs and records referenced (all Proposed or Open):**

- **ADR-02**: Kafka consumer model, dead-letter rule, `ProcessedEvent` TTL proposal.
- **ADR-03**: Transactional outbox pattern, including the short lease claim and no lock during ACK (`docs/adr/ADR-03-transactional-outbox.md`, Proposed).
- **ADR-08**: Projection store; decides the idempotence backend (§4.2).
- **ADR-01**: Two-service boundary (G-01). **ADR-14**: One database per service.
- **ADR-10**: Spring Boot 3.5.x baseline (the Boot 3.x tracing support is Micrometer Tracing, not Sleuth).
- **`docs/decisions/OPEN-09-10-outbox-durability-recovery.md`**: outbox durability versus `EventoHistorico`.

**Open decisions linked to this change:**

- **OD-3 (decided in documents, 2026-11-24):** Kafka retains `triaje.eventos` for 30 days; `EventoHistorico` is the accepted durable source for full reconstruction (OPEN-09/10 closed as Option A on 2026-11-25). The 30 days are not yet verified as configuration. The marker retention of §4.1 C5 derives from this value.
- **OD-5 (open):** retention period of `EventoHistorico`.
- **OPEN-09/OPEN-10 and G-T29 (closed 2026-11-25):** Option A, 7-day configurable cleanup; OD-5 remains open.
- **G-T06 (closed 2026-11-25):** per-patient counter incremented in the same transaction via `UPDATE ... RETURNING`; not implemented or runtime-verified here. **G-T25 (closed 2026-11-25):** spring-kafka async adapter for the C-05 publisher only; the ADR-02 consumer model remains open.

**Capabilities enabled by C-05:**

- **C-07** (Urgency State Projection): the consumer subscribes to `triaje.eventos` and maintains the projection in the store chosen by ADR-08.
- **C-09** (Escalation alerts): consumes `paciente_escalado` events; the delivery mechanism is open (G-T09).
