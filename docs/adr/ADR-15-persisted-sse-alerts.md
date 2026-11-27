# ADR-15: RF-06 escalation alerts via persisted rows and SSE

- **Status:** Accepted
- **Date:** 2026-11-26
- **Deciders:** developer (chose persisted SSE over the proposed webhook)
- **Scope:** Notification channel for RF-06/DER-03 escalation alerts in `triage-service`. Supersedes both Proposed ADR-06 files (`ADR-06-escalation-notification-channel-webhook.md` and `ADR-06-notification-channel.md`). It does not change escalation detection (ADR-13), the outbox (ADR-03), or the event contract.

## Context

RF-06 requires the medical team to be alerted when a patient is escalated; AMB-10 leaves the channel open and DER-03 requires at least one alert and at most one alert per escalation. The earlier ADR-06 proposal selected an outbox-driven webhook with a mock receiver. The developer chose the persisted-alert design instead: alerts are persisted in `medical_alert` inside the escalation transaction and streamed to the UI over Server-Sent Events. No external receiver, SMTP, pager, or URL is required, and no patient data outside the regular protected API is exposed.

## Decision

1. **Persist first.** `SlaEvaluator` writes the escalation flag, audit row, outbox event, and one `medical_alert` row in the same Reactor transaction. `ON CONFLICT (patient_id) DO NOTHING` enforces one alert per patient. A rollback leaves no alert.
2. **Resolve with the patient flow.** `PatientLifecycleService` resolves the active alert when the patient moves to `EN_CONSULTA` or `ALTA`, in the same transaction. States are `PENDIENTE → RECONOCIDA → RESUELTA`.
3. **Notify from PostgreSQL.** A trigger sends `pg_notify('medical_alert', id)` after each committed insert or update. `AlertChangeListener` consumes notifications on a dedicated LISTEN connection outside the R2DBC pool, reloads the row by id, reconnects with 1–30 s backoff, and replays changes missed while disconnected using `actualizada_en` with a 5 s margin.
4. **Stream with SSE.** `GET /triage/alertas/stream` emits `event: alerta` messages with the Spanish JSON contract, an initial active snapshot, `:latido` heartbeat comments every `alerts.stream.heartbeat` (default 15 s; `ALERTS_HEARTBEAT_INTERVAL`), and closes streams whose 256-event buffer overflows. Clients reconnect and deduplicate by `actualizadaEn`.
5. **Query and acknowledge.** `GET /triage/alertas` lists active alerts; `POST /triage/alertas/{id}/reconocimiento` with `{"responsable":"..."}` takes a pending alert. Errors: 400 `TRG-004` (bad id or responsible), 404 `TRG-008` (unknown alert), 409 `TRG-009` (already acknowledged or resolved).
6. **Privacy.** Alert payloads carry `pacienteId`, level, SLA, timestamps, status, and responsible only. They never carry patient name or chief complaint.

## Alternatives considered

| Option | Why not selected |
|---|---|
| Outbox-driven webhook (ADR-06 proposals) | Requires a receiver, retry policy, and receiver-side idempotency contract; delivers nothing to a browser without another service; ADR-06 was never accepted. |
| Email/SMS/pager | External provider credentials and delivery latency; unnecessary for the evaluation and for an in-app shared screen. |
| Kafka alerts topic | Adds a topic and consumer group; escalation already emits `paciente_escalado` in `triaje.eventos` for future extensions. |
| Polling `GET /triage/alertas` only | Simpler, but no immediate push and no lifecycle change feed. |

## Consequences

- Positive: no external dependency; the alert is durable and transactional with the escalation; browsers can consume a standard HTTP stream; reconnects replay the active snapshot.
- Positive: the same API key and HTTP surface protect alerts; no patient personal data is broadcast.
- Negative: SSE is a push channel, not a delivery guarantee to an offline receiver; if no client is connected, the persisted row still represents the alert and `GET /triage/alertas` remains authoritative.
- Negative: a long-lived LISTEN connection and heartbeat must stay below ingress read timeouts; a browser `EventSource` cannot send `X-API-Key`, so browser UIs must use streaming `fetch` or a thin backend.
- Negative: at-least-once SSE delivery means duplicate events are possible; clients deduplicate by `actualizadaEn`.

## Verification

- Unit tests cover transactional alert creation, patient-flow resolution, conditional acknowledgement, error codes, row mapping, watch ordering, stream heartbeat, and the Spanish JSON contract.
- The dedicated LISTEN connection, PostgreSQL trigger, and end-to-end escalation stream are verified by CI integration tests with Testcontainers; the local environment has no usable Docker socket.
