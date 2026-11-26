# Contradictions & Tensions Register v2 — 2026-11-26

**Estado:** Actualización v2.  
**No sobrescribe:** `contradictions-tensions-register-20261126.md`.  
**Regla:** Distingue contradicción real, tensión documental y diferencia resuelta por decisión humana. `CERRADA_POR_DECISION_HUMANA` no implica ADR formalmente ACCEPTED.

## Registro

| ID | Tipo | Tema | Documentos/fuentes | Estado v2 | Resolución / posición | Impacto |
|---|---|---|---|---|---|---|
| **C05-01** | Tensión | Ollama vs multi-proveedor | ADR-05-assistant-llm-provider.md, ADR-05-llm-provider-spring-ai.md, P-02 | **CERRADA_POR_DECISION_HUMANA** | Se adopta cadena configurable `ollama,gemini,opencode` (con mock tests). Framework/provider se mantienen como niveles distintos; ADR-05 sigue PROPOSED formalmente. | Desbloquea G-01 a nivel decisión; falta implementación/config. |
| **C05-02** | Tensión | Dimensión embeddings fija vs pendiente | ADR-05, migración V3, P-02 | **CERRADA_POR_DECISION_HUMANA** | Dimensión 768 fijada por decisión humana. Falta validar migración/esquema/ingesta real. | Desbloquea parte de G-01/G-15; G-02 sigue pendiente. |
| **C07-01** | Tensión | Ejemplo 401 vs contrato pendiente | ADR-07, G-21, NFR-20, P-03 | **CERRADA_POR_DECISION_HUMANA** | Contrato 401 definido: `{error,mensaje,timestamp}`. Queda pendiente alinear con NFR-20/correlationId. | Desbloquea D07-02; requiere decisión menor de compatibilidad. |
| **C07-02** | Tensión | Claves de prueba/hardcode vs secretos externos | ADR-07, NFR-09, P-03 | **CERRADA_POR_DECISION_HUMANA** | Secretos solo por env vars/K8s Secrets; prohibido hardcode en `application.yml`. | Reduce R-11; falta auditoría de manifests. |
| **C09-01** | Contradicción real (histórica) | Un circuito/503 directo vs fallback Model B | ADR-09 docs, P-04 | **CERRADA_POR_DECISION_HUMANA** | Se elige política intermedia explícita: fallback en cadena con CB por proveedor; 503 solo si fallan todos. No es “Model B único” ni “503 al primer fallo”. | Actualiza API-04-ERR-503, TST-RES; ADR-09 formal sigue PROPOSED. |
| **C09-02** | Contradicción real (histórica) | 503 directo vs fallback automático | ADR-09 docs, P-04 | **CERRADA_POR_DECISION_HUMANA** | 503 final solo ante fallo total. Fallback automático interno permitido/esperado según cadena. | Debe reflejarse en tests y observabilidad (`providerUsed`, estado CB). |
| **T09-03** | Tensión | Valores numéricos antiguos vs pendientes | ADR-09 matriz antigua, P-04 | **CERRADA_POR_DECISION_HUMANA** | P-04 fija 10/50%/30s/3/15s. Valores antiguos no vigentes. | Cierra G-05 parcialmente. |
| **T09-04** | Tensión | Equivalencia clínica Model B | ADR-09 equivalence, P-04 | **DEFERRED** | Con cadena multi-proveedor, la equivalencia clínica/modelos aceptables sigue requiriendo criterio humano si se usa fallback clínico. | No bloquea P-04; sí bloquea aceptación clínica final. |
| **T09-06** | Diferencia de alcance | Resiliencia LLM vs validación citas/relevancia | ADR-09, RN-05/C-06 | **VIGENTE** | ADR-09 cubre resiliencia proveedor; RN-05/C-06 cubren relevancia/citas/anti-alucinación. | Evita mezclar G-02/G-03 con G-05. |
| **T-RN05-01** | Tensión | Lista vacía vs umbral de relevancia | RN-05.3/RN-05.4, G-02 | **PENDING** | RN-05.3 debe evaluarse en 3 escenarios; no basta lista vacía. | P-09 debe implementar/validar. |
| **T-RN05-02** | Tensión | Citas en corpus vs citas en contexto recuperado | RN-05.1/5.2, G-03 | **PENDING** | Definir alcance de validación y formato estructurado. | P-09. |