# ADR-11: Azure target for deployment

- **Status:** Proposed. Not accepted. Amended 2026-11-27: Option B (AKS) replaces Option A (Azure Container Apps) as the proposed route, and the subscription, region and SKU values below are recorded as empirically validated by the Developer/User. Formal acceptance still requires closing the verification gates below; ADR-10 is accepted for evaluation scope (2026-11-27) after its R-02 support-risk decision was recorded in writing, which is not a production OSS support claim. The GPU requirement was removed from the ADR-05 workload by the Developer/User record of 2026-11-26 (C17).
- **Date:** 2026-11-25 (amended 2026-11-27; evidence/gates updated 2026-11-27)
- **Delivered artifacts:** [ADR-12](ADR-12-aks-deployment.md) records AKS manifests/IaC. [Despliegue en Kubernetes y registro de pruebas](../operations/despliegue-azure-y-pruebas.md) is the in-repo R-06/T-24 execution record for the validated AKS run, including blockers and pending hardening.
- **Deciders:** developer; AKS amendment and infrastructure parameters recorded 2026-11-27, ADR acceptance still pending verification gates
- **Scope:** C-01 Azure manifests only. This ADR chooses the Azure compute target. Dockerfiles, docker-compose and CI do not wait for it, as recorded in `adr-roadmap.md` §3.3.
- **Related:** NFR-13, NFR-14, AMB-11, R-06, G-20, CA-G-18, DEC-12, DEC-13, §14 of [analysis.md](../analysis.md), [ADR-05](ADR-05-assistant-llm-provider.md), [ADR-12](ADR-12-aks-deployment.md), T-22, T-23, T-24 in [implementation-roadmap.md](../implementation-roadmap.md)

## Context

NFR-14 requires an Azure target. D0 §14 lists AKS and Azure Container Apps: Container Apps is simpler for the three-day evaluation window; AKS demonstrates Kubernetes directly. AMB-11 and R-06 record that manifests are delivered regardless, but the deployment URL depends on subscription access and quota.

The 2026-11-27 Developer/User amendment selects AKS over the earlier Container Apps proposal and records the following validated infrastructure:

| Item | Recorded value |
|---|---|
| Subscription | Azure for Students, US$100/year; 50 %/80 % budget alerts to configure (not yet evidenced as active) |
| Region | `northcentralus` |
| Cluster node | `Standard_B4as_v2` (4 vCPUs, 16 GB RAM) |
| Database | PostgreSQL Flexible Server `Standard_B1ms` |

These values were recorded by the Developer/User as empirically validated. The in-repo execution record `docs/operations/despliegue-azure-y-pruebas.md` now includes the public HTTPS URL and the deployment output, but the environment was retired on 2026-11-27 and the run required manual workarounds. A reproducible unattended rerun remains a separate acceptance gate. The budget alerts are a control to configure, not a claim that they are already active in Azure.

The target also has to support ADR-05. Option D of that ADR originally proposed serving chat and embedding models through Ollama inside the Azure perimeter, with a provisional GPU requirement. The Developer/User record of 2026-11-26 (C17) removes that requirement from the chat workload: Ollama runs on hardware the Developer/User already owns and is reached through a Cloudflare Tunnel, with Google AI Studio (`gemini-3.5-flash-lite`) and OpenCode inference (`glm-5.3`) as the cloud failover nodes. Azure is therefore used for lightweight containers, Kafka and pgvector, not for GPU model serving. ADR-05 still needs a repository artifact showing that the chosen target can host that reduced workload; the parameters recorded here do not replace that evidence and do not block this amendment. If that evidence shows the workload is not viable, ADR-05 changes route.

Local delivery is independent: T-22 delivers Dockerfiles and docker-compose, and T-23 delivers Kubernetes manifests. Only the Azure deployment in T-24 depends on this decision. No secret may be committed; configuration goes through ConfigMap and Secret resources under NFR-13.

## Decision drivers

- NFR-14: a reachable Azure deployment, or a documented blocker that names R-06.
- NFR-13: valid Deployment, Service, probes, ConfigMap and Secret manifests.
- Support for the ADR-05 workload, now without a GPU requirement for chat.
- No change to the local one-command run (NFR-12).
- No secrets in the repository or the image (NFR-09, NFR-10).
- A recorded infrastructure target with budget guardrails, without treating a recorded value as repository evidence.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Azure Container Apps** | Deploy both services as managed containers in the target region | Simpler operations; fits the three-day window; manifest and probe work stays small | Superseded by the AKS amendment; demonstrates Kubernetes less directly | **Superseded/historical as of 2026-11-27.** It was the original Option A proposal, not accepted |
| **B. AKS on Azure for Students** | Deploy both services to a managed Kubernetes cluster with the recorded `Standard_B4as_v2` node and PostgreSQL Flexible `Standard_B1ms` in `northcentralus` | Direct Kubernetes demonstration; direct support for the ADR-05 workload; recorded as empirically validated by the Developer/User | More cluster setup and operations than Container Apps; budget alerts must be configured; the R-06 execution record exists but a reproducible rerun is pending | **Proposed by the 2026-11-27 amendment.** It replaces Option A; formal acceptance still pending |
| **C. Deliver manifests only and record the R-06 blocker** | Stop before deploying; document that access or quota is unavailable | Guarantees the manifest deliverable without depending on external access | Does not satisfy the deployment-URL part of NFR-14 | Rejected as a primary choice. Allowed only as the recorded outcome of T-24 when R-06 blocks deployment |

## Decision

**Proposed, as amended 2026-11-27: Option B, AKS on Azure for Students in `northcentralus`, with a `Standard_B4as_v2` node and PostgreSQL Flexible Server `Standard_B1ms`.** This supersedes the earlier Option A (Container Apps) proposal. The subscription is Azure for Students with a US$100/year budget and alerts to be configured at 50 % and 80 %. The Developer/User records these values as empirically validated; the repository now has the execution record `docs/operations/despliegue-azure-y-pruebas.md`, but it documents a retired environment and manual workarounds, so a reproducible R-06 rerun is still required.

AKS is chosen for the three-day evaluation window because it demonstrates Kubernetes directly and hosts the reduced ADR-05 workload without a GPU node pool: the chat node is the already-owned Ollama endpoint behind Cloudflare Tunnel. OpenCode inference remains a prepaid cost, so the overall solution is not zero-cost. Dockerfiles, docker-compose and CI remain independent of this decision. ConfigMap and Secret resources carry configuration under NFR-13; no secret is committed. If R-06 prevents deployment, T-24 records the blocker rather than working around it. This remains a proposal, not an accepted ADR.

## Consequences

- Positive: AKS demonstrates Kubernetes directly, and the recorded node size and database tier are sized for the lightweight containers, Kafka and pgvector workload.
- Negative: the cluster needs more setup than Container Apps, and the US$100/year budget needs active 50 %/80 % alerts to be effective.
- ADR-05 no longer depends on GPU capacity in Azure after the C17 record; it still needs the target evidence listed as pending under Verification. OpenCode inference remains a prepaid cost, so the overall solution is not zero-cost.
- C-01 Azure manifests are delivered independently of this decision, but their adoption waits for this ADR to be accepted; the rest of C-01 does not wait.
- A blocked deployment still produces a manifest deliverable and a named R-06 record.
- The Container Apps analysis is kept as Option A history and must not be cited as the current proposal.

## Risks and mitigations

- **R-06 (access or quota):** the in-repo execution record exists at `docs/operations/despliegue-azure-y-pruebas.md`, but it documents manual workarounds and pending hardening. Keep it as evidence, not as proof of a fully reproducible unattended deployment.
- **Budget overrun:** configure the 50 % and 80 % alerts on the Azure for Students subscription and verify them before the evaluation window; the US$100/year value is recorded, not monitored by this ADR.
- **Regional/SKU availability:** `northcentralus`, `Standard_B4as_v2` and PostgreSQL Flexible `Standard_B1ms` are recorded as validated by the Developer/User; re-check availability before the Azure build rather than inferring it from another subscription. The Bicep default should match the recorded node SKU or require an explicit override.
- **ADR-05 dependency:** record workload support, region and cost as evidence for ADR-05; do not infer support from documentation for another line. A negative result changes ADR-05, not this ADR automatically.
- **CA-G-18 (version consistency):** verify the pinned Boot, Spring Cloud and Spring AI versions before the first Azure build under DEC-12.
- **G-20 (deployment artifacts):** T-22 and T-23 produce the container image and manifests independently of R-06.
- **NFR-09/NFR-10:** keep credentials in Secret resources and scan the image and repository for committed secrets. Rotate any credential exposed during validation.

## Verification

Repository evidence currently present:

- [x] **T-23 manifests/IaC delivered:** Kubernetes manifests and Azure IaC exist in `infra/k8s/`, `infra/azure/`, and service `deployment/k8s/` directories; ADR-12 documents the topology.
- [x] **R-06/T-24 execution record present:** `docs/operations/despliegue-azure-y-pruebas.md` records the AKS deployment, public HTTPS URL, validation, manual workarounds, and pending hardening. It is repository evidence, but not a clean unattended rerun.
- [x] **T-22 local stack present:** `docker-compose.yml`, Dockerfiles, and service workflows exist; local/E2E verification remains the separate E2E track.
- [x] **Manifest/schema validation automated:** `.github/workflows/k8s-manifests.yml` renders and validates `infra/k8s`, `infra/k8s/cluster`, `infra/k8s/edge`, `infra/k8s/routes`, `triage-service/deployment/k8s`, and `clinical-assistant-service/deployment/k8s` with `kustomize build` + `kubeconform -strict -ignore-missing-schemas`. Green `k8s-manifests` runs recorded in [46066172188](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46066172188) and [46066177746](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46066177746) (PR #119).

Gates already closed:

- [x] **IaC/SKU alignment:** `infra/azure/main.bicep` defaults to the recorded `Standard_B4as_v2` node; PostgreSQL `Standard_B1ms` and `northcentralus` remain consistent. Re-check these values before another Azure build.
- [x] **ADR-10 gate:** satisfied by ADR-10 acceptance for evaluation scope (2026-11-27); keep the non-production OSS Boot 3.5.x constraint visible.

Pending before formal acceptance:

- [ ] **R-06 reproducible rerun:** rerun `infra/azure/provision.ps1` and `deploy.yml` from current `develop`/target commit with OIDC/RBAC hardening; attach CI run URL/log showing no manual `kubectl scale` workaround. Evidence template: `docs/operations/adr11-r06-rerun-evidence-template.md`.
- [ ] **Budget alerts evidence:** show 50 % and 80 % Azure for Students budget alerts configured (screenshot/export/reference), not just recorded.
- [ ] **Secret hygiene:** confirm no credential in repo/image/history; rotate credentials exposed during validation and record rotation without publishing values. Evidence template: `docs/operations/adr11-secret-hygiene-evidence-template.md`. Partial repo/Actions evidence is recorded in [`github-actions-secret-hygiene.md`](../audits/20261127/github-actions-secret-hygiene.md); the rotation record and image scan remain open.
- [ ] **ADR-05 target evidence:** record that the chosen AKS target can host the reduced ADR-05 workload in region/budget; chat GPU capacity is not required after C17.
