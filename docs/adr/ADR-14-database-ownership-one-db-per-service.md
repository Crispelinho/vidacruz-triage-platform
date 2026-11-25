# ADR-14: Database Ownership — One PostgreSQL Database per Service

**Status:** Proposed  
**Date:** 2026-11-25  
**Decision:** Triage and Clinical Assistant each have their own PostgreSQL database; Redis is used only for the Assistant's projection store.

---

## Context

The brief is silent on database layout. Two services are required (TC-09). The question is whether they:
1. Share a single PostgreSQL database (classic monolithic data model)
2. Each own their own PostgreSQL database (data ownership per service)
3. Use a hybrid approach (some shared, some private tables)

**Data flows:**
- **Triage:** Writes patients, vital signs, state, audit, outbox events, idempotency keys.
- **Clinical Assistant:** Reads events from Kafka, maintains a projection (`EstadoUrgenciaProyeccion`) in a fast store, reads protocol metadata and embeddings, writes rebuild history.
- **Cross-service communication:** Kafka events (async). No synchronous SQL calls across services.

**Non-functional requirements:**
- NFR-04: Triage never depends on Assistant availability (no synchronous HTTP or SQL calls to Assistant).
- Reactive programming (NFR-01): Both services use R2DBC (async driver), no blocking JDBC on the request path except at startup (Flyway migrations).

---

## Problem

A shared database creates:
1. **Tight coupling at the data layer.** Schema changes in one service may require coordination with the other.
2. **Scaling constraints.** Triage and Assistant may have different query patterns, indexing needs, and backup policies.
3. **Transaction scope ambiguity.** Outbox pattern (DER-02) requires atomic writes within one service's domain; a shared schema makes it unclear which service "owns" the transaction.
4. **Team scaling.** Two teams working on separate services should not contend for schema reviews and deployments.

One database per service avoids these but requires:
- Schema versioning (Flyway runs per service, per database).
- Event-driven eventual consistency (via Kafka).
- No distributed transactions (each service's transaction is local).

---

## Alternatives Considered

| Alternative | Pros | Cons |
|---|---|---|
| **One PostgreSQL per service** (recommended) | Loose coupling; independent scaling; clear ownership; supports reactive outbox pattern; aligns with microservice principles | Schema evolution must be coordinated via events; no synchronous cross-service queries; requires Kafka for all data flow |
| **Shared PostgreSQL database** | Simpler initial setup; synchronous queries possible; ACID across services | Tight coupling; schema changes coordinate across teams; not suitable for independent scaling; violates microservice principle of data ownership |
| **PostgreSQL + separate read-only replica for Assistant queries** | Triage owns writes; Assistant can query without blocking writes | Still one source-of-truth database; replication lag; Assistant still depends on Triage database uptime |

---

## Decision

**Each service owns its own PostgreSQL database.**

**Databases:**
1. **`vidacruz_triage`** (Triage service)
   - Tables: `Paciente`, `SignoVital`, `Clasificacion`, `AuditoriaClinica`, `OutboxEvento`, `IdempotencyKey`
   - Migrations: Flyway in `triage-service`
   - Owner: Triage team

2. **`vidacruz_assistant`** (Clinical Assistant service)
   - Tables: `DocumentoClinico`, `FragmentoDocumental`
   - Migrations: Flyway in `clinical-assistant-service`
   - Owner: Assistant team

**Shared infrastructure:**
- **Redis:** Used only by the Assistant for the urgency projection (`EstadoUrgenciaProyeccion`).
- **Kafka topic `triaje.eventos`:** Carries all events; Triage publishes, Assistant subscribes.
- **pgvector extension:** Loaded on Assistant database for embeddings (DER-06).

**Cross-service data access:**
- Triage → Assistant: via Kafka events (async).
- Assistant → Triage: **not allowed**. All data about patients is consumed from Kafka events.

---

## Consequences

### Positive
- **Data ownership is clear.** Each service owns and versioning its schema independently.
- **Loose coupling.** No schema coordination overhead between teams.
- **Reactive by design.** The outbox pattern (ADR-03) is naturally supported: Triage writes an OutboxEvento row in the same transaction as the state change, and a publisher drains it to Kafka.
- **Scalability.** Triage database can be tuned for high-write workloads; Assistant database can be tuned for fast lookups and embeddings.
- **Resilience.** Assistant database downtime does not block Triage; Triage database downtime does not prevent the Assistant from rebuilding its projection from the durable Kafka log (OD-3).

### Negative
- **No synchronous cross-service queries.** If the Assistant needs real-time patient state, it must ask Triage via HTTP (which violates NFR-04) or consume it from Kafka (eventual consistency).
  - **Mitigation:** The brief and NFR-04 explicitly forbid synchronous Assistant → Triage calls. This is by design.
- **Eventual consistency.** The Assistant's projection lags Kafka events by seconds to minutes, depending on message lag.
  - **Mitigation:** The projection is used only for a summary count in `/asistente/estado-urgencias`. Stale counts are acceptable for a 3-day prototype.
- **Migration coordination.** Triage and Assistant migrations are independent; breaking changes must be managed per service.
  - **Mitigation:** Each service uses Flyway; schema versions are independent. API versioning (if needed) is handled in Kafka contracts (DEC-03, schema evolution rules).

---

## Risks

- **R-07 (dual writes):** If Triage were to write directly to the Assistant database in addition to publishing events, we'd have a dual-write problem (data inconsistency if the event publish fails).
  - **Mitigation:** Triage writes **only** to `vidacruz_triage` and `triaje.eventos` (Kafka). The Assistant reads Kafka and writes to `vidacruz_assistant`. Outbox pattern (ADR-03) ensures atomicity for Triage writes.

- **R-08 (duplicate/out-of-order Kafka messages):** The Assistant must deduplicate events (ADR-02, DEC-03, `pacienteSequence` + `eventId`).
  - **Mitigation:** Idempotent consumer logic and deduplication set in Redis (DER-03).

---

## References

- Brief: TC-09 (two services), NFR-04 (Triage not dependent on Assistant availability)
- Decision register: DEC-10 (one database per service)
- Risk register: R-07 (dual writes), R-08 (duplicate messages)
- Related ADRs: ADR-03 (transactional outbox), ADR-02 (event envelope), ADR-08 (projection store)
- OpenSpec changes: 1 (platform setup), 5 (event contracts), 9 (projection), all others (service-specific)

---

## Linked Open Decision

**OD-5** (retention of `EventoHistorico`): Post-delivery decision. The durable audit history in Triage allows the Assistant to rebuild its projection even after losing Redis; the retention period of that history is a production SLA choice, not an architecture choice for this evaluation.
