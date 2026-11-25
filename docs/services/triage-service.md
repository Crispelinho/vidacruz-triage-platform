# VidaCruz — Triage Service Analysis

Status: **Draft for review.** Service-level analysis of the Triage Service, derived from the master specification (`docs/requirements.md`, D1) and the requirements analysis (`docs/analysis.md`, D0, sections 3.1, 4, 5, 10, 11). IDs (RF, RN, API, EVT, VAL, NFR, DER, AMB, DEC, R) refer to D1 unless stated otherwise. Gap IDs (`G-T..`) are introduced by this document. Gaps marked **new** are not registered in D1 and need a decision before they are added there.

Baseline: Spring Boot 3.5.16, Spring Cloud 2025.0.3, Java 21 (DEC-13), PostgreSQL with R2DBC, Kafka topic `triaje.eventos`.

Skeleton read from `feature/scaffold-skeletons` (`triage-service/triage-service`) with `git show`; the branch was not checked out. Skeleton observations are in §12.

---

## 1. Bounded context and responsibilities

**Context:** Patient intake and triage for the 12 emergency rooms. The Triage Service is the system of record for patients, their triage level, their SLA, their clinical state and the clinical audit trail.

**Responsibilities (owned):**
- Register a patient with vital signs and classify it (RN-01), computing the SLA (RN-02). Covers RF-01.
- Move the patient through the clinical state flow `REGISTRADO → EN_CONSULTA → ALTA` and expose the patient view. Covers RF-02.
- Evaluate SLA breaches and escalate each patient at most once (RN-03). Covers RF-03.
- Write the clinical audit for every state or flag change (RN-04).
- Produce the hourly operations report from PostgreSQL (RF-05, API-06).
- Publish EVT-01 to EVT-04 through a transactional outbox (DER-02, DEC-14).
- Deliver escalation alerts to the medical team (RF-06, channel per DEC-09).

**Must not (boundaries from D0 §3.3 and D1):**
- Call the Clinical Assistant, synchronously or otherwise (NFR-04). Triage only emits events.
- Consume events to decide state. It owns its state.
- Call an LLM or perform any retrieval (RN-05 is Assistant-only).
- Expose any endpoint that deletes or updates audit rows (RN-04, DER-04).
- Compute the operations report in memory (RF-05).
- Share a database with the Assistant (DEC-10).

**Out of scope for this document:** RF-04 (Assistant), RN-05 (Assistant), API-04, API-05, EVT consumers in the Assistant, Redis projection.

---

## 2. Use cases

| ID | Use case | Trigger | Requirement | Main outcome |
|---|---|---|---|---|
| UC-T01 | Register and classify patient | `POST /triage/pacientes` | RF-01 | Patient `REGISTRADO` with level and SLA; audit `REGISTRO`; EVT-01 in outbox |
| UC-T02 | Start consultation | `PATCH …/estado` with `EN_CONSULTA` | RF-02 | State `EN_CONSULTA`; audit `ENTRADA_CONSULTA`; EVT-03 in outbox; SLA stops |
| UC-T03 | Discharge patient | `PATCH …/estado` with `ALTA` (from `REGISTRADO` or `EN_CONSULTA`) | RF-02 | State `ALTA`; audit; EVT-04; SLA stops; patient leaves evaluation |
| UC-T04 | Read patient | `GET /triage/pacientes/{id}` | RF-02 | Full view with `escalado` and `tiempoEnEstadoMinutos` |
| UC-T05 | Evaluate SLA and escalate | Scheduled evaluator every `triage.sla.evaluation-interval` | RF-03, RN-03 | Escalated patients: `escalado = true`; audit `ESCALAMIENTO`; EVT-02 in outbox |
| UC-T06 | Deliver escalation alert | EVT-02 available (see G-T09) | RF-06 | Webhook call to the configured endpoint; retries; terminal failure visible |
| UC-T07 | Operations report | `GET /triage/reportes/operacion` | RF-05 | Hourly buckets computed in PostgreSQL |
| UC-T08 | Publish outbox events | Background publisher, restartable | DER-02, DEC-14 | Pending outbox rows delivered to `triaje.eventos` |

### 2.1 UC-T01 — Register and classify patient
- **Preconditions:** request authenticated (DEC-05); body valid (VAL-01…VAL-11).
- **Main flow:** validate all fields and report all failures together (DEC-08); classify with RN-01 (first match, levels 1→4, level 5 refines level 4 — DEC-01); compute `slaMinutos` from RN-02; in one R2DBC transaction insert `Paciente`, `Clasificacion`, `AuditoriaClinica` (`REGISTRO`), `OutboxEvento` (EVT-01) and, when the header is present, `IdempotencyKey` (DEC-15).
- **Postconditions:** exactly one patient and one EVT-01 per successful registration (RF-01 acceptance).
- **Alternatives:** replay of an existing `Idempotency-Key` with the same body returns the original response and creates nothing (§7.3).

### 2.2 UC-T02 / UC-T03 — State transitions
- **Valid transitions:** `REGISTRADO → EN_CONSULTA`, `REGISTRADO → ALTA`, `EN_CONSULTA → ALTA` (RF-02).
- **Invalid or same-state:** 409 `INVALID_TRANSITION`, nothing changes (AMB-06, DEC-08).
- **Atomicity:** state update, audit row and outbox row in one transaction (DER-02). The update is conditional on the expected current state (§7.1).

### 2.3 UC-T05 — Evaluate SLA and escalate
- **Candidates:** `estado = REGISTRADO AND escalado = false` whose elapsed time since `fechaRegistro` is strictly greater than the SLA stored at registration (AMB-04, DEC-02).
- **Action per candidate:** conditional update `WHERE id = ? AND escalado = false AND estado = 'REGISTRADO'`. Only the request that changes the row writes audit (`ESCALAMIENTO`, equal states) and EVT-02 (RN-03, DER-03, DEC-04).
- **Failure:** a failed run leaves no partial escalation (single transaction per patient); the next interval retries.

### 2.4 UC-T06 — Alert the medical team
- Channel and dispatch mechanism are not settled. See G-T09 to G-T11 and AMB-10 / DEC-09.

### 2.5 UC-T07 — Operations report
- Range `desde`/`hasta` in ISO 8601 with offset or `Z` (DEC-11). Grouping by UTC hour; whether the hour is the registration hour or the consultation hour is open (G-T14) and is not decided here. Metrics per hour: total registered, count per level, average and p95 of minutes from registration to first `EN_CONSULTA`. Computed by SQL with window functions, `FILTER` and `percentile_cont` (RF-05). Patients without a consultation entry are counted in the total and excluded from average and p95 (AMB-05, DEC-11).

---

## 3. Endpoints

### 3.1 Cross-cutting rules
- **Authentication:** `X-API-Key` header validated by a WebFlux filter on all `/triage/**` (DEC-05, NFR-10). Missing or wrong key → 401. Actuator probes are public (NFR-05, D1 §9).
- **Correlation:** `X-Correlation-Id` is read or generated, returned in responses, written to logs and propagated to Kafka headers (NFR-06, DER-07).
- **Error body:** see G-T01 for the shape conflict. Codes are stable and in English; messages follow the Spanish examples of D1 §9.
- **Field names:** Spanish camelCase in JSON (DEC-08). Timestamps ISO 8601 with offset in responses; stored as UTC (`timestamptz`).
- **Input validation:** every field validated before any database work (NFR-11).

### 3.2 API-01 — `POST /triage/pacientes` (RF-01)

**Request fields and rules**

| Field | Rule | ID |
|---|---|---|
| `nombreCompleto` | 3–100 characters after trim | VAL-10 |
| `edad` | 0–120 | VAL-09 |
| `frecuenciaCardiaca` | 20–250 bpm | VAL-01 |
| `presionSistolica` | 60–260 mmHg | VAL-02 |
| `presionDiastolica` | 30–160 mmHg, and strictly less than `presionSistolica` (cross-field, reported on `presionDiastolica`) | VAL-03, VAL-04 |
| `spo2` | 70–100 % | VAL-05 |
| `temperatura` | 34.0–42.0 °C | VAL-06 |
| `dolor` | 0–10 | VAL-07 |
| `gcs` | 3–15 | VAL-08 |
| `quejaPrincipal` | non-empty after trim; maximum length 500 characters is proposed (G-T20), not yet accepted | VAL-11, DER-05 |

**Optional header:** `Idempotency-Key` (DEC-15). Semantics in §7.3.

**Responses**

| Status | When | Body |
|---|---|---|
| 201 | Created, or replay of the same key with the same body (D1 §9: 201 on replay) | `{ id, nivel, slaMinutos, estado: "REGISTRADO", fechaRegistro }` |
| 400 | Any validation failure | `{ code: "VALIDATION_ERROR", errors: [{ campo, mensaje }], correlationId }` |
| 409 | Same key, different body | `{ code: "IDEMPOTENCY_KEY_REUSED", message, correlationId }` |
| 401 | Missing or wrong API key | proposed `UNAUTHORIZED` (G-T02) |
| 400 | Malformed JSON or malformed `Idempotency-Key` | proposed `MALFORMED_REQUEST` (G-T02) |

**Side effects:** `Paciente`, `Clasificacion`, `AuditoriaClinica` (`REGISTRO`), `OutboxEvento` (EVT-01, once per successful creation), optional `IdempotencyKey`.

### 3.3 API-02 — `PATCH /triage/pacientes/{id}/estado` (RF-02)

- **Request:** `{ "estado": "EN_CONSULTA" | "ALTA" }`. Values outside the enum → 400 `VALIDATION_ERROR` (G-T02). `REGISTRADO` as a target is never valid.
- **Transition matrix**

| From \ To | REGISTRADO | EN_CONSULTA | ALTA |
|---|---|---|---|
| REGISTRADO | 409 (same state) | valid | valid |
| EN_CONSULTA | 409 (not reachable, G-T21) | 409 (same state) | valid |
| ALTA | 409 | 409 | 409 (same state) |

- **Responses:** 200 with the full patient view (same shape as API-03); 400; 404 (unknown id, proposed `NOT_FOUND`); 409 `INVALID_TRANSITION` with message such as `ALTA no puede pasar a EN_CONSULTA`.
- **Side effects:** on success, the state update, `AuditoriaClinica` (`estadoAnterior`, `estadoNuevo`, `tipoEvento`), and the outbox row EVT-03 or EVT-04 commit together.

### 3.4 API-03 — `GET /triage/pacientes/{id}` (RF-02)

- **Response 200:** `{ id, nombreCompleto, nivel, slaMinutos, estado, escalado, fechaRegistro, tiempoEnEstadoMinutos }`.
- `tiempoEnEstadoMinutos` = minutes since the last state change, or since `fechaRegistro` if none (RF-02). Requires a persisted last-change timestamp (G-T22).
- **Errors:** 404 `NOT_FOUND` (proposed, AMB-06 / DEC-08). Malformed UUID handling is part of G-T02.
- No side effects.

### 3.5 API-06 — `GET /triage/reportes/operacion` (RF-05)

- **Query:** `desde`, `hasta`, ISO 8601 with offset or `Z` (DEC-11).
- **Rules:** `desde` and `hasta` are both inclusive natural dates, in UTC. The span is limited to 90 natural dates; a span of 91 is rejected with HTTP 400 (G-T15, G-T17). The limit and the inclusive semantics are decided by the user and are pending their record in DEC-11.
- **Response 200:** array of hourly buckets `{ hora, totalRegistrados, porNivel, promedioEsperaMinutos, p95EsperaMinutos }` (D1 §9). `promedioEsperaMinutos` and `p95EsperaMinutos` are `null` when an hour has no patient with a consultation entry (proposed, G-T14).
- **Errors:** 400 for missing or unparseable parameters, reversed range or range over the limit. Error code open (G-T02); `INVALID_RANGE` is a working name, not a decision.
- No side effects. Read-only query; no caching assumed.

### 3.6 Error codes summary (Triage)

| Code | HTTP | Endpoint(s) | Status in D1 |
|---|---|---|---|
| `VALIDATION_ERROR` | 400 | API-01, API-02 | Defined (D1 §9) |
| `INVALID_TRANSITION` | 409 | API-02 | Defined (D1 §9) |
| `IDEMPOTENCY_KEY_REUSED` | 409 | API-01 | Defined (DEC-15) |
| `NOT_FOUND` | 404 | API-02, API-03 | Status defined, code not (G-T02) |
| `INVALID_RANGE` | 400 | API-06 | Status defined; code open (G-T02) |
| `MALFORMED_REQUEST` | 400 | all with body | Not defined (G-T02) |
| `UNAUTHORIZED` | 401 | all protected | Status defined (NFR-10), code not (G-T02) |
| `INTERNAL_ERROR` | 500 | all | Status defined (D0 §3.1), code not; no details leaked (DER-08) |

The Assistant codes (`LLM_UNAVAILABLE`) are out of scope.

---

## 4. Events and outbox

### 4.1 Envelope (DEC-03)

```json
{
  "eventId": "uuid",
  "eventType": "paciente_registrado",
  "schemaVersion": 1,
  "pacienteId": "uuid",
  "pacienteSequence": 1,
  "occurredAt": "2026-11-24T21:00:00Z",
  "correlationId": "string",
  "payload": {}
}
```

- Topic `triaje.eventos`, Kafka key `pacienteId` (ordering per patient), JSON, at-least-once delivery (TC-04, TC-08, DEC-03).
- `pacienteSequence` increases by one per patient for each event; generation is covered in G-T06.
- `occurredAt` is the domain timestamp taken in the same transaction as the state change.
- Event payloads carry no patient name and no free text (NFR-19 spirit, data minimization).

### 4.2 Event catalog

| ID | eventType | Published when | Payload | Consumers |
|---|---|---|---|---|
| EVT-01 | `paciente_registrado` | Registration committed | `nivel`, `slaMinutos`, `estado`, `fechaRegistro` | Assistant (projection) |
| EVT-02 | `paciente_escalado` | Escalation committed | `nivel`, `slaMinutos`, `fechaEscalamiento` | Alert dispatch (RF-06); Assistant (sequence continuity only, no count effect) |
| EVT-03 | `paciente_en_consulta` | Transition to `EN_CONSULTA` committed | `estadoAnterior`, `fechaEntradaConsulta` | Assistant (projection). "Triage metrics" listed in D1 §10 is not a Kafka consumer (G-T08) |
| EVT-04 | `paciente_alta` | Transition to `ALTA` committed | `estadoAnterior`, `fechaAlta` | Assistant (projection) |

Schema evolution: additive within `schemaVersion`; breaking changes bump the version (DEC-03).

### 4.3 Transactional outbox (DER-02, DEC-14)

- **Write path:** the outbox row is inserted in the same R2DBC transaction as the business change. No Kafka call inside the request transaction (no dual write, R-07).
- **Row content (proposed):** `eventId`, `pacienteId`, `eventType`, `pacienteSequence`, `payload` (JSON), `correlationId`, `occurredAt`, `createdAt`, `publicadoEn` (nullable), `intentos`, `ultimoError`.
- **Index:** partial index on `(publicadoEn)` `WHERE publicadoEn IS NULL` (D1 §11).
- **Publisher:** background, reactive, restartable and idempotent. Reads pending rows in bounded batches, publishes with `acks=all` and idempotent producer settings (proposed), marks `publicadoEn` in a separate transaction after broker acknowledgement. No database transaction or row lock is held while waiting for Kafka; ADR-03 specifies the short lease claim. On a crash between send and mark, the row is re-published after lease expiry: consumers tolerate duplicates through `eventId` and `pacienteSequence` (DER-03).
- **Multiple replicas:** the publisher can run on every replica. Concurrent readers claim rows with `FOR UPDATE SKIP LOCKED` inside a short transaction that stamps `claimadoPor`/`claimExpiraEn`, then release the lock before the send; ordering per patient is protected by `pacienteSequence` at the consumer, not by the publisher (G-T06).
- **Retention:** published rows deleted after a configurable retention (D0 §7); accepted default 7 days (G-T29, closed 2026-11-25). Unpublished and flagged rows are never deleted; `EventoHistorico` is not affected.

---

## 5. Persistence

Database: one PostgreSQL database for Triage (DEC-10). Access: R2DBC at runtime; Flyway migrations at startup through JDBC, outside the request path (TC-12). All timestamps are `timestamptz`, stored in UTC.

### 5.1 `Paciente`

| Column (proposed name) | Type | Constraint / note |
|---|---|---|
| `id` | uuid | PK |
| `nombre_completo` | varchar(100) | CHECK length ≥ 3 on the stored value; the application trims before insert (VAL-10) |
| `edad` | smallint | CHECK 0–120 (VAL-09) |
| `frecuencia_cardiaca` | smallint | CHECK 20–250 (VAL-01) |
| `presion_sistolica` | smallint | CHECK 60–260 (VAL-02) |
| `presion_diastolica` | smallint | CHECK 30–160 and `presion_diastolica < presion_sistolica` (VAL-03, VAL-04) |
| `spo2` | smallint | CHECK 70–100 (VAL-05); integer proposed (G-T18) |
| `temperatura` | numeric(3,1) | CHECK 34.0–42.0 (VAL-06) |
| `dolor` | smallint | CHECK 0–10 (VAL-07) |
| `gcs` | smallint | CHECK 3–15 (VAL-08) |
| `queja_principal` | text | CHECK non-empty after trim (VAL-11); length limit of 500 characters proposed (G-T20), not yet accepted |
| `estado` | varchar | CHECK in (`REGISTRADO`, `EN_CONSULTA`, `ALTA`) |
| `escalado` | boolean | NOT NULL, default false (DEC-02) |
| `fecha_registro` | timestamptz | NOT NULL |
| `fecha_ultimo_cambio_estado` | timestamptz | proposed; needed for `tiempoEnEstadoMinutos` (G-T22) |
| `ultima_secuencia` | integer | proposed; per-patient `pacienteSequence` counter (G-T06) |

**Indexes:** `(estado, escalado)` for the evaluator filter (D1 §11). The overdue test needs `sla_minutos` from `Clasificacion`, so the evaluator joins on `clasificacion(paciente_id)`, which needs its own index (unique FK). `(fecha_registro)` for RF-05 (D1 §11).

**Note on SignoVital:** D1 §11 lists `SignoVital` as "may be columns of Paciente". This document proposes columns (no separate table): one-to-one, no extra join, same constraints (G-T23).

### 5.2 `Clasificacion`

| Column | Type | Note |
|---|---|---|
| `id` | uuid | PK |
| `paciente_id` | uuid | FK to `Paciente`, unique (one classification per patient, no reclassification in scope) |
| `nivel` | varchar | CHECK in the five levels |
| `sla_minutos` | integer | the SLA applied at registration (D1 §11): a snapshot, so later config changes do not move existing deadlines |
| `fecha_clasificacion` | timestamptz | equals registration instant |

**Design note:** `nivel` lives in `Clasificacion` as the source of truth. Reads for the report and the API join on `paciente_id`. A denormalized copy in `Paciente` is not proposed.

### 5.3 `AuditoriaClinica` (append-only, RN-04)

| Column | Type | Note |
|---|---|---|
| `id` | bigint identity | PK |
| `timestamp` | timestamptz | UTC, server time |
| `paciente_id` | uuid | FK |
| `estado_anterior` | varchar, nullable | `NULL` for `REGISTRO` (G-T13) |
| `estado_nuevo` | varchar | |
| `tipo_evento` | varchar | enumeration open, see G-T13 |

**Indexes:** `(paciente_id, timestamp)` and `(tipo_evento, timestamp)` (D1 §11, RF-05).

**Append-only enforcement (DER-04):**
- The application role has `INSERT` and `SELECT` only on this table. `REVOKE UPDATE, DELETE, TRUNCATE` for that role.
- Migrations run under a separate owner role. If the application connects as the table owner, grants are not enough, so the owner must not be the runtime user.
- Defense in depth: a `BEFORE UPDATE OR DELETE` trigger that raises an exception.
- Integration test asserts that `UPDATE` and `DELETE` fail for the runtime role (TST-AUD).

### 5.4 `OutboxEvento`

Described in §4.3. Rows are inserted in the business transaction. Publication marks `publicadoEn`. Retention deletion is a scheduled job with the accepted 7-day default (G-T29, closed).

### 5.5 `IdempotencyKey` (DEC-15)

| Column | Type | Note |
|---|---|---|
| `key` | varchar(255) | PK (scope: see G-T03) |
| `request_hash` | char(64) | SHA-256 of the canonical request body |
| `paciente_id` | uuid | FK |
| `response_status` | smallint | 201 |
| `response_body` | jsonb | the original API-01 response (`id`, `nivel`, `slaMinutos`, `estado`, `fechaRegistro`; no patient name) |
| `created_at` | timestamptz | |
| `expires_at` | timestamptz | `created_at + 24 h` (DEC-15) |

Written in the same transaction as the `Paciente` insert. Expired rows removed by a scheduled job (G-T05).

### 5.6 Migrations

- Flyway versioned scripts (`V1__…`) for all tables, constraints, indexes, grants and trigger.
- Executed at startup through the JDBC driver (`spring-jdbc`, `flyway-database-postgresql`, `postgresql` JDBC), not on the request path (TC-12). The request path uses R2DBC only.
- Startup fails fast if the schema cannot be migrated (DER-01 spirit).

---

## 6. Requirement mapping

Status key: **Covered** = specified precisely enough to implement and test; open items listed do not block the acceptance criteria. **Gap** = at least one open item blocks an acceptance criterion or a contract.

| Req | Name | Covered by (this doc) | Status | Open items |
|---|---|---|---|---|
| RF-01 | Register and classify patient | §2.1, §3.2, §4.2 (EVT-01), §5.1–5.5, §7.3 | **Gap** | G-T01 error shape, G-T02 codes, G-T03 idempotency scope, G-T06 sequence storage |
| RF-02 | Clinical state flow and patient query | §2.2, §3.3, §3.4, §4.2 (EVT-03/04), §5.1, §7.1 | **Gap** | G-T08 EVT-03 consumer, G-T21 `EN_CONSULTA → REGISTRADO`, G-T22 last-change timestamp |
| RF-03 | SLA monitoring and escalation | §2.3, §5.2 (SLA snapshot), §5.1 index, §7.2 | **Covered** | Evaluator interval default is configuration (G-T27); not blocking |
| RF-05 | Operations report with advanced SQL | §2.5, §3.5, §5.3 indexes, §9 (TST-RPT) | **Gap** | G-T14 grouping timestamp and null averages, G-T15 range limit, G-T17 range semantics |
| RF-06 | Alert medical team on escalation | §2.4, §4.2 (EVT-02) | **Gap** | G-T09 dispatch mechanism, G-T10 delivery guarantee conflict, G-T11 retry exhaustion |
| RN-01 | Triage level classification | §9.1 (E1–E6 and boundaries) | **Covered** (open edge gaps) | G-T18 numeric precision (**new**), G-T19 whitespace in complaint match; AMB-01 documented clinical risk, not a gap in implementation |
| RN-02 | SLA by level | §2.3, §5.2 (snapshot), §9.1 | **Covered** | Configurable values (NFR-08), startup validation (DER-01) |
| RN-03 | Escalation idempotency | §2.3, §7.2, §9.3 (TST-ESC) | **Covered** | None blocking |
| RN-04 | Clinical audit | §5.3, §9.4 (TST-AUD) | **Gap** | G-T13 `tipo_evento` enumeration and registration `estado_anterior` undefined |

RN-05 and RF-04 belong to the Assistant and are not mapped here.

**Acceptance coverage (D1 §13 test IDs) for Triage:** TST-CLS-01…06, TST-STA, TST-ESC, TST-AUD, TST-IDM, TST-RPT, TST-RXN, and the Triage side of TST-RES. TST-PRJ is Assistant-only.

---

## 7. Concurrency and idempotency rules

### 7.1 State transitions (RF-02)
- Update with an expected-state guard: `UPDATE paciente SET estado = :nuevo, fecha_ultimo_cambio_estado = :now, ultima_secuencia = ultima_secuencia + 1 WHERE id = :id AND estado = :esperado`.
- Zero rows updated → the state changed under the caller: re-read, and return 409 `INVALID_TRANSITION` if the requested transition is no longer valid, or apply it if still valid. Simpler alternative: fail with 409 and let the client retry. Choice in §10 (G-T24).
- Audit and outbox insert in the same transaction, after the guarded update succeeds.

### 7.2 Escalation (RN-03, DEC-04)
- Single conditional update: `UPDATE paciente SET escalado = true … WHERE id = :id AND escalado = false AND estado = 'REGISTRADO'`.
- Only the request that changes a row (1 row affected) writes `AuditoriaClinica` (`ESCALAMIENTO`, `estado_anterior = estado_nuevo = REGISTRADO`) and EVT-02 in outbox.
- PostgreSQL re-evaluates the `WHERE` after waiting on the row lock, so a concurrent transition to `EN_CONSULTA` is serialized: if the transition committed first, escalation affects 0 rows.
- Several replicas run the same evaluator: each candidate has exactly one winner (TST-ESC concurrency test).
- Escalated patients that later enter `EN_CONSULTA` keep `escalado = true`; the flag is never reset.

### 7.3 Idempotent registration (DEC-15, R-09)
1. Compute `request_hash` over the canonical body (sorted keys, normalized values after validation, so that equivalent bodies hash equally).
2. Within the registration transaction, insert `IdempotencyKey` first. Its primary key makes concurrent requests with the same key wait on each other.
3. If the insert conflicts: read the stored row. Same hash → return the stored response (201, no new patient, no event). Different hash → 409 `IDEMPOTENCY_KEY_REUSED`.
4. A request that fails validation (400) never stores a key, so the key stays free.
5. Without the header, each POST creates a patient (D1 §4 RF-01).
6. Concurrent same-key requests create exactly one patient and one EVT-01 (TST-IDM). Scope and in-flight behavior are open (G-T03).

### 7.4 Outbox publisher and evaluator across replicas
- Evaluator: runs on every replica; correctness comes from §7.2, not from leader election.
- Publisher: row claiming (`SKIP LOCKED`) to avoid duplicate work. Duplicates are possible after crashes; they are harmless by contract (DER-03).

### 7.5 Consumer idempotency
- Triage consumes no events for state. If the alert dispatcher consumes EVT-02 (G-T09), it deduplicates on `eventId` and sends `eventId` as the webhook idempotency key (G-T10).

---

## 8. Reactive constraints

| Area | Rule | Source |
|---|---|---|
| HTTP | WebFlux handlers return `Mono`/`Flux`; no `.block()`, no blocking filters; API-key filter reactive | NFR-01, TC-02, DEC-05 |
| Persistence | R2DBC only on the request path; `TransactionalOperator` for multi-statement transactions | NFR-01, TC-03 |
| Migrations | Flyway over JDBC at startup only, on a dedicated thread, not on the request path | TC-12, DER-06 (principle) |
| Kafka | Reactive producer for the outbox publisher and any consumer; no blocking `send().get()` | NFR-01, NFR-02 |
| Scheduler | Evaluator as a reactive pipeline (`Flux.interval`-style or scheduler delegating to R2DBC), bounded batches, backpressure via bounded concurrency | DEC-04, D0 §6 |
| Timeouts | R2DBC acquisition, Kafka send, webhook call (alerts) | D0 §6 |
| Cancellation | Client disconnect propagates through `Mono` chains | D0 §6 |
| Testing | BlockHound on request threads; test fails when a blocking call runs on an event loop thread | TST-RXN |

**Library gap (new, G-T25):** the skeleton uses `spring-boot-starter-kafka` (spring-kafka, blocking `KafkaTemplate` API). A reactive pipeline needs `reactor-kafka` or an explicit reactive adapter. Decision required before the outbox publisher is implemented.

Triage has no JDBC on the request path. The DER-06 pgvector risk belongs to the Assistant.

---

## 9. Test plan

### 9.1 Classification (RN-01) — E1 to E6

Input values and expected results come from D1 §5. Checked by hand against the table in this document.

| Case | FC | PAS | PAD | SpO2 | Temp | Dolor | GCS | Queja | Expected | Rule that decides |
|---|---|---|---|---|---|---|---|---|---|---|
| E1 | 130 | 180 | 110 | 97 | 36.8 | 8 | 15 | Dolor torácico | CRITICO | FC > 120 (level 1). Dolor 8 would match level 3 but first match wins |
| E2 | 95 | 150 | — | 91 | 37.0 | 4 | 15 | Dificultad para respirar | URGENTE | SpO2 90–93 (level 2). FC 95 would match level 3 |
| E3 | 88 | 130 | — | 98 | 38.2 | 6 | 15 | Fiebre | URGENTE_2 | Temp 38.0–38.5 (level 3). FC 88 is in the AMB-01 gap |
| E4 | 72 | 120 | — | 99 | 36.5 | 2 | 15 | Herida por cortar | NO_URGENTE | No level 1–3; complaint not administrative |
| E5 | 70 | 115 | — | 99 | 36.4 | 0 | 15 | Renovación de recetario | NO_URGENTE_2 | Level 4 plus administrative complaint |
| E6 | 35 | 80 | — | 85 | 35.9 | 0 | 6 | Inconsciente | CRITICO | FC < 40, PAS < 90, SpO2 < 90, GCS ≤ 8 |

E4 and E5 depend on the FC 40–89 gap (AMB-01). The tests must assert the documented result, not a clinically corrected one.

**Boundary table (unit, RN-01):**

| Input | Expected | Note |
|---|---|---|
| SpO2 = 90 | URGENTE | inclusive boundary |
| SpO2 = 89 | CRITICO | |
| SpO2 = 93 | URGENTE | inclusive upper bound |
| Temperatura = 38.5 | URGENTE | level 2 starts at 38.5 |
| Temperatura = 38.0 | URGENTE_2 | |
| Temperatura = 37.9 | NO_URGENTE (with other values normal) | |
| FC = 120 | URGENTE_2 | level 1 needs > 120 |
| FC = 121 | CRITICO | |
| FC = 40 | NO_URGENTE | AMB-01 gap |
| FC = 39 | CRITICO | |
| PAS = 90 | URGENTE | |
| PAS = 89 | CRITICO | |
| PAS = 100 | URGENTE | |
| GCS = 8 / 9 / 13 / 14 | CRITICO / URGENTE / URGENTE / NO_URGENTE (others normal) | |
| Dolor = 7 | URGENTE_2 | |
| Queja `"  RENOVACIÓN de recetario "` | NO_URGENTE_2 (level 4 base) | trim, case, accent |
| Queja `"Renovacion de recetario"` | NO_URGENTE_2 | accent-insensitive |
| Queja `"Renovación  de recetario"` (double space) | to be decided (G-T19) | |

**Validation (VAL-01…VAL-11):** each range at min-1, min, max, max+1 returns 400 with the field name; VAL-04 reports `presionDiastolica` when equal to systolic; multiple failures reported together (DEC-08).

### 9.2 State machine (RF-02, TST-STA)
- Each valid transition: 200, state persisted, one audit row, one outbox row with the right eventType and sequence.
- `ALTA → EN_CONSULTA`, `EN_CONSULTA → EN_CONSULTA`, `ALTA → ALTA`, `REGISTRADO → REGISTRADO`: 409, no audit, no outbox, no state change.
- Concurrent transitions for the same patient: exactly one succeeds.
- `tiempoEnEstadoMinutos` equals time since last change, or since registration when none.

### 9.3 Escalation (RF-03, RN-03, TST-ESC)
- Overdue patient (elapsed > SLA) escalated once: one `escalado` update, one audit row, one EVT-02.
- Elapsed == SLA exactly: not escalated (AMB-04).
- Second evaluation: no change, no audit, no event.
- `EN_CONSULTA` and `ALTA` patients: never escalated, even when overdue by time.
- N concurrent evaluations (or N replicas): exactly one EVT-02.
- Short configured SLA (seconds) for test runs.

### 9.4 Audit (RN-04, TST-AUD)
- Rows exist for registration, each transition, and escalation, with the expected `tipo_evento` and states.
- No endpoint deletes or updates audit rows (contract test: no such route).
- Runtime DB role: `UPDATE` and `DELETE` on `AuditoriaClinica` fail (Testcontainers PostgreSQL).

### 9.5 Idempotency (DEC-15, TST-IDM)
- Same key, same body → 201 with the original body; `COUNT(paciente)` unchanged; no second EVT-01.
- Same key, different body → 409 `IDEMPOTENCY_KEY_REUSED`.
- No key → a new patient each POST.
- Concurrent requests with the same key → exactly one patient and one EVT-01.
- Failed validation with a key → 400, key not stored, a later valid request with the same key succeeds.
- Key expires after 24 h (time-controlled test); expired keys removed by the job.

### 9.6 Report (RF-05, TST-RPT)
- Fixture set with hand-computed results: hourly totals, per-level counts, average and p95 per hour.
- Patients without consultation entry: counted in total, excluded from average and p95 (AMB-05).
- Reversed range, missing offset, range over limit → 400.
- `EXPLAIN ANALYZE` on fixture data shows index use for `(paciente_id, timestamp)`, `(tipo_evento, timestamp)` and `fecha_registro` (RF-05).

### 9.7 Outbox and Kafka (DER-02, DEC-14, TST-IDM and E2E)
- Each successful write has exactly one outbox row in the same transaction; a forced rollback leaves none.
- Publisher delivers each row at least once; restart mid-batch re-publishes without loss.
- Kafka unavailable: registration and transitions still succeed (201/200); rows stay pending and drain when Kafka returns. Triage depends on Kafka only for delivery.
- Partition key is `pacienteId`; `pacienteSequence` strictly increases per patient.

### 9.8 API, security and errors (D0 §15)
- WebTestClient for every endpoint: 400, 404, 409, 401 paths with the codes of §3.6.
- Missing or wrong `X-API-Key` → 401 on every `/triage/**`; probes public.
- Error body never contains stack traces or SQL (DER-08).
- `X-Correlation-Id` returned and written to logs and Kafka headers (DER-07).

### 9.9 Reactive and resilience (TST-RXN, TST-RES Triage side)
- BlockHound active on request threads; any blocking call fails the test.
- Triage keeps serving with the Assistant absent: no calls are made to it (NFR-04). Covered by a test that asserts no outbound HTTP to the Assistant.

### 9.10 Unit vs integration split
- **Unit (JUnit 5, StepVerifier):** RN-01, RN-02, RN-03 decision logic, validations, transition matrix, complaint normalization.
- **Integration (Testcontainers PostgreSQL and Kafka):** repositories, conditional updates, audit grants, outbox publishing, idempotency transactions, report SQL.
- **API (WebTestClient):** §3 contracts and errors.
- **Concurrency:** parallel escalation and parallel idempotent POSTs.
- **E2E (docker-compose):** register → escalate → alert delivered (mock receiver).

**Skeleton note:** `TriageServiceApplicationTests.contextLoads()` starts the full context. It needs PostgreSQL, Kafka and configuration that the skeleton does not provide yet. It will fail until Testcontainers and the configuration exist (§12).

---

## 10. Gaps and open decisions

### 10.1 Open decisions referenced in D1

| ID | Topic | Gap for this service | Proposed resolution |
|---|---|---|---|
| AMB-01 / R-01 | FC 40–89 with no other criteria → NO_URGENTE | Clinical risk documented; rule kept as written | Keep as written; tests assert documented result; clinical review outside this system |
| AMB-03 | Complaint matching | Internal whitespace not decided (G-T19) | See G-T19 |
| AMB-04 / DEC-02 | Elapsed == SLA | Decided: not escalated | Implement strict `>` |
| AMB-05 / DEC-11 | Patients without consultation; time zone | Hour basis and null averages open (G-T14) | See G-T14, G-T17 |
| AMB-06 / DEC-08 | Unknown ID → 404; same-state → 409 | Codes for 404 and other errors undefined (G-T02) | See G-T02 |
| AMB-07 / DEC-02 | Escalation audit with equal states | `tipo_evento` values undefined (G-T13) | See G-T13 |
| AMB-10 / DEC-09 | RF-06 channel | Dispatch mechanism contradicts itself (G-T09, G-T10) | See G-T09 to G-T11 |
| AMB-14 / DEC-03 | Duplicates and out-of-order messages | Sequence storage not in D1 §11 (G-T06) | See G-T06 |
| DEC-04 | Evaluator with conditional update | Interval default not set (G-T27) | See G-T27 |
| DEC-05 | API key in `X-API-Key` | Key management and rotation not specified (G-T31) | Config secret from environment; rotation documented as out of scope |
| DEC-08 | API conventions and error body | Shape conflict with NFR-20 (G-T01) | See G-T01 |
| DEC-09 | Webhook channel with outbox-driven retries | Retry exhaustion and mock contract (G-T11) | See G-T11 |
| DEC-10 | One database per service | None for Triage | — |
| DEC-12 / R-02 | Boot 3.5 support status | Not verified in this analysis (no web lookup) | Check spring.io before the first build; keep 3.5.16 per skeleton |
| DEC-13 | Java 21 | D1 note says skeleton not yet changed (Java 25); scaffold branch already uses 21 (see §12) | Treat DEC-13 as applied; D1 note to update |
| DEC-14 / R-07 | Transactional outbox | Publisher claiming and retention (G-T06, G-T29) | See §4.3 |
| DEC-15 | Idempotency-Key | Scope, in-flight, cleanup (G-T03, G-T04, G-T05) | See those items |

### 10.2 Gaps and derived items found by this analysis

Items marked **new** are not registered in D1 and should be added to D1 §14 before implementation.

| ID | Gap | Affects | Impact | Proposed resolution |
|---|---|---|---|---|
| G-T01 | Error body shape: NFR-20 `{code, message, field?, correlationId}` is the canonical contract; DEC-08 is the reference for this decision. Documentary inconsistency: the RF-01 text (`requirements.md` line 176) describes `campo` and `mensaje`, pending alignment with NFR-20 | RF-01, RF-02, all 400s | Contract ambiguity | Canonical contract: NFR-20. `errors[]` is not added. Multiple field errors per request remain a pending decision that requires explicit approval |
| G-T02 | Error codes undefined for 404, 401, 500, malformed JSON, malformed header, invalid range, invalid enum | API-01, 02, 03, 06 | Clients cannot branch | Codes in §3.6; record in D1 §9 |
| G-T03 | Idempotency scope (global or per client), in-flight duplicates, key format and length | RF-01 / DEC-15 | Replay correctness across clients | Scope to (client identity, key); length max 255; in-flight waits on PK conflict (§7.3) |
| G-T04 | Empty `Idempotency-Key` header: absent or malformed? | API-01 | Minor | Empty value treated as malformed (400 `MALFORMED_REQUEST`) |
| G-T05 | Cleanup job for expired idempotency keys not specified | DEC-15 | Table growth | Scheduled deletion of `expires_at < now()` in batches |
| G-T06 | `pacienteSequence` generation and storage not in D1 §11 | RF-01, RF-02, RF-03, DEC-03 | EVT-01…04 cannot be emitted correctly | Column `ultima_secuencia` on `Paciente`, incremented in the same transaction as each outbox row |
| G-T07 | `occurredAt` source not defined | §4.1 | Event timestamp semantics | Domain instant taken within the transaction (§4.1) |
| G-T08 | EVT-03 lists "Triage metrics" as consumer; contradicts "no consumption for state" and D0 §8 consumer list | RF-02, EVT-03 | Unneeded self-consumption | Remove; record metrics in the use case |
| G-T09 | RF-06 dispatch: DEC-09 says "outbox-driven" while D0 §8 says "alerts channel consumes EVT-02" | RF-06, EVT-02 | Architecture unclear | Choose one in ADR: (a) Kafka consumer group in Triage reading EVT-02 with its own dedupe table, or (b) dedicated alert outbox table filled in the escalation transaction |
| G-T10 | Delivery guarantee conflict: "at least once" vs "at most one alert per escalation" (RF-06, DER-03) | RF-06 | Acceptance cannot be met literally | Deliver at least once; send `eventId` as the webhook idempotency key; receiver deduplicates. "At most one alert" becomes "at most one logical alert" |
| G-T11 | Retry limit and terminal failure state not defined | RF-06, DEC-09 | Unbounded retries | Max attempts and exponential backoff configurable; terminal state `FALLIDA` visible in logs and metrics |
| G-T12 | Interaction of escalation and transition writes | RF-02, RN-03 | Lost updates if unguarded | Guarded updates (§7.1, §7.2) |
| G-T13 | `tipo_evento` values and `estado_anterior` for registration not defined (D0 §11 uses `REGISTRO`, `ENTRADA_CONSULTA`; D1 uses `ESCALAMIENTO` and an undefined discharge value) | RN-04, RF-05 | Report and audit queries cannot be written | Enumerate `REGISTRO`, `ENTRADA_CONSULTA`, `ESCALAMIENTO`, `ALTA`; `estado_anterior = NULL` for `REGISTRO` |
| G-T14 | RF-05: grouping by registration hour or consultation hour; null average for empty hours; zero-count hours included or omitted | RF-05 | Report results differ | Group by registration hour (D0 §11); include only hours with registrations; null averages when no consultation entries |
| G-T15 | Range limit: D0 §4 cites DEC-11 for a range bound, but DEC-11 sets none; D0 §11 proposes 31 days | RF-05, API-06 | Unbounded query cost | Maximum 90 natural dates, both ends inclusive (decision confirmed by the user; pending record in DEC-11). A span of 91 natural dates is rejected with HTTP 400. This supersedes the 31-day proposal. Record in DEC-11 after approval. The error code for the rejection is open (see G-T02) |
| G-T16 | Operations report authorization scope (all patients or per room) | API-06 | Data exposure | All patients in the single API key scope; per-room filter not in spec |
| G-T17 | Range semantics: inclusive or exclusive bounds; offset required | DEC-11 | Edge results | Both dates inclusive (decision confirmed by the user; pending record in DEC-11). The technical filter is half-open, in UTC: `timestamp >= desde AND timestamp < day after hasta`. Offset or `Z` required; otherwise rejected with HTTP 400. The error code is open (see G-T02) |
| G-T18 | **new:** numeric precision for SpO2, FC, PAS, PAD, GCS, dolor is undefined. If SpO2 accepts decimals, values between the level 2 band top (93) and 100 are not all covered by the table, and boundary behavior is undefined between 93 and 94 | RN-01, VAL-05 | Classification discontinuity | Integers for FC, PAS, PAD, SpO2, GCS, dolor, edad; one decimal for temperature. Clinical confirmation required |
| G-T19 | Complaint normalization: internal whitespace (AMB-03 mentions spacing; DEC-01 says trim only) | RN-01, VAL-11 | Level 5 misses | Collapse internal whitespace before matching. The length limit is owned by G-T20 |
| G-T20 | Maximum length of `quejaPrincipal` (single owner of the length criterion; previously also stated in G-T19) | VAL-11 | Unbounded text in DB and logs | 500 characters (proposal). Referenced by the contract (§3) and the schema (§5) |
| G-T21 | D1 §13 mentions `EN_CONSULTA → REGISTRADO` "(if reachable)"; no transition to `REGISTRADO` exists in RF-02 | RF-02 | Test expectations ambiguous | Treat as 409; confirm |
| G-T22 | Last state change timestamp not persisted in D1 §11, needed for `tiempoEnEstadoMinutos` | RF-02 | Endpoint cannot be computed cheaply | Column `fecha_ultimo_cambio_estado` on `Paciente` |
| G-T23 | `SignoVital` undecided (D1 §11) | Persistence | Schema choice | Columns in `Paciente` (§5.1) |
| G-T24 | Behavior on concurrent transition conflicts: fail with 409 or re-read and apply | RF-02 | Client contract | Fail with 409 and let client retry (simpler, deterministic); re-read is an alternative |
| G-T25 | **new:** Kafka client library: skeleton has `spring-kafka` (blocking API); reactive needs `reactor-kafka` or equivalent | NFR-01, NFR-02, §8 | Non-reactive publisher risk | ADR choosing `reactor-kafka` for the outbox publisher and consumers |
| G-T26 | Skeleton lacks dependencies required by NFR-05, NFR-07, NFR-11, NFR-15, NFR-17 and TST-RXN (actuator, Micrometer Prometheus, validation, Testcontainers, BlockHound) | NFR-05/07/11/15/17, TST-RXN | Cannot meet probes, metrics, validation, tests | Add when implementing; not a D1 decision |
| G-T27 | Default evaluator interval not set (NFR-08 configurable only) | RF-03, DEC-04 | Test and demo timing | Default 30 s (proposal): worst-case escalation delay of 30 s on a 5-minute minimum SLA |
| G-T28 | SLA snapshot versus later configuration changes (**new**). D1 §11 says `Clasificacion` holds the SLA applied at registration, but the rule for later config changes is not stated | RN-02, NFR-08 | Deadlines move unexpectedly if read from config | Use the snapshot in `Clasificacion.sla_minutos`; configuration changes apply to new registrations only (documented in ADR) |
| G-T29 | Retention of published outbox rows | DER-02, DEC-14 | Table growth | **Closed 2026-11-25:** retention 7 days, configurable; scheduled job; never delete unpublished or flagged rows |
| G-T30 | Patient id format in path (non-UUID value → 400 or 404?) | API-02, API-03 | Minor | 404 for any id that is not a stored UUID, consistent with AMB-06 |
| G-T31 | Key management for `X-API-Key` (provisioning, rotation, storage) | DEC-05, NFR-09 | Operational | Key from environment or secret store; rotation out of scope; documented |

---

## 11. Candidate OpenSpec capability names (Triage Service only)

Kebab-case, one capability per cohesive behavior. Names are candidates; the final set is decided when changes are created. No Assistant capabilities are listed.

| Candidate capability | Covers |
|---|---|
| `triage-patient-registration` | RF-01, API-01, VAL-01…VAL-11, EVT-01 |
| `triage-classification-rules` | RN-01, E1–E6, complaint normalization |
| `triage-sla-policy` | RN-02, SLA snapshot, configuration validation (DER-01) |
| `triage-patient-state-flow` | RF-02, API-02, API-03, EVT-03, EVT-04 |
| `triage-sla-escalation` | RF-03, RN-03, EVT-02, evaluator |
| `triage-escalation-alerts` | RF-06, DEC-09 webhook, retries, alert status |
| `triage-clinical-audit` | RN-04, DER-04 (append-only), audit grants |
| `triage-operations-report` | RF-05, API-06, report SQL and indexes |
| `triage-event-outbox` | DER-02, DEC-14, publisher, retention, EVT envelope (DEC-03) |
| `triage-idempotent-registration` | DEC-15, Idempotency-Key, `IdempotencyKey` table, cleanup |
| `triage-api-security` | DEC-05, NFR-10, API key filter, public probes |
| `triage-error-contract` | NFR-20, DEC-08, error codes (§3.6), no-leak rule (DER-08) |
| `triage-observability` | NFR-05, NFR-06, NFR-07, NFR-19, DER-07, Micrometer metrics for Triage |
| `triage-persistence-schema` | §5 tables, constraints, indexes, Flyway migrations, DEC-10 |
| `triage-reactive-runtime` | §8 rules, BlockHound, JDBC isolation for migrations (TC-12) |

---

## 12. Skeleton observations (`feature/scaffold-skeletons`, read only)

Source: `triage-service/triage-service` on `feature/scaffold-skeletons` (commit `9040224`, read with `git show`).

| Item | Observed | Impact |
|---|---|---|
| Boot / Cloud | Spring Boot 3.5.16 plugin, Spring Cloud BOM 2025.0.3 | Matches baseline |
| Java | Toolchain 21 | Matches DEC-13; D1 note still says skeleton uses 25 |
| Gradle | Wrapper 8.14.8 | Within Boot 3.5 support (DEC-13 rationale) |
| Persistence | `spring-boot-starter-data-r2dbc`, `r2dbc-postgresql`; `spring-boot-starter-flyway`, `flyway-database-postgresql`, `spring-jdbc`, `postgresql` JDBC | Matches TC-03, TC-12 |
| Messaging | `spring-boot-starter-kafka` (spring-kafka) | Blocking API; G-T25 |
| Security | `spring-boot-starter-security`, webflux | Matches DEC-05 |
| Resilience | `spring-cloud-starter-circuitbreaker-reactor-resilience4j` | Matches TC-05 |
| Missing | actuator, Micrometer registry, validation, Testcontainers, BlockHound, reactor-kafka | G-T26, G-T25 |
| `application.yaml` | Only `spring.application.name` | Config for R2DBC, Flyway, Kafka, SLA, API key, interval missing (NFR-08, NFR-09, DER-01) |
| Tests | `contextLoads()` with `@SpringBootTest` only | Needs infrastructure (§9.10) |

No code is changed by this analysis.

---

## 13. Deliverable traceability

| Requirement | Document section | Test IDs |
|---|---|---|
| RF-01 | §2.1, §3.2, §4, §5 | TST-CLS-01…06, TST-IDM, VAL tests |
| RF-02 | §2.2, §3.3, §3.4, §7.1 | TST-STA |
| RF-03 | §2.3, §7.2 | TST-ESC |
| RF-05 | §2.5, §3.5 | TST-RPT |
| RF-06 | §2.4, §4.2 | E2E alert test (pending G-T09) |
| RN-01 | §9.1 | TST-CLS-01…06 |
| RN-02 | §5.2, §9.1 | TST-CLS-01…06 |
| RN-03 | §7.2 | TST-ESC |
| RN-04 | §5.3 | TST-AUD |
