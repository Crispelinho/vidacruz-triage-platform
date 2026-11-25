# VidaCruz — Master Requirements Specification (D1)

Status: **Draft for review.** No code, no commits.
Sources: `enunciado-prueba-dev-webflux.pdf` (brief, dated 2026-10-29) and `prompt-init.txt`.
Baseline decisions (from `deliverables-plan.md`): Spring Boot 3.x (D-1), Spring AI 1.x (D-2), Spring Cloud 2025.0.x (D-5). Java 21 target (DEC-13).

## 1. Context and objectives

VidaCruz runs 12 emergency rooms. Triage is manual, which causes inconsistent priority levels, no traceability of patient wait times, and no fast access to internal clinical protocols.

Objectives of the system:
- Register patients with vital signs and classify them automatically (RN-01).
- Monitor SLA per level and escalate overdue patients once (RN-02, RN-03).
- Keep a non-deletable clinical audit trail (RN-04).
- Answer clinical questions only from the official protocol corpus, with citations (RN-05).
- Demonstrate reactive programming, Kafka integration, resilience to LLM failure, and observability.

## 2. Scope

**In scope:** Triage Service and Clinical Assistant Service (minimum two services), PostgreSQL (R2DBC), Kafka topic `triaje.eventos`, Redis (urgency projection), RAG over the protocol corpus, Resilience4j around the LLM, Micrometer metrics, docker-compose, Kubernetes manifests, Azure target, tests, README, ADRs.

**Out of scope:** frontend (explicit), real clinical use, integration with hospital systems, patient identity verification, a production identity provider.

## 3. Conventions

- **Types:** EXPLICIT (required by the brief), DERIVED (required to satisfy an explicit requirement), PROPOSED (recommendation, not mandatory), AMBIGUOUS (multiple readings; see §14).
- **Priority:** M = mandatory, I = important, C = complementary.
- **IDs:** RF (functional), RN (business rule), VAL (validation), NFR (non-functional), TC (technical constraint), API, EVT (event), DER (derived), AMB (ambiguity), DEC (decision), DL (deliverable), TST (test).

## 4. Functional requirements

| ID | Name | Type | Pri | Component |
|---|---|---|---|---|
| RF-01 | Register and classify patient | EXPLICIT | M | Triage |
| RF-02 | Clinical state flow and patient query | EXPLICIT | M | Triage |
| RF-03 | SLA monitoring and automatic escalation | EXPLICIT | M | Triage |
| RF-04 | Clinical assistant (RAG query and urgency state) | EXPLICIT | M | Assistant |
| RF-05 | Operations report with advanced SQL | EXPLICIT | M | Triage |
| RF-06 | Alert medical team on escalation | EXPLICIT (channel open, AMB-10) | M | Triage (+ channel) |

### RF-01 Register and classify patient
- **Endpoint:** `POST /triage/pacientes` (see API-01).
- **Behavior:** validate input (VAL-01…VAL-11), classify by RN-01, compute `slaMinutos` by RN-02, persist with `estado = REGISTRADO`, write audit (RN-04), publish `paciente_registrado` to `triaje.eventos` (EVT-01).
- **Response:** `id`, `nivel`, `slaMinutos`, `estado` (`"REGISTRADO"`), `fechaRegistro` (ISO 8601 with offset).
- **Errors:** invalid field → 400 with message naming the field (DEC-08 for body shape).
- **Idempotency (optional, DEC-15):** the request may carry an `Idempotency-Key` header. Same key and same body → return the original response (no new patient). Same key with a different body → 409 `IDEMPOTENCY_KEY_REUSED`. Without the header, behavior is unchanged: each POST creates a patient.
- **Acceptance:** E1–E6 classify as in §13; invalid ranges return 400 with the field name; event published exactly once per successful registration.

### RF-02 Clinical state flow
- **Endpoints:** `PATCH /triage/pacientes/{id}/estado` (API-02), `GET /triage/pacientes/{id}` (API-03).
- **States:** `REGISTRADO`, `EN_CONSULTA`, `ALTA`.
- **Valid transitions:** `REGISTRADO → EN_CONSULTA`, `REGISTRADO → ALTA` (direct discharge), `EN_CONSULTA → ALTA`.
- **Invalid transition** (e.g. `ALTA → EN_CONSULTA`) → 409 with a descriptive message.
- **Each valid transition:** writes audit (RN-04) and publishes `paciente_en_consulta` or `paciente_alta` (EVT-03, EVT-04), atomically with the state change (DER-02).
- **GET:** returns full patient data, current state, `escalado` flag, and `tiempoEnEstadoMinutos` (time since the last state change, or since registration if none).
- **Acceptance:** each valid transition persists state, audit row, and event; each invalid transition returns 409 and changes nothing.

### RF-03 SLA monitoring and escalation
- **Mechanism:** a scheduled evaluator (decision DEC-04) runs every `triage.sla.evaluation-interval` (configurable, NFR-07).
- **Candidates:** patients with `estado = REGISTRADO` and `escalado = false`.
- **Overdue:** elapsed time since `fechaRegistro` is strictly greater than `slaMinutos` of the patient's level (AMB-04 documents the boundary).
- **Action:** atomically set `escalado = true` (RN-03), write audit, publish `paciente_escalado` (EVT-02).
- **Acceptance:** an overdue patient is escalated once; a second evaluation does nothing; patients in `EN_CONSULTA` or `ALTA` are never escalated; with a short configured SLA the escalation can be tested in seconds.

### RF-04 Clinical assistant
**Part A — RAG query.** `POST /asistente/consulta` (API-04).
- Retrieval over the protocol corpus (§12) with citations.
- If no relevant protocol exists, respond only with the fixed notice (RN-05). No clinical content is generated.
- If the LLM is unavailable, respond 503 (NFR-03).

**Part B — urgency state.** `GET /asistente/estado-urgencias` (API-05).
- Returns the count of patients not in `ALTA`, grouped by level.
- Built only from `triaje.eventos` (no calls to the Triage API). Persisted as a projection under DEC-10 / ADR-08 (Proposed); Redis or equivalent.

### RF-05 Operations report
- **Endpoint:** `GET /triage/reportes/operacion?desde=...&hasta=...` (API-06), ISO 8601 range.
- **Grouping:** by hour (UTC, AMB-05).
- **Metrics per hour:** total registered, count per level, average minutes from registration to first `EN_CONSULTA`, p95 of the same interval.
- **SQL:** advanced SQL in PostgreSQL (window functions, `FILTER`, ordered-set aggregates or equivalent). In-memory aggregation over all records is forbidden.
- **Acceptance:** results match a hand-computed fixture set; query plan uses the indexes in §11.
- **Range (OD-4, decided):** `desde` and `hasta` are required, ISO 8601 with offset or `Z`, interpreted in UTC. Both limits are inclusive. Maximum span: 90 calendar days (a range of exactly 90 days is accepted; 91 days is rejected). Filter: `timestamp >= desde` and `timestamp < día_siguiente(hasta)`, where `día_siguiente(hasta)` is the start of the next UTC calendar day after the UTC date of `hasta`. `desde` later than `hasta`, or a span above 90 days, returns 400 with the NFR-20 body (`code` `INVALID_REPORT_RANGE`, `field` naming the offending parameter). Responses are not claimed to follow RFC 9457.

### RF-06 Escalation alerts to the medical team
- **Requirement:** alert the medical team when a patient is escalated.
- **Open by design:** channel, format and technology are not specified. The choice, assumptions and justification must be documented (ADR and README).
- **Decision:** see AMB-10 / DEC-09. Minimum: alert is delivered at least once, failures are retried, and each escalation produces at most one alert (DER-03).

## 5. Business rules

### RN-01 Triage level classification
Conditions are evaluated in the order below. The first level whose conditions match determines the classification. Conditions inside a level are "any of".

| Order | Level | Conditions (any) |
|---|---|---|
| 1 | CRITICO | FC < 40 or FC > 120, or SpO2 < 90, or PA sistólica < 90, or GCS ≤ 8 |
| 2 | URGENTE | 90 ≤ SpO2 ≤ 93, or 90 ≤ PA sistólica ≤ 100, or Temperatura ≥ 38.5, or 9 ≤ GCS ≤ 13 |
| 3 | URGENTE_2 | 90 ≤ FC ≤ 120, or 38.0 ≤ Temperatura < 38.5, or Dolor ≥ 7 |
| 4 | NO_URGENTE | None of levels 1–3 |
| 5 | NO_URGENTE_2 | Level 4 matches **and** the chief complaint is in the administrative list (§12) |

Rules:
- Boundaries are inclusive exactly as written. Example: SpO2 = 90 is URGENTE, not CRITICO.
- Level 5 is a refinement of level 4, not a separate severity. The output `nivel` is `NO_URGENTE_2`.
- The brief says the highest-severity level applies when several conditions from different levels match. Because the order is 1→4 and the first match wins, the result is always the most severe level (AMB-02 documents the numeric-order issue for level 5).
- Complaint matching: trimmed, case-insensitive, accent-insensitive comparison to the administrative list (AMB-03).

**Decision table (examples):**

| Case | FC | PAS | SpO2 | Temp | Dolor | GCS | Queja | Result |
|---|---|---|---|---|---|---|---|---|
| E1 | 130 | 180 | 97 | 36.8 | 8 | 15 | Dolor torácico | CRITICO (FC > 120 matches level 1; Dolor 8 also matches level 3, which is lower severity and is not applied) |
| E2 | 95 | 150 | 91 | 37.0 | 4 | 15 | Dificultad para respirar | URGENTE (SpO2 90–93) |
| E3 | 88 | 130 | 98 | 38.2 | 6 | 15 | Fiebre | URGENTE_2 (Temp 38.0–38.5) |
| E4 | 72 | 120 | 99 | 36.5 | 2 | 15 | Herida por cortar | NO_URGENTE |
| E5 | 70 | 115 | 99 | 36.4 | 0 | 15 | Renovación de recetario | NO_URGENTE_2 |
| E6 | 35 | 80 | 85 | 35.9 | 0 | 6 | Inconsciente | CRITICO (FC < 40; SpO2 < 90; GCS ≤ 8) |

### RN-02 SLA by level
| Level | SLA (minutes) |
|---|---|
| CRITICO | 5 |
| URGENTE | 10 |
| URGENTE_2 | 30 |
| NO_URGENTE | 60 |
| NO_URGENTE_2 | 120 |

- **Start:** `fechaRegistro` (server time, UTC storage).
- **Stop:** the first transition to `EN_CONSULTA`. Transition to `ALTA` directly also stops the clock, and the patient leaves evaluation.
- **Expiry:** elapsed > SLA (AMB-04).
- SLA values are configurable (NFR-07), but the defaults above are the mandatory values.

### RN-03 Escalation idempotency
- A patient is escalated at most once. A second attempt publishes no event and writes no audit row.
- A patient in `EN_CONSULTA` or `ALTA` is never escalated.
- Implementation requirement: the escalation is a conditional update (`WHERE escalado = false AND estado = 'REGISTRADO'`); only the request that changes a row publishes and audits (DER-03).
- Safe under concurrency across multiple replicas of the Triage service.

### RN-04 Clinical audit
- Every state change or flag change (registration, entry to consultation, escalation, discharge) writes an audit row with: `timestamp`, `pacienteId`, `estadoAnterior`, `estadoNuevo`, `tipoEvento`.
- Escalation does not change `estado`; the audit row records `estadoAnterior = estadoNuevo = REGISTRADO` and `tipoEvento = ESCALAMIENTO` (AMB-07).
- No API endpoint deletes or updates audit rows (brief). Database-level enforcement is also required (DER-04).

### RN-05 Assistant anti-hallucination
1. Every response with clinical content must cite at least one source that exists in the corpus.
2. A citation to a source not in the corpus makes the response invalid. The service must not return it.
3. If no relevant match exists, the response is exactly the notice "no existe protocolo disponible" with no clinical content.
4. Retrieval relevance is gated before any LLM call (DEC-07). If no chunk passes the threshold, the LLM is not called.

**Edge cases:**

| Case | Expected behavior |
|---|---|
| Query matches PROTO-001 | Answer from PROTO-001, `fuentes` contains PROTO-001 |
| Query matches PROTO-002 | Answer from PROTO-002, `fuentes` contains PROTO-002 |
| Query about diabetes type 2 (no protocol) | Fixed notice, `fuentes: []`, LLM not called |
| LLM cites PROTO-999 (not in corpus) | Response rejected, fixed notice returned, event logged as anti-hallucination failure |
| LLM returns text but no citation | Response rejected, fixed notice returned |
| LLM circuit open or timeout | 503 with `LLM_UNAVAILABLE` (NFR-03) |
| Empty or whitespace-only question | 400 |

## 6. Validations (RF-01)

| ID | Field | Rule |
|---|---|---|
| VAL-01 | frecuenciaCardiaca | 20–250 bpm |
| VAL-02 | presionSistolica | 60–260 mmHg |
| VAL-03 | presionDiastolica | 30–160 mmHg |
| VAL-04 | presionDiastolica | must be strictly less than presionSistolica (cross-field) |
| VAL-05 | spo2 | 70–100 % |
| VAL-06 | temperatura | 34.0–42.0 °C |
| VAL-07 | dolor | 0–10 |
| VAL-08 | gcs | 3–15 |
| VAL-09 | edad | 0–120 years |
| VAL-10 | nombreCompleto | 3–100 characters (after trim) |
| VAL-11 | quejaPrincipal | non-empty text (DER-05; brief says "texto") |

Any failure → 400 with `campo` and `mensaje`. Multiple failures should be reported together (DEC-08).

## 7. Non-functional requirements

| ID | Requirement | Type | Pri |
|---|---|---|---|
| NFR-01 | Request paths are non-blocking: no JDBC, no blocking HTTP, no blocking SDK on the event loop | EXPLICIT | M |
| NFR-02 | Kafka integration with documented message format, idempotent consumers | EXPLICIT | M |
| NFR-03 | Circuit breaker on LLM calls with explicit fallback: 503 with clear message | EXPLICIT | M |
| NFR-04 | Triage Service never depends on Assistant availability (no synchronous calls to Assistant) | EXPLICIT | M |
| NFR-05 | Health, readiness and liveness probes | EXPLICIT | M |
| NFR-06 | Structured logs with correlation ID | EXPLICIT | M |
| NFR-07 | Micrometer metrics: classification counts per level, escalations, LLM latency (minimum set); DB and Kafka metrics | EXPLICIT | M |
| NFR-08 | SLAs and evaluation interval are configurable properties | EXPLICIT | M |
| NFR-09 | No hardcoded credentials; LLM API key from environment variable | EXPLICIT | M |
| NFR-10 | Authentication on endpoints (JWT or API key, documented) | EXPLICIT | M |
| NFR-11 | Input validation on all endpoints | EXPLICIT | M |
| NFR-12 | docker-compose starts everything with one command | EXPLICIT | M |
| NFR-13 | Kubernetes manifests: Deployment, Service, probes, ConfigMap, Secret | EXPLICIT | M |
| NFR-14 | Azure target (AKS or Container Apps) | EXPLICIT | M |
| NFR-15 | Unit and integration tests; LLM mockable | EXPLICIT | M |
| NFR-16 | OpenTelemetry tracing | EXPLICIT (bonus) | C |
| NFR-17 | Testcontainers for PostgreSQL and Kafka | EXPLICIT (recommended) | I |
| NFR-18 | Progressive commits with working versions | EXPLICIT (note) | I |
| NFR-19 | Metric labels avoid high cardinality (no patient ID, no free text) | DERIVED | M |
| NFR-20 | Errors use a consistent body: `{code, message, field?, correlationId}` | DERIVED | I |

## 8. Technical constraints

| ID | Constraint | Type |
|---|---|---|
| TC-01 | Spring Boot 3.x (3.5.x line, D-1) | EXPLICIT (line), DEC for patch |
| TC-02 | Spring WebFlux with Mono/Flux | EXPLICIT |
| TC-03 | R2DBC driver for PostgreSQL | EXPLICIT |
| TC-04 | Kafka, topic `triaje.eventos` | EXPLICIT |
| TC-05 | Resilience4j for the LLM | EXPLICIT |
| TC-06 | Spring AI or LangChain4j (choice documented) | EXPLICIT |
| TC-07 | Free choice of vector store, documented in ADR | EXPLICIT |
| TC-08 | Free message format, documented | EXPLICIT |
| TC-09 | At least two microservices: Triage and Clinical Assistant | EXPLICIT |
| TC-10 | Free architecture style, justified | EXPLICIT |
| TC-11 | Spring AI 1.x line (D-2); its pgvector store is JDBC-based (see DER-06) | DERIVED |
| TC-12 | Flyway migrations run at startup with a JDBC driver, outside the request path | DERIVED |

## 9. API contracts

Base paths are as written in the brief. Field names are Spanish camelCase (DEC-08). Timestamps are ISO 8601 with offset.

| ID | Method | Path | Service | Success | Errors | Side effects |
|---|---|---|---|---|---|---|
| API-01 | POST | /triage/pacientes | Triage | 201 (original 201 on replay) | 400, 409 | audit, EVT-01 (once per key) |
| API-02 | PATCH | /triage/pacientes/{id}/estado | Triage | 200 | 400, 404, 409 | audit, EVT-03/EVT-04 |
| API-03 | GET | /triage/pacientes/{id} | Triage | 200 | 404 | none |
| API-04 | POST | /asistente/consulta | Assistant | 200 | 400, 503 | none |
| API-05 | GET | /asistente/estado-urgencias | Assistant | 200 | 503 (projection unavailable) | none |
| API-06 | GET | /triage/reportes/operacion | Triage | 200 | 400 (bad range) | none |

Status code 201 for creation and 404 for unknown IDs are DEC-08 / AMB-06 choices.

**API-01 request**
```json
{
  "nombreCompleto": "Ana Pérez",
  "edad": 54,
  "frecuenciaCardiaca": 130,
  "presionSistolica": 180,
  "presionDiastolica": 110,
  "spo2": 97,
  "temperatura": 36.8,
  "dolor": 8,
  "gcs": 15,
  "quejaPrincipal": "Dolor torácico"
}
```
**API-01 response (201)**
```json
{
  "id": "6f1c2a9e-3b7d-4f0a-9c1e-2d5b8a7f4e10",
  "nivel": "CRITICO",
  "slaMinutos": 5,
  "estado": "REGISTRADO",
  "fechaRegistro": "2026-11-24T16:00:00-05:00"
}
```
**API-01 error (400)**
```json
{
  "code": "VALIDATION_ERROR",
  "errors": [{ "campo": "spo2", "mensaje": "debe estar entre 70 y 100" }],
  "correlationId": "c3a1..."
}
```

**API-01 idempotency error (409)**
```json
{ "code": "IDEMPOTENCY_KEY_REUSED", "message": "La clave ya fue usada con un cuerpo distinto", "correlationId": "c3a1..." }
```

**API-02 request:** `{ "estado": "EN_CONSULTA" }`
**API-02 response (200):** full patient view (same shape as API-03).
**API-02 error (409):** `{ "code": "INVALID_TRANSITION", "message": "ALTA no puede pasar a EN_CONSULTA" }`

**API-03 response (200):**
```json
{
  "id": "6f1c2a9e-...",
  "nombreCompleto": "Ana Pérez",
  "nivel": "CRITICO",
  "slaMinutos": 5,
  "estado": "REGISTRADO",
  "escalado": true,
  "fechaRegistro": "2026-11-24T16:00:00-05:00",
  "tiempoEnEstadoMinutos": 12
}
```

**API-04 request:** `{ "pregunta": "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?" }`
**API-04 response (200):**
```json
{
  "respuesta": "...",
  "fuentes": [{ "protocolo": "PROTO-001", "seccion": "Medidas iniciales" }]
}
```
**API-04 fallback (no protocol):** `{ "respuesta": "no existe protocolo disponible", "fuentes": [] }` (AMB-08 documents this shape).
**API-04 error (503):** `{ "code": "LLM_UNAVAILABLE", "message": "El asistente no está disponible temporalmente" }`

**API-05 response (200):**
```json
{ "total": 7, "porNivel": { "CRITICO": 1, "URGENTE": 2, "URGENTE_2": 2, "NO_URGENTE": 1, "NO_URGENTE_2": 1 }, "actualizadoEn": "2026-11-24T16:05:00Z" }
```

**API-06 response (200):** array of hourly buckets:
```json
[{ "hora": "2026-11-24T16:00:00Z", "totalRegistrados": 4,
   "porNivel": { "CRITICO": 1, "URGENTE": 1, "URGENTE_2": 1, "NO_URGENTE": 1, "NO_URGENTE_2": 0 },
   "promedioEsperaMinutos": 7.5, "p95EsperaMinutos": 12.0 }]
```

**Security:** all `/triage/**` and `/asistente/**` require authentication (NFR-10, DEC-05). Health and probe endpoints are public.

## 10. Event contracts

Topic: `triaje.eventos` (TC-04). Key: `pacienteId` (ordering per patient). At-least-once delivery; consumers must be idempotent (DER-03).

**Envelope (PROPOSED, DEC-03):**
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
`pacienteSequence` increases by one per patient for each event. The Assistant uses it to ignore out-of-order or duplicate messages (DER-03).

| ID | eventType | Producer | Consumers | Published when | Payload |
|---|---|---|---|---|---|
| EVT-01 | paciente_registrado | Triage | Assistant (projection), alerts (optional) | After successful registration, same transaction as outbox row | `nivel`, `slaMinutos`, `estado`, `fechaRegistro` |
| EVT-02 | paciente_escalado | Triage | Alerts (RF-06), Assistant (sequence continuity only) | After atomic escalation | `nivel`, `slaMinutos`, `fechaEscalamiento` |
| EVT-03 | paciente_en_consulta | Triage | Assistant (projection), Triage metrics | After transition to EN_CONSULTA | `estadoAnterior`, `fechaEntradaConsulta` |
| EVT-04 | paciente_alta | Triage | Assistant (projection) | After transition to ALTA | `estadoAnterior`, `fechaAlta` |

Schema evolution: additive changes only within `schemaVersion`; breaking changes bump `schemaVersion` and keep the old consumer path for one release (DEC-03).

**Durable history and reconstruction (OD-3, decided).** Triage keeps every published event in `EventoHistorico` (§11), written in the same transaction as the state change and its outbox row. Kafka keeps `triaje.eventos` for 30 days (`retention.ms=2592000000`). A full projection rebuild does not depend on that window: a reconstruction job in Triage reads `EventoHistorico` in `pacienteSequence` order per patient and republishes each event with its original `eventId`, `pacienteSequence` and payload. Consumers already ignore duplicates by `eventId` and sequence, so republishing is idempotent. The Assistant never calls Triage. After the history is replayed, live events continue with sequences above the last replayed sequence for each patient, without loss or duplicates.

## 11. Persistence

| Entity | Service | Store | Purpose | Key constraints |
|---|---|---|---|---|
| Paciente | Triage | PostgreSQL | Patient, vitals, level, state, flags | PK uuid; check constraints on VAL ranges; index `(estado, escalado)` for SLA evaluator |
| SignoVital | Triage | PostgreSQL | Vital signs (may be columns of Paciente) | Same ranges as VAL-01…VAL-09 |
| Clasificacion | Triage | PostgreSQL | Level and SLA applied at registration | FK to Paciente |
| AuditoriaClinica | Triage | PostgreSQL | RN-04 log | Append-only: no UPDATE/DELETE grants; index `(pacienteId, timestamp)` |
| OutboxEvento | Triage | PostgreSQL | Pending events (DER-02) | Index `(publicadoEn)` partial where null |
| EventoHistorico | Triage | PostgreSQL | Durable copy of every published event, for projection reconstruction (OD-3, DER-09) | PK `eventoId`; unique `(pacienteId, pacienteSequence)`; full envelope in `payload` (jsonb); append-only: no UPDATE or DELETE grants; accepted durable record under OPEN-09/OPEN-10 Option A (developer, 2026-11-25); written in the same transaction as the state change and OutboxEvento; no time-based purge until OD-5 is decided |
| DocumentoClinico | Assistant | PostgreSQL | Protocol metadata (protocolo, seccion, fuente) | Unique `(protocolo, seccion)` |
| FragmentoDocumental | Assistant | pgvector | Chunks and embeddings | Index on embedding; metadata filter by protocolo |
| EstadoUrgenciaProyeccion | Assistant | Redis | Count by level, last sequence per patient | Hash per level; dedupe set of processed eventIds with TTL |
| IdempotencyKey | Triage | PostgreSQL | Replay of POST /triage/pacientes (DEC-15) | PK `key`; stores request hash, patient id and response; expires after 24 h; written in the same transaction as the Paciente insert |
| ProcessedEvent | Assistant | Redis | Consumer dedupe (DER-03) | TTL ≥ retention of Kafka topic |

Indexes for RF-05: `AuditoriaClinica(pacienteId, timestamp)`, `AuditoriaClinica(tipoEvento, timestamp)`, and `Paciente(fechaRegistro)`.

Separation (AMB and DEC-10): PostgreSQL database per service. Redis is used only for the Assistant projection. Vector data stays with the Assistant.

## 12. Protocol corpus and administrative list

**Corpus (minimum, from the brief):**
- **PROTO-001 — Crisis Hipertensiva, manejo inicial.** Definition: BP ≥ 180/120 mmHg; emergency if target-organ damage, urgency otherwise. Initial measures: re-check BP after 5 minutes rest; do not reduce BP more than 25% in the first hour; monitor for encephalopathy. Contraindications: sublingual nifedipine; rapid IV reduction without intensive-care monitoring.
- **PROTO-002 — Sepsis, identificación temprana (qSOFA).** Criteria: 2 or more of respiratory rate ≥ 22, altered mental status, SBP ≤ 100. Initial actions: lactate and blood cultures before antibiotic; broad-spectrum antibiotic in the first hour; 30 ml/kg crystalloids if hypotension or lactate ≥ 4.
- **PROTO-003 — Deshidratación pediátrica.** Classification by weight loss: mild 3–5%, moderate 5–8%, severe > 8% or altered mental status. Rehydration: mild or moderate without vomiting → oral 50 ml/kg over 4 h; severe → IV bolus 20 ml/kg.

**Administrative complaints (level 5 trigger):** `documentos`, `recolección de resultados`, `renovación de recetario`, `vacunación`, `seguimiento`.

**Assistant acceptance questions:**
1. "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?" → PROTO-001 cited.
2. "¿Cuándo se administra el primer antibiótico en sepsis?" → PROTO-002 cited.
3. "¿Cuál es el tratamiento de primera línea para la diabetes tipo 2?" → fixed notice, no clinical content.

The corpus may be extended with our own protocols. Adding protocols must not alter these three.

## 13. Acceptance test cases

**Classification (TST-CLS-01…06):** E1 → CRITICO, E2 → URGENTE, E3 → URGENTE_2, E4 → NO_URGENTE, E5 → NO_URGENTE_2, E6 → CRITICO (§5 table).

**State machine (TST-STA):** each valid transition succeeds with audit and event; `ALTA → EN_CONSULTA`, `EN_CONSULTA → REGISTRADO` (if reachable) and same-state transitions return 409.

**Escalation (TST-ESC):** overdue patient escalated once; repeat evaluation changes nothing; `EN_CONSULTA` and `ALTA` patients not escalated; concurrent evaluations produce one event.

**Audit (TST-AUD):** rows exist for registration, transitions, escalation; no endpoint deletes or updates them.

**RAG (TST-RAG):** the three questions in §12 with mocked LLM; LLM not called when no relevant chunk; invalid citation rejected.

**Resilience (TST-RES):** LLM failure opens circuit; fallback 503 with `LLM_UNAVAILABLE`; Triage keeps responding while Assistant is down.

**Idempotency (TST-IDM):** same key and same body returns the original response without a second patient; same key with a different body → 409; no key → a new patient on each POST; concurrent requests with the same key create exactly one patient.

**Projection (TST-PRJ):** Assistant rebuilds counts from events only; duplicate and out-of-order events are ignored.

**Report (TST-RPT):** hourly counts, averages and p95 match a fixture set; patients without consultation entry are excluded per AMB-05.

**Reactive (TST-RXN):** no blocking calls on request threads (BlockHound or equivalent in tests).

## 14. Ambiguities and decisions

Each AMB is resolved by a DEC proposal. Decisions are ours to document; they are not questions for the evaluator.

| ID | Ambiguity | Proposed decision | DEC |
|---|---|---|---|
| AMB-01 | FC 40–89 with no other criteria matches no level 1–3 → NO_URGENTE | Keep the rule as written. Record as clinical risk. Do not change clinical rules. | — (risk R-01) |
| AMB-02 | Level 5 has the highest number but lowest severity; "mayor gravedad" may read as numeric minimum | Evaluate 1→4 in order, first match wins; level 5 only as a refinement of level 4 | DEC-01 |
| AMB-03 | Complaint matching (case, accents, spacing) | Trimmed, case- and accent-insensitive equality | DEC-01 |
| AMB-04 | Elapsed time exactly equal to SLA | Overdue only if strictly greater | DEC-02 |
| AMB-05 | RF-05: patients who never enter consultation; time zone | Excluded from average and p95, counted in total; range in UTC, offset required | DEC-11 |
| AMB-06 | Undefined errors: unknown ID; same-state transition | 404 for unknown ID; 409 for same-state transition | DEC-08 |
| AMB-07 | ESCALADO is a flag, not a state; audit fields when state does not change | State stays REGISTRADO; audit records ESCALAMIENTO with equal states | DEC-02 |
| AMB-08 | Response for "no protocol" is undefined | `respuesta` = fixed notice, `fuentes: []`, HTTP 200 | DEC-07 |
| AMB-09 | JWT or API key | API key in header for both services (simpler to test); JWT as documented alternative | DEC-05 |
| AMB-10 | RF-06 channel | Email is hard to test; webhook + outbox-driven retry chosen over queue or SMS | DEC-09 |
| AMB-11 | Azure subscription availability | Manifests always delivered; deployment URL depends on access; blocker documented if unavailable | R-06 |
| AMB-12 | Boot 3.5 open-source support: Spring Boot 3.5.16 is the last OSS release of the 3.5.x line (official spring.io announcement, 2026-06-25); continued OSS support requires 4.0.x or 4.1.x | Baseline fixed by the brief (Boot 3.x). Accept the end of OSS support in ADR-10 and record it there; the risk is not removed | DEC-12, R-02 |
| AMB-13 | Java version (skeletons: 25) | Java 21 (DEC-13) | DEC-13 |
| AMB-14 | Duplicates and out-of-order Kafka messages | `pacienteSequence` plus eventId dedupe | DEC-03 |
| AMB-15 | LLM provider | Any provider behind Spring AI; tests use mock | DEC-06 |
| AMB-16 | Relevance threshold for retrieval | Calibrate with the three acceptance questions; document value | DEC-07 |

### Decision register (DEC)

Proposed decisions. Each one becomes an ADR (see `adr-roadmap.md`, D3).

| DEC | Decision | Alternatives considered | Status |
|---|---|---|---|
| DEC-01 | Classification evaluates levels 1→4 in order, first match wins; level 5 refines level 4; complaint matched trimmed, case- and accent-insensitive | Numeric-order interpretation; exact match | Proposed |
| DEC-02 | Escalation: overdue means strictly greater than SLA; `estado` stays REGISTRADO; `escalado` is a persistent flag; audit row ESCALAMIENTO with equal states | Add ESCALADO as a state; ≥ boundary | Proposed |
| DEC-03 | Event envelope with `eventId`, `eventType`, `schemaVersion`, `pacienteId`, `pacienteSequence`, `occurredAt`, `correlationId`, `payload`; JSON; additive schema changes | Avro with registry; per-event topics | Proposed |
| DEC-04 | SLA evaluator is a scheduled job with conditional update (single winner per patient), configurable interval | Kafka-delayed messages; Quartz | Proposed |
| DEC-05 | Authentication: API key in `X-API-Key` header, validated by a WebFlux filter; JWT documented as alternative | JWT with external IdP | Proposed |
| DEC-06 | LLM and RAG via Spring AI 1.x behind an interface; provider chosen by configuration; mocked in tests | LangChain4j | Proposed |
| DEC-07 | RAG: relevance threshold gate before LLM call; citations validated against corpus; no-match returns fixed notice with HTTP 200 and `fuentes: []`; circuit breaker with timeout and 503 fallback | Always call LLM; 404 for no match | Proposed |
| DEC-08 | API conventions: Spanish camelCase fields; 201 on creation; 400 for validation with field list; 404 unknown ID; 409 invalid or same-state transition; error body per NFR-20 | Other status mappings | Proposed |
| DEC-09 | RF-06 channel: outbox-driven webhook to a configured endpoint, retries with backoff, mock receiver in docker-compose | Email (SMTP); queue; SMS | Proposed |
| DEC-10 | One PostgreSQL database per service; Redis only for the Assistant projection | Shared database | Proposed |
| DEC-11 | Report: hours in UTC; range requires offset or `Z`; patients without consultation entry excluded from average and p95, counted in total | Include them with wait to now | Proposed |
| DEC-12 | Version pins: Spring Boot 3.5.x latest patch, Spring Cloud 2025.0.x, Spring AI 1.1.x GA; verified on spring.io before build | Boot 4.x | Proposed (user chose Boot 3.x) |
| DEC-13 | Java 21 target for both services and the Gradle toolchain. Boot 3.5 supports Java 25 per the user's review, but Gradle 9.x (needed for Java 25) is outside Boot 3.5's documented Gradle 7/8 support. Java 21 avoids that combination. Not yet applied to the skeletons (no code changes before D1 approval). | Java 25 (skeleton default) | Accepted (user, 2026-11-24) |
| DEC-14 | Transactional outbox in each service's database; a publisher drains unpublished rows to Kafka | Direct publish after commit (dual write) | Proposed |
| DEC-15 | Optional `Idempotency-Key` header on `POST /triage/pacientes`. Stored with request hash, response and patient id in `IdempotencyKey` (24 h TTL). Same key + same body → original response; same key + different body → 409. Must be implemented and tested; if the deadline is at risk, keep it as a documented proposal and ship mandatory requirements first. | No idempotency (current brief scope) | Accepted (user, 2026-11-24) |

## 15. Deliverables

| ID | Deliverable | Type | Pri |
|---|---|---|---|
| DL-01 | GitHub repository (public or private with evaluator access) with full source | EXPLICIT | M |
| DL-02 | README: one-command local run, architecture diagram, technologies, key decisions with justification | EXPLICIT | M |
| DL-03 | Automated tests for business flows | EXPLICIT | M |
| DL-04 | Azure URL with accessible APIs and Kubernetes/IaC manifests | EXPLICIT | M |
| DL-05 | At least two ADRs | EXPLICIT | M |
| DL-06 | Analysis set: D0 `analysis.md`, D1 `requirements.md`, D2–D6 roadmaps and matrices | EXPLICIT (prompt §11) | M |
| DL-07 | Progressive commit history with working versions | EXPLICIT (note) | I |

## 16. Derived requirements

| ID | Requirement | Reason |
|---|---|---|
| DER-01 | Startup validation of configuration (SLA values, interval, LLM key presence) | NFR-08, NFR-09 |
| DER-02 | Transactional outbox for `triaje.eventos` | Atomic state change and event publication |
| DER-03 | Idempotent consumers and escalation guard | At-least-once Kafka; RN-03 |
| DER-04 | Audit append-only at DB level (no UPDATE/DELETE grants) | RN-04 |
| DER-05 | Non-empty chief complaint | Brief says "queja principal (texto)" |
| DER-06 | pgvector store is JDBC-based; keep it off the event loop (bounded elastic or async adapter) with documented risk | NFR-01, TC-11 |
| DER-07 | Correlation ID propagated via HTTP header and Kafka headers | NFR-06 |
| DER-08 | Consistent error body (NFR-20) | Error handling criteria |
| DER-09 | Durable event history `EventoHistorico` for projection reconstruction, republished with original `eventId` and `pacienteSequence` | OD-3, DER-02, NFR-02 |

## 17. Risks (summary)

| ID | Risk | Mitigation | ADR |
|---|---|---|---|
| R-01 | FC 40–89 gap in classification | Document; do not change rule | — |
| R-02 | Boot 3.5 end of open-source support | Document; plan upgrade path | DEC-12 |
| R-03 | Spring AI / pgvector JDBC blocking | Isolate on bounded elastic; measure | DEC-06, DEC-07 |
| R-04 | LLM latency and cost during evaluation | Timeouts, circuit breaker, mock in tests | DEC-07 |
| R-05 | Spring AI 1.x milestone/GA version drift | Pin GA version in build | DEC-12 |
| R-06 | Azure access or quota at delivery | Manifests first; document blocker | — |
| R-07 | Dual writes (DB and Kafka) | Transactional outbox | DEC-14 |
| R-08 | Duplicate or out-of-order Kafka messages | Sequence and eventId dedupe | DEC-03 |
| R-09 | Duplicate patients on client retry (no key in brief) | Optional Idempotency-Key with replay | DEC-15 |
| R-10 | Anti-hallucination failure in LLM output | Citation validation; no-match gate | DEC-07 |

## 18. Glossary

- **SLA:** maximum minutes from registration to first consultation for a level.
- **Escalado:** flag set once when an SLA is breached while the patient is still `REGISTRADO`.
- **qSOFA:** quick Sepsis-related Organ Failure Assessment score.
