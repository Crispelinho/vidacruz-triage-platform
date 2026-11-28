# ADR-09: Circuit breaker and degraded mode for the Clinical Assistant

- **Status:** Proposed. Not accepted. Option A is the proposed route, amended by the Developer/User on 2026-11-26 with per-node breakers and numeric values (C17). Acceptance of this ADR still depends on the verification section below.
- **Date:** 2026-11-25
- **Deciders:** developer; numeric values and failure classification recorded 2026-11-26, ADR acceptance still pending verification
- **Scope:** C-06. This ADR covers the Resilience4j circuit breaker, timeout and 503 fallback for LLM calls. The relevance gate and citation validation stay in C-06 under RN-05 and R-10, as recorded in `adr-roadmap.md` §2.
- **Accepted sub-decision (developer, 2026-11-25):** the LLM never participates in readiness. An unavailable model returns `503 LLM_UNAVAILABLE` and recovers automatically; breaker values and failure classification remain open (G-05, CA-G-05).
- **Related:** DEC-07, NFR-03, NFR-08, NFR-20, TC-05, R-04, R-10, CA-G-05, CA-G-16, CA-G-22, G-05, G-12, API-04, TST-RES, TST-RAG in [clinical-assistant-service.md](../services/clinical-assistant-service.md), [requirements.md](../requirements.md) and [ADR-05](ADR-05-assistant-llm-provider.md)

## Context

DEC-07 groups the RAG relevance gate, citation validation and circuit breaker in one decision. `adr-roadmap.md` splits their implementation: only the breaker and timeout need this ADR. The gate and citation rules remain C-06 behavior, with the no-match notice returning HTTP 200 and `fuentes: []` (RN-05).

NFR-03 requires a circuit breaker on LLM calls with a 503 fallback. API-04 defines the body `{ "code": "LLM_UNAVAILABLE", "message": "El asistente no está disponible temporalmente" }`, extended with `correlationId` under NFR-20. TC-05 fixes Resilience4j. R-04 records LLM latency and cost during evaluation. G-05 lists timeout values, breaker window, failure rate, open wait, failure definition and retry policy as undecided. CA-G-05 adds 5xx and 429 responses as candidate failure types, especially if ADR-05 chooses a local GPU model whose queue can saturate.

DER-01 requires startup validation of required configuration, including the provider key when the chosen provider uses one (G-12). **Readiness rule (developer, 2026-11-25): the LLM never participates in the readiness gate.** At runtime, D0 §13 says LLM status must not make the Assistant unready: the fallback is intentional and LLM state is reported as a metric, not as a readiness check (CA-G-22). If the configured model is unavailable at startup, the service may become ready; LLM-dependent endpoints return `503 LLM_UNAVAILABLE`, the provider state is recorded, and recovery is automatic when the provider returns. A startup check of the endpoint and model is a separate check under CA-G-22, not a readiness gate; ADR-05 states the same rule. Retrieval, projection and LLM failures are different; the retrieval error code is not defined (G-05), stays with C-06, and must not be conflated with `LLM_UNAVAILABLE`.

## Decision drivers

- NFR-03: deterministic 503 with a clear message when the circuit is open or a call times out.
- No clinical content in fallback responses and no logging of question or answer text (CA-G-16).
- No retry storm against a saturated or unavailable model (R-04).
- Configurable timeout, window, failure rate and open wait (NFR-08).
- Testability with a mocked LLM (NFR-15, TST-RES).
- Metrics use bounded tags such as `outcome` (NFR-07, NFR-19).

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Resilience4j circuit breaker with timeout and no retries** | One breaker around the LLM port; configurable timeout, sliding window, failure rate and open wait; failure types counted; fallback returns 503 `LLM_UNAVAILABLE` | Matches TC-05 and NFR-03; deterministic; protects a saturated model; simple to test with a mock | Values and the full failure classification are still pending; an open circuit rejects calls until half-closes | **Proposed**, not rejected. Acceptance depends on G-05 and CA-G-05 |
| **B. Retry with exponential backoff before opening the breaker** | Retry transient failures up to a configurable limit | Can recover from a brief transient outage | Multiplies latency and GPU load; conflicts with the G-05 proposal of no retries; increases R-04 | Rejected: availability gain does not justify the latency and load cost |
| **C. Serve a cached or generated answer without the LLM** | Return a previously seen answer when the model is unavailable | Keeps the endpoint answering | May return stale or unsupported clinical content; violates RN-05 citation validation and R-10 | Rejected: patient-safety risk is not acceptable |
| **D. Remove the Assistant from readiness when the LLM is down** | Treat the model as a readiness dependency | Prevents requests reaching a degraded service | Conflicts with D0 §13 and CA-G-22; reduces availability; Triage independence is already covered by NFR-04 | Rejected: the fallback is intentional and must not become a readiness gate |

## Decision

**Proposed: Option A.** Wrap the LLM port with one Resilience4j `CircuitBreaker` and timeout configuration. Do not retry LLM calls. Count a failure when a call times out, fails to connect, or returns a failure type classified under CA-G-05; 429 and 5xx responses count as failures under D09-03; 400 domain/validation errors do not. When the circuit is open or a timeout occurs, return HTTP 503 with `code` `LLM_UNAVAILABLE` and a NFR-20 body including `correlationId`. Missing required configuration fails startup under DER-01 and G-12; a model unavailable at startup is not a readiness failure: the service may become ready, LLM-dependent endpoints return 503, the provider state is recorded and recovery is automatic when it returns (CA-G-22, developer 2026-11-25). A runtime provider outage also returns 503 and does not make the service unready. Record LLM state through `vidacruz.llm.latency` with an `outcome` tag, and never log question or answer text (CA-G-16). Retrieval failures stay with C-06 and G-05, not this ADR, and do not use `LLM_UNAVAILABLE`. The relevance gate remains in C-06. This is a proposal, not an accepted decision.

### Developer decision recorded 2026-11-26 (C17 — per-node breakers and numeric values)

Option A is amended from one breaker around the LLM port to **one independent Resilience4j breaker per chain node** (`ollama`, `gemini`, `opencode`), each taken from the same `CircuitBreakerRegistry`. The recorded values are:

| Setting | Value | Scope |
|---|---|---|
| Timeout | `15s` per provider, `45s` worst case for three sequential nodes | `rag.llm.timeout` |
| Sliding window | `10` calls, count-based | per node |
| Minimum calls | `10` | per node |
| Failure rate threshold | `50%` | per node |
| Slow-call rate threshold | `100%` | per node |
| Slow-call duration | `20s` | per node |
| Open wait | `30s` before `HALF_OPEN` | per node |
| Permitted calls in half-open | `3` | per node |

A node failure caused by timeout, connection error, HTTP `429` or HTTP `5xx` fails over to the next node. This classifies `429` and `5xx` as failures, closing the CA-G-05 question for this chain. **Cross-provider failover is not a retry**: no node is called twice for one request, so the "Do not retry LLM calls" rule of Option A is preserved. An open breaker yields `CallNotPermittedException`, which is mapped to `LLMUnavailableException` and causes failover to the next node. When all nodes fail, the endpoint returns HTTP 503 with `code = LLM_UNAVAILABLE` and `correlationId`; the Java exception is `LLMUnavailableException`. The `modelo_usado` field reports the exact responding node as `node/model`, or `none` on terminal failure. The LLM remains excluded from readiness.

These values are recorded by the Developer/User and implemented in `application.yaml`; they do not by themselves accept this ADR, close G-05, or authorize fault-injection execution.

## Consequences

- Positive: the fallback is deterministic, no clinical content is generated during degradation, and a saturated node is protected from retry storms; a degraded node yields to the next configured node.
- Negative: an open circuit keeps returning 503 until it half-closes; the recorded numeric values still have to be validated by TST-RES fault injection before acceptance.
- C-06 keeps ownership of RN-05 gate and citation rules; this ADR owns only the LLM failure path.
- C-11 reports LLM state (startup and runtime) as a metric/health detail and excludes it from readiness; the separate startup check remains under CA-G-22. ADR-05 no longer proposes "service not ready" for an unavailable model.
- ADR-05 was aligned on readiness (2026-11-25): an unavailable model returns 503 and does not remove the service from readiness. The failure-type classification for 429/5xx remains pending under CA-G-05 and this ADR.

## Risks and mitigations

- **R-04 (LLM latency and cost):** timeouts, no retries, a mock in tests and configurable values under NFR-08.
- **R-10 (anti-hallucination):** the gate and citation validation remain in C-06; fallback never generates clinical text.
- **CA-G-05 (failure types):** classify 5xx and 429 after verifying the chosen provider behavior; do not assume them.
- **G-05 (numeric values):** set timeout, window, failure rate and open wait before acceptance.
- **CA-G-16:** logs and metrics carry no question, answer or free-text patient data.

## Verification

- **TST-RES:** a mocked LLM failure opens the circuit; the next call is rejected without invoking the provider and returns 503 `LLM_UNAVAILABLE`.
- **TST-RES:** a slow mock beyond the timeout returns 503 and does not hang the request.
- **TST-RAG boundary:** the no-match question returns the fixed notice with `fuentes: []` and zero LLM invocations; ownership of the gate remains in C-06.
- **NFR-20 evidence:** the 503 body contains `code`, `message` and `correlationId`.
- **NFR-07/NFR-19 evidence:** `vidacruz.llm.latency` exposes bounded `outcome` tags and no patient data.
  Implementation note (2026-11-28): the meter shipped as `assistant.llm.latency`; breaker state is published as
  `resilience4j.circuitbreaker.state`.
- The evidence belongs in the `assistant-llm-resilience` change of `implementation-roadmap.md` (T-14, T-15).
