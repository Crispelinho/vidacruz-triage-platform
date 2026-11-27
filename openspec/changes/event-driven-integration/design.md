# Design: event-driven-integration (C-05)

**Based on:** `spec.md` (normalized from proposal, ADRs, decisions G-T06, G-T25)

**Architecture Decision:** Event-driven integration between Triage and Clinical Assistant services via transactional outbox pattern with Kafka.

---

## 1. Architecture Components

```
Triage Service (Spring Boot 3.5.16)
  ├── API Layer: POST /triage/pacientes, PATCH /triage/pacientes/{id}/estado
  ├── Application Service: Patient registration, escalation, state transitions
  ├── R2DBC Transactions:
  │   ├── Patient state table
  │   ├── OutboxEvento table (DER-02, ADR-03)
  │   ├── Audit table (RN-04)
  │   └── EventoHistorico table (OD-9/OD-10)
  ├── Outbox Publisher Job (§3: at-least-once delivery)
  │   ├── Claim: bounded batch from OutboxEvento
  │   ├── Send: reactive non-blocking Kafka client (spring-kafka async adapter, G-T25)
  │   ├── Mark: publish timestamp tracking
  │   └── Cleanup: 7-day retention (G-T29, configurable)
  └── Kafka Topic: triaje.eventos

Kafka (Message Broker)
  └── Topic: triaje.eventos
      ├── Partition key: pacienteId (all events of one patient in one partition)
      └── Partitions: N (configurable for scalability)

Clinical Assistant Service (Spring Boot 3.5.16)
  ├── Consumer: Listen to triaje.eventos
  ├── Idempotence Store: ProcessedEvent (Redis or PostgreSQL, ADR-02/ADR-08 Proposed)
  ├── Per-Patient Sequence Guard: lastApplied[pacienteId]
  ├── Projection Store: EstadoUrgenciaProyeccion (DEC-10, Proposed)
  └── Dead-Letter Topic: triaje.eventos.dlq (for unresolved gaps)
```

---

## 2. Event Flow

### Registration Flow (EVT-01)
```
1. POST /triage/pacientes (synchronized request)
2. Application Service (R2DBC transaction):
   a. INSERT patient row (estado=REGISTRADO, paciente.ultima_secuencia=1)
   b. INSERT outbox_evento (eventId=UUID, event_type=paciente_registrado, paciente_sequence=1)
   c. INSERT audit row (RN-04)
   d. INSERT EventoHistorico row (durable record, OD-9/OD-10)
   e. COMMIT (all or nothing)
3. Return HTTP 200 (request path not blocked, NFR-04)
4. Outbox Publisher (async background):
   a. Poll every 5s (configurable §3.5)
   b. Claim batch from OutboxEvento WHERE publicado_en IS NULL
   c. FOR each row:
      - Send to Kafka triaje.eventos with key=pacienteId
      - Wait for broker ACK (non-blocking)
      - Mark publicado_en = NOW(), publicado_por = instance_id
      - Handle failures (exponential backoff, no circuit breaker)
5. Consumer (Clinical Assistant):
   a. Receive EVT-01 from triaje.eventos
   b. Check ProcessedEvent[eventId] → not found (new)
   c. Check lastApplied[pacienteId] → 0 (new patient)
   d. Verify pacienteSequence = 1 = lastApplied + 1 ✓ (apply)
   e. Atomically:
      - Update ProjectionEstadoUrgencia[patientId] with nivel from EVT-01.payload
      - Insert ProcessedEvent[eventId] with TTL
      - Set lastApplied[pacienteId] = 1
   f. Acknowledge Kafka offset
```

### Escalation Flow (EVT-02)
Similar to EVT-01, but triggered by SLA evaluation detecting an overdue patient.
- Contract: escalation happens exactly once per SLA threshold (SLA evaluation logic prevents double escalation, ADR-02)
- Event carries: nivel (new classification), slaMinutos, fechaEscalamiento

### State Transition Flows (EVT-03, EVT-04)
- EVT-03: Triggered by `PATCH /triage/pacientes/{id}/estado` to EN_CONSULTA
- EVT-04: Triggered by `PATCH /triage/pacientes/{id}/estado` to ALTA
- Both follow same transactional pattern: state change + outbox insert + audit + history (R2DBC transaction)

---

## 3. Data Contracts

### OutboxEvento Table Schema
```
outbox_evento:
  id BIGSERIAL PK
  event_id UUID UNIQUE ← Deduplication key
  event_type VARCHAR(50) ← EVT-01, EVT-02, EVT-03, EVT-04
  schema_version INTEGER = 1
  paciente_id UUID ← Partition key for Kafka
  paciente_sequence BIGINT ← Order guard (unique per patient)
  occurred_at TIMESTAMPTZ
  correlation_id TEXT ← For tracing (Micrometer Tracing)
  payload JSONB ← Event-specific data

  Publication tracking:
    publicado_en TIMESTAMPTZ ← Null until broker ACK
    publicado_por UUID ← Publisher instance ID
    
  Lease management (short-term exclusive claim, ADR-03):
    claimado_por UUID ← Current owner instance
    claimado_en TIMESTAMPTZ
    claim_expira_en TIMESTAMPTZ ← 30s default (§3.5), must cover full publish budget

  Failure tracking:
    intentos INTEGER
    primer_fallo_en TIMESTAMPTZ ← Start of current failure series
    ultimo_fallo_en TIMESTAMPTZ
    ultimo_error TEXT
    requiere_revision BOOLEAN ← Set when failure threshold reached; operator must clear

Indexes:
  idx_outbox_evento_publicado_en (publicado_en NULLS FIRST) WHERE publicado_en IS NULL
  idx_outbox_evento_claim (claim_expira_en, id) WHERE publicado_en IS NULL AND requiere_revision=FALSE
  idx_outbox_evento_paciente_id (paciente_id)
  idx_outbox_evento_creado_en (creado_en)
```

### EventoHistorico Table
- Durable record (OD-9/OD-10, Option A accepted 2026-11-25)
- Used for recovery if publisher loses the row or for audit
- Retention policy: OD-5 (pending decision)
- Not modified by publisher (retention is separate from OutboxEvento cleanup)

### Event Envelope (JSON)
```json
{
  "eventId": "uuid-v4",
  "eventType": "paciente_registrado | paciente_escalado | paciente_en_consulta | paciente_alta",
  "schemaVersion": 1,
  "pacienteId": "patient-uuid",
  "pacienteSequence": 1,
  "occurredAt": "2026-11-26T08:30:00+00:00",
  "correlationId": "correlation-uuid or inherited-string",
  "payload": { /* event-specific */ }
}
```

---

## 4. Implementation Decisions

| Decision | Chosen | Rationale |
|----------|--------|-----------|
| **Outbox durability** | Option A (accepted OD-9/OD-10) | 7-day cleanup + EventoHistorico recovery |
| **Publisher client** | spring-kafka KafkaTemplate async (G-T25) | Reactive adapter via Mono.fromFuture, no new dependency |
| **Broker outage behavior** | Backoff only, no circuit breaker | Outbox durability prevents data loss; no need for breaker |
| **Idempotence store** | ADR-02/ADR-08 Proposed | Decision pending; spec supports both PostgreSQL and Redis |
| **Partition key** | pacienteId | Ensures per-patient order preserved by Kafka |
| **Sequence assignment** | Per-patient counter in transaction (G-T06) | Contiguous gaps prevented; rolled-back sequences don't leave holes |
| **Reactive composition** | Project Reactor | NFR-01 (non-blocking request path), TC-03 (no blocking on event loop) |
| **Tracing** | Micrometer Tracing + OpenTelemetry | Spring Boot 3.x standard (not Cloud Sleuth, ADR-10) |

---

## 5. Test Strategy

| Scenario | Coverage |
|----------|----------|
| **Atomicity** | Forced rollback on outbox insert failure → patient not registered |
| **Broker outage** | Stop Kafka, publish events, restart Kafka → events reappear in order |
| **Duplicate delivery** | Same eventId received twice → no double-effect (Consumer C1) |
| **Per-patient order** | Three events, one patient → applied in pacienteSequence order (Consumer C2) |
| **Outbox cleanup** | Published row age > 7d → removed; unpublished row → never removed |
| **Dead-letter queue** | Gap in sequence > timeout → event sent to DLQ with dlqReason=GAP_UNRESOLVED |
| **Failure columns** | Publish fails 3x → intentos=3, requiere_revision=TRUE, operator clears |
| **Claim lease** | Multiple publisher replicas, no double-claim while in-flight |
| **Future events** | Consumer receives seq=5 before seq=2 → buffered in pendingByPatient |
| **Reactive client** | Publisher never blocks event loop (BlockHound or profiling) |
| **Sequence assignment** | Concurrent updates to one patient → each gets unique sequence |

---

## 6. Configuration (Illustrative, §3.5)

```yaml
triage:
  outbox:
    publisher:
      enabled: true
      interval-seconds: 5
      batch-size: 100
      max-in-flight-sends: 20
      send-timeout-ms: 10000
      lease-seconds: 30
      retry-base-delay-ms: 5000
      retry-max-delay-ms: 60000
      retry-multiplier: 1.5
      failure-threshold: 10
      failure-window-hours: 24
    cleanup:
      enabled: false
      published-retention: 7d
```

---

## 7. Dependencies

**On Change 1 (platform-foundation):**
- Both services (Triage + Clinical Assistant) built and running
- PostgreSQL with Testcontainers
- Kafka with Testcontainers
- Audit table schema (RN-04)

**On ADRs/Decisions:**
- ADR-02 (Consumer, Proposed): consumer retry strategy, dedupe store backend choice
- ADR-03 (Outbox, Proposed): converted to spec §2–3
- ADR-08 (Projection store, Proposed): impacts Consumer idempotence store choice
- G-T06 (accepted 2026-11-25): pacienteSequence assignment mechanism
- G-T25 (accepted 2026-11-25): reactive publisher client (spring-kafka)
- G-T29 (accepted 2026-11-25): 7-day outbox retention
- OD-9/OD-10 (accepted 2026-11-25): Option A (EventoHistorico + 7-day cleanup)
- OD-5 (pending): EventoHistorico TTL policy

---

## 8. Release Notes

This change delivers:
- Event envelope standard for Triage → Clinical Assistant communication
- Transactional outbox pattern ensuring at-least-once event publication
- Reactive (non-blocking) publisher job for event drainage
- Consumer idempotence framework (contract defined; backend ADR-02/ADR-08 pending)
- Per-patient ordering enforcement via pacienteSequence
- Dead-letter queue handling for sequence gaps
- 7-day outbox retention with configurable cleanup
- Micrometer Tracing integration for correlation

**Not in this change (ADR pending):**
- Consumer implementation (ADR-02 pending)
- Projection store choice (ADR-08 pending)
- EventoHistorico TTL policy (OD-5 pending)
