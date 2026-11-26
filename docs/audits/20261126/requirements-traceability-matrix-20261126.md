# 📋 Evidence & Test Coverage Register — 26 de noviembre de 2026

**Qué existe en código, qué está documentado, qué tests están escritos y cuáles se han ejecutado realmente.**  
Identificadores compartidos con la matriz principal y los demás anexos.

---
## Implementación en Código

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
## Tests Definidos (Escritos, no ejecutados)

| Test ID | Cubre | Casos Esperados | Estado |
|---------|-------|-----------------|--------|
| **TST-RAG-Q1** | Relevance gate + Citation validator | Q1: answer from PROTO-001; fontes contains PROTO-001 | **Propuesto** |
| **TST-RAG-Q2** | Relevance gate + Citation validator | Q2: answer from PROTO-002; fontes contains PROTO-002 | **Propuesto** |
| **TST-RAG-Q3** | RN-05.3: no relevant match | Q3: fixed notice; LLM mock invocado 0 times | **Propuesto** |
| **TST-RAG-PROTO-999** | RN-05.2: invalid citation | 200 notice; anti-hallucination counter incremented | **Propuesto** |
| **TST-RAG-sin cita** | RN-05 edge | 200 notice | **Propuesto** |
| **TST-RES: circuito abierto** | NFR-03, TST-RES | Circuit state OPEN; next call rejected without calling LLM; 503 | **Propuesto** |
| **TST-RES: timeout** | DEC-07 | 503 `LLM_UNAVAILABLE`; no hung request | **Propuesto** |
| **TST-RAG-edge: text sin cita** | RN-05 edge | 200 notice | **Propuesto** |
| **TST-IDM: idempotencia** | TST-IDM | Same key+same body → original response; key+different body → 409; no key → new patient | **Propuesto** |
| **TST-PRJ: proyección** | TST-PRJ | Counts equal fixture expectation; porNivel has five keys | **Propuesto** |
| **TST-RXN: BlockHound** | TST-RXN, Section 10 | No blocking violation on reactor threads | **Propuesto** |

---
## Evidencia de Ejecución (Inexistente en esta fase)

| Test ID | Resultado | Evidencia |
|---------|-----------|-----------|
| **Cualquier test TST-RES** | **No ejecutado** | No hay evidencia de ejecución en fase actual |
| **Cualquier test TST-RAG** | **No ejecutado** | No hay evidencia de ejecución en fase actual |
| **Cualquier test de circuito** | **No ejecutado** | No hay evidencia de ejecución en fase actual |
| **Integración docker-compose** | **No ejecutado** | Requiere Triage operando; fuera del alcance actual |

**Nota:** La fase actual es **solo lectura documental**. No se han ejecutado tests, por lo que todos los estados de test son `NOT_EXECUTED` o `PROPOSED`. No se eleva ningún estado por inferencia.

---
## Mapeo de Brechas (Gap Identified)

| ID | Brecha | Referencia | Impacto | Decisión Pendiente |
|----|--------|------------|---------|--------------------|
| **G-01** | LLM provider y embedding model not chosen | AMB-15, DEC-06, TC-07 | Blocks steps 2, 3, 7 and vector schema | Elegir proveedores y registrar dimensión |
| **G-02** | Relevance threshold, top-k and similarity metric not set | AMB-16, DEC-07, RN-05.4 | Gate determinism; Q1 and Q2 outcomes | Establecer métrica (coseno), k, threshold; calibrar con preguntas |
| **G-03** | Citation check scope: protocol or (protocol, section); corpus index or retrieved set | DEC-07, RN-05.1–2, D0 §9.8 | Step 8 result for edge cases | **(protocolo, seccion) in corpus index and retrieved set** |
| **G-04** | Structured LLM output format not defined | RN-05 edge, DEC-07 | Step 6 and 8 | **(JSON with respuesta and fontes array) any parse error es rejection** |
| **G-05** | Timeout values; circuit breaker parameters; failure definition; retry policy; retrieval error response | DEC-07, NFR-03, NFR-08, R-04 | Section 7 mapping and tests | **No retries; timeout configurable; failure = timeout, 5xx, connection, 429. Define 503 code for retrieval failure** |
| **G-06** | Error body for 400 (API-04) and 401 not defined; pregunta max length not defined; 503 example lacks correlationId (NFR-20) | NFR-11, NFR-20, DER-08 | Consistent error contract | **PROPOSED: VALIDATION_ERROR with field: "pregunta"; UNAUTHORIZED; max length; add correlationId to all errors** |
| **G-07** | API-05 503 has no error code and no detection rule | API-05, RF-04 B | Client contract | **PROPOSED: code PROYECCION_NO_DISPONIBLE; actualizadoEn = server time of last applied event (UTC)** |
| **G-08** | EVT-04 payload has no nivel. Count decrement depends on level stored from EVT-01 | DEC-03 (additive changes allowed), EVT-04 | Correct counts on discharge | **Resolved by protocol**: EVT-01 applied first and its nivel stored; no payload change required |
| **G-09** | DLT name, retry count and numeric gap timeout/map size not set | AMB-14, DEC-03, DER-03, R-08 | Consumer behavior; poison messages | **Adopt §5.3 and §8.3; DLT name triaje.eventos.dlq; numeric limits pending** |
| **G-10** | Consumer scope inconsistent | DEC-02, DEC-03 | What the Assistant subscribes to | **Resolved**: consume all four; EVT-02 advances ultimaSecuencia with no count effect |
| **G-11** | Redis data model ambiguous; atomic update not specified; store decision is DEC-10 / ADR-08; persistence mode not chosen; ProcessedEvent TTL value not set | D1 §11, DEC-10, DEC-03, DER-03, ADR-08 | Projection design; durability | **Adopt §8.2 and Lua protocol (§8.3); AOF proposed, TTL ≥ Kafka retention** |
| **G-12** | pgvector isolation and JDBC stack | TC-07, TC-11, TC-12, DER-06, R-03, ADR-04 | Blocking risk; startup; build | **Startup spike to check that R2DBC and JDBC auto-configuration coexist; ADR-04 with isolation design** |
| **G-13** | Kafka client model: skeleton has spring-kafka @KafkaListener (not reactive) | NFR-01, ADR-02, D0 §6 | Consumer threading and backpressure | **PROPOSED: ReactiveKafkaConsumerTemplate from spring-kafka, bounded prefetch** |
| **G-14** | pgvector isolation and JDBC stack: whether to keep Spring AI's store behind a bounded scheduler or use a custom R2DBC adapter | TC-07, TC-11, TC-12, DER-06, R-03, ADR-04 | Blocking risk; startup; build | **Startup spike; Flyway for schema; ADR-04 with isolation design** |
| **G-15** | Vector store table layout (FragmentoDocumental) not defined in detail | D1 §11 | Ingestion and retrieval filter by protocolo | **Define table and index in the ADR and in the migration** |
| **G-16** | Logging and metric content policy | NFR-06, NFR-19, DER-07, D0 §13 | Compliance with clinical-data handling; cardinality | **PROPOSED: never log question or answer text; log hashes or lengths; whitelist cited protocol IDs before tagging** |
| **G-17** | Cancellation of in-flight blocking LLM call not interruptible | D0 §6 | Resource use on disconnect | **Accept and document; or use provider's streaming API with reactive client if supported** |
| **G-18** | Version and documentation consistency | AMB-12, AMB-13, DEC-12, DEC-13, R-02, R-05 | Build and documentation | **Verify compatibility before first build; update D1** |
| **G-19** | Test infrastructure: no Testcontainers, no BlockHound | NFR-15, NFR-17, TST-RXN, TST-RAG, AMB-15 | Section 13 cannot run as written | **Add test dependencies; choose fake or local embeddings** |
| **G-20** | Deployment artifacts for this service | NFR-12, NFR-13, NFR-14, AMB-11, R-06, ADR-11 | Delivery | **Follow the platform plan** |
| **G-21** | API key design: key source, rotation, per-service key, filter position, public endpoints, 401 body | DEC-05, AMB-09, NFR-10, NFR-09 | Security tests; shared key handling | **PROPOSED: API key from environment variable, one key per service, constant-time comparison in a WebFlux filter** |
| **G-22** | Readiness with empty corpus and Redis/pgvector down | NFR-05, D0 §13 | Probes and rollout | **PROPOSED: readiness requires PostgreSQL and Redis; empty corpus is ready but logs a warning; LLM not in readiness** |

---
## Leyenda de Estados de Test y Evidencia

| Estado | Significado |
|--------|-------------|
| **NOT_DEFINED** | No existe test especificado para este comportamiento |
| **PROPOSED** | Test especificado pero **no implementado** en código |
| **IMPLEMENTED** | Existe código de test, **pero no ejecutado** |
| **EXECUTED** | Test ejecutado, **pero el criterio no ha sido verificado** (resultado PASS/FAIL pendiente) |
| **NOT_VERIFIED** | Existe evidencia, pero **el resultado no satisface el criterio** de aceptación |
| **PASS** | El test satisface el criterio de aceptación (evidencia completa) |
| **FAIL** | El test no satisface el criterio de aceptación |
| **BLOCKED** | El test no puede ejecutarse por dependencia sin resolver |

**Regla fundamental:** IMPLEMENTED no implica PASS; EXECUTED no implica que el criterio haya sido satisfecho.

---
## Mapeo Cruzado de Identificadores

Este documento comparte identificadores con:

- **requirements-traceability-matrix-20261126.md**: ID requisito (ej. RN-05.1, RN-05.3, RN-05.4) → enlace cruzado a decisión y criterio.
- **contradictions-tensions-register-20261126.md**: ID contradicción (ej. C05-01, C07-01, C09-02) → enlace cruzado a tensión documentada.
- **open-decisions-register-20261126.md**: ID decisión (ej. D05-01, D07-01, D09-01) → enlace cruzado a pregunta humana y consecuencias.

**Ejemplo de trazabilidad completa:** `RN-05.3` → `TST-RAG-Q3` (test) + evidencia de implementación (RAGService gate) + `C05-02` (precisión de dimensión) + `D05-03` (decisión humanade modelo/embeddings) + `G-02` (brecha de calibración).

---
---
**📋 Cuarto y último documento generado.** Todos los cuatro documentos están listos para revisión e incorporación, con identificadores compartidos, estados normalizados y sin decisiones arquitectónicas implícitas. La fase de generación documental está completada en modo solo lectura.