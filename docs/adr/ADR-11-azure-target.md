# ADR-11: Azure target for deployment

- **Status:** Proposed. Not accepted. Option A is the proposed route; it is blocked by R-06 until access and quota are verified. The GPU requirement was removed from the ADR-05 workload by the Developer/User record of 2026-11-26 (C17); Azure Free Tier is the environment constraint, not a verified deployment quota.
- **Date:** 2026-11-25
- **Deciders:** developer; environment constraint recorded 2026-11-26, ADR acceptance still pending R-06 evidence
- **Scope:** C-01 Azure manifests only. This ADR chooses the Azure compute target. Dockerfiles, docker-compose and CI do not wait for it, as recorded in `adr-roadmap.md` §3.3.
- **Related:** NFR-13, NFR-14, AMB-11, R-06, G-20, CA-G-18, DEC-12, DEC-13, §14 of [analysis.md](../analysis.md), [ADR-05](ADR-05-assistant-llm-provider.md), T-22, T-23, T-24 in [implementation-roadmap.md](../implementation-roadmap.md)

## Context

NFR-14 requires an Azure target. D0 §14 lists AKS and Azure Container Apps: Container Apps is simpler for the three-day evaluation window; AKS demonstrates Kubernetes directly. AMB-11 and R-06 record that manifests are delivered regardless, but the deployment URL depends on subscription access and quota.

The target also has to support ADR-05. Option D of that ADR originally proposed serving chat and embedding models through Ollama inside the Azure perimeter, with a provisional GPU requirement. The Developer/User record of 2026-11-26 (C17) removes that requirement from the chat workload: Ollama runs on hardware the Developer/User already owns and is reached through a Cloudflare Tunnel, with Google AI Studio and OpenCode inference as the cloud failover nodes. Azure is therefore used for lightweight containers, Kafka and pgvector under the recorded Free Tier constraint, not for GPU model serving. ADR-05 still needs evidence that the chosen target can host that reduced workload; this ADR produces that evidence but is not blocked by it. If the evidence shows the workload is not viable, ADR-05 changes route. Regional availability and quota are not assumed for either option.

Local delivery is independent: T-22 delivers Dockerfiles and docker-compose, and T-23 delivers Kubernetes manifests. Only the Azure deployment in T-24 depends on this decision. No secret may be committed; configuration goes through ConfigMap and Secret resources under NFR-13.

## Decision drivers

- NFR-14: a reachable Azure deployment, or a documented blocker that names R-06.
- NFR-13: valid Deployment, Service, probes, ConfigMap and Secret manifests.
- Support for the ADR-05 workload, including GPU verification where required.
- No change to the local one-command run (NFR-12).
- No secrets in the repository or the image (NFR-09, NFR-10).
- A decision that can be recorded even when Azure access is unavailable.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Azure Container Apps** | Deploy both services as managed containers in the target region | Simpler operations; fits the three-day window; manifest and probe work stays small | GPU capability, regional availability and quota are not assumed and must be verified; demonstrates Kubernetes less directly | **Proposed**, not rejected. Acceptance depends on R-06 access and quota evidence; ADR-05 workload viability is recorded as an output |
| **B. AKS with a GPU node pool** | Deploy both services to a managed Kubernetes cluster with an explicit GPU node pool | Direct Kubernetes demonstration; explicit node pool for the ADR-05 workload | More cluster setup and operations; stronger dependency on quota and cost; longer path to a URL | Kept as the fallback if Container Apps cannot satisfy the verified GPU requirement |
| **C. Deliver manifests only and record the R-06 blocker** | Stop before deploying; document that access or quota is unavailable | Guarantees the manifest deliverable without depending on external access | Does not satisfy the deployment-URL part of NFR-14 | Rejected as a primary choice. Allowed only as the recorded outcome of T-24 when R-06 blocks deployment |

## Decision

**Proposed: Option A, Azure Container Apps, for the three-day evaluation window.** Acceptance depends on access, region and quota evidence for this target, not on the ADR-05 model workload. This ADR records separately whether the target can host that workload, including any GPU requirement, within budget; ADR-05 uses that record to decide its own route. If the target cannot host the workload, ADR-05 changes route; a change from Option A to Option B here needs an explicit update of this ADR. Dockerfiles, docker-compose and CI remain independent of this decision. ConfigMap and Secret resources carry configuration under NFR-13; no secret is committed. If R-06 prevents deployment, T-24 records the blocker rather than working around it. This is a proposal, not an accepted decision.

## Consequences

- Positive: a simple managed target keeps the delivery path short and leaves local delivery untouched.
- Negative: ADR-05 remains blocked until the target is verified; Option A demonstrates Kubernetes less directly than AKS.
- C-01 Azure manifests wait for this ADR; the rest of C-01 does not.
- ADR-05 Option D is not viable until the region, quota and budget evidence recorded here exists; GPU capacity is no longer an input after the C17 record of 2026-11-26. OpenCode inference remains a prepaid cost, so the overall solution is not zero-cost.
- A blocked deployment still produces a manifest deliverable and a named R-06 record.

## Risks and mitigations

- **R-06 (access or quota):** deliver manifests first; if access is unavailable, T-24 records the blocker with its evidence.
- **ADR-05 dependency:** record GPU support, region, quota and cost as evidence for ADR-05; do not infer support from documentation for another line. A negative result changes ADR-05, not this ADR automatically.
- **CA-G-18 (version consistency):** verify the pinned Boot, Spring Cloud and Spring AI versions before the first Azure build under DEC-12.
- **G-20 (deployment artifacts):** T-22 and T-23 produce the container image and manifests independently of R-06.
- **NFR-09/NFR-10:** keep credentials in Secret resources and scan the image and repository for committed secrets.

## Verification

- **T-24:** a deployment URL is available, or a blocker record explicitly names R-06 with the access, quota or region evidence.
- **T-23:** Kubernetes manifests validate with a schema check.
- **T-22:** `docker compose up` still starts both services locally with PostgreSQL, Kafka, Redis and the webhook mock.
- **NFR-14 evidence:** the deployed target is reachable through the documented endpoints.
- **ADR-05 §4 evidence:** the chosen target is recorded as able or unable to host the model workload, including GPU where required, in the target region and within budget; the record is an input to ADR-05.
- **Secret scan:** no credential appears in the repository or the image.
