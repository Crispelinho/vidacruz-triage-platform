# 📋 Contradictions & Tensions Register — 26 de noviembre de 2026

**Identificadores compartidos con requirements-traceability-matrix-20261126.md y open-decisions-register-20261126.md.**  
Las entradas documentan contradicciones reales, tensionesdocumentales y diferencias de nivel —no todas las diferencias son contradicciones.

---
## 1. Contradicciones Reales (Architectural Divergences)

| ID | Contradicción | Documentos afectados | Especificación | Estado |
|----|---------------|---------------------|----------------|--------|
| **C05-01** | Ollama como proveedor frente a Spring AI configurable | ADR-05 × 2 (asmAssistant-llm-provider.md y llm-provider-spring-ai.md) | **No es necesariamente contradicción**: Separar decisión de framework (DEC-06/ADR-05-llm-provider-spring-ai.md = capa de abstracción) de la de proveedor (ADR-05-assistant-llm-provider.md Option D = Ollama local dentro del perímetro Azure). Framework y proveedor son decisiones de niveles distintos. | **Pendiente** — Sin decisión aceptada sobre alcance de proveedores |
| **C05-02** | Dimensión fija en migración frente a dimensión pendiente | ADR-05 + scaffold | **No afirmar contradicción sin comprobar**: 768 puede ser provisional (migración V3) sin ser decisión vinculante. Requerir evidencia explícita de que 768 es definitivo. | **Pendiente** — Sin evidencia de dimensión vinculante |
| **C07-01** | Ejemplo de cuerpo 401 frente a contrato pendiente | ADR-07 × 2 | **Tensión documental, no contradicción automática**: Diferir nivel de especificación (propuesta vs. contrato aprobado). G-21 pendiente hasta definición NFR-20. | **Pendiente** — Sin contrato 401 aprobado |
| **C09-01** | Un circuito frente a dos circuitos con router | ADR-09 × 3 (degraded-mode, proposal, architecture-diagram) | **Contradicción arquitectónica real** entre alternativas: Degraded-mode propone circuito único + 53 directo; Proposal propone dos circuitos + router a Model B; Architecture-diagram muestra arquitectura con fallback. Son alternativas incompatibles, no variantes de configuración. | **Pendiente** — Sin aceptación de ninguna opción |
| **C09-02** | 503 directo frente a fallback Model B | ADR-09 × 3 | **Alternativas de comportamiento incompatibles si se aplican al mismo escenario de fallo**: 503 directo = respuesta indisponibilidad; fallback Model B = estrategia selección proveedor. No son equivalentes, pero pueden coexistir en políticas diferentes. La incompatibilidad depende del escenario y política seleccionada. | **Pendiente** — Sin política de degradación aceptada |

---
## 2. Tensiones Documentales (Diferencias de Nivel y Exposición)

| ID | Tensión | Documentos afectados | Motivo | Tratamiento |
|----|---------|---------------------|--------|-------------|
| **T05-01** | Ollama exclusivo vs. proveedores configurables | ADR-05 × 2 | El documento más reciente propone Ollama dentro del perímetro Azure y descarta OpenAI API directa y Anthropic para este alcance. El documento paralelo describe una configuración genérica y utiliza OpenAI como valor por defecto. | **Registrar como tensión**: No son decisiones equivalentes; arquitectura multi-proveedor no implica aprobación de ninguno para producción. |
| **T05-02** | Modelo y dimensión pendientes frente a configuración genérica previa | ADR-05 × 2 | Ambos documentos proponen Spring AI, pero el framework y el proveedor son decisiones distintas. El documento nuevo dice que Spring AI 1.1.1 está fijado por DEC-12 y que las claves se inspeccionaron estáticamente; no afirma que el runtime se haya verificado. | **Registrar como tensión**: Dependencia técnica indirecta, no bloqueante. |
| **T07-01** | Contrato 401 de ejemplo frente a contrato aún pendiente | ADR-07 × 2 | El documento más reciente deja abierto el cuerpo exacto de la respuesta 401 y exige alinearlo con NFR-20. El documento antiguo presenta un ejemplo concreto (code, message, correlationId). Ese ejemplo no debe tratarse como contrato aprobado. | **Registrar como tensión**: Diferir definición formal. |
| **T07-02** | Claves de prueba por defecto frente a secretos externos | ADR-07 × 2 | El documento nuevo exige variables de entorno o configuración de secretos y prohíbe claves hardcodeadas. El antiguo admite propiedades de configuración y ejemplos de claves de prueba por defecto. | **Registrar como tensión**: Evitar que ejemplos se conviertan en valores reales. |
| **T07-03** | Allowlist de salud no consolidada | ADR-07 × 2 | Uno enumera /health, /health/ready y /health/live; el otro habla de /actuator y probes. | **Registrar como tensión**: Consolidar allowlist concreta que coincida con endpoints reales. |
| **T09-03** | Valores numéricos de la matriz antigua frente a valores pendientes | ADR-09 × 2 | La matriz antigua fija 5s timeout, 3 fallos y 60s de espera. El documento nuevo deja estos valores pendientes y no incluye fallback automático. | **Registrar como tensión**: Los números antiguos no son decisiones vigentes. |
| **T09-04** | Umbrales generales de equivalencia frente a cero tolerancia clínica | ADR-09 × 2 | El documento de equivalencia exige comparar ambos modelos en precisión de retrieval, citas, latencia, corrección clínica y ausencia de alucinaciones. Un porcentaje global no debe permitir compensar un fallo clínico crítico con buenos resultados en otras consultas. | **Registrar como tensión**: Criterios no compensables definidos después. |
| **T09-05** | Subdecisión readiness atribuida como aceptada | ADR-09 + roadmap | El documento degraded-mode declara expresamente que el LLM nunca participa en readiness y que el servicio puede estar listo aunque el proveedor no esté disponible. Pero el ADR completo sigue en estado Proposed. No he encontrado en los PR inspeccionados una aprobación independiente que permita elevar el ADR completo a Accepted. | **Registrar como tensión**: Subdecisión atribuida pero ADR no aceptado. |
| **T09-06** | Retrieval y citas mezclados con resiliencia | ADR-09 + C-06 | El documento nuevo excluye la relevancia y validación de citas del alcance de ADR-09, dejándolas en C-06 bajo RN-05 y R-10. La matriz antigua incluye esos temas dentro de sus capas de mitigación y los marca como P1. | **Registrar como tensión**: **Frontera clara**: ADR-09 decide resiliencia proveedor LLM; C-06 decide reglas evidencia clínica. No se deben mezclar. |

---
## 3. Diferencias de Nivel Documental (No Son Contradicciones)

| ID | Diferencia de nivel | Documentos afectados | Justificación |
|----|---------------------|---------------------|---------------|
| **L05-01** | Ollama como proveedor frente a Spring AI configurable | ADR-05 × 2 | **Complementariedad, no contradicción**: Spring AI puede ser la capa de abstracción y Ollama el proveedor seleccionado. Son decisiones de niveles distintos. |
| **L05-02** | Dimensión fija en migración frente a dimensión pendiente | ADR-05 + scaffold | **Sin afirmar contradicción**: 768 puede ser provisional (migración V3) sin ser decisión vinculante. Requerir evidencia explícita. |
| **L07-01** | Ejemplo de cuerpo 401 frente a contrato pendiente | ADR-07 × 2 | **Clasificar como tensión documental**: No convertir ejemplo en contrato aprobado. G-21 debe permanecer pendiente. |
| **L09-01** | Uno circuito frente a dos circuitos (documentación) | ADR-09 × 3 | **Documentación de alternativas**: El diagrama arquitectónico muestra fallback a Model B; el degraded-mode lo propone sin fallback. Documentar ambas sin elevar ninguna a accepted. |
| **L09-02** | 503 directo frente a Model B (contexto) | ADR-09 × 3 | **Distingir contextos**: 503 directo = indisponibilidad; fallback Model B = estrategia selección. Dependencia del escenario y política seleccionada. |

---
## 3. Aclaraciones Metodológicas

1. **Para diferir:** Una entrada aparece en la sección "Contradicciones Reales" solo si hay **conflicto técnico** entre decisiones que no pueden coexistir en el mismo sistema. Si pueden coexistir (aunque sean opciones mutuamente excluyentes para un escenario determinado), aparece en "Tensiones Documentales" o "Diferencias de Nivel".

2. **No todas las diferencias documentales son contradicciones:** Las tensiones documentales (T-xx) registran diferencias de enfoque, nivel de especificación o detalle de implementación sin implicar conflicto técnico directo. Las diferencias de nivel (L-xx) registran variaciones en el mismo tema sin implicar conflicto.

3. **El renombrado es intencional:** Pasar de "Contradictions Register" a "Contradictions & Tensions Register" evita presentar todas las diferencias documentales como contradicciones reales, manteniendo el rigor diagnóstico.

---
---
**📋 Anexo 1 completado.** Registro de contradicciones y tensiones con identificadores compartidos, listo para ser cruzado con la matriz principal y el open-decisions-register.