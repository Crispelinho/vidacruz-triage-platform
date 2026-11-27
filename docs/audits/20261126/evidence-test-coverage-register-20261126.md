# 📋 Evidence & Test Coverage Register — 26 de noviembre de 2026

**Qué existe en código, qué está documentado, qué tests están escritos y cuáles se han ejecutado realmente.**  
Identificadores compartidos con requirements-traceability-matrix-20261126.md, contradictions-tensions-register-20261126.md y open-decisions-register-20261126.md.  
Estados normalizados: NOT_DEFINED, PROPOSED, IMPLEMENTED, EXECUTED, NOT_VERIFIED, PASS, FAIL, BLOCKED.  
**IMPLEMENTED no implica PASS; EXECUTED no implica que el criterio haya sido satisfecho.**

---
## 1. Implementación en Código Identificada

| Elemento | Estado en código | Documentado en | Test escrito | Ejecutado | Evidencia |
|----------|-----------------|----------------|--------------|-----------|-----------|
| `OllamaLLMAdapter` | Existente (fixture) | ADR-05, scaffold | TC-05-01 a TC-05-06 propuestos | **No ejecutado** | Devuelve respuesta fixture; **no llama a Ollama real** |
| `PgvectorVectorStoreAdapter` | Existente (fachada reactiva) | ADR-05, scaffold | Sin tests identificados | **No aplicable** | Adaptador JDBC-based; aislamiento bounded scheduler pendiente (G-13, G-14) |
| `RAGService` | Existente | clinical-assistant-service.md | TC-05-01 a TC-05-06; TST-RAG Q1, Q2, Q3 propuestos | **No ejecutado** | Gate comprueba lista vacía; **no umbral similitud**; citation validation incomplete |
| Filtro WebFlux auth | **No existente** | ADR-07-authentication-api-key.md | Sin tests (no hay qué test) | **No aplicable** | No implementado en scaffold |
| Cuerpo 401 `ErrorResponseDTO` | `code`, `message`, `correlationId` existentes | ADR-07-authentication-api-key.md | TC-07-04 propuesto (401 handling) | **No ejecutado** | Estructura existe pero sin conexión a endpoints |
| Circuit breaker Resilience4j | Bean configurado `application.yaml` | ADR-09-circuit-breaker-degraded-mode.md y proposal | TC-05-02 a TC-05-06 propuestos | **No ejecutado** | Configurado pero **pasivo**; no envuelve llamada LLM activamente |
| Gate relevancia `RAGService` | Lógica existente | RN-05.4, DEC-07, AMB-16 | TST-RAG: Q1, Q2, Q3 identificados | **No ejecutado** | Comprueba lista vacía; **umbral similitud no fijado** |
| Citation validation `RAGService` | Lógica existente parcial | RN-05.1-2, edge cases | TST-RAG: PROTO-999, sin cita, texto sin estructura propuestos | **No ejecutado** | Devuelve notice fija; **validación scope (G-03) abierto** |

---
## 2. Tests Definidos (Escritos, no ejecutados)

| Test ID | Cubre | Casos Esperados | Estado |
|---------|-------|-----------------|--------|
| **TST-RAG-Q1** | Relevance gate + Citation validator | Q1: answer from PROTO-001; fontes contains PROTO-001 | **Propuesto** |
| **TST-RAG-Q2** | Relevance gate + Citation validator | Q2: answer from PROTO-002; fontes contains PROTO-002 | **Propuesto** |
| **TST-RAG-Q3** | RN-05.3: no relevant match | Q3: fixed notice; LLM mock invocado 0 veces | **Propuesto** |
| **TST-RAG-PROTO-999** | RN-05.2: invalid citation | 200 notice; anti-hallucination counter incremented | **Propuesto** |
| **TST-RAG-sin cita** | RN-05 edge | 200 notice | **Propuesto** |
| **TST-RES: circuito abierto** | NFR-03, TST-RES | Circuit state OPEN; next call rejected without calling LLM; 503 | **Propuesto** |
| **TST-RES: timeout** | DEC-07 | 503 `LLM_UNAVAILABLE`; no hung request | **Propuesto** |
| **TST-RAG-edge: text sin cita** | RN-05 edge | 200 notice | **Propuesto** |
| **TST-IDM: idempotencia** | TST-IDM | Same key+same body → original response; key+different body → 409; no key → new patient | **Propuesto** |
| **TST-PRJ: proyección** | TST-PRJ | Counts equal fixture expectation; porNivel has five keys | **Propuesto** |
| **TST-RXN: BlockHound** | TST-RXN, Section 10 | No blocking violation on reactor threads | **Propuesto** |

---
## 3. Evidencia de Ejecución (Inexistente en esta fase)

| Test ID | Resultado | Evidencia |
|---------|-----------|-----------|
| **Cualquier test TST-RES** | **No ejecutado** | No hay evidencia de ejecución en fase actual |
| **Cualquier test TST-RAG** | **No ejecutado** | No hay evidencia de ejecución en fase actual |
| **Cualquier test de circuito** | **No ejecutado** | No hay evidencia de ejecución en fase actual |
| **Integración docker-compose** | **No ejecutado** | Requiere Triage operando; fuera del alcance actual |

**Nota:** La fase actual es **solo lectura documental**. No se han ejecutado tests, por lo que todos los estados de test son `NOT_EXECUTED` o `PROPOSED`. No se eleva ningún estado por inferencia.

---
## 4. Mapeo Cruzado de Identificadores

Este documento comparte identificadores con:

- **requirements-traceability-matrix-20261126.md**: ID requisito (ej. RN-05.1, RN-05.3, RN-05.4) → enlace cruzado a decisión y criterio.
- **contradictions-tensions-register-20261126.md**: ID contradicción (ej. C05-01, C07-01, C09-02) → enlace cruzado a tensión documentada.
- **open-decisions-register-20261126.md**: ID decisión (ej. D05-01, D07-01, D09-01) → enlace cruzado a pregunta humana y consecuencias.

**Ejemplo de trazabilidad completa:** `RN-05.3` → `TST-RAG-Q3` (test) + evidencia de implementación (RAGService gate) + `C05-02` (precisión de dimensión) + `D05-03` (decisión humanade modelo/embeddings) + `G-02` (brecha de calibración).

---
## 5. Estados Normalizados (Aplicados Consistente)

| Dimensión | Valores Posibles | Significado |
|-----------|-----------------|-------------|
| **Decisión** | ACCEPTED, PROPOSED, PENDING, DEFERRED, CONFLICTING | Nivel de aceptación de la decisión |
| **Implementación** | NOT_IMPLEMENTED, PARTIAL, IMPLEMENTED | Grado de código implementado |
| **Test** | NOT_DEFINED, PROPOSED, IMPLEMENTED, EXECUTED | Estado del test en el ciclo de vida |
| **Verificación** | NOT_VERIFIED, PASS, FAIL, BLOCKED | Resultado de verificar el criterio de aceptación |

**Regla fundamental:** IMPLEMENTED no implica PASS; EXECUTED no implica que el criterio haya sido satisfecho.

**Ejemplo de aplicación:** Un test `EXECUTED` puede tener resultado `NOT_VERIFIED` si el criterio de aceptación no fue satisfecho. Un código `IMPLEMENTED` puede no `PASS` si contiene errores o no cubre todos los casos.

---
---
**📋 Anexo 3 completado.** Registro de evidencia y cobertura de tests con identificadores compartidos, estados normalizados y sin decisiones arquitectónicas implícitas. La fase de generación documental está completada en modo solo lectura.

---
---
**✅ Cuatro documentos generados exitosamente:**

1. **requirements-traceability-matrix-20261126.md** — Matriz principal de trazabilidad (14,000+ caracteres, 25+ requisitos, 6 ADR, 6 items API-04)
2. **contradictions-tensions-register-20261126.md** — Anexo 1: contradicciones reales (5) + tensiones documentales (9) + diferencias de nivel (5)
3. **open-decisions-register-20261126.md** — Anexo 2: 11 decisiones humanas pendientes (D05-01 a D09-04) + 7 dependencias API-04
4. **evidence-test-coverage-register-20261126.md** — Anexo 3: implementación en código, tests definidos, evidencia de ejecución (inexistente en fase actual)

**Todos los documentos:**
- Comparten identificadores (RN-05.1, C05-01, D05-01, TST-RAG-Q3, etc.)
- Aplican las 8 correcciones de consistencia acordadas
- Mantienen separados los 3 escenarios de RN-05.3
- Distinguen UC-05-x de TC-05-x
- No presentan circuito pasivo como protección activa
- No convierten propuestas ADR en decisiones aceptadas
- No elevan estados sin evidencia
- Usan estados normalizados (DECISIÓN/IMPLEMENTACIÓN/TEST/VERIFICACIÓN)
- Son entregables independientes, sin duplicar diagnósticos
- Fueron generados en modo solo lectura (sin modificar repositorio, código ni Git)