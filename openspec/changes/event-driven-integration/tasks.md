# Tasks: event-driven-integration (C-05)

**Traceability:** spec.md §1–7, design.md §1–8

**Deadline:** Day 1-2 (Nov 26-27)

**Branch:** feature/change-2-event-driven

**Done Criteria (all 5 must pass):**
1. Outbox row ⟺ state change committed (force rollback test)
2. Broker outage → delayed publication, nothing lost (broker stopped test)
3. Duplicate delivery → no second effect (consumer test)
4. Per-patient order via pacienteSequence
5. Architecture: Triage NO sync HTTP client to Assistant (NFR-04)

---

## Task 1: Database Schema — Flyway Migrations

**Scope:** Triage database schema (one database per service, ADR-14)

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 1.1 | OutboxEvento table (DDL) | `db/migration/V*__create_outbox_evento.sql` | Table exists with all columns (spec §2.1); indexes created; constraints enforced |
| 1.2 | EventoHistorico table (DDL) | `db/migration/V*__create_evento_historico.sql` | Table exists; audit columns (creado_en, actualizado_en); linked to patient via paciente_id |
| 1.3 | paciente.ultima_secuencia column | `db/migration/V*__add_sequence_to_paciente.sql` | Column added; DEFAULT 0; used for sequence assignment (spec §5.1) |
| 1.4 | Testcontainers PostgreSQL image | `docker-compose-test.yml` or test config | PostgreSQL 16+ with pgvector extension runs; Flyway migrations auto-applied |

**Test:** `DatabaseSchemaTest`: verify schema structure, constraints, indexes, and sequence behavior

---

## Task 2: Event Envelope — Java Classes

**Scope:** Serializable event model matching spec §1.2–1.4

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 2.1 | EventEnvelope (base) | `src/.../domain/event/EventEnvelope.java` | Fields: eventId, eventType, schemaVersion, pacienteId, pacienteSequence, occurredAt, correlationId, payload; schemaVersion is JSON number 1 |
| 2.2 | EventType enum | `src/.../domain/event/EventType.java` | PACIENTE_REGISTRADO, PACIENTE_ESCALADO, PACIENTE_EN_CONSULTA, PACIENTE_ALTA |
| 2.3 | Payload DTOs | `src/.../domain/event/payload/` | EVT-01 (nivel, slaMinutos, estado, fechaRegistro), EVT-02 (nivel, slaMinutos, fechaEscalamiento), EVT-03 (estadoAnterior, fechaEntradaConsulta), EVT-04 (estadoAnterior, fechaAlta) |
| 2.4 | JSON validation | Test: `EventEnvelopeValidationTest` | Missing eventId rejected; schemaVersion other than 1 accepts but logs; pacienteSequence is integer (not string) |

**Test:** `EventEnvelopeSerializationTest`: envelope → JSON → envelope round-trip; schema version validation

---

## Task 3: Outbox Insert Logic — R2DBC Transactions

**Scope:** Triage application service integration with event publication

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 3.1 | OutboxEvento repository (R2DBC) | `src/.../infrastructure/db/repository/OutboxEventoRepository.java` | Methods: insert(row), findUnpublished(batch), updatePublished(id, timestamp), updateFailure(id, error), cleanup(retentionDays) |
| 3.2 | Transactional wrapper for registration | `src/.../application/RegisterPatientService.java` or existing | Wraps: patient insert, outbox insert, audit insert, EventoHistorico insert in **single R2DBC transaction** (TransactionalOperator) |
| 3.3 | Sequence assignment in transaction | `src/.../application/RegisterPatientService.java` | On registration: `UPDATE paciente SET ultima_secuencia = ultima_secuencia + 1 RETURNING ultima_secuencia`; use returned value as pacienteSequence |
| 3.4 | Atomicity test: forced rollback | Test: `TransactionalOutboxTest.testRollbackPreventsPublish()` | Insert fails (e.g., constraint violation) → transaction rolls back → patient NOT registered, outbox row NOT created |
| 3.5 | Atomicity test: broker outage | Test: `BrokerOutageTest.testNoDataLossWhenKafkaDown()` | Kafka stopped; events inserted to outbox (publicado_en NULL); Kafka restarted; publisher drains backlog; all events appear in Kafka in insertion order |

**Test:** `OutboxAtomicityTest`: verify all-or-nothing behavior per event type (EVT-01, EVT-02, EVT-03, EVT-04)

---

## Task 4: Outbox Publisher — Reactive Kafka Client

**Scope:** Async background job draining unpublished outbox rows to Kafka

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 4.1 | Kafka producer configuration | `src/.../infrastructure/kafka/KafkaProducerConfig.java` | spring-kafka KafkaTemplate; async adapter via `Mono.defer(() → Mono.fromFuture(...))` (G-T25); no blocking |
| 4.2 | Publisher job class | `src/.../infrastructure/kafka/OutboxPublisherJob.java` | Reactive timer at configurable interval (default 5s); non-blocking throughout |
| 4.3 | Claim batch step | OutboxPublisherJob | `SELECT ... WHERE publicado_en IS NULL AND requiere_revision=FALSE FOR UPDATE SKIP LOCKED` in short transaction; set claimado_por, claimado_en, claim_expira_en |
| 4.4 | Send step | OutboxPublisherJob | FOR each claimed row: serialize to JSON, send via KafkaTemplate to topic triaje.eventos with key=pacienteId, partition determined by Kafka; wait for broker ACK reactively |
| 4.5 | Mark step | OutboxPublisherJob | After ACK: `UPDATE outbox_evento SET publicado_en=NOW(), publicado_por=instance_id WHERE id=row_id AND claimado_por=instance_id` |
| 4.6 | Failure handling | OutboxPublisherJob | On error: increment intentos, set primer_fallo_en (if NULL), set ultimo_fallo_en, set ultimo_error; exponential backoff; check failure threshold (spec §3.4); no circuit breaker |
| 4.7 | Cleanup job (optional) | Optional step | Delete published rows WHERE publicado_en < (now - retention) AND requiere_revision=FALSE; keep unpublished and flagged rows forever |
| 4.8 | Publisher test: reactive client | Test: `ReactivePublisherTest` | Publisher never blocks; BlockHound or profiling confirms no blocking on reactor threads |
| 4.9 | Publisher test: duplicate tolerance | Test: `DuplicateDeliveryTest.testPublisherRetryProducesKafkaAck()` | Publisher crashes after sending but before marking; on restart, row resent → broker accepts (Kafka idempotence or consumer dedupe) |

**Test:** `OutboxPublisherIntegrationTest`: end-to-end with Testcontainers (PostgreSQL + Kafka); verify claim, send, mark, cleanup cycle

---

## Task 5: Consumer Framework — Idempotence Contract

**Scope:** Clinical Assistant consumer contract implementation (backend ADR-02/ADR-08 pending)

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 5.1 | ProcessedEvent contract (interface) | `src/.../domain/event/ProcessedEventStore.java` | Abstraction for dedupe store; methods: markProcessed(eventId, timestamp), isProcessed(eventId), cleanup(olderThan) |
| 5.2 | Per-patient sequence tracker | `src/.../domain/event/PatientSequenceTracker.java` | In-memory or persisted per-patient counter; getLastApplied(patientId), setLastApplied(patientId, sequence) |
| 5.3 | Consumer listener | `src/.../infrastructure/kafka/TriageEventConsumer.java` | Receives from triaje.eventos; deserializes EventEnvelope; applies order/dedupe rules (spec §4.1) |
| 5.4 | Order guard logic (C2) | TriageEventConsumer | IF seq <= lastApplied: skip (ack); IF seq = lastApplied+1: apply + mark; IF seq > lastApplied+1: buffer in pendingByPatient |
| 5.5 | Duplicate detection (C1) | TriageEventConsumer | IF eventId in ProcessedEvent: skip (ack); ELSE: continue |
| 5.6 | Atomicity (C3) | TriageEventConsumer | Effect + ProcessedEvent insert + offset commit in coordinated manner (ADR-02 TBD whether PostgreSQL transaction or Redis Lua script) |
| 5.7 | Dead-letter queue | TriageEventConsumer | IF pendingByPatient entry > timeout: send to triaje.eventos.dlq with dlqReason=GAP_UNRESOLVED; ack original offset after DLT write confirmed |
| 5.8 | Idempotence test: second delivery | Test: `ConsumerIdempotenceTest.testDuplicateEventIgnored()` | Same eventId received twice; second receipt skipped; projection count NOT incremented twice |
| 5.9 | Order test: per-patient sequences | Test: `ConsumerOrderTest.testPatientSequenceEnforced()` | EVT-01 (seq=1), EVT-02 (seq=2), EVT-03 (seq=3) for same patient; applied in that order regardless of Kafka delivery order |
| 5.10 | Gap handling test | Test: `ConsumerGapTest.testFutureEventBuffered()` | Consumer receives seq=5 before seq=2 for same patient; seq=5 buffered; when seq=2,3,4 arrive, all applied in order |

**Test:** `ConsumerIntegrationTest`: end-to-end with Testcontainers + publisher; verify all 5 done criteria

---

## Task 6: Configuration & Documentation

**Scope:** Deployment configuration, README, and operational guides

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 6.1 | Publisher configuration (application.yml) | `src/main/resources/application.yml` | Properties: triage.outbox.publisher.* (interval, batch-size, timeouts, backoff, thresholds) |
| 6.2 | Consumer configuration (application.yml) | `src/main/resources/application.yml` | Properties: kafka.consumer.* (group-id, offsets), triaje.consumer.* (gap-timeout, map-size) |
| 6.3 | Kafka topic creation | `src/main/resources/kafka/topics.yml` or script | Topic triaje.eventos with N partitions; replication factor; retention policy; DLT triaje.eventos.dlq |
| 6.4 | README: event-driven-integration | `docs/changes/C-05-event-driven-integration.md` or similar | Event contracts, outbox pattern, publisher/consumer responsibilities, deployment checklist |
| 6.5 | Ops guide: failure handling | `docs/runbooks/outbox-failures.md` | How to identify and clear requiere_revision flag; monitor intentos and ultimo_error; retention policy |
| 6.6 | Correlation ID setup | Micrometer Tracing + OpenTelemetry config | X-Correlation-Id header → Kafka header → consumer logs; tracing context inherited across services |

**Test:** `ConfigurationTest`: all required properties present; defaults sensible; topology healthy on startup

---

## Task 7: Integration & End-to-End Tests

**Scope:** Comprehensive verification of all 5 done criteria

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 7.1 | Test: Atomicity (Criterion 1) | `test/.../OutboxAtomicityTest.java` | Forced rollback → patient NOT created, outbox row NOT created (all-or-nothing) |
| 7.2 | Test: Broker outage (Criterion 2) | `test/.../BrokerOutageTest.java` | Kafka stopped → events queued in outbox (publicado_en NULL); Kafka restarted → all events published in order; nothing lost |
| 7.3 | Test: Duplicate delivery (Criterion 3) | `test/.../DuplicateDeliveryTest.java` | Same eventId delivered twice → second receipt no business effect (Consumer C1); count NOT incremented twice |
| 7.4 | Test: Per-patient order (Criterion 4) | `test/.../PatientOrderTest.java` | Three events (EVT-01, EVT-02, EVT-03) with same patientId; applied in pacienteSequence order (1, 2, 3) regardless of Kafka partition rebalancing |
| 7.5 | Test: Architecture (Criterion 5) | `test/.../ArchitectureTest.java` | Grep: Triage service has NO HTTP client to Assistant; NO synchronous calls to Assistant endpoints; validation passes static analysis |
| 7.6 | Full E2E flow | `test/.../EventDrivenE2ETest.java` | Register patient (EVT-01) → escalate (EVT-02) → state change (EVT-03) → discharge (EVT-04); consumer applies all in order; projection consistent |

**Test:** All tests use Testcontainers (PostgreSQL + Kafka); coverage ≥ 70%; duration < 2min

---

## Task 8: Verification & Sign-off

**Scope:** Final acceptance and merge preparation

**Subtasks:**

| # | Task | File | Acceptance Criteria |
|---|------|------|-------------------|
| 8.1 | All 5 done criteria PASS | Test suite | All tests in §7 green; Testcontainers suite runs in CI |
| 8.2 | Branch readiness | feature/change-2-event-driven | Commits use work-unit pattern (1 task = 1 commit); force-pushed only if squashing; rebased on develop if needed |
| 8.3 | PR created | GitHub PR against develop | Title: "Implement event-driven integration (outbox + Kafka consumer framework)"; links to spec/design/tasks; description includes done criteria |
| 8.4 | Code review gate | PR | +1 from Change 1 session (downstream dependency); +1 from architecture (ADR alignment); no MUST-FIX comments |
| 8.5 | Update coordination file | COORDINATION-change-1-and-2-implementation.md | Note completion time, commit/PR link, any blockers for downstream changes |

---

## Implementation Order

**Recommended sequence (can parallelize some):**

1. **Task 1** (Database) — blocking for all others
2. **Task 2** (Event envelope) — in parallel with Task 1 (last 20% of Task 1)
3. **Task 3** (Outbox insert) — depends on Task 1; test Task 1 atomicity
4. **Task 4** (Publisher) — depends on Task 1, Task 2, Task 3; can start after Task 3 schema is in place
5. **Task 5** (Consumer) — in parallel with Task 4; specification-first (write contract before implementation)
6. **Task 6** (Config) — in parallel with Task 4, Task 5; needed for end-to-end testing
7. **Task 7** (E2E tests) — depends on all others; runs last
8. **Task 8** (Verification) — final gate

**Day 1-2 timeline:**
- **Day 1 (Nov 26) morning:** Tasks 1, 2, 3 complete (database, envelope, outbox logic)
- **Day 1 afternoon:** Tasks 4, 5 implementation begins; config drafted
- **Day 1 EOD:** Unit tests for Tasks 1-5 passing
- **Day 2 (Nov 27) morning:** Integration tests (Task 7) added; E2E flow tested
- **Day 2 afternoon:** Code review, fixes, PR merged

---

## Git Commit Pattern (work-unit-commits)

Each task = one or more focused commits:

```
c05-1-1: schema: create outbox_evento table with indices
c05-1-2: schema: create evento_historico table
c05-1-3: schema: add paciente.ultima_secuencia column
c05-2-1: domain: add EventEnvelope and EventType
c05-2-2: domain: add payload DTOs for EVT-01..04
c05-3-1: infra: add OutboxEventoRepository
c05-3-2: app: wrap patient registration in outbox transaction
c05-3-3: app: implement sequence assignment in transaction
c05-4-1: infra: configure spring-kafka KafkaTemplate (async)
c05-4-2: infra: implement OutboxPublisherJob (claim, send, mark)
c05-4-3: infra: implement publisher failure handling
c05-5-1: domain: add ProcessedEventStore contract
c05-5-2: app: implement consumer order guard and dedupe logic
c05-5-3: infra: implement dead-letter queue handling
c05-6-1: config: add publisher and consumer properties
c05-6-2: docs: README and ops runbook
c05-7-1: test: OutboxAtomicityTest
c05-7-2: test: BrokerOutageTest, DuplicateDeliveryTest
c05-7-3: test: PatientOrderTest, E2E flow
c05-8-1: docs: update COORDINATION file, PR ready
```

Each commit message: `c05-<task>-<seq>: <area>: <what>`

---

## Success Criteria Summary

✅ **All 5 done criteria PASS:**
1. Outbox row ⟺ state change committed
2. Broker outage → no data loss
3. Duplicate delivery → no effect
4. Per-patient order enforced
5. Architecture verified (no sync Triage→Assistant)

✅ **Code quality:**
- Coverage ≥ 70%
- No MUST-FIX comments in review
- Follows Bancolombia Clean Architecture

✅ **Ready for downstream:**
- Change 3 scaffold can proceed (waiting on these tests only)
- Changes 4-7 unblocked by this change
