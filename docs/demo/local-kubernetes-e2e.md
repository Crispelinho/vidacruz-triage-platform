# Local Kubernetes E2E verification (2026-11-27)

This record covers the full checklist run against the `k8s/local` overlay on Docker Desktop Kubernetes. It is
local evidence only: no Azure resource, public route, real credential or production data is involved. The
Compose run is recorded separately in `docs/demo/local-verification.md`.

## Result

**33/33 checks passed, 0 failed** (`2026-11-27T13:18:39-05:00`).

| Area | Checks | Observed result |
|---|---:|---|
| Health and UI | 3 | Both `/actuator/health` returned `UP`; Jaeger UI returned 200 |
| RF-01 classification | 6 | E1..E6 returned the expected `CRITICO`, `URGENTE`, `URGENTE_2`, `NO_URGENTE`, `NO_URGENTE_2`, `CRITICO` with HTTP 201 |
| RF-02 lifecycle | 5 | `REGISTRADO` → `EN_CONSULTA` → `ALTA`; invalid transition 409 `INVALID_TRANSITION`; unknown id 404 `PATIENT_NOT_FOUND` |
| RF-16 / RF-04 / RF-03 | 3 | Registration with explicit `traceparent` returned 201; urgency projection reported active critical alerts; 1-minute SLA escalated to `PENDIENTE` |
| RF-06 acknowledgement and SSE | 5 | Invalid body 400 `TRG-004`; acknowledgement 200 `RECONOCIDA`; duplicate 409 `TRG-009`; SSE snapshot, heartbeat and live `alerta` event; alert resolved and removed from the active list |
| RF-05 report | 2 | Valid range returned 200 with hourly buckets; missing parameters returned 400 `VALIDATION_ERROR` |
| RF-04 degraded assistant | 1 | `/asistente/consulta` without a provider key returned 503 `LLM_UNAVAILABLE` |
| Auth | 2 | Missing and invalid `X-API-Key` returned 401 `UNAUTHORIZED` with `X-Correlation-Id`; public health stayed unauthenticated |
| NFR-07 metrics | 2 | Triage counters `triage_patients_classified_total`, `triage_patients_escalated_total`, `triage_alerts_acknowledged_total`; assistant metrics series present |
| Outbox → Kafka → trace | 3 | Three outbox rows carried the trace id; the `triaje.eventos` header contained the same `traceparent`; Jaeger `/api/traces/{traceId}` contained spans from both services |

The code uses `VALIDATION_ERROR` for an invalid operation-report request, not `TRG-004`; `TRG-004` is the code
for an invalid acknowledgement. The verification document was corrected accordingly.

No provider key was configured, so the degraded 503 is the expected assistant result. Patient names, complaints,
prompts, answers and keys are not attached to spans or metrics. The trace id is carried only as a Kafka header.

## Prerequisites

- Windows with `kubectl`, `docker` and PowerShell available.
- Docker Desktop Kubernetes running, with the context explicitly selected as `docker-desktop`. The default
  context in this environment was `vidacruz-aks-admin`, so every command below passes `--context`.
- Use `127.0.0.1`, not `localhost`, because local AKS forwards may be listening on `::1`.
- The overlay from `k8s/local` (including Jaeger, JDBC/R2DBC bindings and the `restricted`-compatible
  `local-db`) applied from this branch. On `develop` before the merge, apply the overlay from
  `fix/local-k8s-tracing-db`.

## Steps to reproduce

From the repository root:

```powershell
docker build -t triage-service:local ./triage-service/triage-service
docker build -t clinical-assistant-service:local ./clinical-assistant-service/clinical-assistant-service

kubectl --context docker-desktop apply -k ./k8s/local
kubectl --context docker-desktop -n vidacruz rollout status statefulset/local-db
kubectl --context docker-desktop -n vidacruz rollout status statefulset/kafka
kubectl --context docker-desktop -n vidacruz rollout status statefulset/redis
kubectl --context docker-desktop -n vidacruz wait --for=condition=complete job/kafka-topic-init --timeout=5m
kubectl --context docker-desktop -n vidacruz rollout status deployment/triage-service
kubectl --context docker-desktop -n vidacruz rollout status deployment/clinical-assistant-service
kubectl --context docker-desktop -n vidacruz rollout status deployment/jaeger
```

Confirm both deployments are at `2/2` before running the checks. Start three port-forwards in separate terminals:

```powershell
kubectl --context docker-desktop -n vidacruz port-forward svc/triage-service 18086:8081
kubectl --context docker-desktop -n vidacruz port-forward svc/clinical-assistant-service 18085:8082
kubectl --context docker-desktop -n vidacruz port-forward svc/jaeger 18087:16686
```

Run the harness. It generates new synthetic patients, polls the one-minute SLA, opens an SSE stream, checks the
outbox and Kafka header through `kubectl`, and queries Jaeger:

```powershell
powershell -ExecutionPolicy Bypass -File ./scripts/verify-local-k8s.ps1
```

The script writes a timestamped report to `$env:TEMP\vidacruz-local-k8s-e2e.txt` and exits with code 1 if any
check fails. Override `-ReportPath`, the base URLs or `-KubectlContext` when the environment differs. The
harness takes roughly two minutes because it waits for the real SLA escalation and SSE heartbeat.

Use only the placeholder API keys from the overlay (`local-triage-key`, `local-assistant-key`). Do not put real
credentials into the script, the manifests or the report.

## Scope boundary

This run validates the local overlay and the application flow; it does not close the Azure acceptance gates in
`docs/adr/ADR-11-azure-target.md`. Those still require a reproducible `provision.ps1`/`deploy.yml` run without
manual workarounds, budget-alert evidence, manifest/schema validation and a secret-rotation record.
