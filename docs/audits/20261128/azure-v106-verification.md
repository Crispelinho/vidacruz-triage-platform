# Azure v1.0.5/v1.0.6 verification record

> No secrets, tokens, passwords, kubeconfig contents or raw cloud credential output are recorded here. Commands use the existing `vidacruz-rg` / `vidacruz-l7rsr6` target and the public host `https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com`.

## Release and deploy

| Artifact | Value |
|---|---|
| `v1.0.5` commit | `6dddcf524f3c649516c5adb4748139a1b21762dc` |
| `v1.0.5` deploy | [run 46102731375](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46102731375), success in 4m9s |
| `v1.0.6` commit | `38d9d7742bb573d494e9e64e0fd0340e712fdea0` |
| `v1.0.6` deploy | [run 46105547724](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46105547724), success in 9m38s |
| Workflow ref | `main`; A-5 branch guard in `.github/workflows/deploy.yml:27` skips tag refs, so `workflow_dispatch` targets `main` at the tagged commit |
| Workloads | `kubectl rollout status deployment/triage-service deployment/clinical-assistant-service -n vidacruz` succeeded for both; pods ran images tagged `38d9d77` |

## API smoke without `X-API-Key`

Checked with `curl -k -s -o NUL -w '%{http_code}'` against the public host:

| Path | Result |
|---|---|
| `/docs/triage/swagger-ui.html` | `302` to `/docs/triage/swagger-ui/index.html` |
| `/docs/triage/swagger-ui/index.html` | `200` |
| `/docs/triage/v3/api-docs` | `200` |
| `/docs/triage/v3/api-docs/swagger-config` | `200` |
| `/docs/asistente/swagger-ui.html` | `302` to `/docs/asistente/swagger-ui/index.html` |
| `/docs/asistente/swagger-ui/index.html` | `200` |
| `/docs/asistente/v3/api-docs` | `200` |
| `/docs/asistente/v3/api-docs/swagger-config` | `200` |
| `/docs/{triage,asistente}/webjars/swagger-ui/{index.css,swagger-ui-bundle.js,swagger-ui-standalone-preset.js}` | `200` |
| `/ops/{triage,asistente}/health/readiness` | `200` |

The `v1.0.5` run reached only `/v3/api-docs`; the UI, `swagger-config` and WebJars returned `401` until #171/#172 normalized the gateway `X-Forwarded-Prefix` in both API-key filters.

## Newman

`npx newman run VidaCruz.azure-final.postman_collection.json --insecure --timeout-request 30000` on the public host:

| Commit | Requests | Assertions | Failures |
|---|---:|---:|---:|
| `6dddcf5` | 31 | 70 | 0 |
| `38d9d77` | 31 | 70 | 0 |

The corrected collection uses `/ops/{triage,asistente}/health/readiness` for the public health checks. Those routes are declared in `infra/k8s/routes/routes.yaml:240-302` but were absent from the pre-existing cluster because it predated #160; they were applied with `kubectl apply -f` from the rendered `infra/k8s/routes` kustomization before these runs. A clean ADR-11 rerun still requires `infra/azure/provision.ps1` to reconcile them from IaC.

## R-06 / RAG / trace checks

Final run against `v1.0.6`: **13 PASS / 0 FAIL** (`verify-azure-r6.ps1`, temporary harness outside the repository; it reads API keys from Kubernetes Secrets, never prints them, and writes a sanitized report to `C:\Users\HP\AppData\Local\Temp\vidacruz-azure-r6.txt`, not committed).

| Check | Result |
|---|---|
| Health via `/ops/{triage,asistente}` | `200` / `200` |
| Critical registration with injected `traceparent` | `201`, `nivel=CRITICO`, trace `7c2e9b41d8a64f30b5e7c1a9f2d6b845`, patient `16476d7d-1d50-4157-b406-9e9edc2958e5` |
| RF-03 SLA escalation | `PENDIENTE`, `slaMinutos=5`, alert `9e86da05-aa83-48b2-875e-987918379aa2`; SSE payload recorded `minutosEsperaAlEscalar=5.03` |
| RF-06 acknowledgement flow | invalid `400`/`TRG-004`, valid `200`/`RECONOCIDA`, duplicate `409`/`TRG-009` |
| RF-06 SSE snapshot + heartbeat | `event:alerta` snapshot present, `:latido` heartbeat present, 12,150 bytes |
| RF-06 SSE live alert + resolution | same alert id seen live, then `RESUELTA` |
| RF-06 transition to `EN_CONSULTA` | `200`, `estado=EN_CONSULTA` |
| RAG chat, PROTO-001 | 5/5 runs `200` with non-empty answer and `PROTO-001` citation |
| Outbox → Kafka `traceparent` | injected trace found in the `triaje.eventos` headers consumed from `kafka-0` |
| Jaeger in Azure | absent, as expected; shared-trace evidence remains local-only |

A direct grounded-chat call confirmed the real provider: `modelo_usado=gemini/gemini-3.5-flash-lite`, `fuentes=PROTO-001`, non-empty 204-character answer. This is a **partial** chat five-run gate: only PROTO-001 was repeated five times; PROTO-002, the four PROTO-003 probes and the negative query were not repeated in Azure. Cost was not measured and the remaining chain nodes were not exercised.

Limitations: direct `psql` to Azure PostgreSQL times out from the verification host (firewall), so no row-level DB check is attached; there is no Jaeger deployment in Azure, so the end-to-end shared trace is still local-only evidence.

### Verification-harness note

The first harness run reported `400` for every authenticated POST. This was a harness defect, not an application finding: Windows PowerShell 5.1 passed the inline JSON to `curl.exe --data-binary` with mangled encoding. A diagnostic POST with the same payload and key returned `HTTP/1.1 201 Created` with `nivel=CRITICO`, `slaMinutos=5`. The harness was changed to write UTF-8-without-BOM JSON to a temporary file and pass it with `--data-binary @file`; the rerun uses a fresh trace id so it cannot match pre-existing Kafka events.

The Kafka excerpt from the earlier run also shows `traceparent` on `paciente_registrado`, `paciente_en_consulta` and `paciente_alta` outbox events, while scheduler-emitted `paciente_escalado` events have no inbound trace context and are logged as `NO_HEADERS`. That is expected for a timer-originated event and is not presented as propagated-trace evidence.

## Scope boundary

These are functional checks on an existing Azure resource group. They do **not** close the ADR-11 gates that require a reproducible `provision.ps1` + `deploy.yml` run without manual workarounds, a fresh budget-alert export, a secret-rotation record and an image scan. ADR-11 and ADR-05 remain `Proposed`.
