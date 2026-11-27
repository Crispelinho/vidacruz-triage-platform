# Clinical Assistant Service — Service Analysis

Status: **Draft for review.** Service-level analysis of the Clinical Assistant Service. It is derived from `docs/requirements.md` (D1) and `docs/analysis.md` (D0, sections 3.2, 4, 5, 6, 9 and 13), and it reviews the Gradle skeleton on branch `feature/scaffold-skeletons`. It does not change code or any other document. Requirement and decision IDs refer to D1.

Conventions used in this document:
- **Covered**: the behavior is specified, has a testable criterion, and no open decision blocks a faithful implementation.
- **Gap**: an open decision, a missing specification, or a contradiction between sources blocks a faithful implementation. Every gap has an ID (`G-xx`, section 14) that references the AMB or DEC it depends on.
- Items marked **PROPOSED** are recommendations made by this analysis. They are not yet decisions in the register.

---

## 1. Purpose, scope and sources

The Clinical Assistant Service answers clinical questions from the official protocol corpus with citations (RF-04 Part A, RN-05), and it exposes an aggregate urgency state built only from `triaje.eventos` (RF-04 Part B).

**In scope for this service:** RF-04 (Parts A and B), RN-05, API-04, API-05, the consumption side of EVT-01, EVT-02, EVT-03 and EVT-04, the protocol corpus, the RAG pipeline, the LLM resilience policy, the Redis projection, and the non-functional requirements that apply to this service (NFR-01 to NFR-20 where relevant).

**Out of scope (owned by the Triage Service):** RF-01, RF-02, RF-03, RF-05, RF-06, RN-01 to RN-04, API-01, API-02, API-03, API-06, the outbox and the SLA evaluator. This service consumes some of their events, and those events are described here only as contracts.

**Sources read for this analysis:**
- D1 `docs/requirements.md`: §4 (RF-04), §5 (RN-05), §7 (NFR), §8 (TC), §9 (API-04, API-05), §10 (EVT), §11 (persistence), §12 (corpus and acceptance questions), §13 (TST-RAG, TST-RES, TST-PRJ, TST-RXN), §14 (AMB), §14 decision register (DEC), §16 (DER), §17 (R).
- D0 `docs/analysis.md`: §3.2, §4, §5, §6, §9 (RAG pipeline), §13 (observability).
- Skeleton: `clinical-assistant-service/clinical-assistant-service` on `feature/scaffold-skeletons` (read with `git show`).

---

## 2. Bounded context and responsibilities

**Bounded context:** *Clinical knowledge and urgency visibility.* The service answers questions against approved protocols and shows how many active patients are waiting per triage level. It never decides a triage level, never changes a patient's state, and never writes audit records.

| Responsibility | Owned here | Source |
|---|---|---|
| Ingest and index the protocol corpus | Yes | D0 §9 step 1–3; D1 §12 |
| Retrieve relevant protocol chunks and gate on relevance | Yes | RN-05.4, DEC-07 |
| Call the LLM behind a circuit breaker with timeout | Yes | NFR-03, DEC-07 |
| Validate citations against the corpus; reject invalid answers | Yes | RN-05.1–2 |
| Consume `triaje.eventos` and maintain the urgency projection | Yes | RF-04 B, DER-03 |
| Classify patients, compute SLA, escalate, audit, report | **No** (Triage) | RN-01…RN-04, RF-03, RF-05 |
| Call the Triage API to rebuild state | **No** | RF-04 B, NFR-04 |
| Publish events | **No** (produces none) | D0 §3.2 |

**Boundary rules (from D0 §3.3 and NFR-04):**
- The Assistant reads events only. It has no synchronous dependency on Triage, and Triage has no synchronous dependency on the Assistant.
- The LLM and citation logic live only here. Triage never calls the LLM.
- Protocol retrieval is a read-only concern. Its only write path is corpus ingestion, which runs at startup or on demand, never on a request path.

**Dependencies:** PostgreSQL (DocumentoClinico via R2DBC; FragmentoDocumental via pgvector), Redis (projection and dedupe), Kafka (consumer of `triaje.eventos`), and one LLM provider plus one embedding model behind Spring AI (DEC-06, AMB-15). The LLM API key comes from an environment variable (NFR-09).

---

## 3. Use cases

| ID | Use case | Actor | Trigger | Main outcome | Requirements |
|---|---|---|---|---|---|
| UC-A1 | Answer a clinical question with citations | Authenticated API client | `POST /asistente/consulta` | 200 with `respuesta` and validated `fuentes`, or the fixed notice | RF-04 A, RN-05 |
| UC-A2 | Report the current urgency state | Authenticated API client | `GET /asistente/estado-urgencias` | 200 with counts per level, or 503 | RF-04 B, API-05 |
| UC-A3 | Maintain the urgency projection | Kafka consumer | Message on `triaje.eventos` (EVT-01…EVT-04; EVT-02 advances sequence only) | Projection updated once per distinct event | RF-04 B, DER-03, NFR-02 |
| UC-A4 | Ingest the protocol corpus | Startup routine or operator action | Application start, or on demand | Chunks and embeddings stored; metadata stored | RF-04 A, D0 §9 step 1–3 |
| UC-A5 | Expose probes, metrics and correlated logs | Platform | Probe call, scrape, log | Liveness, readiness, Micrometer metrics, structured logs | NFR-05, NFR-06, NFR-07 |

Main flow of UC-A1 is described step by step in section 6. Main flow of UC-A3 is described in section 5.

---

## 4. API endpoints: API-04 and API-05

Both endpoints require the API key header (`X-API-Key`, ADR-07, DEC-05, NFR-10). The key comes from `ASSISTANT_API_KEY` (local default `test-assistant-key`) and is compared in constant time. `/actuator/health[/**]` and `/health[/**]` stay public. Missing or wrong key → 401 with `{"code":"UNAUTHORIZED","message":"Invalid or missing X-API-Key header","correlationId":"..."}`; no basic-auth fallback. Spring Security's generated chain is disabled. Timestamps are ISO 8601 with offset. Field names are Spanish camelCase (DEC-08).

### 4.1 API-04 `POST /asistente/consulta`

**Request:**
```json
{ "pregunta": "¿Cuáles son las medidas iniciales ante una crisis hipertensiva?" }
```

**Responses:**

| Status | Condition | Body | Source |
|---|---|---|---|
| 200 | Answer with at least one valid citation | `{ "respuesta": "...", "fuentes": [{ "protocolo": "PROTO-001", "seccion": "Medidas iniciales" }] }` | D1 §9 |
| 200 | No relevant chunk passes the gate (LLM not called) | `{ "respuesta": "no existe protocolo disponible", "fuentes": [] }` | RN-05.3, AMB-08, DEC-07 |
| 200 | LLM answer rejected by citation validation (unknown citation, or no citation) | Same fixed notice and `fuentes: []` | RN-05.2, edge table |
| 400 | Empty or whitespace-only `pregunta` | Error body (see below) | RN-05 edge table, NFR-11 |
| 401 | Missing or wrong `X-API-Key` | `{ "code": "UNAUTHORIZED", "message": "Invalid or missing X-API-Key header", "correlationId": "..." }` with `X-Correlation-Id` | DEC-05, NFR-10, NFR-20, D-API-401-NFR20 |
| 503 | LLM circuit open, call timed out, or provider error | `{ "code": "LLM_UNAVAILABLE", "message": "El asistente no está disponible temporalmente", "correlationId": "..." }` | NFR-03, RN-05 edge table |
| 500 | Unexpected error | Generic body, no stack trace or SQL (DER-08) | DER-08 |

**Error body:** NFR-20 requires `{ code, message, field?, correlationId }`. D1 §9 gives the 503 example without `correlationId`; this analysis follows NFR-20 (see G-06).

**Error codes for this endpoint:**

| Code | HTTP | Status in D1 | Notes |
|---|---|---|---|
| `LLM_UNAVAILABLE` | 503 | Defined | Only code the RAG path returns |
| `VALIDATION_ERROR` | 400 | Used by API-01 example; not defined for API-04 | PROPOSED: `field: "pregunta"`, `mensaje` text (G-06) |
| `UNAUTHORIZED` | 401 | Canonical body `{code, message, correlationId}` with `X-Correlation-Id` | D-API-401-NFR20; implemented and tested |
| `INTERNAL_ERROR` | 500 | Not defined | PROPOSED, no internal details (DER-08) |

**Rules that must hold for every 200 response:**
1. `fuentes` is built from DocumentoClinico metadata of the validated citations, not from text produced by the LLM. The LLM cannot inject a section name that the service then returns.
2. If `fuentes` is empty, `respuesta` is exactly the fixed notice. An answer with clinical content and an empty `fuentes` never leaves the service.
3. Anti-hallucination rejections are not HTTP errors. They return 200 with the fixed notice, and they are recorded in logs and metrics (section 6, step 8).

### 4.2 API-05 `GET /asistente/estado-urgencias`

**Response (200):**
```json
{
  "total": 7,
  "porNivel": { "CRITICO": 1, "URGENTE": 2, "URGENTE_2": 2, "NO_URGENTE": 1, "NO_URGENTE_2": 1 },
  "actualizadoEn": "2026-11-24T16:05:00Z"
}
```

**Rules:**
- `total` counts patients whose latest applied state is `REGISTRADO` or `EN_CONSULTA` (not `ALTA`). `porNivel` must always contain all five keys, with `0` for empty levels (PROPOSED).
- The endpoint never calls the Triage API (RF-04 B).
- The response reflects the projection at the time of the read. It is eventually consistent with `triaje.eventos`.

**Responses and error codes:**

| Status | Condition | Body | Source |
|---|---|---|---|
| 200 | Projection readable | Shape above | D1 §9 |
| 401 | Missing or wrong `X-API-Key` | `{ "code": "UNAUTHORIZED", "message": "Invalid or missing X-API-Key header", "correlationId": "..." }` with `X-Correlation-Id` | DEC-05, D-API-401-NFR20 |
| 503 | Projection unavailable (Redis timeout or error) | Error body. The D1 status is defined, the code is not. PROPOSED code: `PROYECCION_NO_DISPONIBLE` | API-05, G-07 |
| 500 | Unexpected | Generic body | DER-08 |

API-05 has no 400 case, because it takes no input.

---

## 5. Event consumption: EVT-01…EVT-04

### 5.1 Envelope and contract

All events use the envelope in D1 §10 (DEC-03): `eventId`, `eventType`, `schemaVersion`, `pacienteId`, `pacienteSequence`, `occurredAt`, `correlationId`, `payload`. Kafka key: `pacienteId`. Delivery: at-least-once.

| ID | eventType | Consumed | Fields used | Effect on projection |
|---|---|---|---|---|
| EVT-01 | `paciente_registrado` | **Yes (required)** | `payload.nivel`, `payload.estado`, `payload.fechaRegistro` | Records the patient's level; state `REGISTRADO`; patient becomes counted |
| EVT-02 | `paciente_escalado` | **Yes (sequence only)** | `pacienteSequence`, `eventId` | Advances `ultimaSecuencia`; no count change. Escalation changes `escalado`, not `estado` (DEC-02) |
| EVT-03 | `paciente_en_consulta` | **Yes (required)** | `payload.estadoAnterior`, `payload.fechaEntradaConsulta` | State `EN_CONSULTA`; patient remains counted |
| EVT-04 | `paciente_alta` | **Yes (required)** | `payload.estadoAnterior`, `payload.fechaAlta` | State `ALTA` (terminal); patient leaves the counts |

**Why EVT-02 is consumed:** under DEC-02 escalation does not change `estado`, so it has no count effect, but skipping it would create a false gap in `pacienteSequence`. ADR-02/ADR-08 require contiguous sequences, so the consumer acknowledges EVT-02 and advances `ultimaSecuencia` without changing counts. This resolves the source disagreement in G-10 in favour of "all four consumed, different effects".

**Missing field resolved:** EVT-03 and EVT-04 have no `nivel`, but the fixed order protocol applies EVT-01 first and stores its `nivel` in the per-patient hash. EVT-04 therefore decrements using the stored level; no additive payload field is required. See G-08.

### 5.2 Consumer main flow (UC-A3)

1. Read the record; validate the envelope (`schemaVersion` supported, `eventId`, `pacienteId` and `pacienteSequence` positive integer).
2. If the `eventId` was already processed (section 8.4), acknowledge and stop.
3. Apply the sequence protocol of ADR-02 / Spec C-05 §5.1: `sequence = lastApplied + 1` continues; `sequence <= lastApplied` is `STALE` and is acknowledged; `sequence > lastApplied + 1` is `GAP`, is kept unacknowledged in `pendingByPatient`, and goes to the DLT with `GAP_UNRESOLVED` on timeout.
4. Run one atomic Lua update in Redis that applies section 8.3 and writes the `eventId` marker in the same step. For EVT-03/EVT-04 the consumer passes the `nivel` already stored for the patient.
5. Acknowledge the offset only after step 4 has succeeded (at-least-once). A Redis failure or a pending gap leaves the offset uncommitted and retries with backoff.

**Open contradiction for ADR-02 (not decided here):** Spec C-05 §5.1 currently says consumption of other patients and partitions continues during a gap, while this section and ADR-02's partition-recovery rule require pausing the affected partition and committing no offset past the pending record. Both cannot hold for records in the same partition. No policy is selected here; ADR-02 remains Proposed.

### 5.3 Duplicate and out-of-order handling

| Case | Expected behavior | Rule |
|---|---|---|
| Exact duplicate (same `eventId`) | `DUPLICATE`: no state change; acknowledge | DER-03, eventId dedupe |
| Same patient, same `pacienteSequence` but different `eventId` | `STALE`: no state change; acknowledge | ADR-02 / Spec C-05 §5.1 |
| State event older than the last applied sequence (`pacienteSequence` ≤ `ultimaSecuencia`) | `STALE`: ignored for state; acknowledge | ADR-08 Lua protocol |
| EVT-03 or EVT-04 arrives before EVT-01 | `GAP`: not applied and not acknowledged; apply after the missing contiguous events arrive, or dead-letter with `GAP_UNRESOLVED` on timeout | ADR-02 / ADR-08, Spec C-05 §5.1 |
| Sequence gap (for example EVT-02 missing) | `GAP`: pending, not silently accepted. EVT-02 is consumed precisely to avoid this false gap | ADR-02 / ADR-08 |
| EVT-03 arrives after EVT-04 for the same patient | `STALE`: ignored; `ALTA` is terminal and is never reverted | RF-02, sequence protocol |
| EVT-04 arrives and no EVT-01 was ever seen | `GAP` until EVT-01 arrives; on timeout it is dead-lettered. No `ALTA` record with unknown level is created | ADR-02, ADR-08 |
| Unsupported `schemaVersion` or unknown `eventType` | `INVALID`: not applied; logged and counted; routed to the DLT | DEC-03; G-09 |

**Open pending-map items (ADR-02/ADR-08, not decided here):** (a) two future records with the same `pacienteSequence` but different `eventId` inside `pendingByPatient`; (b) the criterion for the "oldest pending entry" when the map is full (`occurredAt` is not an ordering contract); (c) the behavior when an entry dead-lettered with `GAP_UNRESOLVED` later becomes contiguous. The `STALE` row above applies after an event has been applied, not to a collision between two pending records. No rule is selected in this document.

**Ordering assumption:** per-patient order is guaranteed only if the producer publishes events for one patient in sequence through one Kafka partition (key `pacienteId`). That is a Triage responsibility (DEC-14). Retries and parallel publishers can still break order; the pending map and DLT rule above handle that case without silently applying a future event.

---

## 6. RAG pipeline (RF-04 A, RN-05)

Pipeline steps follow D0 §9. Each step lists its responsibility, the owning component, and the requirement it serves.

| Step | Name | What happens | Owner | Requirement / decision |
|---|---|---|---|---|
| 1 | Ingestion | Read corpus files at startup (or on demand). Extract text. Split by section; keep `protocolo`, `seccion`, `fuente` as metadata. Upsert one `DocumentoClinico` per `(protocolo, seccion)` | Corpus ingestion adapter | D0 §9.1, D1 §11 unique key. Corpus format and location not defined (G-23) |
| 2 | Embedding | Create one embedding per chunk with the configured embedding model | Spring AI `EmbeddingModel` behind an interface | DEC-06, AMB-15. Model not chosen; vector dimension unknown (G-01) |
| 3 | Storage | Store chunk text, embedding and metadata (`protocolo`, `seccion`) in `FragmentoDocumental` (pgvector) | pgvector adapter (isolated, section 10) | TC-07, DER-06, D1 §11 |
| 4 | Retrieval | Cosine top-k similarity search (`k=4`) with metadata visible. Keep chunks whose similarity passes the threshold | Retrieval port and adapter | DEC-07, AMB-16, D-RN05-01 (cosine, `k=4`) |
| 5 | **Relevance gate** | If no chunk passes the threshold (including an empty corpus), return the fixed notice. **The LLM is not called.** | Domain (pure function, no I/O) | RN-05.3, RN-05.4, DEC-07 |
| 6 | Context build | Include only retrieved chunks in the prompt. Label each chunk with `protocolo` and `seccion`. Require the G-04 JSON answer | Prompt builder (domain) | D0 §9.6, D-RN05-03 (`respuesta` + `fuentes`) |
| 7 | Generation | Call the chat model with timeout, wrapped by the circuit breaker. Failures map to 503 | LLM adapter + Resilience4j | NFR-03, DEC-07, section 7 |
| 8 | **Citation validation** | Parse the G-04 JSON. Every cited `(protocolo, seccion)` must exist in the in-memory corpus index **and** in the retrieved set. Any invalid citation, or no citation, rejects the whole answer. A JSON parse error is rejected directly, without regeneration. Log the rejection; do not return the text | Domain (citation validator) | RN-05.1, RN-05.2, D-RN05-02, D-RN05-03 |
| 9 | Response | Return `respuesta` and `fuentes` built from the validated citations' corpus metadata | Response mapper | D1 §9 API-04 |

### 6.1 Relevance gate (step 5) in detail

- The gate is a pure decision on the retrieval result: "does at least one chunk have similarity ≥ threshold?"
- If the gate fails, the service returns the fixed notice with HTTP 200 and `fuentes: []`. The LLM port must not be invoked. Tests verify this with a mock that records zero calls (TST-RAG).
- The gate is not a circuit-breaker event. A gate rejection does not count as an LLM failure.
- The similarity metric is cosine and `top-k=4` is definitive (D-RN05-01 / G-02). The base threshold target is **0.65**, pending TST-RAG calibration with a real embedding model. The operational value stays at 0.20 because that is the only calibrated value (lexical adapter); applying 0.65 to it would reject every clinical query. This decision does not make ADR-05 `Accepted`.

### 6.2 Citation validation (step 8) in detail

- The validator receives the LLM's structured answer, the set of retrieved chunks, and the corpus index (set of `(protocolo, seccion)` pairs).
- A citation is valid only when `(protocolo, seccion)` is in the in-memory corpus index **and** in the retrieved set above the threshold (D-RN05-02 / G-03). The index is refreshed on ingestion; `DocumentoClinico` is not read per request.
- The LLM must return `{"respuesta":"...","fuentes":[{"protocolo":"...","seccion":"..."}]}` (D-RN05-03 / G-04). A parse error rejects the answer directly, with no regeneration.
- Any invalid citation rejects the entire response (RN-05.2: "the response is invalid. The service must not return it"). The service does not filter the invalid citations and return the rest.
- Metric and log: a rejection increments an anti-hallucination failure counter (PROPOSED metric name: `vidacruz.assistant.citation.rejected`). Do not put the LLM's cited protocol ID as a metric tag unless it is first checked against the corpus whitelist, because LLM output is free text and would create unbounded cardinality (NFR-19).
- Logs must not contain the question text or the LLM answer text, because both can contain clinical data (G-16).

### 6.3 Acceptance questions and expected outcomes

| # | Question (D1 §12) | Step path | Expected | Test |
|---|---|---|---|---|
| Q1 | ¿Cuáles son las medidas iniciales ante una crisis hipertensiva? | 1→9 | Answer from PROTO-001; `fuentes` contains PROTO-001 | TST-RAG |
| Q2 | ¿Cuándo se administra el primer antibiótico en sepsis? | 1→9 | Answer from PROTO-002; `fuentes` contains PROTO-002 | TST-RAG |
| Q3 | ¿Cuál es el tratamiento de primera línea para la diabetes tipo 2? | 1→5 | Fixed notice; `fuentes: []`; LLM not called | TST-RAG |

Adding protocols to the corpus must not change these three outcomes (D1 §12). That is a regression test, not a one-time check.

### 6.4 Empty-protocol case

Two cases are both "empty protocol":
- **No relevant protocol for the question (Q3).** Retrieval runs, nothing passes the threshold, the fixed notice is returned, and the LLM is not called.
- **Empty corpus (no `DocumentoClinico` or `FragmentoDocumental` rows).** Retrieval returns nothing, so the gate fails and the fixed notice is returned for every question. The service must still start, because nothing in the requirements makes an empty corpus a startup failure. Readiness behavior for this state is not defined (G-22).

---

## 7. Resilience: circuit breaker, timeout and 503 fallback

Requirements: NFR-03 (circuit breaker with explicit fallback, 503), TC-05 (Resilience4j), DEC-07 (timeout and 503 fallback), R-04 (latency and cost).

**Protected call:** only the LLM call (step 7). The relevance gate, retrieval, and citation validation are not protected by this circuit breaker.

**Policy:**

| Aspect | Decision in sources | Status in this analysis |
|---|---|---|
| Circuit breaker name | Not specified | PROPOSED: `llm` |
| Timeout on the LLM call | "timeout" required; value not set | Gap G-05. The timeout applies with `Mono` timeout operator, and it must be shorter than the HTTP gateway timeout |
| Failure definition | Not specified | Gap G-05. PROPOSED: timeouts, provider 5xx, connection errors, and rate limiting (429) count as failures; citation rejection and gate rejection do not |
| Open-state behavior | "circuit open → 503" | Covered: a call on an open circuit fails immediately with 503 `LLM_UNAVAILABLE` and does not call the provider |
| Retries | Not specified | PROPOSED: no automatic retries on the LLM call, to keep latency bounded. Retry belongs to the caller |
| Fallback | 503 `LLM_UNAVAILABLE` | Covered (API-04 error table) |
| Metrics | `vidacruz.llm.latency` timer, tag `outcome` ∈ {success, timeout, fallback} (D0 §13) | Covered. PROPOSED: add `citation_rejected` as an outcome of the answer path, not of the LLM timer |

**Mapping of failures to responses:**

| Condition | Response | Counts as LLM failure |
|---|---|---|
| Circuit open | 503 `LLM_UNAVAILABLE` | No call made |
| Timeout | 503 `LLM_UNAVAILABLE` | Yes |
| Provider error (5xx, connection) | 503 `LLM_UNAVAILABLE` | Yes |
| Gate fails (no chunk) | 200 fixed notice | No (LLM not called) |
| Citation invalid | 200 fixed notice | No (LLM answered; the answer was rejected) |
| Retrieval (pgvector) error | **Not defined** (G-05 / section 10) | Not an LLM failure. PROPOSED: 503 with `PROYECCION_NO_DISPONIBLE`-style code (new name needed). Gap |

**Cancellation:** when the client disconnects, the reactive chain is cancelled, but a blocking provider call on a worker thread is not interrupted by cancellation. D0 §6 requires cancellation to propagate; that requirement is only met on the reactive path (G-17).

**Readiness:** LLM status must not make the Assistant unready (D0 §13). The fallback is intentional. LLM status is reported as a metric, not as a readiness check.

---

## 8. Urgency projection and dedupe store (Redis)

### 8.1 Purpose

EstadoUrgenciaProyeccion holds, for each patient seen on `triaje.eventos`, the minimum state needed to produce API-05. It is a projection, not a source of truth (D0 §7). The source of truth for patients is Triage (DEC-10).

### 8.2 Data model (PROPOSED, to be confirmed in an ADR)

| Key | Type | Contents | Notes |
|---|---|---|---|
| `vidacruz:assistant:urgencias:conteo` | Hash | field per level (`CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2`) → integer count | D1 §11 says "Hash per level". The exact layout is ambiguous (G-11). This proposal uses one hash with five fields |
| `vidacruz:assistant:paciente:{pacienteId}` | Hash | `nivel` (set once), `estado` (last applied), `ultimaSecuencia` (last applied state sequence), `contado` (0/1) | One record per patient. Needed for the level of EVT-04 and for the sequence rule |
| `vidacruz:assistant:evento:{eventId}` | String with TTL | Marker that the event was applied | One key per event, `SET NX PX <ttl>`. A Redis set cannot give per-member TTL, so the D1 wording "set of processed eventIds with TTL" is implemented as one expiring key per event (PROPOSED) |

**Key cardinality:** one per-patient hash and one per-event marker. Per-event markers grow with traffic within the TTL window. Patient keys grow with the number of patients ever seen. Neither key contains free text, so metric labels are not affected (NFR-19).

### 8.3 Update rules

Applied inside one Redis Lua script so that the read, the rules, and the count changes are atomic (PROPOSED; MULTI/EXEC cannot express the conditional logic). The script returns `APPLIED`, `DUPLICATE`, `STALE`, `GAP` or `INVALID`:

1. **Identity**: an existing `eventId` marker returns `DUPLICATE` before any other rule.
2. **Sequence**: `pacienteSequence = ultimaSecuencia + 1` continues; `pacienteSequence <= ultimaSecuencia` returns `STALE`; `pacienteSequence > ultimaSecuencia + 1` returns `GAP` and changes nothing.
3. **Attributes (`nivel`)**: set once, from EVT-01 only. It is never overwritten. EVT-02/03/04 pass the stored `nivel` to the script; they do not redefine it.
4. **State (`estado`, `ultimaSecuencia`)**: on `APPLIED`, set `estado` from the event type (EVT-01 → `REGISTRADO`, EVT-02 → unchanged, EVT-03 → `EN_CONSULTA`, EVT-04 → `ALTA`) and advance `ultimaSecuencia`. If `estado` is `ALTA`, no later event changes it.
5. **Counting**: `contado` must equal `(nivel is known) AND (estado ≠ ALTA)`. After each applied update, if the recomputed `contado` differs from the stored one, increment or decrement `conteo[nivel]` by one and store the new `contado`.

**Why this rule is order-safe:** the count is a function of the current record, and contiguity prevents a future event from being applied before its predecessors. Every applied change is an exact delta from the stored `contado`; the DLT record is the trace for events that never became contiguous.

**Worked examples (level L):**

| Arrival order | Result |
|---|---|
| EVT-01 (seq 1), EVT-02 (seq 2), EVT-03 (seq 3), EVT-04 (seq 4) | +1 at registration; no count change for EVT-02 or EVT-03; −1 at EVT-04. Final count 0 |
| EVT-03 (seq 3) before EVT-02 (seq 2) | EVT-03 returns `GAP` and stays unacknowledged. EVT-02 applies first, then EVT-03 applies and advances to 3. Final count 1 |
| EVT-04 (seq 4) before EVT-01 (seq 1) | EVT-04 returns `GAP`. If EVT-01 and the missing sequences arrive, they are applied first; otherwise EVT-04 is dead-lettered with `GAP_UNRESOLVED`. No `ALTA` record with unknown level is created |
| EVT-01 (seq 1) twice | Second delivery returns `DUPLICATE` by marker, or `STALE` after marker expiry; no change |

**Resolved issue:** an `ALTA` patient with unknown level cannot be created under the contiguity protocol because EVT-01 must be applied first. If legacy data already has an unknown-level patient, it stays uncounted; D1 does not require a per-level discharge count.

### 8.4 Dedupe store (ProcessedEvent)

- Stored as the per-event marker key (section 8.2) with TTL.
- TTL must be at least the Kafka retention of `triaje.eventos` (DER-03). Neither value is set in the sources (G-09, G-11).
- Dedupe does not depend only on the TTL. Once the marker expires, duplicates are still ignored by the sequence rule (8.3.2) and by the level rule (8.3.1). The marker is an optimization and a guard against side-effect-free repeats, not the only protection.

### 8.5 Rebuild and durability

- Record the current counts as a comparison baseline before clearing anything. The projection is not the source of truth; the baseline is only a cutover guard.
- While the retained Kafka window covers the patients' history, the projection can be rebuilt from `triaje.eventos` alone (TST-PRJ): clear projection keys and markers, reset the consumer group to the earliest retained offset and reapply the §8.3 protocol. If the window is insufficient, use the full reconstruction from `EventoHistorico` in the next bullet (ADR-08:55).
- Cut over only when rebuilt counts equal the baseline. A mismatch blocks activation and raises an alert; the old projection stays in place until then.
- If Kafka retention is shorter than the history of open patients, request full reconstruction from Triage's durable record (`EventoHistorico`, OPEN-09/OPEN-10 closed as Option A on 2026-11-25) with original `eventId`/`pacienteSequence`. The Assistant never calls Triage from the API path (RF-04-B, NFR-04). The record is decided (`EventoHistorico`); its clinical retention is not (OD-5), and G-24 remains open.
- Redis persistence (AOF proposed, RDB rejected) is needed to avoid a full rebuild after a restart. The policy is not yet accepted (G-11).
- Multi-key scripts across Redis Cluster slots fail. The projection keys must share a hash tag or run on a single Redis node (PROPOSED: single node in the three-day scope; document it).

### 8.6 Projection unavailable

When Redis times out or errors on a read for API-05, return 503 (API-05). The code and the detection rule are not defined (G-07).

---

## 9. Persistence

| Entity | Store | Access stack | Purpose | Key constraints (D1 §11) | Notes for this analysis |
|---|---|---|---|---|---|
| DocumentoClinico | PostgreSQL | R2DBC | Protocol metadata: `protocolo`, `seccion`, `fuente` | Unique `(protocolo, seccion)` | D-RN05-02 / G-03: citation validation uses an in-memory index loaded at startup and refreshed on ingestion; the table is not read per request |
| FragmentoDocumental | PostgreSQL + pgvector | JDBC (Spring AI `PgVectorStore` or equivalent) | Chunks, embeddings and metadata | Index on embedding; metadata filter by `protocolo` | Spring AI's default table name and column layout differ from this entity; mapping is needed (Section 10). Vector dimension depends on the embedding model (G-01) |
| EstadoUrgenciaProyeccion | Redis | Reactive Redis | Counts per level; per-patient state | See 8.2 | Eventually consistent; rebuildable |
| ProcessedEvent | Redis | Reactive Redis | Consumer dedupe | TTL ≥ Kafka retention (DER-03) | See 8.4 |

**Store separation (DEC-10):** this service has its own PostgreSQL database. DocumentoClinico and FragmentoDocumental live in it. Redis holds only the projection and the dedupe markers. Vector data does not leave this service.

**Schema management:** the skeleton has no Flyway dependency. DER/TC-12 require migrations at startup, outside the request path, with a JDBC driver. Spring AI's pgvector starter can also create its table at startup. Which mechanism owns the schema is not decided (G-14).

**Two stacks on one database:** DocumentoClinico (R2DBC) and FragmentoDocumental (JDBC) use the same PostgreSQL database through two connection pools. Both pools must be sized and monitored separately. No cross-store transaction exists between the two tables, so ingestion must be idempotent (re-running it must not duplicate chunks).

---

## 10. Blocking risk: Spring AI pgvector store and its isolation

### 10.1 The risk

- Spring AI's pgvector store is JDBC-based (TC-11, DER-06). Its `VectorStore` methods (`similaritySearch`, `add`, `delete`) are synchronous; they do not return `Mono` or `Flux` (verify against the 1.1.1 API before implementing). Calling them on a Netty event-loop thread blocks the loop and violates NFR-01.
- The starter also needs a JDBC `DataSource`. The skeleton provides an R2DBC stack and a pgvector starter, but no JDBC URL or driver (section 11).
- Rated R-03 (medium probability, performance impact) in D0 §18. In this analysis it is the main blocking risk for the request path of UC-A1.

### 10.2 Isolation design (PROPOSED)

1. **Port**: a retrieval port with a reactive signature, for example `Mono<List<RetrievedChunk>> retrieve(String query, int k)`. The domain (gate, citation validator, notice) depends only on this port.
2. **Adapter**: `PgVectorRetrievalAdapter` runs the synchronous Spring AI call inside `Mono.fromCallable(...)` on a dedicated bounded scheduler, for example `Schedulers.newBoundedElastic(threads, queueSize, "pgvector-jdbc")`. The queue must be bounded so overload fails fast instead of queueing without limit.
3. **Separate JDBC pool**: sized for the scheduler's thread count (the JDBC pool must not be larger than needed, and must not share sizing with the R2DBC pool).
4. **Ingestion off the request path**: corpus ingestion runs at startup or in an operator action on its own scheduler. It never runs on a request thread.
5. **Verification**: BlockHound (or equivalent) runs in integration tests on the request path. The retrieval adapter must pass because its calls run on a non-reactor thread. A negative test calls the synchronous store directly from a reactor thread and must fail (TST-RXN).
6. **Escape hatch (ADR candidate)**: a custom R2DBC retrieval adapter that runs pgvector SQL directly (for example `embedding <=> :query`). This removes JDBC from the runtime path entirely. The cost is losing Spring AI's store abstraction and its ingestion helpers. The decision belongs in an ADR (ADR-04, TC-07).

**Residual risk:** the bounded scheduler still uses a blocking thread per in-flight retrieval. Under heavy load, the queue fills and requests fail, so the failure mode must be defined. Retrieval failure has no response code yet (G-05 / section 7).

---

## 11. Skeleton review (`feature/scaffold-skeletons`)

Path: `clinical-assistant-service/clinical-assistant-service`. Spring Boot 3.5.16, Spring Dependency Management 1.1.7, Spring AI BOM 1.1.1, Spring Cloud BOM 2025.0.3, Java toolchain 21, Gradle wrapper 8.14.8.

**Dependencies present:** actuator, `data-r2dbc`, `data-redis-reactive`, `spring-kafka` (`starter-kafka`), security, webflux, `spring-ai-starter-vector-store-pgvector`, `spring-cloud-starter-circuitbreaker-reactor-resilience4j`, `micrometer-registry-prometheus`, and test starters for actuator, R2DBC, Redis, Kafka, security and WebFlux.

**Dependencies missing for this service's scope:**

| Missing | Why it matters | Gap |
|---|---|---|
| R2DBC PostgreSQL driver | DocumentoClinico read path cannot run | G-14 |
| PostgreSQL JDBC driver and a JDBC URL | Required by the pgvector starter (DataSource) | G-14 |
| Flyway (or documented alternative) | TC-12 migrations at startup | G-14 |
| Spring AI LLM provider starter and embedding starter | No `ChatModel` or `EmbeddingModel` bean exists; the pgvector store and the LLM adapter cannot start | G-01 |
| Testcontainers (PostgreSQL pgvector image, Redis, Kafka) | NFR-17 recommended; section 13 needs them | G-19 |
| BlockHound | TST-RXN | G-19 |
| Bean validation starter | Input validation (NFR-11) for `pregunta` | G-06 |
| Reactor Kafka or `ReactiveKafkaConsumerTemplate` decision | spring-kafka's `@KafkaListener` model is not reactive (NFR-01) | G-13 |

**Application configuration:** `application.yaml` contains only `spring.application.name`. No datasource, Redis, Kafka, LLM, API key, circuit breaker, or corpus configuration exists yet. NFR-08 and NFR-09 require all of these to be configurable and externalized.

**Build risk:** the pgvector starter, once the JDBC pieces are added, must not conflict with the R2DBC starter on bean names or `DataSource` auto-configuration. This must be confirmed with a startup spike before implementation (G-14).

**Version review:** the pins are consistent with TC-01 (Boot 3.5.x) and DEC-12 (Spring AI 1.1.x GA, Spring Cloud 2025.0.x). Boot 3.5 open-source support ended (AMB-12, R-02). The versions must be verified on spring.io before the first build (DEC-12). DEC-13 (Java 21) is already applied on this branch, while D1 §14 still describes the skeleton default as 25. The D1 text should be updated (G-18).

**Existing test:** `ClinicalAssistantServiceApplicationTests.contextLoads` loads the full context. With no infrastructure, it fails. It needs Testcontainers or slicing before it is a useful test (section 13).

---

## 12. Requirements mapping

### 12.1 RF-04 Part A and Part B, RN-05

| Requirement | Part | Section(s) of this analysis | Status | Gap IDs |
|---|---|---|---|---|
| RF-04 A: retrieval over the corpus with citations | A | 6 (steps 3, 4, 9), 4.1 | **Decided, evidence pending** (cosine, `k=4`; 0.65 target under TST-RAG) | G-02 |
| RF-04 A: fixed notice when no relevant protocol exists (no clinical content generated) | A | 6 (step 5), 6.4 | **Covered** | — |
| RF-04 A: 503 when the LLM is unavailable (NFR-03) | A | 4.1, 7 | **Gap** (timeout and breaker values, failure definition) | G-05 |
| RF-04 B: count of patients not in `ALTA`, grouped by level | B | 4.2, 8 | **Covered by protocol** (level from EVT-01 applied in sequence; §8.3; G-08 resolved) | G-09, G-11 |
| RF-04 B: built only from `triaje.eventos`, no Triage API calls | B | 2, 5, 8 | **Covered** | — |
| RF-04 B: persisted as a projection (Redis or other store) | B | 8.2 | **Gap** (store source is DEC-10 / ADR-08; layout ambiguous) | G-11 |
| RF-04 B: 503 when the projection is unavailable (API-05) | B | 4.2, 8.6 | **Gap** (no error code, no detection rule) | G-07 |

### 12.2 RN-05 rules and edge cases

| Rule or case | Section(s) | Status | Gap IDs |
|---|---|---|---|
| RN-05.1: every clinical answer cites at least one corpus source | 6 (step 8), 6.2 | **Decided, evidence pending** (`(protocolo, seccion)` in corpus index and retrieved set) | G-03 |
| RN-05.2: a citation outside the corpus makes the response invalid; the service must not return it | 6 (step 8), 6.2 | **Covered** | — |
| RN-05.3: no relevant match → exact notice "no existe protocolo disponible", no clinical content | 6 (step 5), 4.1 | **Covered** (text and status fixed) | — |
| RN-05.4: relevance gate before any LLM call (DEC-07) | 6 (step 5), 6.1 | **Decided, evidence pending** (cosine, `k=4`, 0.65 target; 0.20 operational) | G-02 |
| Edge: query matches PROTO-001 → answer, `fuentes` contains PROTO-001 | 6.3 (Q1) | **Decided, evidence pending** (G-04 JSON contract; TST-RAG) | G-02, G-04 |
| Edge: query matches PROTO-002 → answer, `fuentes` contains PROTO-002 | 6.3 (Q2) | **Decided, evidence pending** (same) | G-02, G-04 |
| Edge: diabetes type 2, no protocol → notice, `fuentes: []`, LLM not called | 6.3 (Q3), 6.1 | **Covered** | — |
| Edge: LLM cites PROTO-999 → rejected, notice returned, anti-hallucination event logged | 6 (step 8), 6.2 | **Covered** (logging content policy in G-16) | G-16 |
| Edge: JSON without `fuentes`, or malformed JSON → rejected directly, notice returned | 6 (step 8) | **Decided, evidence pending** (D-RN05-03: no regeneration) | G-04 |
| Edge: LLM circuit open or timeout → 503 `LLM_UNAVAILABLE` | 4.1, 7 | **Gap** (timeout and breaker values) | G-05 |
| Edge: empty or whitespace-only question → 400 | 4.1 | **Gap** (error body and max length) | G-06 |

### 12.3 Non-functional requirements that apply to this service

| NFR | Status | Note | Gap IDs |
|---|---|---|---|
| NFR-01 non-blocking request paths | **Gap** | Blocking pgvector and LLM calls are isolated (sections 7, 10); Kafka listener model open | G-13, G-14 |
| NFR-02 Kafka documented format, idempotent consumers | **Covered** | Sections 5, 8 | — |
| NFR-03 circuit breaker with 503 fallback | **Gap** | Values unset | G-05 |
| NFR-04 Triage never depends on the Assistant | **Covered** (this side) | No Triage call; events only | — |
| NFR-05 health, readiness, liveness | **Gap** | Readiness with empty corpus and Redis down is undefined | G-22 |
| NFR-06 structured logs with correlation ID | **Covered** | Header and Kafka header propagation (DER-07) | G-16 (names and content policy) |
| NFR-07 Micrometer metrics (LLM latency minimum) | **Covered** | `vidacruz.llm.latency` | — |
| NFR-08 configurable properties | **Gap** | No property names or defaults exist | G-05, G-02 |
| NFR-09 no hardcoded credentials; LLM key from env | **Covered** (requirement); DER-01 conflict | Startup failure vs 503 open | G-12 |
| NFR-10 authentication (API key) | **Gap** | Filter design, key source, 401 body | G-21 |
| NFR-11 input validation | **Gap** | Max length of `pregunta` undefined | G-06 |
| NFR-15 tests, LLM mockable | **Gap** | Embedding mock strategy open | G-19 |
| NFR-17 Testcontainers | **Gap** | Not in skeleton | G-19 |
| NFR-19 bounded metric cardinality | **Covered** | Section 6.2 rule on cited protocol IDs | — |
| NFR-20 consistent error body | **Gap** | 503 example in D1 §9 lacks `correlationId` | G-06 |
| NFR-12, NFR-13, NFR-14 compose, Kubernetes, Azure | Out of this analysis | Deployment artifacts for this service follow the platform plan (D0 §14) | G-20 (R-06 access) |
| NFR-16 OpenTelemetry (bonus) | Not analyzed | Optional | — |
| NFR-18 progressive commits | Process | Applies to implementation, not to this document | — |

### 12.4 Requirements owned by Triage (not analyzed here)

RF-01, RF-02, RF-03, RF-05, RF-06, RN-01, RN-02, RN-03, RN-04. They are listed for traceability only. The Assistant consumes EVT-01…EVT-04, produced by RF-01, RF-02 and RF-03; EVT-02 advances sequence only. G-08 is resolved by the order protocol: EVT-01 supplies the level used by EVT-04, so no Triage payload change is required.

---

## 13. Test plan

Test IDs follow D1 §13. Layers follow D0 §15.

### 13.1 Unit tests (no Spring context)

| Test | Covers | Cases |
|---|---|---|
| Relevance gate | RN-05.4 | Empty result → notice; one chunk below threshold → notice; one chunk at threshold → passes (boundary) |
| Citation validator | RN-05.1–2 | Valid citation; PROTO-999; citation to a section not in the corpus; no `fuentes`; malformed JSON; citation in index but not in retrieved set; parse error is not retried |
| Notice and response mapper | RN-05.3, API-04 | Exact text "no existe protocolo disponible"; `fuentes: []` on notice; `fuentes` built from corpus metadata, never from LLM text |
| Projection rule function | Section 8.3 | Contiguous worked examples; `DUPLICATE`/`STALE`/`GAP`/`INVALID` returns; ALTA terminal; EVT-04 before EVT-01 dead-lettered; EVT-02 changes no count |
| Envelope validation | Section 5.2 | Unsupported `schemaVersion`; missing `eventId`; non-positive `pacienteSequence` |
| Empty question rule | API-04 400 | Empty, whitespace-only, and over-length (once bound defined) |

### 13.2 Slice and API tests (WebTestClient, ports mocked)

| Test | Covers | Expected |
|---|---|---|
| Q1, Q2 with mocked LLM and retrieval | TST-RAG | 200; `fuentes` contains PROTO-001 or PROTO-002 |
| Q3 with mocked LLM | TST-RAG | 200 notice; LLM mock invoked 0 times |
| PROTO-999 from mocked LLM | RN-05.2 | 200 notice; anti-hallucination counter incremented |
| Text without citation from mocked LLM | RN-05 edge | 200 notice |
| LLM mock throws, times out, or circuit forced open | TST-RES, NFR-03 | 503 `LLM_UNAVAILABLE`; body shape per NFR-20 |
| Missing or wrong `X-API-Key` | NFR-10 | 401 on API-04 and API-05; health public |
| Empty `pregunta` | NFR-11 | 400 |
| API-05 with projection fixtures | TST-PRJ | Counts equal the fixture expectation; `porNivel` has five keys |
| Redis read failure on API-05 | G-07 | 503 with the defined code |

### 13.3 Integration tests (Testcontainers)

| Test | Covers | Expected |
|---|---|---|
| Corpus ingestion twice | Section 6 step 1 | No duplicate `DocumentoClinico` or chunks (idempotent) |
| pgvector retrieval with the three questions | TST-RAG | Same outcomes as 13.2, against real pgvector |
| Retrieval on the scheduler, BlockHound on request path | TST-RXN, Section 10 | No blocking violation on reactor threads |
| Kafka consumer: duplicate, stale, future-event gap, EVT-04 before EVT-01 | TST-PRJ | Pending offsets are not applied; `GAP_UNRESOLVED` reaches the DLT; counts match the rule function; no negative counts |
| Consumer with Redis outage then recovery | DER-03 | Offset not committed during outage; no double counting after recovery |
| Projection rebuild from topic | TST-PRJ | Same counts as live consumption |
| Kafka messages with the same `eventId` published twice | DER-03 | Applied once |

### 13.4 Resilience and reactive tests

| Test | Covers | Expected |
|---|---|---|
| LLM fails N times → circuit opens | NFR-03, TST-RES | Circuit state OPEN; the next call is rejected without calling the LLM; 503 |
| LLM slow beyond timeout | DEC-07 | 503 `LLM_UNAVAILABLE`; no hung request |
| Client disconnect during LLM call | D0 §6 | Reactive chain cancelled; document the non-interruptible blocking call (G-17) |
| BlockHound negative | TST-RXN | Synchronous `VectorStore` call from reactor thread fails the test |

### 13.5 End-to-end (docker-compose, needs Triage)

| Test | Covers | Expected |
|---|---|---|
| Register patient in Triage → API-05 total increases | RF-04 B | Eventually (within a bounded wait) `total` and `porNivel[level]` increase |
| Change state to `EN_CONSULTA` → API-05 unchanged | RF-04 B | Count unchanged |
| Change state to `ALTA` → API-05 decreases | RF-04 B | Count decreases by one at the patient's level |
| Stop Assistant, keep Triage operating | NFR-04, TST-RES | Triage responds; Assistant catches up on restart |

**Mocks:** the LLM is always mocked in automated tests (NFR-15, DEC-06). Embeddings need a decision: a fake embedding model with hand-set vectors per question makes the gate deterministic, but does not test semantic retrieval. A local embedding model tests real retrieval, but adds size and time. This is open (G-19).

---

## 14. Gaps and open decisions

Each gap has an impact on implementation, a reference to the AMB or DEC it depends on, and the decision needed.

| ID | Gap | Refs | Impact | Decision needed (PROPOSED where noted) |
|---|---|---|---|---|
| G-01 | LLM provider and embedding model not chosen. No provider starter in the skeleton. Embedding dimension unknown, so the vector column size is unknown | AMB-15, DEC-06, TC-07 | Blocks steps 2, 3, 7 and the vector schema | Choose one chat and one embedding provider; record dimension; ADR-05 |
| G-02 | **Closed by decision D-RN05-01 (2026-11-27):** cosine metric, `top-k=4` definitive, 0.65 base threshold target. Evidence remains open: TST-RAG with a real embedding model must record the calibrated value; 0.20 stays operational for the lexical adapter | AMB-16, DEC-07, RN-05.4 | Gate determinism; Q1 and Q2 outcomes | Run TST-RAG; record measured threshold. Do not apply 0.65 to `LexicalEmbeddingAdapter` |
| G-03 | **Closed by decision D-RN05-02 (2026-11-27):** `(protocolo, seccion)` must be in the in-memory corpus index and in the retrieved set; index refreshed on ingestion; no `DocumentoClinico` read per request | DEC-07, RN-05.1–2, D0 §9.8 | Step 8 result for edge cases | Implemented; verification tracked by TST-RAG. No longer an open design question |
| G-04 | **Closed by decision D-RN05-03 (2026-11-27):** JSON `{"respuesta","fuentes":[{"protocolo","seccion"}]}`; parse error is rejected directly, without regeneration | RN-05 edge, DEC-07 | Step 6 and 8 | Implemented; verification tracked by TST-RAG. No longer an open design question |
| G-05 | Timeout values; circuit breaker parameters (window, failure rate, open wait); failure definition; retry policy; retrieval error response (no code) | DEC-07, NFR-03, NFR-08, R-04 | Section 7 mapping and tests | PROPOSED: no retries; timeout configurable; failure = timeout, 5xx, connection, 429. Define 503 code for retrieval failure |
| G-06 | 401 body is now canonical (`{code,message,correlationId}` + `X-Correlation-Id`, D-API-401-NFR20); still open: 400 body for API-04, `pregunta` max length, and the D1 503 example without `correlationId` | NFR-11, NFR-20, DER-08 | Consistent error contract | PROPOSED: `VALIDATION_ERROR` with `field: "pregunta"`; max length (for example 1000 characters); add `correlationId` to all errors |
| G-07 | API-05 503 has no error code and no detection rule. `actualizadoEn` meaning not defined | API-05, RF-04 B | Client contract | PROPOSED: code `PROYECCION_NO_DISPONIBLE`; `actualizadoEn` = server time of last applied event (UTC) |
| G-08 | EVT-04 payload has no `nivel`. Count decrement depends on the level stored from EVT-01 | DEC-03 (additive changes allowed), EVT-04 | Correct counts on discharge | **Resolved by protocol:** EVT-01 is applied first and its `nivel` is stored; no payload change required. Additive `nivel` remains optional only if the order protocol is relaxed |
| G-09 | DLT name, retry count and numeric gap timeout/map size are not set; unknown `schemaVersion`/`eventType` handling needs the `INVALID` code | AMB-14, DEC-03, DER-03, R-08 | Consumer behavior; poison messages | Adopt §5.3 and §8.3; DLT name is `triaje.eventos.dlq`; numeric limits pending under ADR-02/ADR-08 |
| G-10 | Consumer scope inconsistent: D0 §8 says "all four"; D1 §10 and D0 §3.2 say EVT-01/03/04, EVT-02 optional | DEC-02, DEC-03 | What the Assistant subscribes to | **Resolved:** consume all four; EVT-02 advances `ultimaSecuencia` with no count effect so sequences stay contiguous |
| G-11 | Redis data model ambiguous ("Hash per level"); atomic update not specified; store decision is DEC-10 / ADR-08; persistence mode not chosen; ProcessedEvent TTL value not set | D1 §11, DEC-10, DEC-03, DER-03, ADR-08 | Projection design; durability | Adopt §8.2 and the Lua protocol (§8.3); AOF proposed, TTL ≥ Kafka retention; numeric margin pending confirmation |
| G-12 | Fail-fast at startup when the LLM key is missing (DER-01) vs. service must answer 503 when the LLM is unavailable (NFR-03) | DER-01, NFR-03, NFR-09 | Startup behavior | PROPOSED: missing configuration fails startup (misconfiguration); provider outage returns 503 at runtime. Record in an ADR |
| G-13 | Kafka client model: skeleton has spring-kafka `@KafkaListener` (not reactive); analysis allows Reactor Kafka or a reactive adapter | NFR-01, ADR-02, D0 §6 | Consumer threading and backpressure | PROPOSED: `ReactiveKafkaConsumerTemplate` from spring-kafka, bounded prefetch; record in ADR-02 |
| G-14 | pgvector isolation and JDBC stack: whether to keep Spring AI's store behind a bounded scheduler or use a custom R2DBC adapter; JDBC URL and driver; schema owner (Flyway vs Spring AI init); FragmentoDocumental mapping to the store's table | TC-07, TC-11, TC-12, DER-06, R-03, ADR-04 | Blocking risk; startup; build | Startup spike to check that R2DBC and JDBC auto-configuration coexist; ADR-04 with the isolation design (section 10); Flyway for schema |
| G-15 | Vector store table layout (FragmentoDocumental) not defined in detail: column names, metadata keys, ANN index type | D1 §11 | Ingestion and retrieval filter by `protocolo` | Define table and index in the ADR and in the migration |
| G-16 | Logging and metric content policy: question and answer text are clinical data; cited protocol IDs from LLM output must not become tags; correlation header and Kafka header names not fixed | NFR-06, NFR-19, DER-07, D0 §13 | Compliance with clinical-data handling; cardinality | PROPOSED: never log question or answer text; log hashes or lengths; whitelist cited protocol IDs before tagging; header `X-Correlation-Id` in HTTP and a Kafka header of the same name |
| G-17 | Cancellation of an in-flight blocking LLM call is not interruptible; D0 §6 expects cancellation to propagate | D0 §6 | Resource use on disconnect | Accept and document; or use the provider's streaming API with a reactive client if the chosen provider supports it |
| G-18 | Version and documentation consistency: Boot 3.5 open-source support ended (AMB-12, R-02); version compatibility of Spring Cloud 2025.0.x, Spring AI 1.1.1 and Boot 3.5.16 must be verified on spring.io; D1 §14 says skeletons use Java 25 while this branch uses 21 (DEC-13) | AMB-12, AMB-13, DEC-12, DEC-13, R-02, R-05 | Build and documentation | Verify compatibility before first build; update D1 AMB-13 and DEC-13 status to match the skeleton |
| G-19 | Test infrastructure: no Testcontainers, no BlockHound, and no decision on the embedding strategy for tests | NFR-15, NFR-17, TST-RXN, TST-RAG, AMB-15 | Section 13 cannot run as written | Add test dependencies; choose fake or local embeddings (see 13.5) |
| G-20 | Deployment artifacts for this service (container image, Kubernetes manifests, Azure target) are not specified in this analysis | NFR-12, NFR-13, NFR-14, AMB-11, R-06, ADR-11 | Delivery | Follow the platform plan; no decision here |
| G-21 | API key mechanism and 401 body are decided (`X-API-Key`, D-API-401-NFR20); still open: key rotation and per-service provisioning | DEC-05, AMB-09, NFR-10, NFR-09 | Security tests; shared key handling | Implemented: API key from environment variable, one key per service, constant-time comparison in a WebFlux filter |
| G-22 | Readiness with empty corpus and with Redis or pgvector down is undefined | NFR-05, D0 §13 | Probes and rollout | PROPOSED: readiness requires PostgreSQL and Redis; empty corpus is ready but logs a warning; LLM not in readiness |
| G-23 | Protocol corpus format, location, versioning and re-ingestion rule not defined. D1 says the corpus "may be extended" | D1 §12, D0 §9 step 1 | Ingestion design; acceptance regression | Define a corpus file format (for example Markdown with front matter) and a re-ingestion rule (replace by `protocolo`) |
| G-24 | Projection rebuild completeness depends on Kafka retention; no rule for patients older than retention | DER-03, TST-PRJ, D0 §7 | Correctness after Redis loss | Define retention ≥ longest open patient episode, or accept and document incomplete rebuild |

**Clinical observation carried from D0 (not an Assistant gap):** AMB-01 (heart rate 40–89 with no other criteria → NO_URGENTE) affects the counts shown by API-05 because it determines the level the Assistant reports. The rule is kept as written and recorded as R-01.

---

## 15. Candidate OpenSpec capability names (this service only)

These are candidate names for OpenSpec capabilities. They have not been created. Each capability is limited to this service.

| Capability | Scope | Main requirements |
|---|---|---|
| `assistant-clinical-query` | API-04 flow: validation, gate, LLM call, notice, response mapping | RF-04 A, RN-05.3–4, API-04 |
| `assistant-protocol-corpus-ingestion` | Corpus parsing, chunking, embedding, storage, idempotent re-ingestion | RF-04 A, D0 §9 steps 1–3, G-23 |
| `assistant-citation-validation` | Structured answer parsing, citation checks, rejection, fuentes construction | RN-05.1–2, D0 §9 steps 8–9 |
| `assistant-llm-resilience` | Circuit breaker, timeout, retry policy, 503 fallback, LLM metrics | NFR-03, DEC-07, TST-RES |
| `assistant-urgency-projection` | API-05, Redis projection, per-patient state, counts, rebuild | RF-04 B, API-05, TST-PRJ |
| `assistant-event-consumption` | Envelope validation, dedupe markers, sequence/gap rules, DLT | DEC-03, DER-03, NFR-02, EVT-01…EVT-04 |
| `assistant-vector-store-isolation` | pgvector adapter, bounded scheduler, JDBC pool, BlockHound checks | TC-11, DER-06, NFR-01, R-03, TST-RXN |
| `assistant-api-key-auth` | `X-API-Key` WebFlux filter, public endpoints, 401 body | DEC-05, NFR-10 |
| `assistant-observability` | Probes, correlated logs, metric names and tag rules | NFR-05, NFR-06, NFR-07, NFR-19 |
| `assistant-runtime-configuration` | Externalized properties, startup validation of LLM key and config | NFR-08, NFR-09, DER-01 |

---

## 16. Summary of coverage for this service

| Requirement | Status |
|---|---|
| RF-04 Part A (RAG query with citations) | Decided, evidence pending (G-02 calibration, G-05) |
| RF-04 Part A, fixed notice when no protocol | Covered |
| RF-04 Part A, 503 when LLM unavailable | Gap (G-05) |
| RF-04 Part B (count by level from events) | Gap (G-08, G-09, G-11) |
| RF-04 Part B, no Triage API calls | Covered |
| RF-04 Part B, projection persistence | Gap (G-11) |
| RF-04 Part B, 503 projection unavailable | Gap (G-07) |
| RN-05.1 citation required | Decided, evidence pending (G-03) |
| RN-05.2 invalid citation rejected | Covered |
| RN-05.3 exact notice, no clinical content | Covered |
| RN-05.4 relevance gate before LLM | Decided, evidence pending (G-02) |
| Acceptance questions Q1, Q2 | Decided, evidence pending (G-02, G-04) |
| Acceptance question Q3 and empty-protocol case | Covered (empty corpus readiness: G-22) |
| Edge: 400 for empty question | Gap (G-06) |
| Edge: 503 for LLM circuit or timeout | Gap (G-05) |

Requirements owned by Triage (RF-01, RF-02, RF-03, RF-05, RF-06, RN-01 to RN-04) are not analyzed here (section 12.4).

---

## 17. Next steps

1. G-02, G-03 and G-04 are closed by decisions D-RN05-01/02/03; their verification remains in TST-RAG. Still open for the first implementation slice: G-01, G-11, G-12 and G-14. G-08 and G-10 are resolved by the fixed order protocol; G-09 needs only numeric limits.
2. Run the startup spike for R2DBC and pgvector JDBC coexistence (G-14) before any feature work.
3. Verify version compatibility (G-18) before the first build.
4. Confirm the numeric gap timeout, pending-map size, DLT retry count and Redis TTL margin under ADR-02/ADR-08. No EVT-04 payload change is required.
