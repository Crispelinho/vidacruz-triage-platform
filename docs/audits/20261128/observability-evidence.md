# Observability evidence — Grafana, Prometheus, alerts and Jaeger

- **Date:** 2026-11-28, 09:25–09:52 local (GMT-5).
- **Commit under test:** `87cf99a` (`origin/develop`).
- **Scope:** local Docker Compose only. No Azure and no Kubernetes namespace was touched. No application code or
  observability configuration was changed.
- **Verdict:** metrics collection and cross-service tracing work, and no sensitive text was found in spans. **Four
  checks fail** (Grafana Jaeger datasource, two dashboard panels with wrong numbers, one wrong unit), failed requests
  are not marked as errors in most spans, and **there are no alert rules at all**.

## 1. Where the evidence comes from

| Id | Compose project | What it ran | Data source |
|---|---|---|---|
| **A** | `vidacruz-e2e` (ports 13000, 19090, 16687, 18081, 18082) | Full stack. Created by another agent from a clean `87cf99a` checkout; BuildKit recorded revision `87cf99a` for both builds. SLA demo override, sampling `1`, `RAG_LLM_CHAIN=ollama`. | That agent's Compose E2E run (29 PASS / 0 FAIL, 09:25–09:28). Read only. |
| **B** | `vidacruz-obs` (ports 23000, 29090, 26686, 28082), created for this audit and removed afterwards | Assistant only: configuration from `87cf99a`, the assistant image of stack A (`sha256:5d789726…`), a **loopback LLM stub** instead of a model, Kafka consumer disabled, JVM capped at 384 MiB. Triage and Kafka were not started because the host was short of memory. | [`generate_traffic.py --assistant-only`](observability/tools/generate_traffic.py), 6 minutes, 09:40–09:46. |

Reading stack A added a few requests of its own to it: dashboard queries, Jaeger searches and one actuator call per
service with and without a key (so one extra `401` per service in A's HTTP metrics and traces).

Screenshots named `e2e-*` come from A and `stub-*` from B. Sanitized machine-readable results:
[`evidence-e2e-stack.json`](observability/evidence-e2e-stack.json),
[`evidence-obs-stack.json`](observability/evidence-obs-stack.json),
[`stub-traffic-summary.json`](observability/stub-traffic-summary.json).

## 2. Results

| # | Check | Result | Evidence |
|---|---|---|---|
| P-1 | Prometheus targets of both services are `up` | **PASS** (A) | [e2e-02](observability/e2e-02-prometheus-targets.png). In B `triage-service` is `down` as expected: it was not started. |
| P-2 | Actuator Prometheus endpoint needs a key (`401` without, `200` with) | **PASS** | both JSON files, `actuators` |
| P-3 | `triage_patients_classified_total` | **PASS** | A: 7 = the six E1–E6 cases plus the traced registration. [e2e-05](observability/e2e-05-prometheus-custom-metrics.png) |
| P-4 | `triage_patients_escalated_total` | **PASS** | A: 6 across four levels at 09:33, increasing as short demo SLAs expire |
| P-5 | `triage_alerts_acknowledged_total` | **PASS** | A: 1. The rejected duplicate acknowledgement did not increment it. |
| P-6 | `assistant_answers_total`, `assistant_llm_latency_seconds`, `assistant_urgency_events_total` | **PASS** | A: `unavailable`=2, `timeout`=2, `applied`=15. B matches the client exactly: `answered`=18, `no_evidence`=17, `unavailable`=5, `invalid_citation`=4; attempts `success`=22, `server_error`=5. |
| P-7 | No key or test text in the metrics exposition | **PASS** | `forbiddenHits`=0 |
| P-8 | NFR-07 "DB and Kafka metrics" | **PARTIAL** | Present: `r2dbc_pool_*`, `hikaricp_*`, `kafka_consumer_*`, `spring_kafka_template_*`, `lettuce_*`. Absent: `kafka_producer_*`, any outbox metric, any circuit-breaker metric. |
| G-1 | Grafana Prometheus datasource | **PASS** | [e2e-08](observability/e2e-08-grafana-prometheus-datasource-health.png) |
| G-2 | Grafana Jaeger datasource | **FAIL** | Finding F-1. [e2e-07](observability/e2e-07-grafana-jaeger-datasource-health.png), [stub-04](observability/stub-04-grafana-jaeger-datasource-test.png), [stub-05](observability/stub-05-grafana-explore-jaeger-search.png) |
| G-3 | Provisioned dashboard loads for the anonymous viewer, 22 panels, no query errors | **PASS** | [e2e-01](observability/e2e-01-grafana-dashboard.png), [stub-01](observability/stub-01-grafana-dashboard.png) |
| G-4 | Panels show correct data | **FAIL** for panels 11, 20, 22; partial for 4, 14, 19 | Section 3 |
| AL-1 | Prometheus alert rules | **FAIL — none exist** | [e2e-03](observability/e2e-03-prometheus-rules.png), [e2e-04](observability/e2e-04-prometheus-alerts.png) |
| AL-2 | Grafana alert rules | **FAIL — none exist** | [e2e-06](observability/e2e-06-grafana-alert-rules.png) |
| J-1 | Jaeger lists both services | **PASS** | `triage-service`, `clinical-assistant-service` (plus `jaeger` itself) |
| J-2 | One trace spans both services through outbox → Kafka → consumer | **PASS** with gaps (F-6) | All 7 registrations of the E2E run. [e2e-12](observability/e2e-12-jaeger-search.png), [e2e-09](observability/e2e-09-jaeger-cross-service-trace.png), [e2e-10](observability/e2e-10-jaeger-consumer-span-attributes.png) |
| J-3 | Spans carry no patient names, complaints, prompts, answers or keys | **PASS** (limits in section 8) | Section 5 |
| J-4 | Failed operations are marked as errors in traces | **FAIL** | Finding F-4. [e2e-11](observability/e2e-11-jaeger-llm-timeout-span.png) |

## 3. Dashboard panels

Dashboard `vidacruz-nfr07`, range of 15 minutes. `DATA` = non-zero series, `ZERO` = series present, all zero at
collection time, `EMPTY` = no series. The exact PromQL of every panel is stored per panel in both JSON files and in
[`vidacruz.json`](../../../observability/grafana/dashboards/vidacruz.json). No query returned an error.

| # | Panel | Query (abridged) | A | B | Verdict |
|---|---|---|---|---|---|
| 1 | Pacientes clasificados | `sum(increase(triage_patients_classified_total[$__range]))` | DATA | EMPTY¹ | PASS |
| 2 | Escalados por SLA | `sum(increase(triage_patients_escalated_total[$__range]))` | DATA | EMPTY¹ | PASS |
| 3 | Alertas reconocidas | `sum(increase(triage_alerts_acknowledged_total[$__range]))` | DATA | EMPTY¹ | PASS |
| 4 | Escalados sin reconocer | escalated − acknowledged in range, floored at 0 | DATA | EMPTY¹ | PARTIAL: a difference over the window, not the number of pending alerts |
| 5 | Clasificación y escalado por nivel | `sum by (level) (rate(...[1m]))` ×2 | DATA | EMPTY¹ | PASS |
| 6 | Alertas reconocidas por nivel | `sum by (level) (rate(triage_alerts_acknowledged_total[1m]))` | DATA | EMPTY¹ | PASS |
| 7 | Preguntas respondidas | `increase(assistant_answers_total{outcome="answered"})` | ZERO² | DATA (18.1 vs 18) | PASS |
| 8 | Sin protocolo | `...{outcome="no_evidence"}` | ZERO² | DATA (17.1 vs 17) | PASS |
| 9 | Modo degradado (503) | `...{outcome="unavailable"}` | DATA (2) | DATA (5) | PASS |
| 10 | Desenlace de consultas | `sum by (outcome) (rate(assistant_answers_total[1m]))` | DATA | DATA | PASS |
| 11 | LLM provider latency p50/p95 | `histogram_quantile(... assistant_llm_latency_seconds_bucket[5m])` | DATA | DATA | **FAIL** (F-2) |
| 12 | Eventos de urgencia procesados | `sum by (result) (rate(assistant_urgency_events_total[1m]))` | DATA | EMPTY³ | PASS |
| 13 | Latencia HTTP p95 por servicio | `histogram_quantile(0.95, ... http_server_requests_seconds_bucket)` | DATA | DATA | PASS |
| 14 | Respuestas HTTP 4xx/5xx | `rate(http_server_requests_seconds_count{status=~"4..\|5.."}[1m])` | ZERO⁴ | DATA | PARTIAL (F-5) |
| 15 | LLM attempts by outcome | `increase(assistant_llm_latency_seconds_count[$__range])` | DATA | DATA | PASS |
| 16 | Kafka consumer lag by partition | `max by (partition) (kafka_consumer_fetch_manager_records_lag{topic="triaje_eventos"})` | ZERO (3 partitions, no lag) | EMPTY³ | PASS |
| 17 | JVM heap memory | `sum by (application) (jvm_memory_used_bytes{area="heap"})` | DATA | DATA | PASS |
| 18 | Process CPU | `max by (application) (process_cpu_usage)` | DATA | DATA | PASS |
| 19 | PostgreSQL pool connections | `r2dbc_pool_{acquired,idle,pending}_connections` | DATA | DATA | PARTIAL (F-7) |
| 20 | Redis command latency | `rate(sum) / clamp_min(rate(count), 1)` | DATA | DATA | **FAIL** (F-3) |
| 21 | HTTP p95 by route | route-level `histogram_quantile` | DATA | DATA | PASS |
| 22 | Escalations and acknowledgements (per minute) | `sum(increase(...[1m]))` ×2 | DATA | EMPTY¹ | **FAIL**: values are events per minute but the unit shows `req/s` |

¹ Triage not started in B. ² No reachable model and no out-of-corpus question in A. ³ Consumer disabled in B.
⁴ ZERO when queried at 09:28; the 09:29 screenshot shows one non-zero series.

Cosmetic: `increase()` extrapolates, so stat panels show values such as `2.02` or `18.1` for integer counts; event
rates in panels 5, 6, 10 and 12 are labelled `req/s`; several table legends are cut off at panel height.

## 4. Alert rules

**There are no monitoring alert rules in the repository or in the running stacks.**

- No rule file, `rule_files`, `alerting` or Alertmanager entry in `observability/prometheus/prometheus.yml`.
- Prometheus `/api/v1/rules` returns no groups; `/api/v1/alertmanagers` reports no active Alertmanager.
- No `observability/grafana/provisioning/alerting/`; Grafana `/api/v1/provisioning/alert-rules` returns `[]`.

These are different from the application's **SLA alerts** (`GET /triage/alertas`, the SSE stream and
acknowledgement, RF-03/RF-06). Those are domain records for the medical team; in A's E2E run they were created,
acknowledged, protected against a duplicate acknowledgement and streamed. They do not notify an operator about the
platform: nothing fires if a target goes down, the LLM stays unavailable, consumer lag grows or the outbox stops
publishing. [ADR-03](../../adr/ADR-03-transactional-outbox.md) lists "Alerting is configured" for the outbox
failure threshold as an unchecked criterion, and there is no outbox metric to alert on.

## 5. Traces and span hygiene

**Cross-service trace** `a4af10a181574c64b1bbfc2c12f43404` (A): `http post /triage/pacientes` (55.8 ms) → security
spans → `secured request` → **`triaje.eventos receive`** in `clinical-assistant-service`, starting 1.19 s later. The
Jaeger "Incomplete" badge is expected: the E2E client sends a `traceparent` whose parent span it never exports.

**What was searched** (case-insensitive, over the raw trace JSON):

| Stack | Traces / spans | Terms with zero hits |
|---|---|---|
| A | 266 / 1013 | the E2E run's synthetic names and complaints, acknowledging user, question topic and corpus words, `nombre_completo`, `queja_principal`, prompt delimiters, `x-api-key`, both local API keys |
| B | 178 / 913 | `ZZCANARY` markers placed in the question and in the stub answer, corpus words, prompt delimiters, `x-api-key`, both local API keys |

B covers the **successful** LLM path: the `chat llama3.1:8b` span carries only `gen_ai.*` operation, system, model,
finish reason and token counts ([stub-02](observability/stub-02-jaeger-llm-success-span.png),
[stub-03](observability/stub-03-jaeger-llm-success-attributes.png)). Spring AI prompt and completion logging is not
enabled in the configuration.

Attributes seen: HTTP method, status, outcome, route templates and `http.url`; Spring Security scopes and decisions;
Kafka peer, client id and type; `gen_ai.*`; scheduler class and method; resource attributes.

Observations, not failures of this check:

- `http.url` holds the **raw path including patient and alert UUIDs** (for example
  `/triage/pacientes/<uuid>/estado`). They are pseudonymous identifiers, not names; metric labels use templates.
- When the provider answers with an HTTP error, the span status message contains the exception text with the provider
  URL. It held no sensitive data with the stub.

Extra check: the logs of both services in A and of the assistant in B had no hit for the same names, topics, keys or
canaries.

## 6. Findings and proposed fixes (not applied)

| Id | Severity | Finding | Proposed fix |
|---|---|---|---|
| F-1 | High | **Grafana cannot use Jaeger 2.23.0.** `/api/services` and `/api/traces?service=` return `404`, so the datasource health check fails (`cannot unmarshal number into … ServicesResponse`) and Explore search fails with `404`. Opening a trace **by ID still works** ([stub-06](observability/stub-06-grafana-explore-jaeger-trace-id.png)). | Pin Jaeger to a release that serves the legacy API in `docker-compose.yml` and `k8s/local/local-jaeger.yaml`. Checked here: `2.10.0` answers `/api/services` with `200` and a temporary Grafana 12.1.1 datasource pointing at it reported "Data source is working" (empty instance; search with data not tested). Alternative: a Grafana release that speaks Jaeger's v3 API (not tested). |
| F-2 | Medium | **LLM latency panel overstates latency.** Timeouts that lasted 15.04 s on average (max 15.08 s) show p50 = 22.5 s and p95 = 29.3 s, because the node timeout is 15 s and the next bucket is 30 s. In B, successful calls peaked at 2.28 s while p95 showed up to 4.85 s (bucket 2–5 s). | In `LlmCallMetrics`, add buckets just above the timeout (for example 16 s and 20 s) and between 2 s and 5 s. |
| F-3 | Medium | **Redis latency panel understates latency** whenever traffic is below one command per second: `clamp_min(rate(count), 1)` divides by 1. Measured in A: 0.022 ms shown vs 1.24 ms real; 0.33 vs 2.92 ms; 0.16 vs 2.19 ms. | Divide by the real rate and drop idle samples: `sum(rate(..._sum[1m])) / (sum(rate(..._count[1m])) > 0)`. |
| F-4 | Medium | **Failed requests look successful in traces.** HTTP server spans that returned `503` have no error status, and timed-out `chat` spans are `OK` with `response.model = none`. Provider HTTP `500` responses are marked as errors. A had 0 error spans out of 1013 despite two LLM timeouts. | Mark `5xx` server observations and cancelled or timed-out provider calls as errors, with a message that never includes question or answer text; add a test. |
| F-5 | Low | The first occurrence of a new `status`/`uri` series is invisible to `rate()`, so isolated 4xx/5xx errors can read as zero. | Use a stat or table over `increase()` with a note, or pre-register the relevant status series. |
| F-6 | Low | The trace has **no span for the outbox publication or the Kafka producer**, and no PostgreSQL, pgvector or Redis spans; the 1 s+ outbox delay is an unexplained gap. SLA escalations start from a timer and are not linked to the registration (documented behaviour). | Add a publish observation that continues the stored `traceparent`; consider R2DBC/JDBC/Lettuce observations. |
| F-7 | Low | The pool panel shows only R2DBC. The assistant queries pgvector through **JDBC/Hikari**, which is not displayed; its R2DBC pool sits idle. | Add `hikaricp_connections_{active,idle,pending}`. |
| F-8 | Medium | **No alerting** (section 4), no outbox backlog or failure metric, no circuit-breaker state. | Decide on Prometheus rules or Grafana provisioned alerting (candidates: target down, 5xx ratio, LLM unavailable ratio, consumer lag, outbox pending age and `requiere_revision`); expose the outbox and breaker metrics first. |
| F-9 | Low | Prometheus and Grafana exist only in Compose; `k8s/local` deploys Jaeger only. | State this in the docs, or add a local monitoring overlay. |
| F-10 | Info | The scraper uses the owner API keys. | Use a dedicated key with only `metrics:read` (already supported by `*_API_KEYS`). |

## 7. Documentation that does not match behaviour

- [`local-verification.md`](../../demo/local-verification.md) §8 says the Grafana Jaeger datasource can query the
  trace id. That holds for lookup by ID only (F-1).
- [`enunciado-compliance-matrix.md`](../../demo/enunciado-compliance-matrix.md) marks observability `PASS` and names
  `triage.classification.total` and `triage.escalations.total`. The real meters are `triage.patients.classified` and
  `triage.patients.escalated` (plus `triage.alerts.acknowledged`).
- [`ADR-09`](../../adr/ADR-09-circuit-breaker-degraded-mode.md) names `vidacruz.llm.latency`; the meter is
  `assistant.llm.latency`.

## 8. Not verified

- **No real model.** A's Ollama calls only timed out; B used a stub. Gemini, OpenCode and OpenRouter spans, metrics
  and error messages (including whether a provider URL could carry anything sensitive) were not exercised.
- LLM outcomes `rate_limited`, `rejected`, `network`, `other`; an open circuit breaker; urgency events
  `ignored_older` and `skipped`; triage `5xx` responses.
- Triage data was observed only in A, which used the E2E run's ordinary synthetic text. Canary markers were exercised
  on the assistant only; the full stack variant of the tooling (triage canaries) was **not run**.
- The hygiene check is a text search for known terms plus a review of attribute names and samples. It does not prove
  that no other input could reach a span.
- Kubernetes (local and AKS) and Azure observability.
- The screenshots show a point in time; both stacks are ephemeral and Jaeger stores traces in memory.

## 9. How to reproduce

Needs Docker Desktop and Python 3.12 (standard library only). Run from the repository root. Export the two API keys
using the **local demo defaults declared in `docker-compose.yml`** (they are deliberately not repeated here):

```bash
export OBS_TRIAGE_KEY=...      # default of TRIAGE_API_KEY
export OBS_ASSISTANT_KEY=...   # default of ASSISTANT_API_KEY
T=docs/audits/20261128/observability/tools
python -I $T/llm_stub.py &     # loopback stub on 127.0.0.1:21434, not a real model
```

**Full stack** (recommended on a machine with free memory; not executed in this audit):

```bash
docker compose -p vidacruz-obs --env-file $T/obs.env -f docker-compose.yml -f docker-compose.demo.yml \
  -f $T/compose.obs.override.yml up -d --build --wait
python -I $T/generate_traffic.py --minutes 8 --out traffic.json
python -I $T/collect_evidence.py --traffic traffic.json --out evidence.json
```

**Assistant only** (what B ran; needs an assistant image already built from the commit under test):

```bash
export OBS_ASSISTANT_IMAGE=<image id>
COMPOSE="docker compose -p vidacruz-obs --env-file $T/obs.env -f docker-compose.yml -f docker-compose.demo.yml \
  -f $T/compose.obs.override.yml -f $T/compose.obs.assistant-only.yml"
$COMPOSE up -d --no-build --no-deps assistant-db redis jaeger prometheus grafana
$COMPOSE up -d --no-build --no-deps clinical-assistant-service   # once assistant-db is healthy
python -I $T/generate_traffic.py --assistant-only --minutes 6 --out traffic.json
python -I $T/collect_evidence.py --traffic traffic.json --out evidence.json
$COMPOSE down -v
```

To read another running stack, pass `--grafana-port`, `--prometheus-port`, `--jaeger-port`, `--triage-port` and
`--assistant-port`, and add its synthetic text with `--forbid`. Then open Grafana (`http://127.0.0.1:23000`),
Prometheus (`:29090`) and Jaeger (`:26686`) to compare with the screenshots.
