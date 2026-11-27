# ADR-11 secret hygiene and rotation evidence template

> Record hygiene/rotation evidence without exposing secret values. Never paste keys, tokens, passwords, connection strings, kubeconfigs, certificates, or secret-bearing logs.

## Metadata

- **Date (UTC/local):**
- **Operator:**
- **Related ADR gate:** ADR-11 secret hygiene
- **Related deployment/rerun evidence:** 
- **Scope:** repo / GitHub Actions / Azure / AKS / images / history

## Secret inventory (names only)

| Secret name | Location class | Purpose | Rotated? | Date rotated | Evidence ref |
|---|---|---|---:|---|---|
| `TRIAGE_API_KEY` | GitHub env / K8s secret | Service auth |  |  |  |
| `ASSISTANT_API_KEY` | GitHub env / K8s secret | Service auth |  |  |  |
| `REDIS_PASSWORD` | GitHub env / K8s secret | Redis auth |  |  |  |
| `POSTGRES_PASSWORD` / DB creds | Azure/GitHub/K8s | DB auth |  |  |  |
| `GEMINI_API_KEY` | GitHub env / K8s secret | LLM provider |  |  |  |
| `OPENCODE_API_KEY` | GitHub env / K8s secret | LLM provider |  |  |  |
| Other: |  |  |  |  |  |

## Hygiene checks

- [ ] No secrets committed in working tree (`git grep`/scanner result attached, redacted).
- [ ] No secrets in GitHub Actions logs reviewed (run IDs listed).
- [ ] No secrets baked into container images/configmaps committed as literals.
- [ ] Kubernetes manifests use secret references/templates, not literal values.
- [ ] Any credential exposed during validation/chat/log was identified.
- [ ] Exposed credentials rotated/revoked at source.
- [ ] Dependent services/workflows restarted/redeployed after rotation (if required).
- [ ] Old credentials invalidated/confirmed unusable.

## Rotation record (no values)

| Secret name | Rotation reason | Rotated at | Rotated by | Systems updated | Verification | Notes |
|---|---|---|---|---|---|---|

## Evidence attachments

- Scanner/gitleaks/trufflehog output (redacted): 
- GitHub Actions run URLs reviewed: 
- Azure/Key Vault/portal change reference: 
- K8s rollout/restart evidence: 

## Outcome

- **Status:** PASS / FAIL / PARTIAL
- **Remaining risk:** 
- **ADR-11 acceptance impact:** 
