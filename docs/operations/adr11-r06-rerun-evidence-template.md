# ADR-11 R-06/T-24 rerun evidence template

> Fill this template when rerunning Azure deployment evidence. Do not include secrets, tokens, passwords, kubeconfigs, private keys, or raw cloud credential output.

## Metadata

- **Date (UTC/local):** 2026-11-28 UTC / 2026-11-27 local
- **Operator:** repository owner, agent-assisted verification
- **Target commit/tag:** `v1.0.5` = `6dddcf524f3c649516c5adb4748139a1b21762dc`; `v1.0.6` = `38d9d7742bb573d494e9e64e0fd0340e712fdea0`
- **Branch/workflow ref:** `main` (`workflow_dispatch`; A-5 branch guard in `.github/workflows/deploy.yml:27` skips tag refs)
- **Azure subscription:** Azure for Students (id redacted)
- **Region:** `northcentralus`
- **Resource group:** `vidacruz-rg`
- **Cluster:** AKS `vidacruz-aks` (public host `vidacruz-l7rsr6.northcentralus.cloudapp.azure.com`), node `Standard_B4as_v2`
- **ADR/requirement:** ADR-11 R-06 / T-24

## Preconditions

- [x] `az login` completed with approved account.
- [x] `infra/azure/provision.ps1` reviewed; parameters match the recorded target (`northcentralus`, `Standard_B4as_v2`, repo `Crispelinho/vidacruz-triage-platform`, RG `vidacruz-rg`) in `run-provision.ps1` (temporary verification script, not a repository file).
- [x] OIDC/RBAC hardening changes present in target commit (`a42e3f6`, PR #160; pinned actions and branch guard in `.github/workflows/deploy.yml:21-30`).
- [x] GitHub environment/vars/secrets verified by name only; no values recorded here.
- [x] Budget alerts 50 %/80 % verified by a fresh export at [`docs/audits/20261128/azure-budget-alert-export.json`](../audits/20261128/azure-budget-alert-export.json).
- [x] Clean `provision.ps1` rerun from the v1.0.6 infrastructure tree: completed 2026-11-28T10:10:56Z; no manual `kubectl apply` was needed. The earlier redeploy cycle is retained below as history.

## Commands / workflow evidence

| Step | Command/workflow | Expected | Result | Evidence link/log |
|---|---|---|---|---|
| Provision from target commit | `infra/azure/provision.ps1 ...` on 2026-11-28 | RG/AKS/ACR/PG/network reconciled from current IaC | PASS | `clean-provision-v106.log` (redacted, not committed); `vidacruz-ops configured`, Gateway `Programmed`, certificate `Ready` |
| Deploy v1.0.5 | `.github/workflows/deploy.yml`, `workflow_dispatch` on `main` at `6dddcf5` | Deploy completes without manual intervention | PASS | [run 46102731375](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46102731375), success in 4m9s |
| Deploy v1.0.6 | `.github/workflows/deploy.yml`, `workflow_dispatch` on `main` at `38d9d77` | Deploy completes and images use the tagged commit | PASS | [run 46105547724](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46105547724), success in 9m38s; both deployments report `successfully rolled out` with image tag `38d9d77` |
| Clean redeploy | `.github/workflows/deploy.yml`, `workflow_dispatch` on `main` at `38d9d77` after `provision.ps1` | Deploy completes with no manual scale; both deployments `2/2` | PASS | [run 46134085417](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46134085417), success in 2m33s |
| Workloads (v1.0.5) | `kubectl ... get pods/rollout status -n vidacruz` | triage/assistant rolled out, Kafka/Redis healthy | PASS | Both deployments reported `successfully rolled out`; images `...:6dddcf5` |
| Gateway/TLS | `kubectl ... get httproute -n vidacruz` | API, docs and ops routes attached | PASS | All four routes present after the clean rerun; the earlier `vidacruz-ops` manual apply (D-1) is no longer required |
| API smoke (v1.0.5) | `curl` on public host | Four Swagger URLs 200 without key; health 200 | PARTIAL | `/docs/{triage,asistente}/v3/api-docs` = 200; `swagger-ui.html`, `swagger-config` and UI assets = 401 (D-2) |
| API smoke (v1.0.6) | `curl` on public host | Four Swagger URLs and assets 200 without key | PASS | `/docs/{triage,asistente}/swagger-ui.html` = 302 to `/swagger-ui/index.html` = 200; `/v3/api-docs` = 200; `/v3/api-docs/swagger-config` = 200; WebJars CSS/JS bundles = 200; no `X-API-Key` sent |
| Newman (v1.0.5 / v1.0.6) | `npx newman run VidaCruz.azure-final.postman_collection.json --insecure` | 31 requests / 70 assertions, 0 failures | PASS | `newman-v105-health-fixed.json` and `newman-v106.json` (verification artifacts, not committed); health URLs use `/ops/{triage,asistente}/health/readiness` |
| Newman after clean rerun | same collection at `38d9d77` | 31 requests / 70 assertions, 0 failures | PASS | `newman-v106-clean-rerun.json` (not committed) |
| Functional R-06 (v1.0.6) | `verify-azure-r6.ps1` against the public host | SLA escalation, SSE, acknowledgement, real-provider RAG and outbox→Kafka trace | PASS | 13 checks / 0 failures before and after the clean rerun; full detail in `docs/audits/20261128/azure-v106-verification.md` |

### Functional R-06 detail (v1.0.6)

- [x] Critical registration with injected `traceparent`: `201`, `CRITICO`, trace `7c2e9b41d8a64f30b5e7c1a9f2d6b845`.
- [x] RF-03 escalation: alert `9e86da05-aa83-48b2-875e-987918379aa2`, `slaMinutos=5`, SSE payload `minutosEsperaAlEscalar=5.03`.
- [x] RF-06 acknowledgement: `400`/`TRG-004`, `200`/`RECONOCIDA`, `409`/`TRG-009`.
- [x] RF-06 SSE: snapshot `event:alerta`, `:latido` heartbeat, live alert and `RESUELTA` observed.
- [x] RAG chat: 5/5 `200` with `PROTO-001` and a non-empty answer; direct call reported `modelo_usado=gemini/gemini-3.5-flash-lite`.
- [x] Outbox→Kafka: injected trace found in `triaje.eventos` headers from `kafka-0`.
- [ ] End-to-end shared trace in Jaeger: not available in Azure; local-only evidence remains.
- [x] Row-level PostgreSQL check: ran `psql` from a temporary pod inside AKS (firewall-allowed egress IP); patient, outbox `traceparent`, audit transitions and alert state matched the live R-06 run. See [`azure-db-rowlevel-verification.txt`](../audits/20261128/azure-db-rowlevel-verification.txt).

## R-06 gate checks

- [x] No manual `kubectl scale` workaround was needed for the v1.0.5/v1.0.6 deploys.
- [x] No manual post-deploy patching required in the clean rerun: the four HTTPRoutes were reconciled from IaC. D-1 remains as the history of the earlier cycle.
- [x] v1.0.5 deploy run URL attached above.
- [x] Clean provision output/log captured (redacted, not committed): `clean-provision-v106.log`; final line `Provisioned. Public host of the APIs: https://vidacruz-l7rsr6.northcentralus.cloudapp.azure.com`.
- [x] AKS node SKU confirmed: `Standard_B4as_v2`.
- [x] PostgreSQL SKU/region confirmed from the v1.0.4 provision record: `Standard_B1ms` / `northcentralus`.

## Deviations / incidents

| Time | Deviation | Manual action taken? | Impact | Follow-up issue/PR |
|---|---|---|---:|---|
| 2026-11-28 03:30Z | `vidacruz-ops` HTTPRoute from `infra/k8s/routes/routes.yaml:240-302` was absent because the cluster predated PR #160; `/ops/*/health` returned 404 | Yes: rendered routes with `kubectl kustomize infra/k8s/routes` and applied with `kubectl apply` | Resolved by the 2026-11-28 clean rerun; historical deviation only | PR #160 back-merge; clean rerun evidence above |
| 2026-11-28 03:31Z | `/docs/.../swagger-ui.html` and `swagger-config` returned 401 because gateway `X-Forwarded-Prefix` reached the API-key filter as part of the path | No manual patch; fixed in source | Closed by v1.0.6 deploy | #171 / PR #172 |

## Outcome

- **Status:** PARTIAL. Functional verification, clean infrastructure reproducibility and budget evidence are complete; secret rotation/image scan is not.
- **ADR-11 acceptance impact:** None. ADR-11 stays `Proposed`; only the secret-rotation/image-scan gate remains open in ADR-11, alongside the ADR-05 functional/cost gates.
- **Next action:** attach the credential rotation/image-scan record, then reassess ADR-11 for promotion. ADR-05 still requires measured cost, a full runtime spike and five runs per chat probe.
