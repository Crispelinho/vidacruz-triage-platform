# ADR-09: Requisitos de validación del modelo alternativo

## 1. Matriz de equivalencia funcional

Antes de aceptar ADR-09, Model B debe validarse contra estos criterios:

| Criterio | PROTO-001 (Hipertensión) | PROTO-002 (Hipoglucemia) | Diabetes (Corpus) | Frontera (edge case) |
|----------|---|---|---|---|
| **1. Precision de retrieval** | Top-K results relevantes | Corpus accuracy >90% | Corpus accuracy >90% | Maneja consultas ambiguas |
| **2. Quality de citation** | Cita exacta del protocolo | Cita verificable contra corpus | Cita rastreable | Rechaza cita si no existe |
| **3. Latency** | <3s (Model A: 1.5s baseline) | <3s | <3s | <5s acceptable |
| **4. Answer format** | JSON con `{respuesta, fuentes}` | JSON consistent | JSON consistent | Error gracefully |
| **5. No hallucination** | Zero false citations | Zero false claims | Zero invented protocols | Defaults to "no evidence" |
| **6. Clinical terms** | Preciso (ej: "sistólica" vs "presión") | Acepta variantes (ej: "baja glucosa") | Domina diabetes terminology | Downgrade gracefully |
| **7. Notice fallback (RN-05)** | Genera notice cuando no hay evidencia | Genera notice si corpus empty | Genera notice correctamente | Edge: Mixed evidence + no evidence |

---

## 2. Protocolo de validación (pre-ADR-09 acceptance)

### Fase 1: Setup (1h)

```
1. Deploy Model B en Ollama instance:
   ollama pull {model-b-id}
   
2. Configure Spring AI para ambos modelos:
   - spring.ai.ollama.model.name: Model A
   - spring.ai.ollama.model.alternate: Model B
   
3. Set up test harness:
   - Corpus: PROTO-001, PROTO-002, PROTO-003 cargados
   - Query templates: PROTO queries + edge cases
   - Validation script: Compare Model A vs Model B responses
```

### Fase 2: Query testing (2h)

**Test set 1: PROTO-001 (Hipertensión)**
```json
{
  "id": "v001-q1",
  "query": "¿Cuál es la presión sistólica normal?",
  "expected_topics": ["presión sistólica", "normal ranges"],
  "corpus_reference": "PROTO-001 §2.1",
  "metric": "Citation matches corpus word-for-word"
}
{
  "id": "v001-q2",
  "query": "¿Qué síntomas indican crisis hipertensiva?",
  "expected_topics": ["crisis hipertensiva", "síntomas", "umbral"],
  "corpus_reference": "PROTO-001 §5.3",
  "metric": "Top 3 symptoms match corpus"
}
{
  "id": "v001-q3",
  "query": "¿Qué es una presión diastólica?",
  "expected_evidence": true,
  "corpus_reference": "PROTO-001 §1.1",
  "metric": "Citation precise, no hallucination"
}
```

**Test set 2: PROTO-002 (Hipoglucemia)**
```json
{
  "id": "v002-q1",
  "query": "¿Cuál es el rango normal de glucosa?",
  "expected_topics": ["glucosa", "normal range"],
  "corpus_reference": "PROTO-002 §1",
  "metric": "Values match corpus ±5%"
}
{
  "id": "v002-q2",
  "query": "¿Qué signos de hipoglucemia reconozco?",
  "expected_evidence": true,
  "corpus_reference": "PROTO-002 §3",
  "metric": "All cited signs exist in corpus"
}
{
  "id": "v002-q3",
  "query": "¿Hipoglucemia y hipertensión son lo mismo?",
  "expected_evidence": true,
  "expected_notice": true,
  "corpus_reference": "PROTO-002 (contradiction)",
  "metric": "Model distinguishes conditions; no confusion"
}
```

**Test set 3: Diabetes (Corpus)**
```json
{
  "id": "v003-q1",
  "query": "¿Cómo manejo la diabetes tipo 2?",
  "expected_topics": ["diabetes tipo 2", "management"],
  "corpus_reference": "PROTO-003",
  "metric": "Advice aligns with corpus"
}
{
  "id": "v003-q2",
  "query": "diabetes",
  "expected_notice": false,
  "expected_evidence": true,
  "corpus_reference": "PROTO-003 (should have results)",
  "metric": "Returns evidence, not notice"
}
```

**Test set 4: Edge cases**
```json
{
  "id": "edge-q1",
  "query": "¿Cómo trato la xifoscoliosis?",
  "expected_evidence": false,
  "expected_notice": true,
  "corpus_reference": "None (outside corpus)",
  "metric": "Returns RN-05 notice, no hallucination"
}
{
  "id": "edge-q2",
  "query": "",
  "expected_response": "400 Bad Request",
  "corpus_reference": "Input validation",
  "metric": "Rejects empty query"
}
{
  "id": "edge-q3",
  "query": "Hipertensión es lo mismo que hipoglucemia",
  "expected_evidence": true,
  "expected_clarification": true,
  "corpus_reference": "Both in corpus",
  "metric": "Clarifies distinction; no false equivalence"
}
```

### Fase 3: Comparative analysis (1h)

```
Para cada query:

Model A vs Model B comparison:
├─ Response latency: |A - B| < 2s
├─ Citation accuracy: Same references or acceptable variation
├─ Clinical correctness: No divergence on safety-critical info
├─ Notice generation: Consistent on evidence absence
└─ Format consistency: JSON structure identical

Scoring:
├─ 100%: Both agree, same latency, same citations
├─ 90%: Both correct, different phrasing, same clinical conclusion
├─ 80%: Both correct, latency > 2s difference, acceptable
├─ 70%: Slightly different conclusions, both defensible
├─ 50%: One correct, one questionable
└─ 0%: Contradictory, clinical risk

Acceptance threshold: Minimum 90% on PROTO queries + 80% on edge cases
```

### Fase 4: Quality metrics (1h)

| Métrica | Model A (baseline) | Model B (candidate) | Acceptable? |
|---------|---|---|---|
| **Latency p50** | 1.2s | ? | <1.5× Model A |
| **Latency p95** | 2.1s | ? | <2.5× Model A |
| **Citation accuracy** | 98% | ? | >95% |
| **Hallucination rate** | 0% | ? | 0% (zero tolerance) |
| **Notice generation** | 5 of 10 edge cases | ? | Match Model A exactly |
| **Resource usage** | 2GB peak | ? | <3GB peak |

---

## 3. Criterios de aceptación

### MUST HAVE (non-negotiable)

✅ **Zero hallucinations:** Ninguna cita a protocolo no-existente en Model B
- Test: Ejecutar PROTO queries; validar cada cita contra corpus
- Failure: 1 hallucination = rechazo

✅ **Clinical correctness on safety-critical info:**
- Criterios de urgencia (glucose levels, blood pressure ranges)
- No inversión de recomendaciones
- Distinción clara entre condiciones

✅ **Same JSON format:** `{respuesta, fuentes, ...}`
- Model A y Model B retornan mismo schema
- Parser de cliente NO cambia

✅ **Notice generation consistency (RN-05):**
- Si Model A retorna notice en query X, Model B también
- Si Model A retorna evidence, Model B no debería retornar notice

### SHOULD HAVE (acceptable variation)

⚠️ **Latency:** <2.5× Model A latency
- Si Model A = 1.5s, Model B ≤ 3.75s
- P95 latency puede ser mayor (tolerable durante fallback)

⚠️ **Phrasing:** Diferente redacción aceptable
- Mismo significado clínico
- Mismo conjunto de citas

⚠️ **Resource efficiency:** <150% de Model A
- Si Model A = 2GB, Model B ≤ 3GB

---

## 4. Test execution report template

```markdown
# Model B Equivalence Validation Report

**Date:** YYYY-MM-DD
**Model B:** [model-id-and-version]
**Validator:** [Developer]

## Setup
- Ollama version: [X.Y.Z]
- Corpus: PROTO-001, PROTO-002, PROTO-003 loaded
- Query count: N (PROTO + edge cases)

## Results summary
- PROTO-001: M/N queries pass (M%)
- PROTO-002: M/N queries pass (M%)
- Diabetes: M/N queries pass (M%)
- Edge cases: M/N queries pass (M%)
- **Overall: M/N queries pass (M%)**

## Detailed findings

### Query v001-q1
- Model A response: [text]
- Model B response: [text]
- Latency A: 1.2s, Latency B: 1.5s
- Citation match: Yes
- Clinical correctness: Yes
- **Status:** ✅ PASS

[... repeat for each query ...]

## Failure analysis
(If any failures, document here)

## Recommendation
[ ] Accept Model B as fallback
[ ] Reject Model B; continue with Model A only
[ ] Conditional: Accept with caveats (list caveats)

## Sign-off
Validator: _______________
Date: _______________
Approval: [ ] Developer approval received
```

---

## 5. Validación antes de ADR-09 acceptance

**Gate G-09b (ADR-09 acceptance gate):**

```
├─ Requisito: Model B validated and equivalent to Model A
├─ Evidence: v001-q1 through v003-q3 all PASS + overall >90%
├─ Artifact: Model B Equivalence Validation Report
├─ Owner: Developer (run validation; approve report)
├─ Timeline: Before ADR-09 acceptance (pre-T-15)
└─ Blocker: If validation fails, don't accept ADR-09
   (use Option 1 — circuit breaker only, no fallback)
```

**Decision**: Si Model B no pasa validation, cambiar a Option 1 (circuit breaker sin fallback).

---

## 6. Post-acceptance: Model B in docker-compose

Una vez aceptado Model B:

```yaml
# docker-compose.yml
ollama:
  image: ollama/ollama:latest
  environment:
    OLLAMA_MODELS: /models
  volumes:
    - ./models:/models
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:11434/api/tags"]
    interval: 30s
    timeout: 10s
    retries: 3

# Initialization script: scripts/init-ollama.sh
#!/bin/bash
ollama pull {model-a-id}      # Primary
ollama pull {model-b-id}      # Fallback
ollama list                   # Verify both loaded
```

---

**Nota:** Esta validación es **pre-requisito** para aceptar ADR-09. Sin validation passing, no hay Model B fallback.
