# Acta de aceptación formal de ADR — 2026-11-27

**Fecha:** 2026-11-27
**Referencia de orden:** `docs/adr-roadmap.md` §3.3 (Order of acceptance)
**Regla aplicada:** D-ADR-FORMAL, `docs/audits/20261126/open-decisions-register-v2-20261126.md:41`

> Generar acta en el orden de `docs/adr-roadmap.md` §3.3 **sólo para ADRs con evidencia completa**; los demás permanecen `Proposed` con bloqueos nominales. La decisión **no autoriza** marcar ADRs como `Accepted` sin que pasen sus propias verificaciones.

Esta acta es un registro de evidencia, **no** un cambio de estado. Ningún ADR cambia de estado por este documento.

## Criterio de evidencia

Se considera "evidencia completa" únicamente cuando el cierre de la decisión consta en el registro de decisiones y, para los ADR con compuertas de verificación explícitas, cuando existe resultado ejecutado (XML de test, artefacto o salida de comando) que acredite cada compuerta. La existencia de código, tests fuente o configuración **no** constituye evidencia de ejecución.

El inventario de resultados ejecutados disponibles en el árbol al emitir esta acta es:

- `triage-service/triage-service/build/test-results/test/TEST-com.vidacruz.triageservice.OutboxPublisherIntegrationTest.xml`
- `triage-service/triage-service/build/test-results/test/TEST-com.vidacruz.triageservice.OutboxPublisherSchedulerIntegrationTest.xml`
- `clinical-assistant-service/clinical-assistant-service/build/test-results/test/TEST-com.vidacruz.clinical.infrastructure.adapters.CloudPromptPrivacyTest.xml`

Los dos primeros registraron `failures="3"` y `failures="1"` en el momento de la auditoría; por eso no se citan como pase en el dictamen original. El tercero registró `failures="0"`. Esta foto se sustituye por la ejecución final registrada en § Actualización post-ejecución.

## Actualización post-ejecución

Las ejecuciones finales se hicieron por unidad de trabajo, sobre ramas derivadas de `origin/develop` `fe2915e` y con JDK 21:

- `fix/blockhound-outbox-nonblocking`: triage 32 suites / 144 tests y clinical 44 suites / 219 tests, con 0 failures y 0 errors; los controles negativos de BlockHound pasaron en ambos módulos.
- `feat/rn05-grounded-answer-contract`: clinical 46 suites / 229 tests, con 0 failures y 0 errors.
- Árbol combinado previo al split en unidades de trabajo: clinical 47 suites / 230 tests y triage 31 suites / 137 tests, con 0 failures y 0 errors.
- GitHub Actions reran both modules on Ubuntu after the portability fix in `fix/blockhound-outbox-nonblocking` `ba8ed80`: [run 46032155156](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46032155156), success.

Los XML de outbox que en la foto inicial tenían `failures="3"` y `failures="1"` quedaron sin fallos en la ejecución final. Ninguno de estos resultados cierra compuertas distintas de OD-16 y TST-RXN; el dictamen por ADR no cambia.

## Dictamen por ADR en orden §3.3

| # | ADR | Estado del ADR | Evidencia | Dictamen |
|---:|---|---|---|---|
| 1 | ADR-01 — Service decomposition | `Proposed` | G-01 no cerrada; sin resultado ejecutado para NFR-04 ni RF-04-B | **Sin acta — bloqueado** |
| 2 | ADR-10 — Spring Boot platform and Java baseline | `Proposed` | R-02 cerrada por escrito y suites completas en verde; sin reporte de dependencias Boot/Cloud | **Sin acta — bloqueado** |
| 3 | ADR-14 — Database ownership | `Proposed` | Aislamiento Flyway y canal Kafka verificables estáticamente; sin compuerta CI de revisión de esquema | **Sin acta — bloqueado** |
| 4 | ADR-03 — Transactional outbox | `Proposed` | XML de outbox sin fallos tras corrección del bloqueo de Kafka; sin evidencia de lease concurrente, reclaim, backoff acotado ni alertado | **Sin acta — bloqueado** |
| 5 | ADR-02 — Assistant Kafka consumer | `Proposed` | Propuestas de configuración CA-G-18/CA-G-11 sin aceptar; retención de 30 días no ejecutable en este entorno | **Sin acta — bloqueado** |
| 6 | ADR-13 — SLA evaluation | `Proposed` | XML de `TriageSLAEscalationIntegrationTest` en verde; G-T27/G-T28 pendientes y TST-ESC sin ejecutar | **Sin acta — bloqueado** |
| 7 | ADR-07 — Authentication via API key | `Accepted` | Cierre D-ADR-07 en `open-decisions-register-v2-20261126.md:39`; `X-API-Key` definitivo, JWT rechazado; implementado en ambos servicios | **ACTA EMITIDA** (ver § Acta emitida) |
| 8 | ADR-12 — Idempotency key | `Proposed` | XML de `TriageIdempotencyEvidenceTest` en verde; limpieza a 24 h deshabilitada y dependencias G-08/G-07/ADR-03 pendientes | **Sin acta — bloqueado** |
| 9 | ADR-04 — pgvector JDBC isolation | `Proposed` | Control negativo de BlockHound PASS; sin evidencia de recuperación pgvector real ni carga p95 < 200 ms | **Sin acta — bloqueado** |
| 10 | ADR-05 — Assistant LLM provider | `Proposed` | OD-16 PASS, TST-RXN PASS y TST-RAG PASS ([run 46045071910](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46045071910)); evidencia cruda y replicación en `docs/audits/20261127/gemini-embedding-tst-rag-verification.md`; R-07 (chat), R-06 y spike de configuración sin verificar | **Sin acta — bloqueado** |
| 11 | ADR-09 — Circuit breaker / degraded mode | `Proposed` | Sin XML de TST-RES, frontera sin coincidencia ni métricas | **Sin acta — bloqueado** |
| 12 | ADR-08 — Urgency projection store | `ACCEPTED — AMENDED` | Aceptación acotada a Redis+Lua; TST-PJ, edge cases y 503 por Redis indisponible sin ejecutar | **Sin acta de verificación — bloqueado** |
| 13 | ADR-06 — Notification channel | `Superseded` | ADR-15 (`Accepted`) supersede ambos borradores y prohíbe implementar el webhook | **Sin acta — no elegible** |
| 14 | ADR-11 — Azure target | `Proposed` | R-06 sin artefacto; SKU IaC `Standard_B4ms` ≠ `Standard_B4as_v2`; sin alertas de presupuesto ni escaneo de secretos | **Sin acta — bloqueado** |

## Acta emitida

### ADR-07 — Authentication — API Key via HTTP Header

- **Estado:** `Accepted` (estado preexistente, no modificado por esta acta).
- **Cierre de decisión:** D-ADR-07 en `docs/audits/20261126/open-decisions-register-v2-20261126.md:39`: `X-API-Key` es definitivo y JWT queda rechazado.
- **Documento canónico:** `docs/adr/ADR-07-authentication-api-key.md`. El borrador supersedido `ADR-07-authentication.md` no se usa como referencia.
- **Contrato de error 401:** cuerpo canónico `{code, message, correlationId}` con cabecera `X-Correlation-Id` (D-API-401-NFR20), implementado y testeado en ambos servicios y alineado en `docs/services/triage-service.md` y `docs/services/clinical-assistant-service.md`.
- **Alcance de la evidencia:** cierre de decisión e implementación. El ADR canónico no define una sección §Verification con compuertas ejecutables, por lo que no se alega pase de compuertas de test para este ADR.

## Bloqueos nominales (sin acta)

Los ADR restantes permanecen en su estado actual. Los bloqueos se registran por nombre, sin inventar resultados ni fechas:

- **G-01, NFR-04 y RF-04-B** (ADR-01); TST-RXN ya pasó pero no cierra estas compuertas.
- **Reporte de dependencias Boot/Cloud** (ADR-10); R-02 está cerrada y las suites están en verde, pero no equivale al pase de sus compuertas.
- **Compuerta CI de revisión de esquema / despliegue mixto** (ADR-14).
- **Outbox: lease concurrente, reclaim, backoff acotado y alertado** (ADR-03); el XML de G-T25 ya está sin fallos.
- **Aceptación de CA-G-18/CA-G-11 y ejecutabilidad de la retención de 30 días** (ADR-02).
- **G-T27, G-T28, T-11/T-12 y TST-ESC** (ADR-13); el XML de SLA está en verde.
- **Configuración de limpieza a 24 h y dependencias G-08/G-07/ADR-03** (ADR-12); el XML de idempotencia está en verde.
- **Recuperación real pgvector y carga p95 < 200 ms** (ADR-04); el control negativo de TST-RXN ya pasó.
- **R-07 (endpoint/id, latencia y coste del nodo de chat), spike de configuración y artefacto ADR-11** (ADR-05). OD-16, TST-RXN y TST-RAG tienen PASS aislados y no cierran el ADR. TST-RAG verificó `gemini-embedding-2` con 5/5 por sonda en [run 46045071910](https://github.com/Crispelinho/vidacruz-triage-platform/actions/runs/46045071910); el coste no se midió y el nodo de chat no participó en esa ejecución.
- **TST-RES, frontera sin coincidencia y métricas** (ADR-09).
- **TST-PJ, edge cases, cutover, 503 por Redis y métricas NFR-19** (ADR-08).
- **Conflicto ADR-15: no implementar webhook y no marcar ADR-06 como `Accepted`** (ADR-06).
- **Artefacto R-06, corrección de SKU a `Standard_B4as_v2`, alertas 50 %/80 %, comprobación de esquema y escaneo de secretos** (ADR-11).

## Declaración final

- No se ha cambiado el estado de ningún ADR.
- ADR-06 sigue `Superseded` y no se implementa el receiver webhook.
- ADR-15 no se modifica.
- `top-k=4` y el objetivo `0.65` quedan confirmados para `gemini-embedding-2` por TST-RAG (mínimo positivo 0.7473, negativo 0.5842). El umbral operacional permanece `0.20` porque producción sigue usando el adaptador léxico y el corpus sembrado no fue reembedido; habilitar el modelo semántico exige reingesta completa.
- Esta acta queda sujeta a la evidencia disponible en la fecha indicada; si se ejecutan las compuertas pendientes, debe reemitirse en el mismo orden §3.3.
