# Observability evidence — Grafana, Prometheus, alerts and Jaeger

- **Date:** 2026-11-28, 09:25–09:52 local (GMT-5).
- **Commit under test:** `87cf99a` (`origin/develop`).
- **Scope:** local Docker Compose only. No Azure and no Kubernetes namespace was touched. No application code or
  observability configuration was changed.
- **Audit verdict (before remediation):** metrics collection and cross-service tracing work, and no sensitive text
  was found in spans. **Four checks fail** (Grafana Jaeger datasource, two dashboard panels with wrong numbers, one
  wrong unit), failed requests are not marked as errors in most spans, and **there are no alert rules at all**.
- **After remediation (section 0):** everything above is fixed and verified, including F-1, the Grafana Jaeger
  datasource. Limits and what remains are in section 0.4.

## 0. Remediation and verification (same day)

Sections 1–9 describe the audit of `87cf99a` and are kept as the "before" state. The fixes are separate commits on top
of it.

### 0.1 Status per finding

| Id | Status | Change | Before → after |
|---|---|---|---|
| F-1 | Fixed by pinning | Jaeger `2.20.0` in Compose and `k8s/local` (approved by the repository owner); section 0.5 | Health check and search failing → **working with real data** |
| F-2 | Fixed | Histogram steps at 1.5, 2.5, 3, 4, 7.5, 12.5, 15.5, 16 and 20 s | Timeouts p50/p95 22.5 / 29.3 s → **15.25 / 15.48 s** (real max 15.01 s). Success p95 up to 4.85 s (max 2.28) → at most 2.98 s (max 2.53) |
| F-3 | Fixed | Redis query divides by the real rate and has no sample while idle | 0.022 ms shown for 1.24 ms → real average (promtool test; panel reads 1–3.6 ms) |
| F-4 | Fixed, with one limit | Handled `5xx` answers mark the server span; one `llm attempt <node>` span per provider attempt is failed on error, timeout or cancellation | 0 error spans of 1013 → `HTTP 503` and `LLM provider call failed: timeout` errors. **Limit:** Spring AI still closes its `chat` span as successful on a timeout; the parent attempt span carries the error |
| F-5 | Fixed | The error panel counts responses of the previous minute, including the first one of a new series | Isolated errors read 0 → counted (promtool test) |
| F-6 | Partly fixed | `triaje.eventos publish` producer span, child of the stored `traceparent`; the Kafka header is unchanged | 6 spans with an unexplained gap → 7 spans. PostgreSQL, pgvector and Redis spans were **not** added |
| F-7 | Fixed | Hikari series added to the pool panel | JDBC pool invisible → shown |
| F-8 | Fixed | Outbox gauges, circuit-breaker metrics and seven alert rules | No rules → see 0.2 |
| F-9 | Documented | `k8s/local/README.md` states that monitoring exists only in Compose | No overlay added |
| F-10 | **Not changed** | A `metrics:read` key for the scraper would need a second default key in Compose, that is, one more credential accepted by the local stack. That is an owner decision. | Owner keys still used locally |
| Panel 22 and event rates | Fixed | `short` for per-minute counts, `ops/s` for event rates | `req/s` |
| New: counter colours | Fixed | Stat panels turned red above 80 (Grafana default thresholds), seen once there was more traffic | Green at any volume |
| Documentation (section 7) | Fixed in current documents | Meter names in the compliance matrix and the service document; implementation note in ADR-09; alerts and `promtool` tests in `local-verification.md` | `docs/analysis.md` keeps its planned names as a historical analysis. `local-verification.md` §8 is accurate again and explains the pin |

### 0.2 Alerts

Rules: [`alerts.yml`](../../../observability/prometheus/alerts.yml); tests:
[`alerts.test.yml`](../../../observability/prometheus/alerts.test.yml). No Alertmanager and no notification channel.
Thresholds are demo defaults.

Live run on the isolated full stack, UTC ([timeline](observability/after/alert-timeline.json)): its Kafka broker was
stopped and the LLM stub was made to time out and fail.

| Alert | pending | firing | Quiet / recovery | Live | promtool |
|---|---|---|---|---|---|
| `LlmCircuitBreakerOpen` | 16:52:59 | 16:53:59 | cleared after successful calls closed the breaker | yes | yes |
| `LlmUnavailableRatioHigh` | 16:52:59 | 16:54:57 | quiet during normal traffic; cleared by 17:03:41 | yes | yes |
| `OutboxPublishingStalled` | 16:54:35 | 16:55:34 | backlog 0 and alert cleared by 17:00:52 after the broker returned | yes | yes |
| `HttpServerErrorRatioHigh` | 16:53:59 | 16:56:00 | also fired in the normal run, where injected failures were 5 of about 58 assistant requests | yes | yes |
| `TargetDown` | seen while the services started | not fired | stayed quiet with both targets up | pending only | yes |
| `UrgencyConsumerLagHigh` | – | – | stayed quiet (lag 0) | quiet only | yes |
| `OutboxEventsNeedReview` | – | – | stayed quiet (no flagged rows) | quiet only | yes |

The live run exposed a defect in the first version of the breaker rule: an open breaker turns half-open after 30 s,
which would have reset its one-minute pending period. The rule now matches `open` or `half_open`, with a test.
Consequence: with no traffic a half-open breaker keeps the alert until a call closes it.

Screenshots: [one firing, six quiet](observability/after/02-prometheus-alerts-one-firing.png),
[provoked](observability/after/05-prometheus-alerts-provoked.png),
[recovered](observability/after/07-prometheus-alerts-recovered.png).

### 0.3 Verification

| Check | Result |
|---|---|
| `triage-service` Gradle suite | **188 tests, 0 failures, 0 errors, 0 skipped** |
| `clinical-assistant-service` Gradle suite | **341 tests, 0 failures, 0 errors, 3 skipped** (`GeminiChatVerificationTest`, `GeminiEmbeddingVerificationTest`, `OpenRouterChatVerificationTest`: they need real provider keys) |
| `python -m unittest discover -s observability/tests` | **15 tests, OK**: dashboard queries, units, colours, alert rules with `promtool`, and the pinned Jaeger |
| Full isolated stack built from this branch, synthetic traffic | 22 panels without query errors ([dashboard](observability/after/01-grafana-dashboard.png), [results](observability/after/evidence-after.json)) |
| Span hygiene | **5200 spans, 1288 traces: no canary, corpus word, prompt delimiter or API key**; no hit in the logs of both services. Canaries now cover triage names, complaints and the acknowledging user, closing that gap of section 8 |
| Traces | [publish span](observability/after/03-jaeger-trace-with-publish-span.png), [provider error](observability/after/04-jaeger-failed-attempt.png), [timeout](observability/after/06-jaeger-timed-out-attempt.png) |

**After integrating the newer `origin/develop` (`e57f6fe`, merge `ada3650`).** It changed assistant code comments,
scripts and documents, and nothing under `triage-service`, so only the assistant suite was repeated. **No single full
run on the merged commit was green**, with the host at 100 % CPU from other builds and stacks:

| Run | Result | Cause |
|---|---|---|
| Full suite, first | 336 tests, 1 failure, 3 skipped | `PgvectorVectorStoreContractTest`: its PostgreSQL container did not become ready in time |
| Full suite, second | 341 tests, 7 failures, 3 skipped | all in `CorpusIngestionIntegrationTest`: its Kafka container failed to start, so the context did not load |
| The two affected classes plus `PiiRedactorTest` | **41 tests, 0 failures** | – |

Every non-skipped assistant test therefore passed on the merged commit in at least one run.

The Gradle suites ran from a copy in an ASCII-only path: on this Windows host the test worker cannot load classes from
a path with accented characters. BlockHound is active in both suites, including its negative control; the new outbox
gauges read values refreshed by a non-blocking R2DBC query, never during a scrape.

### 0.4 Behaviour that changes, and what remains

- New metric families: `triage_outbox_*`, `resilience4j_circuitbreaker_*`, `assistant_llm_attempt_seconds*`.
- HTTP server series of handled `5xx` answers now carry `exception="ServerErrorResponse"` and
  `error="ServerErrorResponse"` instead of `none` (seen on a live scrape).
- The scheduled backlog read adds one short trace every 15 s.
- **Still reaching spans unchanged:** exception messages of failures that escape the controllers, and of unhandled
  `4xx` such as an unreadable body (Spring marks those itself), and Spring AI's `chat` error message with the provider
  URL. No sensitive text was seen, but none of it is sanitized. Changing it means replacing framework tracing
  handlers.
- The application never sets `requiere_revision`, retries rows already flagged and writes `FALSE` back on the next
  failure, unlike ADR-03. `OutboxEventsNeedReview` can only react to a manual flag. Not changed.
- Cancellation of a provider call is covered by a unit test only. Real LLM providers were still not exercised, and
  `gemini` and `opencode` emit no Spring AI `chat` span (only the new attempt span).
- Reproduce the alert run: with the full stack of section 9 running, `docker compose -p vidacruz-obs stop kafka`, then
  run [`provoke_alerts.py`](observability/tools/provoke_alerts.py) from its folder with `python` (without `-I`, it
  imports the traffic script), and start Kafka again.

### 0.5 F-1: pinned Jaeger

**Why `2.20.0` and not `2.23.0`.** Jaeger 2.21.0 removed the v1 query endpoints (`/api/services`, `/api/operations`,
search on `/api/traces`). The Jaeger datasource of Grafana 12.1.1 uses them, and no released Grafana fix was found
([grafana-jaeger-datasource#87](https://github.com/grafana/grafana-jaeger-datasource/issues/87), checked
2026-11-28). `2.20.0` is the newest release that serves them.

**What the pin gives up** ([Jaeger changelog](https://github.com/jaegertracing/jaeger/blob/main/CHANGELOG.md)): the
2.21–2.23 hardening, including code-scanning and token-permission clean-ups (2.21), and in 2.23 panic recovery in the
query gRPC and MCP handlers, escaping of index and service names in queries, rejection of empty tenant values, filter
depth checks, an Apache Thrift update and a non-root group for the image. No CVE identifier was listed in the part of
the changelog reviewed; vulnerability scanners were not run. The group is compensated with `user: "10001:10001"` in
Compose and `runAsGroup: 10001` in the overlay. Jaeger remains a local-only tool.

**Upgrade condition:** a Grafana release whose Jaeger datasource uses `/api/v3` for health, services and search;
then raise both images and update [`test_jaeger.py`](../../../observability/tests/test_jaeger.py), which fails if
the pinned image stops serving the endpoints Grafana uses.

**Verification** on the full isolated stack with synthetic traffic ([results](observability/after-jaeger/evidence-jaeger.json)):

| Check | Result |
|---|---|
| Datasource health | `OK` ([screenshot](observability/after-jaeger/01-grafana-jaeger-datasource-test.png)) |
| Service and operation lists | both services listed |
| Explore search, by service and by operation | traces returned ([screenshot](observability/after-jaeger/02-grafana-explore-jaeger-search.png)) |
| Explore by trace id | 7 spans across both services ([screenshot](observability/after-jaeger/03-grafana-explore-jaeger-trace-id.png), [Jaeger UI](observability/after-jaeger/04-jaeger-2.20-trace.png)) |
| Span hygiene | 1281 spans: no canary, corpus word, delimiter or key |
| Local overlay | `kubectl kustomize k8s/local` renders the pinned image and group. **Not applied to a cluster** |

Screenshots and results under `observability/after/` (section 0.3) were taken before this pin, with Jaeger `2.23.0`.

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

**Full stack** (not executed in the first audit; used for the verification in section 0):

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
