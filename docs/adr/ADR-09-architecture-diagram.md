# ADR-09: Arquitectura de Circuit Breaker + Fallback

> **Alcance histórico:** estos diagramas reflejan el borrador original con un único breaker y Modelo A/B. La decisión vigente es la enmienda de 2026-11-26 en [ADR-09](ADR-09-circuit-breaker-degraded-mode.md): un breaker por nodo (`ollama`, `gemini`, `opencode`) con `slidingWindowSize=10`, `failureRateThreshold=50 %`, `waitDurationInOpenState=30 s`, `permittedNumberOfCallsInHalfOpenState=3` y `timeoutDuration=15 s`. La cadena y el modelo de embeddings vigentes están en [ADR-05](ADR-05-assistant-llm-provider.md).

## Diagrama 1: Flujo primario y fallback

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Clinical Assistant Service                      │
│                                                                      │
│  POST /asistente/consulta                                           │
│  ├─ Query: "Hipertensión"                                          │
│  ├─ Corpus: PROTO-001, PROTO-002, PROTO-003                        │
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐│
│  │ 1. Retrieval (pgvector embedding)                              ││
│  │    ├─ Query → Embedding (Spring AI + Ollama embedding model)   ││
│  │    ├─ Similarity search in pgvector (Clinical Assistant DB)    ││
│  │    └─ Top-K results (relevance threshold)                      ││
│  └────────────────────────────────────────────────────────────────┘│
│                                                                      │
│  ┌────────────────────────────────────────────────────────────────┐│
│  │ 2. LLM Interface (abstraction layer)                            ││
│  │                                                                 ││
│  │    ┌─────────────────────────────────────────────────────────┐││
│  │    │ [Circuit Breaker #1 — Model A (Primary)]               │││
│  │    │ Config: failures=3, timeout=5s, halfOpenRequests=1    │││
│  │    │                                                        │││
│  │    │ ┌──────────────────────────────────────────────────┐  │││
│  │    │ │ Ollama Model A (e.g., mistral-7b)              │  │││
│  │    │ │ ├─ POST http://ollama:11434/api/generate       │  │││
│  │    │ │ └─ Timeout: 5s                                 │  │││
│  │    │ └──────────────────────────────────────────────────┘  │││
│  │    │                                                        │││
│  │    │ [Circuit State]                                       │││
│  │    │ ├─ CLOSED: Normal operation                           │││
│  │    │ ├─ OPEN: 3 failures or timeout detected              │││
│  │    │ │  └─→ ROUTE TO MODEL B (fallback)                   │││
│  │    │ └─ HALF_OPEN: Testing recovery (1 request allowed)   │││
│  │    └─────────────────────────────────────────────────────┘││
│  │                                                             ││
│  │    ┌─────────────────────────────────────────────────────┐││
│  │    │ [Circuit Breaker #2 — Model B (Fallback)]           │││
│  │    │ Config: failures=3, timeout=5s, halfOpenRequests=1  │││
│  │    │                                                      │││
│  │    │ ┌──────────────────────────────────────────────────┐ │││
│  │    │ │ Ollama Model B (e.g., neural-chat-7b)           │ │││
│  │    │ │ ├─ Same Ollama service instance                │ │││
│  │    │ │ │ (different model loaded via API call)         │ │││
│  │    │ │ └─ Timeout: 5s                                 │ │││
│  │    │ └──────────────────────────────────────────────────┘ │││
│  │    │                                                      │││
│  │    │ [Circuit State]                                     │││
│  │    │ ├─ CLOSED: Normal operation                         │││
│  │    │ ├─ OPEN: Both models failed                         │││
│  │    │ │  └─→ RETURN 503 Service Unavailable              │││
│  │    │ └─ HALF_OPEN: Testing recovery                     │││
│  │    └─────────────────────────────────────────────────────┘││
│  │                                                             ││
│  │  [Router Logic]                                            ││
│  │  if (circuitBreaker1.isOpen()) {                           ││
│  │    modelUsed = "Model B"                                   ││
│  │    response = callModel2()                                 ││
│  │  } else {                                                  ││
│  │    modelUsed = "Model A"                                   ││
│  │    response = callModel1()                                 ││
│  │  }                                                         ││
│  └────────────────────────────────────────────────────────────┘│
│                                                                  │
│  ┌────────────────────────────────────────────────────────────┐│
│  │ 3. Response building                                       ││
│  │    ├─ LLM completion (with citations)                     ││
│  │    ├─ Citation validation (against corpus)                ││
│  │    └─ Fallback: If no evidence → fixed notice RN-05       ││
│  │                                                            ││
│  │  Response body:                                           ││
│  │  {                                                        ││
│  │    "respuesta": "...",                                   ││
│  │    "fuentes": [...],                                     ││
│  │    "modelUsed": "Model A | Model B",        ← NEW         ││
│  │    "circuitBreakerState": "CLOSED|OPEN",   ← NEW (optional)││
│  │    "timestamp": "..."                                    ││
│  │  }                                                        ││
│  └────────────────────────────────────────────────────────────┘│
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Ollama Service (docker-compose)
├─ Instance: ollama:11434
├─ Models: Model A (primary), Model B (fallback)
└─ Stateless per request
```

---

## Diagrama 2: Escenarios de fallo

```
Escenario 1: Model A healthy, Model B ready
─────────────────────────────────────────────
Client: POST /asistente/consulta
  ↓
Circuit #1 CLOSED → Call Model A
  ↓
✅ Response (200 OK, modelUsed=A)


Escenario 2: Model A fails (failure count reached)
──────────────────────────────────────────────────
Request 1-3: Model A fails
  ↓
Circuit #1 OPEN (after 3 failures)
  ↓
Request 4: Router detects OPEN → Call Model B
  ↓
✅ Response (200 OK, modelUsed=B)


Escenario 3: Model A slow (timeout threshold)
─────────────────────────────────────────────
Request N: Model A → timeout (5s)
  ↓
Failure recorded
  ↓
(after N timeouts + failures) Circuit #1 OPEN
  ↓
Request N+1: Router → Model B
  ↓
✅ Response (200 OK, modelUsed=B)


Escenario 4: Model B also fails
────────────────────────────────
Circuit #1 OPEN → Route to Model B
  ↓
Circuit #2 also OPEN (or Model B fails)
  ↓
❌ 503 Service Unavailable
   Header: Retry-After: 60
   Body: {"error": "LLM service unavailable", "circuitState": "open"}


Escenario 5: Ollama instance down
─────────────────────────────────
Client: POST /asistente/consulta
  ↓
Circuit #1 CLOSED → Call Model A
  ↓
❌ Connection refused (Ollama:11434 unreachable)
  ↓
Failure recorded
  ↓
(Circuit opens after threshold)
  ↓
Request N+1: Router → Model B
  ↓
❌ Connection refused (same instance down)
  ↓
Circuit #2 OPEN
  ↓
❌ 503 Service Unavailable
   (Both circuit breakers open, Ollama infrastructure failure)


Escenario 6: Circuit HALF_OPEN recovery attempt
───────────────────────────────────────────────
Circuit #1 was OPEN, timeout elapsed (default 60s)
  ↓
Circuit #1 → HALF_OPEN (allows 1 request)
  ↓
Request N: Call Model A (test recovery)
  ↓
✅ Success
  ↓
Circuit #1 → CLOSED (recovery successful)
  ↓
Subsequent requests → Model A (normal)


Escenario 7: Model equivalence failure (clinical risk)
──────────────────────────────────────────────────────
Circuit #1 OPEN → Route to Model B
  ↓
✅ Response (200 OK) BUT:
  - Citations don't match corpus
  - Different clinical conclusion
  - Quality degraded
  ↓
❌ DETECTED BY: Pre-validation matrix (before accepting ADR-09)
   NOT by this code path
```

---

## Diagrama 3: Configuración Resilience4j

```yaml
# application.yaml - Clinical Assistant Service

resilience4j:
  circuitbreaker:
    configs:
      default:
        registerHealthIndicator: true
        slidingWindowSize: 10            # Last 10 calls
        minimumNumberOfCalls: 3           # Need 3 calls to calculate failure rate
        slowCallDurationThreshold: 5000   # 5s timeout
        slowCallRateThreshold: 50         # 50% slow → open
        failureRateThreshold: 50          # 50% failures → open
        waitDurationInOpenState: 60000    # 60s in open state before half-open
        permittedNumberOfCallsInHalfOpenState: 1
        
    instances:
      ollama-model-a:
        baseConfig: default
        
      ollama-model-b:
        baseConfig: default
        
  retry:
    configs:
      default:
        maxAttempts: 1                    # No retry; let circuit breaker handle
        waitDuration: 100
        
  timelimit:
    instances:
      ollama-call:
        timeoutDuration: 5s
```

---

## Diagrama 4: Integración con Spring AI

```java
// LLM Interface (abstraction)
public interface LLMProvider {
    String generateResponse(String prompt, List<String> context);
}

// Implementation with Circuit Breaker
@Component
public class OllamaLLMProvider implements LLMProvider {
    
    private final ChatClient chatClientA;  // Model A
    private final ChatClient chatClientB;  // Model B
    
    @CircuitBreaker(name = "ollama-model-a", 
                   fallbackMethod = "fallbackToModelB")
    @Retry(name = "default")
    @TimeLimiter(name = "ollama-call")
    public String generateResponse(String prompt, List<String> context) {
        // Model A
        return chatClientA.prompt()
            .user(prompt)
            .call()
            .content();
    }
    
    @CircuitBreaker(name = "ollama-model-b")
    public String fallbackToModelB(String prompt, List<String> context, 
                                   Exception ex) {
        log.warn("Model A failed, attempting Model B", ex);
        
        try {
            return chatClientB.prompt()
                .user(prompt)
                .call()
                .content();
        } catch (Exception e) {
            log.error("Both models failed", e);
            throw new LLMUnavailableException("All LLM models unavailable", e);
        }
    }
}
```

---

## Diagrama 5: Respuesta con model tracking

```json
{
  "respuesta": "La hipertensión se caracteriza por...",
  "fuentes": [
    {
      "titulo": "Protocolo de Hipertensión",
      "referencia": "PROTO-001-2026",
      "relevancia": 0.92
    }
  ],
  "modelUsed": "Model A",
  "circuitBreakerState": "CLOSED",
  "timestamp": "2026-11-25T14:32:45Z",
  "correlationId": "req-abc123"
}
```

---

## Consideraciones de infraestructura

```
docker-compose.yml:

ollama:
  image: ollama/ollama:latest
  ports:
    - "11434:11434"
  environment:
    - OLLAMA_MODELS=/models
  volumes:
    - ./models:/models
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:11434/api/tags"]
    interval: 30s
    timeout: 10s
    retries: 3

# Post-deployment:
# ollama pull mistral        (Model A)
# ollama pull neural-chat    (Model B)
```

**Nota:** Ambos modelos en la MISMA instancia de Ollama. Si la instancia cae, ambos fallan. No es una mitigación de infraestructura.
