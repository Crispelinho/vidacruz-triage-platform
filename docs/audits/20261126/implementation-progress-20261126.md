# Progreso de implementación — 2026-11-26

## Alcance

Registro de trabajo realizado sobre `develop 3352fd0` y la rama `feature/triage-report-outbox`.
No modifica el veredicto de la fuente, la disposición del Developer ni el estado de ADR-05/07/09/10/11/12.
D7 permanece `OPEN — unrecorded`; las compuertas C-05 y los ADR distintos de ADR-08 permanecen sin cerrar.

## Commits locales

- `1d6409c` — `feat(triage): implement idempotency replay and API contract errors`
- `37b44fc` — `fix(triage): wire opt-in scheduler configuration`
- `d869a3b` — `feat(triage): publish outbox envelope with reactive Kafka sender`
- `92f81ff` — `feat(triage): add RF-05 SQL operations report`

## Implementado con verificación ejecutada

| Ítem | Estado | Evidencia |
|---|---|---|
| TST-IDM (API-01) | Implementado; replay 201, reuso 409, sin clave crea paciente, validación fallida no reserva clave | Tests unitarios de servicio y controller; request principal `snake_case` con alias `camelCase`; cuerpo NFR-20 con `errors[]` y `correlationId` |
| DEC-15 / ADR-12 storage | Implementado; tabla `idempotency_key`, hash canónico, expiración 24 h y limpieza opt-in | Migración `V2__idempotency_key.sql`, `IdempotencyCleanupScheduler`; **sin prueba DB de concurrencia ni de expiración** |
| Contrato Kafka EVT-01…04 | Implementado; envelope `eventId`, `eventType`, `schemaVersion`, `pacienteId`, `pacienteSequence`, `occurredAt`, `correlationId`, `payload` | `OutboxEventWriterTest`; correlación propagada desde `X-Correlation-Id` al contexto Reactor |
| Publicación outbox (G-T25) | Implementado; `KafkaTemplate`, `Mono.defer`/`Mono.fromFuture(..., false)`, temporizador reactivo, sin `.block()` ni `reactor-kafka` | `OutboxPublisherTest`; **sin prueba de integración Kafka real** |
| RF-05 / API-06 | Implementado; rangos UTC inclusivos, límite 90 días naturales, agregación SQL con `ROW_NUMBER`, `FILTER`, `AVG` y `percentile_cont` | `OperationReportServiceTest`, prueba de contrato 400; **sin fixture calculado a mano ni `EXPLAIN ANALYZE`** |
| Índices RF-05 | Implementados | Migración `V3__report_indexes.sql` |
| Configuración de schedulers | Implementada; intervalos opt-in y parametrizados | `application.yaml` |

La suite del módulo `triage-service` quedó verde en la última ejecución. Un intento previo falló por timeout transitorio de arranque de Testcontainers y pasó al repetirse.

## Pendientes que NO se elevan por inferencia

- **D7**: requiere umbrales numéricos de liveness, resultado de spike aceptable, criterio de convergencia por opción, owner y criterio verificable en primera persona.
- **BlockHound / TST-RXN**: C05 exige autorización nominada separada, pin de versión, `blockhound-junit-platform`, negativo/positivo control y XML filtrado por tag; la autorización general de tests no la cubre.
- **Tag-filter `integration`**: requiere demostración C18 con XML no vacío; no ejecutada.
- **TST-RAG / PROTO-001…003**: bloqueado por modelo y dimensión de embeddings, umbral/top-k/métrica, alcance de citas, formato estructurado y formato de corpus. No se inventan esos valores.
- **TST-RES**: circuito y timeout están implementados, pero falta evidencia HTTP 503 integral y ADR-09 sigue `Proposed`.
- **API-04 `/asistente/consulta` y API key**: bloqueado por el conflicto de forma 401 entre P-03 y NFR-20; no se elige esquema sin disposición.
- **TST-PRJ / ADR-08**: bloqueado por D2, D6 y compuertas C-05; no se elige backend/TTL/durabilidad por inferencia.
- **Compose completo**: falta decisión sobre el webhook mock (ADR-06) y evidencia de `docker compose up` con checks de salud.
- **Kubernetes `00-04 + KEDA`**: la numeración y el requisito exacto de autoscaling no están trazables en el repositorio actual; no se inventan nombres ni KEDA.
- **ADR-10 / ADR-11 / Azure URL**: requieren aceptación del riesgo EOL de Boot 3.5 y evidencia R-06 de acceso, región y cuota. No se infiere URL.
- **RF-05 evidencia final**: falta fixture calculado a mano y plan `EXPLAIN ANALYZE` sobre los tres índices.
- **Outbox evidencia final**: falta prueba de integración con Kafka real, rollback forzado, reintento tras reinicio y caída del broker.
