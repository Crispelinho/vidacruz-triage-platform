# Local verification: E1–E6 → SLA → alerts → report → assistant

This is the executable companion to the root `README.md`. Everything runs on `localhost` against `docker compose up -d --build`. No cloud credentials, public URL, k8s manifests or provider API keys are required except where noted.

## Before you start

```bash
docker compose up -d --build
docker compose ps
curl -fsS http://localhost:8081/actuator/health
curl -fsS http://localhost:8082/actuator/health
```

Both health responses must be `{"status":"UP"}`. All business calls need `X-API-Key`: `test-triage-key` on port 8081, `test-assistant-key` on port 8082 (`docs/adr/ADR-07-authentication-api-key.md`).

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

An offset or `Z` is mandatory. Missing or malformed parameters return `400 TRG-004` with a `field` hint.

## 5. RF-04 — assistant (provider-dependent)

The implemented path is `POST /rag`; API-04 documents `/asistente/consulta` but the controller has not been aligned yet.

```bash
curl -sS -X POST http://localhost:8082/rag \
  -H 'X-API-Key: test-assistant-key' -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-001' \
  -d '{"pregunta":"¿Cuáles son las medidas iniciales ante una crisis hipertensiva?"}'
curl -sS http://localhost:8082/asistente/estado-urgencias -H 'X-API-Key: test-assistant-key'
```

With no reachable provider, `/rag` returns `503 LLM_UNAVAILABLE` and the fixed notice — that is the documented degraded mode, not a failure of the stack. To exercise a real answer, start `docker compose --profile ollama up -d`, pull a model (`docker compose --profile ollama run --rm ollama ollama pull llama3.1:8b`) and point `OLLAMA_BASE_URL` at it. Provider keys are read from `GEMINI_API_KEY` / `OPENCODE_API_KEY`; never commit them.

## 6. Auth negatives

```bash
curl -sS -i http://localhost:8081/triage/alertas
curl -sS -i http://localhost:8081/triage/alertas -H 'X-API-Key: wrong'
```

Both return `401` with `{"code":"UNAUTHORIZED","message":"Invalid or missing X-API-Key header","correlationId":"..."}` and an `X-Correlation-Id` header. Health probes stay public.

## Scope boundary

No Azure deployment, public URL, k8s/Helm manifests or webhook receiver exist. ADR-11 remains blocked by unverified subscription/quota (R-06) and must be recorded as a blocker, not worked around. Full Testcontainers integration (including PostgreSQL LISTEN/NOTIFY) runs in CI; the local harness used for this demo has no usable Docker socket for it.
