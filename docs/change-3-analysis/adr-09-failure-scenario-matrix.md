# ADR-09: Matriz de escenarios de fallo

## 1. Matriz de decisión por tipo de fallo

| # | Tipo de fallo | Causa probable | Detección | Respuesta | Código HTTP | Registrado | Clínicamente seguro? |
|---|---|---|---|---|---|---|---|
| **1** | Model A timeout | Ollama overload / slow | Circuit breaker (5s threshold) | Redirige a Model B | 200/503 | ✅ CB#1 timeout | ✓ Si Model B equivalent |
| **2** | Model A error (500) | Model crash / OOM | Catch exception | Redirige a Model B | 200/503 | ✅ CB#1 failure | ✓ Si Model B equivalent |
| **3** | Model B timeout | Model B overload | Circuit breaker (5s threshold) | Return 503 | 503 | ✅ CB#2 timeout | N/A (error state) |
| **4** | Model B error (500) | Model B crash | Catch exception | Return 503 | 503 | ✅ CB#2 failure | N/A (error state) |
| **5** | Ollama service down | Container crashed / OOM | Connection refused | Redirige a Model B → fails | 503 | ✅ Connection error | ⚠️ **Infrastructure failure** |
| **6** | Network unreachable | Docker bridge down | Timeout or connection refused | Both models fail | 503 | ✅ Network timeout | ⚠️ **Infrastructure failure** |
| **7** | Model loading failed | Model file corrupted | First call fails | Circuit opens immediately | 503 | ✅ CB failure on first call | ⚠️ Deployment error |
| **8** | Embedding model fails | pgvector query failed | Exception in retrieval | Catch before LLM call | 400/500 | ✅ Retrieval error | Depends on failure type |
| **9** | No relevant evidence | Query outside corpus | LLM success, empty results | Return RN-05 notice | 200 OK | ✅ Normal path | ✓ By design |
| **10** | Model A very slow (8s) | Model A CPU bound | Timeout at 5s | Circuit opens, tries Model B | 200/503 | ✅ Timeout+fallback | ✓ Si Model B responsive |
| **11** | Circuit HALF_OPEN recovery attempt | Automatic timeout elapsed | 1 test call succeeds | Circuit closes | 200 OK | ✅ CB recovery | ✓ Normal recovery |
| **12** | Model A hallucinates (bad citation) | LLM quality issue | Citation validation fails | Reject response (future P1) | 400 | ✅ Citation validator | ⚠️ **P1 mitigation** |

---

## 2. Matriz de mitigación por capas

```
┌─────────────────────────────────────────────────────────────────────┐
│                        Failure Mitigation Layers                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│ Layer 1: Retrieval (before LLM call)                                │
│ ├─ Failure: pgvector query fails                                   │
│ ├─ Detection: Exception in SearchRequest.execute()                 │
│ ├─ Response: 500 Internal Server Error (retrieval failure)         │
│ └─ Fallback: None (must succeed or fail)                           │
│    Clinical safety: User sees error, not hallucination              │
│                                                                      │
│ Layer 2: Circuit Breaker #1 (Model A primary)                      │
│ ├─ Failure: Model A timeout or exception                           │
│ ├─ Detection: Timeout (5s) or exception caught                     │
│ ├─ Response: Try Model B immediately (no retry delay)              │
│ ├─ Fallback: Model B attempt                                       │
│ └─ Clinical safety: Model B quality validated pre-deployment        │
│                                                                      │
│ Layer 3: Circuit Breaker #2 (Model B fallback)                     │
│ ├─ Failure: Model B timeout or exception                           │
│ ├─ Detection: Timeout (5s) or exception caught                     │
│ ├─ Response: Return 503 Service Unavailable                        │
│ ├─ Fallback: None (both models failed)                             │
│ └─ Clinical safety: User gets explicit error, not degraded answer  │
│                                                                      │
│ Layer 4: Citation Validation (P1)                                  │
│ ├─ Failure: LLM hallucinated citation                              │
│ ├─ Detection: Citation not in corpus                               │
│ ├─ Response: Reject response, return error                         │
│ ├─ Fallback: None in P0 (accepted in D4 P1)                        │
│ └─ Clinical safety: Prevents unsourced claims                      │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Matriz de recuperación

| State | Current | Trigger | Next | Action | Time |
|---|---|---|---|---|---|
| **CLOSED** | Normal | 3 failures OR 5s timeout | OPEN | Stop calling Model A | Immediate |
| **OPEN** | Failing | 60s elapsed | HALF_OPEN | Allow 1 test call | 60s |
| **HALF_OPEN** | Testing | ✅ Success | CLOSED | Resume normal calls | Immediate |
| **HALF_OPEN** | Testing | ❌ Failure | OPEN | Back to failure mode | Immediate |

**Implicación:** Circuit breaker #1 stays OPEN for 60s after first failure. During that time, all requests route to Model B.

---

## 4. Matriz de trazabilidad (observabilidad)

| Evento | Log level | Campos | Propósito |
|---|---|---|---|
| `circuitBreaker1.onSuccess()` | DEBUG | model, latency_ms, timestamp | Confirm Model A working |
| `circuitBreaker1.onError()` | WARN | model, exception, timestamp | Model A failed; expect fallback next |
| `circuitBreaker1.stateTransition(CLOSED→OPEN)` | INFO | failures, timeout_count, threshold | Circuit opened; fallback active |
| `circuitBreaker1.stateTransition(OPEN→HALF_OPEN)` | INFO | waitDuration | Recovery attempt starting |
| `circuitBreaker1.stateTransition(HALF_OPEN→CLOSED)` | INFO | recovery_success | Circuit recovered |
| `router.fallback()` | WARN | from_model, to_model, reason | Switched to Model B |
| `circuitBreaker2.onError()` | ERROR | model, exception | Model B also failed; 503 coming |
| `response.generated()` | INFO | model_used, latency_ms, citations_count | Response sent; include modelUsed |
| `citation.validated()` | DEBUG | source, matched | Citation tracking (P1) |
| `citation.validation_failed()` | ERROR | source, corpus_query | Hallucination detected (P1) |

**Correlation ID:** Incluir en cada log entry para trazar request end-to-end.

---

## 5. Límites conocidos (no mitigados en P0)

| Límite | Descrición | Impacto | Mitigación |
|---|---|---|---|
| **Ollama infrastructure** | Si Ollama service cae, ambos modelos fallan | 503 Service Unavailable | Health check (Option 3, P1) |
| **Shared instance** | Model B en misma instancia de Ollama que A | Si una app consume recursos, ambas se ralentizan | Separate Ollama instances (Phase P1 / Cloud) |
| **Model equivalence** | Model B calidad no validada vs Model A | Fallback podría degradar respuesta clínica | Pre-validate on PROTO queries before accepting ADR-09 |
| **Model selection** | No auto-discovery; modelos codificados en config | Cambiar modelo requiere redeploy | Accept for now; consider dynamic registry in future |
| **Latency budget** | No timeout para fallback attempt (5s × 2) | Requests pueden tardar ~10s en worst case | Acceptable; monitor SLA breach in Phase P1 |
| **Persistent state** | Circuit breaker state in-memory per instance | Scales only to 1 instance during evaluate | Add distributed circuit state (Phase Cloud/Scale) |

---

## 6. Escenarios de prueba (Phase T-15)

### Test: Ambos circuitos CLOSED, respuesta OK
```
Given: Both models operational
When: POST /asistente/consulta
Then:
  ✅ Response 200 OK
  ✅ modelUsed = "Model A"
  ✅ Latency < 2s
```

### Test: Model A timeout, fallback to Model B
```
Given: Model A configured to timeout (5s), Model B responsive
When: POST /asistente/consulta
Then:
  ✅ Request times out on Model A
  ✅ Response 200 OK with Model B answer
  ✅ modelUsed = "Model B"
  ✅ Latency ~5s (wait for timeout) + Model B latency
  ✅ WARN log: "Model A failed, attempting Model B"
```

### Test: Both models fail, 503
```
Given: Both Model A and Model B stubbed to throw exception
When: POST /asistente/consulta
Then:
  ✅ Response 503 Service Unavailable
  ✅ ERROR log: "Both models failed"
  ✅ Retry-After header present
```

### Test: Circuit breaker recovery
```
Given: Circuit #1 OPEN (Model A failed 3+ times)
And: 60s elapsed
When: Next POST /asistente/consulta
Then:
  ✅ Circuit moves to HALF_OPEN
  ✅ 1 test request sent to Model A
  If: Model A succeeds
    ✅ Circuit moves to CLOSED
    ✅ Subsequent requests use Model A
  If: Model A fails again
    ✅ Circuit stays OPEN
    ✅ Request routes to Model B
```

### Test: Citation validation (P1, not P0)
```
Given: Model A returns citation to non-existent corpus entry
When: Citation validator runs (future feature)
Then:
  ✅ Citation marked as invalid
  ✅ Response rejected or marked as unvalidated
  ✅ ERROR log: "Citation validation failed"
```

---

## 7. Decisiones pendientes para developer

| Decisión | Opciones | Implicación |
|----------|----------|-----------|
| **Model B selection** | mistral-7b vs neural-chat vs dolphin | Latency, quality, resource consumption |
| **Circuit thresholds** | 50% failure rate vs 70% | Sensibilidad del circuit breaker |
| **Timeout value** | 5s vs 10s | Trade-off: fast fail vs allow slow model |
| **Health check (P1)** | Add now vs add in P1 | Latency impact, complexity |
| **Distributed state** | In-memory (now) vs Redis (future) | Single instance vs multi-instance scale |

---

**Nota:** Esta matriz es descripción de P0 (ADR-09 scope). Las mitigaciones marcadas como P1 (citation validation, health check) se documentan aquí para clarity pero NO se implementan en T-15.
