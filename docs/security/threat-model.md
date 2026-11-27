# VidaCruz — Threat Model and Clinical Data Privacy

Status: **draft for review.** Method: STRIDE per trust boundary. Scope: `triage-service`, `clinical-assistant-service`, Kafka topic `triaje.eventos`, PostgreSQL, Redis, pgvector, the LLM provider and the medical alert channel. Requirement references follow `docs/requirements.md`.

## 1. Assets

| Asset | Why it matters | Where it lives |
|---|---|---|
| Patient identity and vital signs | Clinical personal data | Triage PostgreSQL (`Paciente`, `SignoVital`) |
| Triage level and escalation state | Drives medical response times | `Paciente`, `AuditoriaClinica` |
| Audit trail | Evidence for clinical traceability (RN-04) | `AuditoriaClinica`, `EventoHistorico` |
| Event stream | Source for the urgency projection | Kafka `triaje.eventos` |
| Protocol corpus | Source of every clinical citation (RN-05) | Assistant DB, pgvector |
| API keys and LLM key | Access control and cost | Environment variables, Kubernetes Secret (NFR-09) |

## 2. Trust boundaries

1. Client → Triage API and Assistant API (HTTP, authenticated by API key or JWT, NFR-10).
2. Triage service → PostgreSQL (triage DB only, ADR-14).
3. Triage service → Kafka (outbox relayer, ADR-03).
4. Kafka → Assistant consumer and alert consumer.
5. Assistant service → Redis, pgvector and the LLM provider (external boundary).
6. Alert consumer → medical team channel (external boundary, ADR-06).

## 3. STRIDE analysis

| # | Boundary | Threat | Category | Control | Requirement | Residual risk |
|---|---|---|---|---|---|---|
| T-01 | 1 | Unauthenticated caller reads or changes patients | Spoofing, Information disclosure | Authentication on every `/triage/**` and `/asistente/**` endpoint; health probes public only | NFR-10, DEC-05 | Medium until the auth decision (AMB-09 / ADR-07) is accepted |
| T-02 | 1 | Caller changes a patient's state without authority | Tampering, Elevation | Authorization by role is **not specified** in the brief; record as a gap | AMB (open) | High until defined |
| T-03 | 1 | Input injection through `nombreCompleto` or `quejaPrincipal` | Tampering | Bean validation on all fields (VAL-01…VAL-11); parameterized R2DBC queries only | NFR-11, VAL-10, VAL-11 | Low |
| T-04 | 1 | Flooding of registrations or RAG queries | Denial of service | Rate limiting **not specified**; rely on gateway. Record as gap | — | Medium |
| T-05 | 2 | Triage DB credentials reused by another service | Elevation | One database per service and separate credentials | ADR-14, NFR-09 | Low |
| T-06 | 3 | Event published without matching DB state, or state without event | Tampering, Repudiation | Transactional outbox in the same transaction as the state change | ADR-03, DER-02 | Low if ADR-03 gates pass |
| T-07 | 4 | Duplicate or replayed event alters projection | Tampering | `eventId` dedupe set with TTL and `pacienteSequence` ordering | DER-03, TST-PRJ | Low |
| T-08 | 4 | Event payload exposes more clinical data than needed | Information disclosure | Envelope carries only fields listed in §10 of the requirements; the Assistant reads counts and levels, not names | §10 | Medium: needs a review of the payload fields |
| T-09 | 5 | Assistant answers with content not in the corpus | Tampering (integrity of clinical content) | Citation validation; reject invalid citations; fixed notice when no evidence exists | RN-05 | Medium until the LLM provider decision (ADR-05) |
| T-10 | 5 | Prompt injection through the question text | Tampering, Elevation | Proposed control: question is passed as data, the answer is validated against corpus citations (RN-05), and the LLM is given no tools or database access. Not yet specified in ADR-05 | RN-05, ADR-05 (Proposed) | Medium |
| T-11 | 5 | Patient data sent to an external LLM provider | Information disclosure | Assistant questions must not contain patient identifiers; the Assistant receives no patient names (only levels and counts) | §10, NFR-19 | Medium: depends on the question content; needs a rule |
| T-12 | 5 | LLM key leaked in logs or image | Information disclosure | Key from environment or Secret, never in source or logs (NFR-09) | NFR-09 | Low |
| T-13 | 6 | Alert sent to wrong recipient or lost | Repudiation, Denial | At-least-once delivery, retries, at most one alert per escalation (DER-03) | RF-06 | Medium: channel is open (AMB-10) |
| T-14 | All | Audit rows altered or deleted | Tampering, Repudiation | No UPDATE or DELETE grants on `AuditoriaClinica` and `EventoHistorico`; no API endpoint for changes | RN-04, DER-04 | Low if grants are verified in a test |
| T-15 | All | Sensitive data in metrics or traces | Information disclosure | Metric labels avoid patient ID and free text; log correlation ID only | NFR-06, NFR-19 | Low |

## 4. Clinical data handling

- **Minimum necessary:** the Assistant does not store patient names. It stores level counts and sequence numbers (§11, `EstadoUrgenciaProyeccion`).
- **Retention:** `EventoHistorico` retention is open (OD-5). It must be set before any production use. Kafka retention is 7 days for the outbox cleanup and 30 days for the topic (OPEN-09/OPEN-10 Option A, OD-3).
- **Logs:** structured logs with correlation ID; no request bodies with vital signs or names in logs by default.
- **Test data:** all fixtures use synthetic patients (E1…E6 vitals are not real people).
- **Out of scope:** real clinical use, hospital integration and identity verification (brief §2). This document does not claim HIPAA or local health-law compliance.

## 5. Gaps to decide

| ID | Gap | Impact | Recommendation |
|---|---|---|---|
| GAP-01 | Authorization model (who may change state or read a patient) is not defined | T-02 | Decide roles before Day 2 endpoints; minimum is one role per service |
| GAP-02 | Rate limiting is not defined | T-04 | Assign to the gateway in Azure, or add a per-key limit in Day 3 security work |
| GAP-03 | Payload minimisation for events and LLM questions is not reviewed | T-08, T-11 | Add a review item to the security checklist (see the pipeline proposal) |
| GAP-04 | Retention period for `EventoHistorico` is open | §4 | Set value before production; OD-5 |

## 6. Verification

| Control | How it is verified |
|---|---|
| T-01 | Integration test: every protected endpoint returns 401 without a valid key |
| T-03 | Unit tests for VAL-01…VAL-11, including boundaries |
| T-06 | Integration test with Testcontainers: kill the relayer after commit, confirm event is published once after restart |
| T-07 | Consumer test: duplicate `eventId` and out-of-order `pacienteSequence` are ignored |
| T-09 | RAG test: invalid citation is rejected, fixed notice returned (TST-RAG) |
| T-14 | Database test: `UPDATE` and `DELETE` on audit tables fail for the application role |
| T-15 | Metrics test: label values contain no patient identifier |
