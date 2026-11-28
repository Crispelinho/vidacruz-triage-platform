# Azure verification of `develop` e57f6fe

## Scope

Deploy run `46163138392` was dispatched from `develop` at `2026-11-28T16:03:58Z` and
completed `success` at `16:07:45Z`. Its head is
`e57f6fec0e439f76a4456ce183cb9177ff11baad`, not the `87cf99a` head that was originally
authorized: `develop` advanced before GitHub took the dispatch. The cancel request arrived
after completion, so the run could not be stopped. The owner chose to validate the deployed
commit instead of reverting `develop`.

Both deployments are `2/2` with images tagged `e57f6fec0e439f76a4456ce183cb9177ff11baad`.
ACR contains tags `206ede7` and `e57f6fe` only; there are no `87cf99a` images. The workflow
takes no SHA input (`.github/workflows/deploy.yml:15`) and uses `github.sha` as the image tag
(`:60`), so the same head cannot be redeployed by a new dispatch on the current `develop`.

`87cf99a..e57f6fe` includes merges #192, #194, #196, #197, #198 and #199. The only code
changes are `a4ed4e5` (`PiiRedactor.java`, dotted-number redaction) and `508a12d`
(`PromptRequest.java`, `application.yaml`, OD-16 scrubber). The rest is documentation.
There is no green CI run for `e57f6fe` other than this deploy.

## Gates

| Gate | Result | Evidence |
|---|---|---|
| Security stack (public API, auth, PHI audit, policies, image) | **25/25** | `azure-develop-e57f6fe-security-stack.txt` |
| R-06 live flow (health, registration, SLA, ack, SSE, chat, outbox→Kafka) | **13/13** | `azure-develop-e57f6fe-r6.txt` |
| Chat gate, 7 probes × 5 runs | **7/7 = 35/35** | `azure-develop-e57f6fe-chat-rerun.txt` |
| Newman collection | **31 requests / 70 assertions / 0 failures** | `azure-develop-e57f6fe-newman-summary.txt` |
| PostgreSQL row level | patient `CRITICO`/`EN_CONSULTA`, `sla_minutos=5`, 3 outbox rows published, **0** `requiere_revision` | `azure-develop-e57f6fe-db-rowlevel.txt` |

R-06 used synthetic patient `1fe7056f-3df7-4ad2-ab5f-4ec869b04d4f`, alert
`65823070-3e5c-4dd1-b929-feb71f2379ec` and trace id `7c2e9b41d8a64f30b5e7c1a9f2d6b845`.
The alert was `RESUELTA` after 5.08 minutes; the outbox holds `paciente_registrado`,
`paciente_escalado` (timer-originated, no traceparent) and `paciente_en_consulta`, all published
with `intentos=0`. Four outbox rows carry the trace id. `auditoria_clinica` shows
`REGISTRO → ESCALAMIENTO → ENTRADA_CONSULTA`. The database holds 79 synthetic patients, with
no failed rows.

## Findings

1. **The first security run was 24/25 because of the harness, not the deployment.** The
   `audit.phi` line with `keyId=owner` existed, but on the second of two triage pods; the
   script only read the first pod. `verify-azure-security-stack.ps1:102-110` now aggregates
   logs from every `triage-service-*` pod. The rerun is 25/25. No key value is logged.
2. **No regression found** against the `206ede7` gates: 25/25, 13/13, 35/35 and 31/70/0
   are unchanged. Chat used the real provider on every positive probe; no fixed-notice
   fallback and no transient G-04 rejection appeared in this run.
3. The Newman raw JSON stays in TEMP and is never committed. The summary and all new
   evidence files were scanned for inline credentials with no matches.

## Limits retained

- No green CI for `e57f6fe` beyond the deploy run itself.
- Reviewer keys are absent in the Azure environment; the `403` path remains untested.
- No Jaeger in Azure, so shared-trace evidence is still local-only.
- NetworkPolicies exist but are not enforced (no AKS policy engine). Trivy is report-only.
- This verification does not isolate the behavior of `a4ed4e5` or `508a12d`; it validates
  the deployed artifact as a whole.
