# ADR-09: Degraded Mode and Circuit Breaker (Propuesta)

**Status:** Proposal (ready for review)  
**Date:** 2026-11-25  
**Deciders:** Developer  
**Context:** Change 3 (C-06 Clinical Assistant RAG)

---

## Context

**Forces and constraints (D1):**
- RF-04-A: Answer clinical questions from protocol corpus using LLM + Spring AI 1.1.1
- RN-05: If no relevant evidence exists, return fixed notice (no generation without evidence)
- NFR-03: If LLM unavailable, return 503
- R-04, R-10: LLM unavailability risk must be mitigated

**Technology stack (ADR-05, DECIDED):**
- Spring AI 1.1.1
- Ollama (local LLM engine)
- Resilience4j (circuit breaker library, included in Spring Cloud 2025.0.3)

**Decision drivers:**
1. **Availability:** Circuit breaker must detect LLM failure and prevent cascading timeout/exhaustion
2. **Graceful degradation:** Return 503 (unavailable) rather than timeout or partial response
3. **Clinical safety:** Fallback must NOT degrade quality or silently change behavior
4. **Observability:** Every response must track which model/provider was used

---

## Alternatives considered

### Option 1: Circuit Breaker only (Resilience4j)
**Description:**
- Resilience4j detects repeated failures (call threshold + timeout)
- After N failures or timeout, circuit opens → return 503 immediately
- No fallback: Ollama unavailable → user gets 503

**Pros:**
- Simple, single point of configuration
- No cross-model complexity
- Clear semantics: works or returns 503

**Cons:**
- Complete service degradation when Ollama fails
- No attempt to use alternative provider/model
- Loses opportunity for partial availability

**Why not recommended:**  
Acceptable only if SLA tolerates Ollama downtime as full service loss. Clinical context suggests attempt at continuity is preferable.

---

### Option 2: Circuit Breaker + Router to Alternative Model
**Description:**
- Resilience4j circuit breaker on primary Ollama model (A)
- When circuit opens, router redirects to secondary Ollama model (B)
- If model B also fails, return 503
- Both models run in same Ollama instance or same docker-compose service

**Architecture:**
```
Client Request
  ↓
LLM Interface (abstraction)
  ↓
  ├─ [Circuit Breaker #1] → Ollama Model A
  │  └─ OPEN → route to Model B
  │
  └─ [Circuit Breaker #2] → Ollama Model B
     └─ OPEN → 503 Service Unavailable
```

**Pros:**
- Leverages same infrastructure (Ollama service)
- Model fallback is lightweight (no additional infra)
- Can test against different models without deployment cost
- Maintains single Ollama instance for ops simplicity

**Cons:**
- If Ollama instance itself is down, both models fail
- Model equivalence must be verified clinically
- Adds router logic and second circuit breaker
- Model selection must be traceable (which model answered?)

**When it works:**
- Ollama instance is healthy, but Model A is overloaded
- Model A has a bug, Model B doesn't
- Canary test: Model B deployed, router tries A first, B as fallback

**When it fails:**
- Ollama service crashes (both models unavailable)
- Docker container dies, network issue, insufficient memory
- Not a mitigation for infrastructure failure

---

### Option 3: Circuit Breaker + Infrastructure Health Check
**Description:**
- Resilience4j circuit breaker on Ollama call
- Separate health check on Ollama service (before attempting call)
- Health check detects instance availability (CPU, memory, connectivity)
- If health check fails, return 503 without attempting LLM call

**Architecture:**
```
Client Request
  ↓
Health Check (Ollama service status)
  ├─ UNHEALTHY → 503 (Service Unavailable, reason: LLM infra down)
  │
  └─ HEALTHY → [Circuit Breaker] → Ollama Model A
     ├─ OPEN/TIMEOUT → 503 (LLM model failure)
     └─ OK → Response
```

**Pros:**
- Distinguishes infra failure (unfixable) from model failure (maybe rotatable)
- Fail-fast: don't wait for circuit breaker timeout if infra is dead
- Clearer observability: health vs. circuit breaker as separate concerns

**Cons:**
- Adds health check latency (must be fast, <100ms)
- If health check is wrong, misroutes traffic
- Doesn't add redundancy (still single Ollama instance)

**When it works:**
- Ollama container is dead/restarting: quick fail instead of timeout
- Network to Ollama is broken: detected before circuit timeout

---

## Recommended Decision

**Primary:** Option 2 (Circuit Breaker + Router to Alternative Model)  
**Supplementary:** Option 3 health check can be added later (Phase P1 robustness)

**Rationale:**
- Uses same infrastructure (single Ollama instance) → no Azure deployment cost
- Provides fallback path for model-specific failures (overload, bug, compatibility)
- **Explicit constraint:** Infrastructure failure (Ollama service down) is **not** mitigated; return 503
- Clinical fallback is verifiable and testable within Day 2 scope
- Aligns with 3-day delivery: implement primary path Day 2, health check as P1

---

## Consequences

**Positive:**
- LLM unavailability returns 503 instead of timeout
- Model fallback provides soft degradation if Model A fails
- Traceability: each response logs which model answered

**Negative:**
- Adds router and second circuit breaker complexity
- Requires equivalence validation for Model B
- If Ollama infrastructure is down, cannot recover (no P0 mitigation)
- Model B must be pre-validated and deployed (not on-the-fly selection)

**Forces on other capabilities:**
- C-06 must implement model tracking in response metadata
- Logging must capture circuit breaker state and model selection
- Tests must validate both primary and fallback paths

---

## Risks and mitigations

| Risk | Description | Mitigation |
|------|-------------|-----------|
| **Model equivalence failure** | Model B answers differently, clinical quality degrades | Validation matrix (§Verification): test PROTO queries and edge cases on both models before accepting Model B |
| **Silent model switch** | User doesn't know Model A vs B answered | Response includes `modelUsed` field; logged in correlation ID |
| **Router complexity** | New code path, testing burden | Implement as simple Spring bean with explicit routing rules; test both paths in T-15 |
| **Infrastructure not mitigated** | Ollama service down = both models down | Document this limit clearly; health check as P1 mitigation |
| **Latency increase** | Fallback attempt adds latency | Health check (Option 3) added in P1 to avoid timeout-induced fallback |

---

## Verification

**Acceptance criteria for ADR-09:**
1. Circuit breaker opens after N failures (Resilience4j config) → no more calls to Ollama
2. When circuit is open, router redirects to Model B
3. When Model B circuit opens, return 503
4. Each response includes `modelUsed: "Model A"` or `modelUsed: "Model B"`
5. Test: Stub Ollama to fail → Circuit opens → response uses Model B → Circuit opens → 503
6. Test: Both models stubbed to fail → 503 within 2 seconds (no timeout)
7. Equivalent behavior: PROTO-001, PROTO-002, diabetes query return same quality on Model B as Model A

**Verification step (Phase):**
- T-15 (Day 2): Circuit breaker + Model B fallback implemented; both circuit breakers tested
- Model equivalence: Validated before accepting this ADR (pre-verify in PROTO corpus)

---

## Gates and dependencies

| Gate | ADR | Requires before | Blocker |
|------|-----|-----------------|---------|
| G-09a | ADR-05 | ADR-09 | ✅ DECIDED: Spring AI 1.1.1 + Ollama |
| G-09b | ADR-09 | T-14, T-15 (Change 3 Day 2) | Needs Model B selection + equivalence validation |

**Model B selection decision:** Outside scope of this ADR. Recommend: Ollama supports multiple models; test both on same instance. Post-acceptance, choose Model B based on latency + quality tradeoff.

---

## Questions for developer approval

Before writing the full ADR-09:

1. **Model B:** Which Ollama model for fallback? (Same family as Model A, or different provider signature?)
2. **Equivalence validation:** Acceptable to validate PROTO queries by hand, or require automated test?
3. **Latency budget:** Current expectation for `/asistente/consulta` timeout before circuit opens?
4. **Logging:** Log every model selection, or only on fallback?
5. **Future flexibility:** Should Option 3 (health check) be pre-designed, or add in P1?

---

**Next:** Await developer input on Q1-Q5, then finalize ADR-09 with selected model and validation matrix.
