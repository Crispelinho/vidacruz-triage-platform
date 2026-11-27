# 📋 Informe de Reconciliación ADR-05, ADR-07 y ADR-09

**Fecha:** 26 de noviembre de 2026  
**Fase:** Consolidación y auditoría de consistencia  
**Modo:** Solo lectura, sin modificaciones  

> **Nota de vigencia (2026-11-27):** Este informe es una fotografía del estado al 2026-11-26. El estado corriente de ADR-05 está en [ADR-05-assistant-llm-provider.md](../../adr/ADR-05-assistant-llm-provider.md): es el documento canónico, el borrador de Spring AI quedó `Superseded`, la cadena C17 reemplaza la opción Ollama única y ADR-05 sigue `Proposed`. En consecuencia, las afirmaciones históricas "Supersesión explícita: Ninguna", "DECIDED: Spring AI 1.1.1 + Ollama", "dentro del perímetro de Azure" y "números de breaker pendientes" se leen como hallazgos de esa fecha, no como decisiones vigentes. La consolidación no registra aceptación.

---

## 1. ADR-05: LLM provider and embedding model

### Documentos inspeccionados:

| Archivo | Status | Fecha | Deciders |
|---------|--------|-------|----------|
| `ADR-05-assistant-llm-provider.md` | Proposed. Not accepted. | 2026-11-25 | developer (pending) |
| `ADR-05-llm-provider-spring-ai.md` | Proposed | 2026-11-25 | — |

### Contenido y decisiones:

**ADR-05-assistant-llm-provider.md:**
- **Opción D propuesta:** Local Ollama para chat y embeddings (dentro del perímetro de Azure)
- **Estado:** "Proposed, not accepted. Viabilidad depende de la evidencia de ADR-11"
- **Conductor de decisión:** developer (pending)
- **Relacionado con API-04:** Define el proveedor LLM y dimensión de embeddings como `PENDING DECISION`
- **Riesgos clave (R-04, R-05, R-06, R-10, CA-G-05):** Llamadas LLM, versión drift, acceso Azure, relevancia y citas

**ADR-05-llm-provider-spring-ai.md:**
- **Decisión:** Usar Spring AI 1.1.x GA como capa de abstracción LLM
- **Proveedores configurables:** OPENAI, ANTHROPIC, OLLAMA
- **Configuración:** `spring.ai.provider`, `spring.ai.ollama.base-url`, `spring.ai.ollama.chat.options.model`, `spring.ai.ollama.embedding.options.model`
- **Aislamiento JDBC:** Pgvector operations en bounded elastic scheduler
- **Consequencias positivas:** Flexibilidad de provider, optimización de costos, testing local con Ollama
- **Consequencias negativas:** pgvector blocking I/O, Spring AI 1.1.x no es latest, features provider-specific no expuestas

### Dependencias sobre API-04:
- Define el proveedor LLM y dimensión de embeddings como `PENDING DECISION`
- La dimensión 768 es **provisional** hasta resolución de ADR-05
- `OllamaLLMAdapter` existente es fixture, no proveedor real
- El contrato REST no debe exponer dimensión de embeddings

### Decisiones coincidentes y contradictorias:

| Aspecto | ADR-05-assistant-llm-provider.md | ADR-05-llm-provider-spring-ai.md | Conclusión |
|---------|----------------------------------|----------------------------------|------------|
| **Proveedor LLM** | Ollama propuesta (Option D), no aceptado | Spring AI 1.1.x GA con provider configurable | **Complementarios**: el primero decide el proveedor, el segundo la capa de abstracción |
| **Dimensión embeddings** | Fija en migración; cambiar requiere re-ingestión | No fija definitivamente; depende del modelo | **Coherentes**: ambas dejan la dimensión como decisión pending |
| **Azure perimeter** | Mantener texto clínico dentro del perímetro Azure | Configurable a cualquier proveedor | **Conflito potencial**: el primero asume Azure, el segundo es agnóstico |
| **Status** | Proposed, Not accepted | Proposed | **Ambos pending** - ninguno accepted |

### Supersesión explícita: Ninguna
No hay declaración de que uno sustituye al otro. Son documentos complementarios en diferentes aspectos del mismo problema.

> **Actualización 2026-11-27:** Ver la nota de vigencia al inicio. La consolidación no registra aceptación: ADR-05 sigue `Proposed` y las verificaciones continúan pendientes.

### Preguntas que requieren decisión humana:
1. ¿Aceptar Option D (Ollama local) de ADR-05-assistant-llm-provider.md?
2. ¿Definir dimensión definitiva de embeddings?
3. ¿Verificar acceso y cuota Azure (ADR-11)?

---

## 2. ADR-07: Authentication mechanism

### Documentos inspeccionados:

| Archivo | Status | Fecha | Deciders |
|---------|--------|-------|----------|
| `ADR-07-authentication.md` | Proposed. Not accepted. | 2026-11-25 | developer (pending) |
| `ADR-07-authentication-api-key.md` | Proposed | 2026-11-25 | — |

### Contenido y decisiones:

**ADR-07-authentication.md:**
- **Opción A propuesta:** API key en `X-API-Key`, validada por WebFlux filter en `/triage/**` y `/asistente/**`
- **Estado:** "Proposed. Not accepted. API key en X-API-Key es la ruta propuesta pending developer confirmation"
- **Related:** C-10, DEC-05, NFR-09, NFR-10, AMB-09, G-21, G-T31, T-19, T-28
- **Scope:** Decide mecanismo de autenticación y scope de endpoints protegidos; **no decide** reglas de autorización, política de protección de datos ni validación funcional

**ADR-07-authentication-api-key.md:**
- **Decision:** Secure endpoints with API key passed in `X-API-Key` HTTP header, validated by WebFlux filter
- **Implementation:** API key storage como propiedad de configuración (env variable o application.properties)
- **Filter chain:** WebFlux filter que checks `isPublicPath()`, validates API key, returns 401 if invalid
- **Error response:** `{ "code": "UNAUTHORIZED", "message": "Invalid or missing X-API-Key header", "correlationId": "..." }`
- **Public paths:** `/health`, `/health/ready`, `/health/live`
- **Consequences positives:** Quick to implement, testable locally, service-to-service ready, non-stateful
- **Consequences negatives:** Minimal cryptography (HTTP header plaintext), no expiration, shared key across all clients, no RBAC

### Dependencias sobre API-04:
- **NFR-10 requiere autenticación** en endpoints `/api/asistente/consulta` (aunque el contrato usa `/asistente/consulta`)
- El mecanismo (API Key vs JWT) es `PENDING DECISION`
- No se debe asumir protección solo porque existe el documento ADR
- Cambio 3 y cambio 10 dependen de esta decisión

### Decisiones coincidentes y contradictorias:

| Aspecto | ADR-07-authentication.md | ADR-07-authentication-api-key.md | Conclusión |
|---------|-------------------------|----------------------------------|------------|
| **Mecanismo** | API key en `X-API-Key` propuesto | API key en `X-API-Key` implementada | **Idénticos** en el mecanismo elegido |
| **Status** | Proposed. Not accepted. | Proposed | **Ambos pending** - ninguno accepted |
| **Endpoints protegidos** | `/triage/**` y `/asistente/**` | `/triage/**` y `/asistente/**` | **Idénticos** |
| **Health probes públicos** | Sí | Sí | **Coherentes** |
| **Key lifecycle** | Pending (G-21, G-T31) | Config from env, no hardcoded | **Complementarios**: el primero deja pending, el segundo da detalles de implementación |
| **401 body shape** | Pending definition (G-21) | Especificado: `{code: UNAUTHORIZED, message, correlationId}` | **Conflito**: el segundo especifica, el primero deja pending |

### Supersesión explícita: Ninguna
No hay declaración de que uno sustituye al otro. `ADR-07-authentication-api-key.md` parece ser más detallado/implementación-oriented, mientras que `ADR-07-authentication.md` es más de decisión/alcance.

### Preguntas que requieren decisión humana:
1. ¿Confirmar API key en `X-API-Key` como mecanismo definitivo?
2. Definir cuerpo 401 (`G-21`)?
3. Proveer keys desde entorno/secret config (G-12)?

---

## 3. ADR-09: Circuit breaker and degraded mode

### Documentos inspeccionados:

| Archivo | Status | Fecha | Deciders |
|---------|--------|-------|----------|
| `ADR-09-architecture-diagram.md` | Proposed | 2026-11-25 | — |
| `ADR-09-circuit-breaker-degraded-mode.md` | Proposed. Not accepted. | 2026-11-25 | developer (pending) |
| `ADR-09-circuit-breaker-proposal.md` | Proposal (ready for review) | 2026-11-25 | Developer |

### Contenido y decisiones:

**ADR-09-architecture-diagram.md:**
- **Diagrama completo** con circuit breakers para Model A y Model B
- **Fallback automático** a Model B cuando circuit breaker #1 se abre
- **7 escenarios de fallo** documentados (Model A healthy, Model A fails, timeout, Model B also fails, Ollama instance down, HALF_OPEN recovery, Model equivalence failure)
- **Configuración Resilience4j** con sliding window size 10, minimumNumberOfCalls 3, timeout 5s
- **Implementación Java** con `@CircuitBreaker(name = "ollama-model-a", fallbackMethod = "fallbackToModelB")`
- **Formato de respuesta** con `modelUsed`, `circuitBreakerState`, `correlationId`
- **Status:** Proposed (no accepted)
- **Nota importante:** "Ambos modelos en la MISMA instancia de Ollama. Si la instancia cae, ambos fallan. No es una mitigación de infraestructura."

**ADR-09-circuit-breaker-degraded-mode.md:**
- **Opción A propuesta:** "Resilience4j circuit breaker with timeout and **no retries**... fallback returns 503 LLM_UNAVAILABLE"
- **Estado:** "Proposed. Not accepted. Option A is the proposed route; numeric timeout and breaker values and the complete failure classification remain open."
- **Decision aceptada (developer, 2026-11-25):** "the LLM never participates in readiness. An unavailable model returns `503 LLM_UNAVAILABLE` and recovers automatically; breaker values and failure classification remain open (G-05, CA-G-05)."
- **Related:** DEC-07, NFR-03, NFR-08, NFR-20, TC-05, R-04, R-10, CA-G-05, CA-G-16, API-04
- **Conductores de decisión:** NFR-03 (503 determinista), NFR-08 (timeout configurable), R-04 (LLM latency and cost), CA-G-05 (failure types)
- **Alternativas consideradas:**
  - **Option A** (propuesta): Circuit breaker + timeout, **no retries**, fallback 503
  - **Option B** (rechazado): Retry with exponential backoff (conflicta con G-05)
  - **Option C** (rechazado): Servir answer cached (violates RN-05)
  - **Option D** (rechazado): Remove Assistant from readiness (conflicts with D0 §13)

**ADR-09-circuit-breaker-proposal.md:**
- **Primary:** Option 2 (Circuit Breaker + Router to Alternative Model)
- **Supplementary:** Option 3 health check can be added later (Phase P1)
- **Rationale:** Usa misma infraestructura (single Ollama instance), proporciona fallback path para model-specific failures, aligns with 3-day delivery
- **Consequences positives:** LLM unavailability returns 503, model fallback provides soft degradation, traceability con `modelUsed`
- **Consequences negatives:** Adds router and second circuit breaker complexity, requiere equivalence validation para Model B, si Ollama infrastructure is down, no puede recover
- **Verification acceptance criteria:**
  1. Circuit breaker opens after N failures → no more calls to Ollama
  2. When circuit is open, router redirects to Model B
  3. When Model B circuit opens, return 503
  4. Each response incluye `modelUsed: "Model A"` o `modelUsed: "Model B"`
  5-7. Tests adicionales de stub y equivalence
- **Gates and dependencies:**
  - G-09a: ADR-05 → ADR-09 | ✅ DECIDED: Spring AI 1.1.1 + Ollama
  - G-09b: ADR-09 → T-14, T-15 (Change 3 Day 2) | Needs Model B selection + equivalence validation
- **Questions for developer approval (Q1-Q5):**
  1. Model B: Which Ollama model for fallback?
  2. Equivalence validation: hand validation or automated test?
  3. Latency budget: timeout before circuit opens?
  4. Logging: log every model selection or only on fallback?
  5. Future flexibility: pre-design Option 3 health check or add in P1?

### Dependencias sobre API-04:
- **Item 5 (Fallback modelo):** Directamente dependiente de la decisión ADR-09
- **HTTP 503 + LLM_UNAVAILABLE:** Documentado en API-04, pero comportamiento depende de cuál opción ADR-09 se acepte
- **Sin fallback automático:** Coherente con ADR-09-circuit-breaker-degraded-mode.md (Opción A)
- **Con fallback automático a Model B:** Coherente con ADR-09-circuit-breaker-proposal.md (Opción 2)

### Decisiones coincidentes y contradictorias:

| Aspecto | ADR-09-architecture-diagram.md | ADR-09-circuit-breaker-degraded-mode.md | ADR-09-circuit-breaker-proposal.md |
|---------|-------------------------------|----------------------------------------|------------------------------------|
| **Fallback automático** | Sí, redirige a Model B | **No**, retorna 503 directamente | **Sí**, router a Model B (Opción 2) |
| **Status** | Proposed | Proposed. Not accepted. | Proposal (ready for review) |
| **Opción A** | — | **Opción A propuesta** (sin fallback automático) | — |
| **Opción 2** | — | — | **Recomendada** (fallback a Model B) |
| **Circuit Breaker** | Un breaker por modelo (A y B) | Uno solo con timeout + 503 | Uno primary + router a Model B |
| **Infraestructura** | Misma instancia Ollama | Misma instancia Ollama | Misma instancia Ollama |
| **Equivalence validation** | REQUIRED (before acceptance) | Pendiente (G-05) | REQUIRED (before acceptance) |

### Supersesión explícita: Ninguna declarada
No hay ninguna declaración de supersesión entre los tres documentos. Parecen ser **propuestas en progreso** en diferentes etapas:
- `ADR-09-circuit-breaker-degraded-mode.md`: Option A propuesta, no accepted
- `ADR-09-circuit-breaker-proposal.md`: Opción 2 recomendada, awaiting developer input
- `ADR-09-architecture-diagram.md`: Diagrama de arquitectura, status Proposed

### La contradicción crítica:

| Documento | Postura sobre fallback automático |
|-----------|----------------------------------|
| **ADR-09-circuit-breaker-degraded-mode.md** (Option A) | **SIN fallback automático:** Circuit breaker + timeout → 503 LLM_UNAVAILABLE directo. "no retries". |
| **ADR-09-circuit-breaker-proposal.md** (Option 2) | **CON fallback automático:** Circuit breaker → router a Model B → si Model B falla, 503. |
| **ADR-09-architecture-diagram.md** | Muestra arquitectura **con** fallback a Model B (aunque status Proposed) |

### Decisión provisional en API-04 item 5:

La especificación API-04 item 5 estableció: **"Sin fallback automático, HTTP 503 puro ante fallo LLM, circuit breaker configurado pero pasivo"**.

**Esta decisión es coherente con:**
- ✅ **ADR-09-circuit-breaker-degraded-mode.md** (Opción A - propuesta, no accepted)
- ❌ **ADR-09-circuit-breaker-proposal.md** (Opción 2 - con fallback a Model B)
- ⚠️ **ADR-09-architecture-diagram.md** (Arquitectura con fallback, pero status Proposed, no accepted)

### Resumen del estado de ADR-09:

| Documento | Status | Fallback automático | Próximo paso |
|-----------|--------|---------------------|--------------|
| **ADR-09-circuit-breaker-degraded-mode.md** | Proposed. Not accepted. | **No** (Option A propuesta) | Await developer decision on numeric values (G-05) |
| **ADR-09-circuit-breaker-proposal.md** | Proposal (ready for review) | **Sí** (Opción 2 recomendada) | Developer input en Q1-Q5 |
| **ADR-09-architecture-diagram.md** | Proposed | **Sí** (en el diagrama) | Await acceptance of one of the above |

### Conclusión de reconciliación:

1. **Hay tres documentos ADR-09 con posturas parcialmente superpuestas pero distintas:**
   - `ADR-09-circuit-breaker-degraded-mode.md`: Propone **sin fallback automático** (503 directo)
   - `ADR-09-circuit-breaker-proposal.md`: Recomienda **fallback automático a Model B** (Opción 2)
   - `ADR-09-architecture-diagram.md`: Muestra arquitectura **con** fallback, pero status Proposed

2. **La decisión del item 5 en API-04 (sin fallback automático) es coherente con** `ADR-09-circuit-breaker-degraded-mode.md`, **no con** `ADR-09-circuit-breaker-proposal.md`.

3. **Aún no hay una decisión accepted** sobre cuál camino seguir. Los tres documentos tienen status "Proposed" o "Proposal (ready for review)".

4. **Las decisiones sobre fallback y modelo definitivas quedan BLOCKED** hasta:
   - Aceptación de ADR-05 (proveedor LLM y embeddings)
   - Aceptación explícita de una opción ADR-09
   - Validación de equivalencia de Model B

### Cuadro resumen consolidado:

| Item | Documento que lo respalda | Status | Bloqueante |
|------|-------------------------|--------|------------|
| **Circuit breaker configurado** | ADR-09-circuit-breaker-degraded-mode.md | Proposed (Option A) | ADR-09 no accepted |
| **503 LLM_UNAVAILABLE** | ADR-09-circuit-breaker-degraded-mode.md | Proposed | ADR-09 no accepted |
| **Sin fallback automático** | ADR-09-circuit-breaker-degraded-mode.md (Option A) | Proposed | ADR-09 no accepted |
| **Fallback a Model B** | ADR-09-circuit-breaker-proposal.md (Option 2) | Proposal (ready for review) | Await developer Q1-Q5 |
| **Modelo definitivo bloqueado** | ADR-05 pending | BLOCKED | ADR-05 not accepted |

---
---
**Fin del informe de reconciliación ADR.**  
**Próximo paso:** Sistematizar contradicciones y definir qué decisiones requieren intervención humana antes de considerar el contrato API-04 consistente.  
**Fase:** Solo lectura, documentación de hallazgos. Sin modificaciones en repositorio.