# 📋 Open Decisions Register — 26 de noviembre de 2026

> **Vigencia:** snapshot de 2026-11-26. Las decisiones humanas posteriores (D05-01/02, D-RN05-01/02/03, D-API-401-NFR20, OD-16, D-ADR-07/10) y la evidencia ejecutada el 2026-11-27 superan este registro. Estado vigente en `open-decisions-register-v2-20261126.md` y `../20261127/adr-acceptance-acta-20261127.md`. No se reescribe el snapshot.

**Decisiones humanas todavía necesarias, con sus consecuencias sobre API-04.**  
Identificadores compartidos con requirements-traceability-matrix-20261126.md y contradictions-tensions-register-20261126.md.  
Ninguna decisión ha sido aceptada; todas permanecen abiertas.

---
## 1. Decisiones Pendientes por ADR

| ID | Pregunta | Alternativas | Consecuencias sobre API-04 |
|----|----------|--------------|----------------------------|
| **D05-01** | ¿Se acepta Ollama local como proveedor? | Aceptar / Rechazar / Mantener evaluación | **Sin definición de proveedor**: items API-04 items 5 y 6 permanecen pending; dimensión 768 provisional; sin evidencia de capacidad Azure (ADR-11). |
| **D05-02** | ¿Qué modelo de chat se utilizará? | Selección explícita | **Sin modelo definido**: circuito y respuestas 503 dependen de implementación concreta; no hay evidencia de comportamiento con modelo real. |
| **D05-03** | ¿Qué modelo de embeddings y dimensión? | Selección con evidencia y plan migración | **Sin dimensión definida**: gate relevancia (G-02) incompleto; schema pgvector incompleto; calibración de umbral sin modelo referencia. |
| **D07-01** | ¿Se adopta API Key mediante X-API-Key? | Confirmar mecanismo | **Sin mecanismo**: NFR-10 no cumplido; endpoints `/asistente/**` y `/triage/**` desprotegidos; sin filtro WebFlux. |
| **D07-02** | ¿Cuál será el contrato 401? | Definir cuerpo y códigos | **Sin cuerpo 401 definido**: item API-04 item 3 incompleto; incoherencia con NFR-20; sin código de error estandarizado. |
| **D07-03** | ¿Cómo se gestionan secretos y rotación? | Definir política | **Sin política definida**: riesgo filtración (R-11); implementación con hardcodeo o vars undefined; incumplimiento NFR-09. |
| **D09-01** | ¿Circuit Breaker sin fallback o con Model B? | Seleccionar arquitectura | **Sin decisión**: item API-04 item 5 provisional; comportamiento 503 definido pero arquitectura indefinida; ¿503 directo o fallback Model B? |
| **D09-02** | Si se acepta Model B, ¿qué modelo será? | Selección explícita | **Sin modelo Model B**: equivalencia clínica (ADR-09-model-equivalence-validation.md) no validada; criterios de aceptación pendientes. |
| **D09-03** | ¿Qué parámetros y errores activan el circuito? | Definir valores y clasificación | **Sin parámetros**: circuito Resilience4j configurado pasivamente; valores G-05 undefined; ¿qué activa OPEN estado?. |
| **D09-04** | ¿Qué criterios clínicos permiten aceptar Model B? | Aprobar criterios de equivalencia | **Sin criterios**: validación Model B (ADR-09-model-equivalence-validation.md) no applicable; umbrales de equivalencia sin definir. |

---
## 2. Decisiones Pendientes por API-04

| ID | Pregunta | Alternativas | Consecuencias |
|----|----------|--------------|----------------|
| **API-04-01** | ¿El circuito protege activamente el puerto LLM? | Confirmar integración activa | **Circuit breaker configurado pero pasivo** en scaffold. No hay evidencia de integración activa al flujo de llamada LLM. |
| **API-04-02** | ¿El umbral de relevancia está calibrado? | Definir threshold y método | **G-02 pendiente**: umbral de similitud no fijado. La comprobación de lista vacía no satisface un umbral de similitud. |
| **API-04-03** | ¿Autenticación implementada en scaffold? | Confirmar filtro WebFlux | **No implementado**: no hay filtro WebFlux de autenticación en scaffold. No hay configuración `X-API-Key`. |
| **API-04-04** | ¿Validación de citas en dominio vs contrato REST? | Delimitación clara | **Dominio (no REST)**: `RAGService` valida citas, pero `RAGResponseDTO` no expone validación. Frontera sin cruzar. |
| **API-04-05** | ¿Comportamiento 503 con o sin fallback Model B? | Seleccionar política | **Provisional sin fallback**: comportamiento 503 definido pero **no implica ADR-09 aceptado**. Distinguir: contrato provisional 503 mientras arquitectura definitiva pendiente. |
| **API-04-06** | ¿Modelo definitivo bloqueado o definido? | Confirmar dependencias | **Bloqueado**: depende de ADR-05 (proveedor, modelos, dimensión), ADR-11 (capacidad Azure), y condicionalmente ADR-09 (si hay Model B). |

---
## 2. Decisiones que Permanecen Abiertas (Sin Aceptación)

Las siguientes decisiones **no se cerrarán durante esta fase**:

| Categoría | Decisión | Estado |
|-----------|----------|--------|
| **ADR-05** | Proveedor definitivo, modelo de chat, modelo de embeddings y dimensión | **Abierta** — Dos documentos paralelos sin supersesión explícita |
| **ADR-07** | Mecanismo principal propuesto, pero contrato 401, gestión de claves y detalles operativos pendientes | **Abierta** — Propuesta alineada en mecanismo, pero sin contrato aprobado |
| **ADR-09** | Política de degradación, fallback automático a Model B o 53 directo, definición de fallos y parámetros del circuito | **Abierta** — Tres alternativas documentadas, ninguna accepted |
| **RN-05 / C-06** | Umbral de relevancia, alcance de validación de citas y formato estructurado de salida | **Abierta** — Dependencias técnicas indirectas, sin bloqueo funcional, pero sin definición completa |

---
## 3. Consecuencias de Mantener Decisiones Abiertas

| Si no se decide | Consecuencia sobre API-04 |
|-----------------|---------------------------|
| **ADR-05 sin aceptar** | Items 5 y 6 de API-04 permanecen `PROPOSED` / `BLOCKED`; dimensión 768 provisional; sin proveedor definido |
| **ADR-07 sin aceptar** | NFR-10 no cumplido; endpoints desprotegidos; sin filtro WebFlux; cuerpo 401 undefined |
| **ADR-09 sin aceptar** | Item 5 de API-04 provisional (503 sin fallback confirmado); política de resiliencia indefinida; circuito pasivo en scaffold |
| **RN-05 / C-06 sin aceptar** | Umbral relevancia sin calibrar; validación de citas alcance indefinido; formato salida sin definir |

**Regla de no-elevación:** Ninguna propuesta, configuración pasiva o comportamiento provisional se elevará accidentalmente a decisión aceptada. Todas las decisiones humanas permanecen abiertas y documentadas.

---
---
**📋 Anexo 2 completado.** Registro de decisiones humanas abiertas con identificadores compartidos, listo para cruce con la matriz principal y el contradictions register.