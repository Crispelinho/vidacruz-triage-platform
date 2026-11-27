# Progreso de implementación — 2026-11-26

## Alcance

Trabajo sobre `develop 839ef60` en la rama `feat/triage-report-outbox`.
No modifica el veredicto de la fuente, la disposición del Developer ni el estado de ADR-05/07/09/10/11/12.
D7 permanece `OPEN — unrecorded`; las compuertas C-05 y los ADR distintos de ADR-08 permanecen sin cerrar.

## Commits locales

- `33d901c` — `feat(triage): implement idempotency replay and API contract errors`
- `207a8a0` — `fix(triage): wire opt-in scheduler configuration`
- `87f9b0b` — `feat(triage): publish outbox envelope with reactive Kafka sender`
- `cd7e5ca` — `fix(triage): persist assigned-id aggregates and serialize idempotency replay`

## Implementado con verificación ejecutada

| Ítem | Estado | Evidencia |
|---|---|---|
| TST-IDM (API-01) | Implementado; replay 201, reuso 409, sin clave crea paciente, validación fallida no reserva clave | Tests unitarios de servicio y controller; request principal `snake_case` con alias `camelCase`; cuerpo NFR-20 con `errors[]` y `correlationId` |
| DEC-15 / ADR-12 storage | Implementado; tabla `idempotency_key`, hash canónico, expiración 24 h y limpieza opt-in | `V3__idempotency_key.sql`, `IdempotencyCleanupScheduler`; `TriageIdempotencyEvidenceTest` cubre reserva concurrente, replay con `FOR UPDATE` y `deleteExpired` |
| Contrato Kafka EVT-01…04 | Implementado; envelope plano `eventId`, `eventType`, `schemaVersion`, `pacienteId`, `pacienteSequence`, `occurredAt`, `correlationId` junto a `payload` | `OutboxEventWriterTest`; correlación propagada desde `X-Correlation-Id` al contexto Reactor |
| Publicación outbox (G-T25) | Implementado; `KafkaTemplate`, `Mono.defer`/`Mono.fromFuture(..., false)`, temporizador reactivo, sin `.block()` ni `reactor-kafka` | `OutboxPublisherTest`; `TriageRollbackEvidenceTest` prueba rollback forzado; **sin prueba de integración Kafka real, reintento tras reinicio ni caída del broker** |
| RF-05 / API-06 | Ya implementado en `develop` por PR #54 | `TriageReportEvidenceTest` agrega fixture calculado a mano y `EXPLAIN (ANALYZE, FORMAT JSON)` sobre la SQL real del feature |
| Índices RF-05 | Agregados | `V4__report_indexes.sql`; el plan usa `idx_paciente_fecha_registro` e `idx_auditoria_tipo_evento_creado_en`; `idx_auditoria_paciente_creado_en` proviene de `V1` |

La suite del módulo `triage-service` quedó verde en la última ejecución: 100 tests, 0 fallos, 0 errores, 0 omitidos.

Durante la evidencia DB se corrigió un defecto real: con UUID asignados por la aplicación, `R2dbcRepository.save()` emitía `UPDATE` en vez de `INSERT` para paciente, auditoría y outbox. Las tres entidades ahora implementan `Persistable` con un flag `@Transient` fuera del constructor de persistencia. El replay concurrente usa `SELECT ... FOR UPDATE` sobre `idempotency_key` para esperar a que la transacción ganadora publique su respuesta.

Al integrar con el `develop` actual se renumeró la migración de idempotencia a `V3` porque `V2` ya pertenece a `medical_alert` (PR #56), y se agregó `V4` para los índices de RF-05.

## Pendientes que NO se elevan por inferencia

- **D7**: requiere umbrales numéricos de liveness, resultado aceptable de spike, aserción por opción, owner y criterio verificable.
- **C-05 / BlockHound (TST-RXN)**: `NOT AUTHORIZED`; requiere autorización nombrada, versión fijada y paquete `blockhound-junit-platform`.
- **Compose completo**: falta decisión sobre el webhook mock (ADR-06) y evidencia de `docker compose up` con checks de salud.
- **Kubernetes `00-04 + KEDA`**: la numeración y el requisito exacto de autoscaling no están trazables en el repositorio actual; no se inventan nombres ni KEDA.
- **ADR-10 / ADR-11 / Azure URL**: requieren aceptación del riesgo EOL de Boot 3.5 y evidencia R-06 de acceso, región y cuota. No se infiere URL.
- **Outbox evidencia final**: el rollback forzado ya está cubierto; falta prueba de integración con Kafka real, reintento tras reinicio y caída del broker.
