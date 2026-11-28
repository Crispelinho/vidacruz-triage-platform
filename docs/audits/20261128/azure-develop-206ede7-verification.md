# Azure verification of `develop` 206ede7

## Scope

Deploy `46149735968` was dispatched from `develop` at `2026-11-28T13:27:54Z` on head
`206ede741b216619b3be967ac317376bd1009e0b` and completed `success` at `13:31:50Z` (3m49s).
`develop` advanced to `e77ff63` at `13:36:46Z` when PR #182 was merged, **after** this dispatch:
the Azure deployment verified here does **not** include the Spring AI migration, and no second
deploy was performed as part of this verification. Both deployments run images tagged
`206ede741b216619b3be967ac317376bd1009e0b`.

## Gates

| Gate | Result | Evidence |
|---|---|---|
| Security stack (public API, auth, PHI audit, policies, image) | **25/25** | `azure-develop-206ede7-security-stack.txt` |
| R-06 live flow (health, registration, SLA, ack, SSE, chat, outbox→Kafka) | **13/13** | `azure-develop-206ede7-r6.txt` |
| Chat gate, 7 probes × 5 runs | first **3/7**, rerun **7/7 (35/35)** | `azure-develop-206ede7-chat-first.txt`, `azure-develop-206ede7-chat-rerun.txt` |
| Newman collection | first valid run **31/70/1**, corrected rerun **31/70/0** | `azure-develop-206ede7-newman-summary.txt` |
| PostgreSQL row level | patient `CRITICO`/`EN_CONSULTA`, `sla_minutos=5`, 3 outbox rows published, 0 needing review | `azure-develop-206ede7-db-rowlevel.txt` |

The security harness checks 16 public Swagger/OpenAPI/WebJars and `/ops` health URLs, `401`
without and with a bogus `X-API-Key`, `200` with the owner key, one `audit.phi` line for the
synthetic patient read with `keyId=owner` (never the key value), 7 NetworkPolicies present, and
the deployed image tags. The first execution showed one `FAIL` solely because PowerShell 5.1
mangled the `jsonpath` quoting; the harness was changed to parse JSON and the rerun is 25/25.

R-06 used synthetic patient `59d239c4-6646-4fed-a9ff-eb90c8d3694b`, alert
`e8ea483c-3399-4f73-a22e-8f4164ebcc78` and trace id `7c2e9b41d8a64f30b5e7c1a9f2d6b845`.
The outbox holds `paciente_registrado` with that `traceparent`, `paciente_escalado` without one
(timer-originated, as documented), and `paciente_en_consulta` with its own; all published with
`intentos=0` and `requiere_revision=false`. `auditoria_clinica` shows `REGISTRO →
ESCALAMIENTO → ENTRADA_CONSULTA`; the alert is `RESUELTA` after 5.10 minutes. Kafka carried the
same trace id. Jaeger is absent in Azure, so shared-trace evidence remains local-only.

## Findings

1. **Transient G-04 rejections.** The first chat gate produced 5 runs with the fixed notice and
   `modelo_usado=none` (30/35 runs). Four `WARN` lines at `14:00:19`, `14:00:40`, `14:00:47` and
   `14:01:28Z` report `G-04: rejected an unparseable LLM answer ... LLM answer is not valid JSON`.
   `JacksonGroundedAnswerDecoder.java:39-42` accepts strict JSON and unwraps one Markdown fence
   (`:75-84`); `RAGExceptionHandler.java:67-75` then returns the fixed notice with `200`. An
   immediate rerun was 35/35 with `modelo_usado=gemini/gemini-3.5-flash-lite` on every positive
   probe. This is provider-response variance, not a deployment regression; no code change was
   made. `e77ff63` does not modify `RAGExceptionHandler`, so its merge is not presented as a fix.
2. **Newman export encoding.** The first valid run failed one assertion (`NO_URGENTE_2` →
   `NO_URGENTE`) because the local no-secrets export had been written with PowerShell 5.1
   `Get-Content` without UTF-8: `á` became `Ã¡` (U+00C3 U+00A1 instead of U+00E1). The collection
   was regenerated as UTF-8 without BOM (U+00F3 confirmed in `Renovación`) and the rerun passed
   31/70/0. This was a harness artifact, not an API defect.
3. Trivy in the green `f4e17ab` workflows reported 19 findings in triage (15 HIGH / 4 CRITICAL)
   and 23 in the assistant (18 HIGH / 5 CRITICAL), all in `app/app.jar`; base images were clean.
   It is report-only by design, so it does not block the deploy.

## Limits retained

- Reviewer keys are not configured in the Azure environment; the `403` scope path remains untested.
- NetworkPolicies exist but are not enforced: AKS has no policy engine. No IP allow-list or rate
  limit, PII redaction is heuristic, TLS for Kafka/Redis and mTLS remain deferred.
- No Jaeger in Azure; no shared-trace evidence there.
- ADR-11 and ADR-05 remain `Proposed`; cost measurement, full runtime spike, secret rotation/scan
  and Azure shared trace are still open. `main` and tag `v1.0.6` were not changed.
- The raw Newman JSON and the collection with real keys stay in `TEMP` and are not committed.

## Evidence files

- `azure-develop-206ede7-security-stack.txt`
- `azure-develop-206ede7-r6.txt`
- `azure-develop-206ede7-chat-first.txt`
- `azure-develop-206ede7-chat-rerun.txt`
- `azure-develop-206ede7-db-rowlevel.txt`
- `azure-develop-206ede7-newman-summary.txt`
