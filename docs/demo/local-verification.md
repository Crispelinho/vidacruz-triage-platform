# Local verification: E1–E6 → SLA → alerts → report → assistant

This is the executable companion to the root `README.md`. Everything runs on `localhost` against `docker compose up -d --build`. No cloud credentials, public URL, k8s manifests or provider API keys are required except where noted.

## Before you start

```bash
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
curl -fsS -o /dev/null http://localhost:16686/
```

Both health responses must be `{"status":"UP"}` and the Jaeger UI must return `200`. All business calls need `X-API-Key`: `test-triage-key` on port 8081, `test-assistant-key` on port 8082 (`docs/adr/ADR-07-authentication-api-key.md`).

Postman: import `postman/vidacruz-local.postman_environment.json` first, then `postman/vidacruz-local-demo.postman_collection.json`. The E1 request stores `patientId` and the alert list stores `alertId` as collection variables.

## 1. RF-01 — six classification cases

Send each body to `POST http://localhost:8081/triage/pacientes` with `X-API-Key` and `Content-Type: application/json`. Expected status is `201` and the `nivel` column below.

| Case | `frecuencia_cardiaca` | `presion_sistolica` | `presion_diastolica` | `spo2` | `temperatura` | `dolor` | `gcs` | `queja_principal` | Expected `nivel` |
|---|---:|---:|---:|---:|---:|---:|---:|---|---|
| E1 | 130 | 180 | 110 | 97 | 36.8 | 8 | 15 | Dolor torácico | `CRITICO` |
| E2 | 95 | 150 | 95 | 91 | 37.0 | 4 | 15 | Dificultad para respirar | `URGENTE` |
| E3 | 88 | 130 | 85 | 98 | 38.2 | 6 | 15 | Fiebre | `URGENTE_2` |
| E4 | 72 | 120 | 80 | 99 | 36.5 | 2 | 15 | Herida por cortar | `NO_URGENTE` |
| E5 | 70 | 115 | 75 | 99 | 36.4 | 0 | 15 | Renovación de recetario | `NO_URGENTE_2` |
| E6 | 35 | 80 | 50 | 85 | 35.9 | 0 | 6 | Inconsciente | `CRITICO` |

Use `edad` between 30 and 70 and any `nombre_completo`. Example curl for E1:

```bash
curl -sS -X POST http://localhost:8081/triage/pacientes \
  -H 'X-API-Key: test-triage-key' -H 'Content-Type: application/json' \
  -d '{"nombre_completo":"Paciente E1","edad":65,"frecuencia_cardiaca":130,"presion_sistolica":180,"presion_diastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"queja_principal":"Dolor torácico"}'
```

There is **no** `Idempotency-Key` handler in the current code; replaying this request creates another patient.

## 2. RF-02 — lifecycle

```bash
curl -sS http://localhost:8081/triage/pacientes/$PATIENT_ID -H 'X-API-Key: test-triage-key'
curl -sS -X PATCH http://localhost:8081/triage/pacientes/$PATIENT_ID/estado \
  -H 'X-API-Key: test-triage-key' -H 'Content-Type: application/json' \
  -d '{"estado":"EN_CONSULTA"}'
curl -sS -X PATCH http://localhost:8081/triage/pacientes/$PATIENT_ID/estado \
  -H 'X-API-Key: test-triage-key' -H 'Content-Type: application/json' \
  -d '{"estado":"ALTA"}'
```

Expected: `estado` changes to `EN_CONSULTA` then `ALTA`. An invalid transition returns `409 INVALID_TRANSITION`; an unknown id returns `404 PATIENT_NOT_FOUND`.

## 3. RF-03/RF-06 — escalation and alerts

Compose shortens the demo SLAs to 1–5 minutes via `SLA_*_MINUTES` (spec defaults remain 5/10/30/60/120 in a plain JVM run). Register a `CRITICO` patient, leave it in `REGISTRADO`, and wait for the configured `slaMinutos`; the scheduler escalates it and creates one `medical_alert`.

```bash
curl -sS http://localhost:8081/triage/alertas -H 'X-API-Key: test-triage-key'
curl -sS -X POST http://localhost:8081/triage/alertas/$ALERT_ID/reconocimiento \
  -H 'X-API-Key: test-triage-key' -H 'Content-Type: application/json' \
  -d '{"responsable":"dra-garcia"}'
```

Expected: `estado` goes `PENDIENTE` → `RECONOCIDA`; a second acknowledgement returns `409 TRG-009`. Changing the patient to `EN_CONSULTA`/`ALTA` resolves the alert (`RESUELTA`). Alert bodies contain `pacienteId` but never patient name or complaint.

Live feed:

```bash
curl -N http://localhost:8081/triage/alertas/stream -H 'X-API-Key: test-triage-key'
```

Expected: initial snapshot events named `alerta`, then `latido` heartbeats every 15s, then live changes after retry/catch-up. Browsers cannot set the header with `EventSource`; use streaming `fetch` or a thin backend (ADR-15).

## 4. RF-05 — operation report

```bash
curl -sS -G http://localhost:8081/triage/reportes/operacion \
  -H 'X-API-Key: test-triage-key' \
  --data-urlencode 'desde=2026-01-01T00:00:00Z' \
  --data-urlencode 'hasta=2027-01-01T00:00:00Z'
```

An offset or `Z` is mandatory. Missing or malformed parameters return `400 VALIDATION_ERROR` with a `field` hint. `TRG-004` is reserved for a repeated alert acknowledgement.

## 5. RF-04 — assistant (provider-dependent)

The canonical path is `POST /asistente/consulta`; `POST /rag` is kept as a compatibility alias.

```bash
curl -sS -X POST http://localhost:8082/asistente/consulta \
  -H 'X-API-Key: test-assistant-key' -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-001' \
  -d '{"pregunta":"¿Cuáles son las medidas iniciales ante una crisis hipertensiva?"}'
curl -sS http://localhost:8082/asistente/estado-urgencias -H 'X-API-Key: test-assistant-key'
```

With no reachable provider, `/asistente/consulta` returns `503 LLM_UNAVAILABLE` and the fixed notice — that is the documented degraded mode, not a failure of the stack. A real answer was verified with `RAG_LLM_CHAIN=gemini` and a valid key supplied only through `GEMINI_API_KEY`. Alternatively, start `docker compose --profile ollama up -d`, pull a model (`docker compose --profile ollama run --rm ollama ollama pull llama3.1:8b`) and point `OLLAMA_BASE_URL` at it. Provider keys are read from `GEMINI_API_KEY` / `OPENCODE_API_KEY`; never commit them.

## 6. Auth negatives

```bash
curl -sS -i http://localhost:8081/triage/alertas
curl -sS -i http://localhost:8081/triage/alertas -H 'X-API-Key: wrong'
```

Both return `401` with `{"code":"UNAUTHORIZED","message":"Invalid or missing X-API-Key header","correlationId":"..."}` and an `X-Correlation-Id` header. Health probes stay public.

## 7. NFR-07 — metrics

The actuators expose the metrics registry at `/actuator/prometheus`. Unlike health probes, this endpoint requires the service API key.

```bash
curl -sS http://localhost:8081/actuator/prometheus -H 'X-API-Key: test-triage-key' \
  | grep -E '^triage_(patients|alerts)_'
curl -sS http://localhost:8082/actuator/prometheus -H 'X-API-Key: test-assistant-key' \
  | grep -E '^assistant_(answers|llm_latency|urgency_events)'
```

Expected series after the flows above: `triage_patients_classified_total{level=...}`, `triage_patients_escalated_total{level=...}`, `triage_alerts_acknowledged_total{level=...}`, `assistant_answers_total{outcome=...}`, `assistant_llm_latency_seconds_bucket{outcome=...}` and `assistant_urgency_events_total{result=...}`. Counters are pre-registered at zero, so a level or outcome with no events reads `0` instead of disappearing.

Open `http://localhost:9090` to query Prometheus and `http://localhost:3000` for the provisioned **VidaCruz — NFR-07 flow and latency** dashboard (anonymous viewer). If `TRIAGE_API_KEY` or `ASSISTANT_API_KEY` was overridden in `.env`, update the matching `secrets` in `observability/prometheus/prometheus.yml` so the scrapes keep returning `200` instead of `401`.

## 8. NFR-16 — one trace across both services

Compose starts Jaeger 2.x and points both services at its OTLP/HTTP receiver (`http://jaeger:4318/v1/traces`). Sampling is `1` in Compose so the demo trace is never dropped; a plain JVM run defaults to `0.1`. Send a registration with a W3C `traceparent` and the outbox carries that context into the Kafka header for the assistant.

```bash
TRACE_ID=4bf92f3577b34da6a3ce929d0e0e4736
SPAN_ID=00f067aa0ba902b7
curl -sS -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8081/triage/pacientes \
  -H 'X-API-Key: test-triage-key' -H 'Content-Type: application/json' \
  -H "traceparent: 00-$TRACE_ID-$SPAN_ID-01" \
  -d '{"nombre_completo":"Paciente E1","edad":65,"frecuencia_cardiaca":130,"presion_sistolica":180,"presion_diastolica":110,"spo2":97,"temperatura":36.8,"dolor":8,"gcs":15,"queja_principal":"Dolor torácico"}'
curl -sS http://localhost:8082/asistente/estado-urgencias -H 'X-API-Key: test-assistant-key'
```

Open `http://localhost:16686/trace/$TRACE_ID`. The trace must contain spans from `triage-service` and `clinical-assistant-service`; the Grafana **Jaeger** datasource (uid `jaeger`) can query the same id from Explore. Trace context is carried only in the `traceparent` Kafka header, never in the event payload. No patient name, complaint, prompt or answer text is attached to spans. Log lines carry `traceId`/`spanId` through Spring Boot's default correlation pattern.

## 9. Verified local E2E run (2026-11-27)

The full flow above was executed against the Compose stack with alternate host ports to avoid collisions with the local Kubernetes forwarding:

```bash
TRIAGE_PORT=18081 ASSISTANT_PORT=18082 TRIAGE_DB_PORT=15432 ASSISTANT_DB_PORT=15433 \
KAFKA_PORT=19092 REDIS_PORT=16379 JAEGER_PORT=16687 PROMETHEUS_PORT=19090 GRAFANA_PORT=13000 \
docker compose -f docker-compose.yml -f docker-compose.demo.yml -p vidacruz-e2e up -d --build --wait
```

Result: **29/29 checks passed**. Verified in one run: both health endpoints; the six E1-E6 classifications; patient detail and the `EN_CONSULTA`/`ALTA` transitions plus the `409` invalid transition and unknown-patient `404`; SLA escalation to `PENDIENTE`, acknowledgement to `RECONOCIDA`, the `TRG-009` duplicate-ack conflict and the SSE `alerta` events; the operation report and its naive-date `400`; the assistant `/asistente/consulta` and `/rag` alias; the urgency projection rebuilt from `triaje.eventos`; `401` negatives with public health; both Prometheus registries; and one trace with spans from both services queried through Jaeger 2.x `/api/traces/{traceId}`.

No provider key was configured for this run, so the assistant correctly returned `503 LLM_UNAVAILABLE`. A later run with `RAG_LLM_CHAIN=gemini` and a valid key injected only as an environment variable returned `200` with `respuesta`, `fuentes`, `citas` and `modelo_usado`, including at least one `[n]` citation. A separate OpenCode trial key returned `503 LLM_UNAVAILABLE` and is not treated as a verified answer. No patient names, complaints, prompts, answers or keys were written to logs, metrics or spans in either run.

## 10. Verified local Kubernetes E2E run (2026-11-27)

The same checklist was executed against the `k8s/local` overlay on Docker Desktop Kubernetes with
`kubectl --context docker-desktop` and `127.0.0.1` forwards: **33/33 checks passed**. The steps to reproduce,
the per-area results and the reusable harness (`scripts/verify-local-k8s.ps1`) are documented in
`docs/demo/local-kubernetes-e2e.md`. This run covers the real one-minute SLA escalation, acknowledgement and
SSE, outbox → Kafka `traceparent`, the urgency projection, auth negatives, both metric registries and a shared
Jaeger trace across both services. It is local evidence only and does not close the Azure gates in ADR-11.

## Scope boundary

Azure deployment is covered by the AKS manifests and IaC delivered for deliverable 4; see ADR-12 and `docs/operations/despliegue-azure-y-pruebas.md` for the public URL and the provisioning evidence. There is no webhook receiver: RF-06 uses persisted alerts and SSE. Full Testcontainers integration (including PostgreSQL LISTEN/NOTIFY) runs in CI; the local harness used for this demo has no usable Docker socket for it.
