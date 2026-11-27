# ADR-11 R-06/T-24 rerun evidence template

> Fill this template when rerunning Azure deployment evidence. Do not include secrets, tokens, passwords, kubeconfigs, private keys, or raw cloud credential output.

## Metadata

- **Date (UTC/local):**
- **Operator:**
- **Target commit/tag:** 
- **Branch/workflow ref:**
- **Azure subscription:** Azure for Students (id redacted)
- **Region:** `northcentralus`
- **Resource group:** 
- **Cluster:** 
- **ADR/requirement:** ADR-11 R-06 / T-24

## Preconditions

- [ ] `az login` completed with approved account.
- [ ] `infra/azure/provision.ps1` reviewed; parameters match intended target (`-Location`, `-NodeVmSize`, repo, RG).
- [ ] OIDC/RBAC hardening changes present in target commit.
- [ ] GitHub environment/vars/secrets verified (names only; no values recorded).
- [ ] Budget alerts 50%/80% checked or tracked in separate evidence.
- [ ] No uncommitted IaC/manifests drift.

## Commands / workflow evidence

| Step | Command/workflow | Expected | Result | Evidence link/log |
|---|---|---|---|---|
| Provision | `infra/azure/provision.ps1 ...` | RG/AKS/ACR/PG/network created or reconciled | PASS/FAIL |  |
| Deploy | `.github/workflows/deploy.yml` run | Deploy completes without manual intervention | PASS/FAIL |  |
| Workloads | `kubectl get pods -n vidacruz` | triage 2/2, assistant running as designed, Kafka/Redis healthy | PASS/FAIL |  |
| Gateway/TLS | `kubectl get gateway,httproute,certificate -A` or equivalent | Gateway programmed, routes attached, cert ready | PASS/FAIL |  |
| API smoke | `curl`/script checks | 401 without key, 200 docs/health as expected | PASS/FAIL |  |

## R-06 gate checks

- [ ] No manual `kubectl scale` workaround required.
- [ ] No manual post-deploy patching required outside documented procedure.
- [ ] `deploy.yml` run URL attached: 
- [ ] Provision output/log attached (redacted): 
- [ ] AKS node SKU confirmed: `Standard_B4as_v2` (or record actual).
- [ ] PostgreSQL SKU/region confirmed: `Standard_B1ms` / `northcentralus` (or record actual).

## Deviations / incidents

| Time | Deviation | Manual action taken? | Impact | Follow-up issue/PR |
|---|---|---:|---|---|

## Outcome

- **Status:** PASS / FAIL / PARTIAL
- **ADR-11 acceptance impact:** 
- **Next action:** 
