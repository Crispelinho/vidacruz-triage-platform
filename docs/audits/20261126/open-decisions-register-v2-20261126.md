# Open Decisions Register v2 — 2026-11-26

**Estado:** Actualización v2 (solo lectura de histórico + registro de decisiones humanas P-01..P-04).  
**No sobrescribe:** `open-decisions-register-20261126.md`.

## Regla de estado
- `CERRADA_POR_DECISION_HUMANA`: decisión tomada por humano en sesión; **no implica ADR formalmente ACCEPTED**.
- `PENDING`: requiere decisión humana.
- `DEFERRED`: pospuesta explícitamente.

## Decisiones

| ID | Pregunta / Tema | Decisión | Estado | Consecuencia sobre API-04 / implementación | Evidencia / Nota |
|---|---|---|---|---|---|
| **P-01 / D-GIT-01** | Manejo de backup y ramas remotas tras divergencia develop | Mantener backup local `backup/develop-local-pre-sync-20261126` (`ee2df39`); no borrar ramas remotas en `origin` hasta mergear PR #38 y PR #42 en `develop`; luego limpieza controlada única. | **CERRADA_POR_DECISION_HUMANA** | Evita pérdida de historial; PRs de auditoría/cambio 3 deben integrarse antes de cleanup remoto. | Sesión 2026-11-26; verificación git P-01. |
| **P-02 / D05-01** | Proveedor/modelo LLM chat | Cadena configurable `RAG_LLM_CHAIN=ollama,gemini,opencode`; `ollama` local vía Cloudflare Tunnel (`llama3.1:8b`/`mistral:7b`), `gemini-2.5-flash`, `opencode` endpoint compatible OpenAI (`glm-5.3`/`mistral-large-4`), `mock` para tests. | **CERRADA_POR_DECISION_HUMANA** | Desbloquea API-04 item 5/6 a nivel de decisión humana; falta implementación/config/verificación. | No eleva ADR-05 a ACCEPTED. |
| **P-02 / D05-02** | Modelo/estrategia de embeddings | Embeddings 768 dims: `nomic-embed-text` (Ollama) o cloud equivalente con `dimensions=768` (`text-embedding-004`/`text-embedding-3-small`). | **CERRADA_POR_DECISION_HUMANA** | G-01/G-02 parcialmente acotados; falta calibración de umbral y migración/esquema verificado. | Dimensión 768 fijada por decisión humana; validar compatibilidad pgvector. |
| **P-02 / D05-03** | Contrato de configuración | Config por env vars (`RAG_LLM_CHAIN`, `OLLAMA_BASE_URL`, `GEMINI_API_KEY`, `OPENCODE_API_KEY`, etc.). | **CERRADA_POR_DECISION_HUMANA** | Requiere actualizar `application.yaml`/manifests sin hardcode. | Alineado con NFR-09. |
| **P-03 / D07-01** | Mecanismo de autenticación | `X-API-Key` obligatoria en `/triage/**` y `/asistente/**`; excluir `/actuator/health`. | **CERRADA_POR_DECISION_HUMANA** | Desbloquea NFR-10/API-04 item 3 a nivel de decisión humana; falta filtro WebFlux. | No eleva ADR-07 a ACCEPTED. |
| **P-03 / D07-02** | Contrato 401 | 401 Unauthorized con JSON estructurado: `{ "error": "UNAUTHORIZED", "mensaje": "...", "timestamp": "..." }`. | **CERRADA_POR_DECISION_HUMANA** | G-21 se acota; debe alinearse con NFR-20/correlationId si el contrato global lo exige. | Definir regla final de campos obligatorios. |
| **P-03 / D07-03** | Gestión de secretos | Secretos solo por env vars / K8s Secrets (`API_KEY_SECRET`, `GEMINI_API_KEY`, `OPENCODE_API_KEY`, `OLLAMA_BASE_URL`); nunca hardcode en `application.yml`. | **CERRADA_POR_DECISION_HUMANA** | Requiere revisión de compose/k8s/.env.example. | Alineado con NFR-09. |
| **P-04 / D09-01** | Política de fallback LLM | Fallback en cadena por `rag.llm.chain` con CB por proveedor; 503 solo si todos fallan o todos abiertos. | **CERRADA_POR_DECISION_HUMANA** | Resuelve tensión 503 directo vs fallback; API-04-ERR-503 debe reflejar “fallo total”. | No eleva ADR-09 a ACCEPTED. |
| **P-04 / D09-02** | Parámetros Resilience4j | `slidingWindowSize=10` COUNT_BASED, `failureRateThreshold=50%`, `waitDurationInOpenState=30s`, `permittedNumberOfCallsInHalfOpenState=3`, `timeoutDuration=15s`. | **CERRADA_POR_DECISION_HUMANA** | G-05 parcialmente cerrado; falta aplicar en config y tests. | Por instancia/proveedor. |
| **P-04 / D09-03** | Clasificación de fallos | Contabilizan: timeout, IO/red, HTTP 5xx, 429. No contabilizan: 400 dominio/validación. | **CERRADA_POR_DECISION_HUMANA** | Ajusta criterios TST-RES; evita abrir CB por errores de cliente/dominio. | Documentar en código/config. |

## Decisiones aún pendientes (no cerradas por P-01..P-04)

| ID | Tema | Estado | Motivo |
|---|---|---|---|
| **D-RN05-01 / G-02** | Umbral de relevancia, top-k, métrica de similitud | **PENDING** | P-02 fija dimensión/modelos posibles, no calibración funcional. |
| **D-RN05-02 / G-03** | Alcance validación de citas (corpus index vs retrieved set) | **PENDING** | Requiere decisión funcional/seguridad. |
| **D-RN05-03 / G-04** | Formato estructurado salida LLM y política ante parse error | **PENDING** | Necesario para validador anti-alucinación. |
| **D-ADR-FORMAL** | Aceptación formal ADR-05/07/09 | **PENDING** | P-01..P-04 son decisiones humanas registradas; falta acta/estado ADR formal. |
| **D-API-401-NFR20** | Compatibilidad exacta 401 P-03 con NFR-20 (`code,message,field?,correlationId`) | **PENDING** | Evitar contratos de error inconsistentes. |