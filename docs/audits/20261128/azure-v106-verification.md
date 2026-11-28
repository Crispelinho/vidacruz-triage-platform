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
| Clean rerun deploy | [run 46134085417](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46134085417), success in 2m33s at `38d9d77`; no manual `kubectl scale` |

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
| `38d9d77` after clean reprovision + redeploy | 31 | 70 | 0 |

The corrected collection uses `/ops/{triage,asistente}/health/readiness` for the public health checks. Those routes are declared in `infra/k8s/routes/routes.yaml:240-302` but were absent from the pre-existing cluster because it predated #160; they were applied with `kubectl apply -f` from the rendered `infra/k8s/routes` kustomization before these runs. The clean rerun below later reconciled them from IaC with no manual apply.

## R-06 / RAG / trace checks

Final run against `v1.0.6`: **13 PASS / 0 FAIL** (`verify-azure-r6.ps1`, temporary harness outside the repository; it reads API keys from Kubernetes Secrets, never prints them, and writes a sanitized report to `C:\Users\HP\AppData\Local\Temp\vidacruz-azure-r6.txt`, not committed). The post-rerun repetition also returned **13 PASS / 0 FAIL**; report `C:\Users\HP\AppData\Local\Temp\opencode\azure-r6-clean-rerun.txt` (not committed).

| Check | Result |
|---|---|
| Health via `/ops/{triage,asistente}` | `200` / `200` |
| Critical registration with injected `traceparent` | `201`, `nivel=CRITICO`, trace `7c2e9b41d8a64f30b5e7c1a9f2d6b845`, patient `16476d7d-1d50-4157-b406-9e9edc2958e5` (synthetic test id) |
| RF-03 SLA escalation | `PENDIENTE`, `slaMinutos=5`, alert `9e86da05-aa83-48b2-875e-987918379aa2`; SSE payload recorded `minutosEsperaAlEscalar=5.03` |
| RF-06 acknowledgement flow | invalid `400`/`TRG-004`, valid `200`/`RECONOCIDA`, duplicate `409`/`TRG-009` |
| RF-06 SSE snapshot + heartbeat | `event:alerta` snapshot present, `:latido` heartbeat present, 12,150 bytes |
| RF-06 SSE live alert + resolution | same alert id seen live, then `RESUELTA` |
| RF-06 transition to `EN_CONSULTA` | `200`, `estado=EN_CONSULTA` |
| RAG chat, PROTO-001 | 5/5 runs `200` with non-empty answer and `PROTO-001` citation |
| Outbox → Kafka `traceparent` | injected trace found in the `triaje.eventos` headers consumed from `kafka-0` |
| Jaeger in Azure | absent, as expected; shared-trace evidence remains local-only |

A direct grounded-chat call confirmed the real provider: `modelo_usado=gemini/gemini-3.5-flash-lite`, `fuentes=PROTO-001`, non-empty 204-character answer. The earlier chat five-run gate was partial, but the dedicated rerun below later covered all probes five times each. Cost and the other chain nodes are still not exercised.

Direct `psql` from the verification host times out because the firewall only allows the fixed AKS egress IP; the row-level check below was instead run from a temporary pod inside the cluster. There is no Jaeger deployment in Azure, so the end-to-end shared trace is still local-only evidence.

### Verification-harness note

The first harness run reported `400` for every authenticated POST. This was a harness defect, not an application finding: Windows PowerShell 5.1 passed the inline JSON to `curl.exe --data-binary` with mangled encoding. A diagnostic POST with the same payload and key returned `HTTP/1.1 201 Created` with `nivel=CRITICO`, `slaMinutos=5`. The harness was changed to write UTF-8-without-BOM JSON to a temporary file and pass it with `--data-binary @file`; the rerun uses a fresh trace id so it cannot match pre-existing Kafka events.

All patient ids in this record are synthetic: the harness posts an `Azure R6 <suffix>` registration and `TriageRegistrationService` assigns `UUID.randomUUID()` (`TriageRegistrationService.java:140`). They do not correspond to real patient records.

The Kafka excerpt from the earlier run also shows `traceparent` on `paciente_registrado`, `paciente_en_consulta` and `paciente_alta` outbox events, while scheduler-emitted `paciente_escalado` events have no inbound trace context and are logged as `NO_HEADERS`. That is expected for a timer-originated event and is not presented as propagated-trace evidence.

## Clean reprovision and redeploy (2026-11-28)

Pre-flight checks before touching the resource group: signed-in subscription `Azure for Students`, resource group `vidacruz-rg` in `northcentralus`, AKS `vidacruz-aks` on `Standard_B4as_v2` with 1 node, PostgreSQL Flexible `Standard_B1ms`, federated credential subject `repo:Crispelinho@23733231/vidacruz-triage-platform@1470820988:environment:azure`, unchanged GitHub secret/variable names, and no in-progress `deploy-azure` run.

`infra/azure/provision.ps1` was rerun from the v1.0.6 infrastructure tree (identical to tag `v1.0.6`) with `-ResourceGroup vidacruz-rg -Location northcentralus -GithubRepository Crispelinho/vidacruz-triage-platform -GithubSubject 'repo:Crispelinho@23733231/vidacruz-triage-platform@1470820988:environment:azure' -NodeVmSize Standard_B4as_v2 -NodeCount 1`. `POSTGRES_PASSWORD` was read from the existing Kubernetes Secret and was never printed. The run completed at 2026-11-28T10:10:56Z (`clean-provision-v106.log`, not committed). It reconciled all four HTTPRoutes, including `httproute.gateway.networking.k8s.io/vidacruz-ops configured`; no manual `kubectl apply` was needed. The Gateway became `Programmed` and the certificate `Ready`.

`deploy.yml` was then dispatched on `main` at `38d9d77`: [run 46134085417](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46134085417), success in 2m33s without a manual scale step. Both deployments reported `READY=2`, `DESIRED=2`, `UPDATED=2` with images tagged `38d9d77`. Post-rerun checks: 16/16 Swagger/OpenAPI/WebJars/`ops` URLs returned `200`, Newman 31 requests / 70 assertions / 0 failures, and R-06 13 PASS / 0 FAIL. This closes the reproducible-rerun part of ADR-11; the earlier manual `/ops` workaround is no longer required for a fresh build.

A fresh budget export is committed at [`azure-budget-alert-export.json`](azure-budget-alert-export.json): budget `vidacruz-azure-for-students`, USD 100 annually, notifications `alert50` and `alert80` both `enabled: true` with `GreaterThan` thresholds 50 and 80. Current spend was USD 0.5212887105024231 at retrieval time. No credential values appear in the export.

## End-to-end reruns on `main` `38d9d77` (2026-11-28)

### Local Kubernetes checklist

`scripts/verify-local-k8s.ps1` was rerun against the `k8s/local` overlay on Docker Desktop Kubernetes after rebuilding both images from `main` and applying the overlay from `main`. Result: **33 PASS / 0 FAIL** (`local-e2e-main-final2.txt`, sanitized report outside the repository). It covers classification E1..E6, lifecycle and invalid transitions, SLA escalation, SSE snapshot/heartbeat/live/resolution, reports, auth negatives, metrics, outbox→Kafka `traceparent` and one trace visible in both services in Jaeger.

The first attempts exposed harness defects, not application findings: the `main` harness uses English fixtures for E5 and the degraded-consult question while the seeded corpus and classifier are Spanish; the 401 correlation id was read only from response headers; the outbox query had no retry; the Kafka consumer stopped at 50 messages, below the accumulated topic offset; and the degraded path takes about 97 seconds to time out against the unavailable local provider. The rerun used the Spanish fixtures from `f16dfcd` plus those four robustness corrections in a temporary copy; the committed harness still needs them to be reproducible without local patches.

### Azure chat five-run gate

A dedicated harness called `/asistente/consulta` five times for each of seven probes against the public host at `38d9d77`, using the assistant API key read from the Kubernetes Secret and never printed. Result: **7 probes × 5/5 = 35/35 passing runs** (`azure-chat-five-runs.txt`, sanitized report outside the repository). Every positive run returned `200`, a non-empty answer, the expected protocol in `fuentes`, and `modelo_usado=gemini/gemini-3.5-flash-lite`. The negative diabetes query returned `200` five times with `fuentes=[]`, the fixed notice `no existe protocolo disponible` and `modelo_usado=none`. An earlier attempt had three transient `503` responses on the 6.5 % probe; the final run retried only `503` responses with bounded backoff and recorded the final status of each run. This closes the chat five-run gate for ADR-05; it does not measure cost or exercise the other chain nodes.

### Azure PostgreSQL row-level check

`psql` from the workstation is blocked by design: `infra/azure/main.bicep:261-268` allows only the fixed egress IP, which AKS uses for outbound traffic (`main.bicep:159-166`). A temporary pod in namespace `vidacruz` therefore used that allowed path. It ran under PodSecurity `restricted` (non-root, dropped capabilities, `RuntimeDefault` seccomp), read `PGUSER` from the ConfigMap and `PGPASSWORD` through a `secretKeyRef` without printing either, issued only `SELECT` statements, and was deleted afterwards. Sanitized output: [`azure-db-rowlevel-verification.txt`](azure-db-rowlevel-verification.txt).

The check confirmed row-level state matching the live R-06 run: patient `16476d7d-1d50-4157-b406-9e9edc2958e5` is `CRITICO` / `EN_CONSULTA` with `sla_minutos=5`; `outbox_evento` holds `paciente_registrado` with `traceparent=00-7c2e9b41d8a64f30b5e7c1a9f2d6b845-...`, `paciente_escalado` with an empty `traceparent` (timer-originated, as documented) and `paciente_en_consulta` with its own trace; all three were published with `intentos=0` and `requiere_revision=false`. `auditoria_clinica` shows `REGISTRO → ESCALAMIENTO → ENTRADA_CONSULTA`, and `medical_alert` `9e86da05-aa83-48b2-875e-987918379aa2` is `RESUELTA` with `minutos_espera=5.03`. No outbox row required review. This closes the row-level gap in Azure; it does not add shared-trace evidence, since Jaeger is not deployed there.

## Scope boundary

The clean reprovision/redeploy, the fresh budget export, the row-level check and the two end-to-end reruns close the reproducible-rerun, budget-alert, row-level and chat five-run gates. The following remain open: secret-rotation/image-scan confirmation, measured ADR-05 cost, the full runtime spike, and committing the harness robustness fixes. Jaeger is absent in Azure, so shared-trace evidence remains local-only. ADR-11 and ADR-05 remain `Proposed`.
