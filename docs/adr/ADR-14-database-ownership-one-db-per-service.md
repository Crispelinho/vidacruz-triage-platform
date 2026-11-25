# ADR-14: Database Ownership — One PostgreSQL Database per Service

**Status:** Proposed  
**Date:** 2026-11-25  
**Decision:** Triage and Clinical Assistant each own their own PostgreSQL database. Where Redis is used (DEC-10 and `requirements.md` §11 name it, both Proposed), only the Assistant uses it, for the urgency projection and consumer deduplication state. Whether Redis is the projection store at all is decided by ADR-08, which is also Proposed.

---

## Context

This ADR depends on **ADR-01 (service decomposition: Triage and Clinical Assistant only)**, which is **Proposed**, not accepted. If ADR-01 changes, this ADR must be revisited. Each service owns its data: Triage owns patient and clinical state, the audit trail, the outbox and the durable event history; the Clinical Assistant owns the protocol corpus metadata, the embeddings and its projection state.

The brief is silent on database layout. Two services are required (TC-09). The question is whether they:
1. Share a single PostgreSQL database (classic monolithic data model)
2. Each own their own PostgreSQL database (data ownership per service)
3. Use a hybrid approach (some shared, some private tables)

**Data flows:**
- **Triage:** Writes patients, vital signs, classification, state, audit, outbox events, the durable event history `EventoHistorico` as documented in DER-09 and OD-3 (durable record selected by OPEN-09/OPEN-10, closed as Option A on 2026-11-25) and idempotency keys.
- **Clinical Assistant:** Reads events from Kafka, maintains the projection `EstadoUrgenciaProyeccion` and the `ProcessedEvent` records in the store decided by ADR-08 and ADR-02 (`requirements.md` §11 currently places them in Redis; Proposed), and reads protocol metadata and embeddings.
- **Cross-service communication:** Kafka events (async). No synchronous SQL or HTTP calls across services (NFR-04, RF-04-B).

**Non-functional requirements:**
- NFR-04: Triage never depends on Assistant availability (no synchronous HTTP or SQL calls to Assistant).
- NFR-01: request paths are non-blocking. Both services use R2DBC at runtime (TC-03). Two documented exceptions are outside the request path or isolated from it: Flyway migrations run through JDBC at startup (TC-12), and the pgvector store of Spring AI is JDBC-based and must be kept off the event loop (DER-06, TC-11, ADR-04).

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
| **Shared PostgreSQL database** | Simpler initial setup; synchronous queries possible; ACID across services | Tight coupling; schema changes coordinate across teams; not suitable for independent scaling; violates microservice principle of data ownership. This is the only alternative recorded in DEC-10 |
| **PostgreSQL + separate read-only replica for Assistant queries** | Triage owns writes; Assistant can query without blocking writes | Still one source-of-truth database; replication lag; Assistant still depends on Triage database uptime, which conflicts with the no-synchronous-dependency rule |

---

## Decision

**Proposed: each service owns its own PostgreSQL database.**

**Databases:**
1. **`vidacruz_triage`** (Triage service)
   - Tables (`requirements.md` §11): `Paciente`, `SignoVital` (may be columns of `Paciente`), `Clasificacion`, `AuditoriaClinica`, `OutboxEvento`, `EventoHistorico` (accepted durable record under OPEN-09/OPEN-10 Option A; the retained-outbox alternative was rejected), `IdempotencyKey`
   - Migrations: Flyway in `triage-service`
   - Owner: Triage service

2. **`vidacruz_assistant`** (Clinical Assistant service)
   - Tables: `DocumentoClinico`, `FragmentoDocumental` (pgvector)
   - Migrations: Flyway in `clinical-assistant-service`
   - Owner: Clinical Assistant service

The database names are working names, not part of the brief.

**Shared infrastructure (not shared data):**
- **Redis (candidate, not accepted):** `requirements.md` §11 and DEC-10 place `EstadoUrgenciaProyeccion` and `ProcessedEvent` in Redis. ADR-08 (Proposed) decides the store and layout; if it selects PostgreSQL, this infrastructure item disappears. This ADR only fixes that Triage never uses the Assistant's store.
- **Kafka topic `triaje.eventos`:** Carries all events; Triage publishes, Assistant subscribes.
- **pgvector extension:** Loaded on the Assistant database for embeddings (DER-06).

**Cross-service data access:**
- Triage → Assistant: via Kafka events (async).
- Assistant → Triage: **not allowed**. All data about patients is consumed from Kafka events. A full projection rebuild also stays event-based: Triage republishes its accepted durable record `EventoHistorico` (OD-3; OPEN-09/OPEN-10 Option A) to the topic; the Assistant never queries Triage.

---

## Verification

The following must pass before this ADR transitions from Proposed to Accepted:

- [ ] ADR-01 is Accepted (this ADR depends on its service boundary)
- [ ] Flyway migrations are independent per service (`triage-service/triage-service/src/main/resources/db/migration` and `clinical-assistant-service/clinical-assistant-service/src/main/resources/db/migration`; confirm the paths against the repository layout at acceptance time)
- [ ] No cross-service SQL queries exist in production code (schema ownership enforced: Triage never reads Assistant tables, Assistant never reads or writes Triage tables)
- [ ] Each service connects with its own database credentials; no credential grants access to the other service's database
- [ ] Kafka is the only cross-service data channel (via the ADR-03 transactional outbox; no direct HTTP or SQL calls between services for data)
- [ ] The accepted durable event record `EventoHistorico` (OPEN-09/OPEN-10 Option A) exists only in the Triage database; no copy of it is stored in the Assistant database
- [ ] CI/CD gate: schema changes reviewed and approved per-service ownership; deployment pipeline prevents mixed schema deployments

---

## Consequences

### Positive
- **Data ownership is clear.** Each service owns and versions its schema independently.
- **Loose coupling.** No schema coordination overhead between teams.
- **Reactive by design.** The outbox pattern (ADR-03) is naturally supported: Triage writes an OutboxEvento row in the same transaction as the state change, and a publisher drains it to Kafka.
- **Scalability.** Triage database can be tuned for high-write workloads; Assistant database can be tuned for fast lookups and embeddings.
- **Resilience.** Assistant database downtime does not block Triage. If the Assistant loses its projection store, it can rebuild within the Kafka retention window (30 days, OD-3) without Triage; beyond that window, a full rebuild needs Triage to republish `EventoHistorico` (accepted Option A), so it depends on the Triage database being available at that moment.

### Negative
- **No synchronous cross-service queries.** If the Assistant needs real-time patient state, it must consume it from Kafka (eventual consistency). A synchronous call to Triage is not an option (RF-04-B, NFR-04).
  - **Mitigation:** The brief and NFR-04 explicitly forbid synchronous calls between the services. This is by design.
- **Eventual consistency.** The Assistant's projection lags Kafka events by the consumer lag.
  - **Mitigation:** The projection is used only for a summary count in `/asistente/estado-urgencias`. Lag must be observable (ADR-02 consumer lag metric).
- **Migration coordination.** Triage and Assistant migrations are independent; breaking changes must be managed per service.
  - **Mitigation:** Each service uses Flyway; schema versions are independent. Breaking changes of the event contract are handled through `schemaVersion` (DEC-03).

---

## Risks

- **R-07 (dual writes):** If Triage were to write directly to the Assistant database in addition to publishing events, we would have a dual-write problem (data inconsistency if the event publish fails).
  - **Mitigation:** Triage writes **only** to `vidacruz_triage` and, through the outbox publisher, to `triaje.eventos`. The Assistant reads Kafka and writes to its own stores. The outbox pattern (ADR-03) makes the Triage state change and the event atomic.

- **R-08 (duplicate/out-of-order Kafka messages):** The Assistant must deduplicate events (ADR-02, DEC-03, `pacienteSequence` + `eventId`).
  - **Mitigation:** Idempotent consumer logic and `ProcessedEvent` deduplication records (DER-03). The backend of those records is part of ADR-08 and ADR-02, both Proposed.

- **Retention of `EventoHistorico`:** this is tracked as open decision **OD-5**, not as a risk entry. The risk register in `requirements.md` §17 (R-01…R-10) has no entry for it, and this ADR does not invent a Risk ID. If a risk entry is added to `requirements.md`, this ADR should cite it.

---

## References

- Brief: TC-09 (two services), NFR-04 (Triage not dependent on Assistant availability)
- Requirements: `requirements.md` §11 (persistence), DER-06, DER-09
- Decision register: DEC-10 (one database per service)
- Risk register: R-07 (dual writes), R-08 (duplicate messages)
- Related ADRs: ADR-01 (service decomposition), ADR-02 (event envelope and consumer), ADR-03 (transactional outbox), ADR-04 (pgvector JDBC isolation), ADR-08 (projection store)
- OpenSpec changes (`openspec-roadmap.md`): 1 `platform-foundation`, 2 `event-driven-integration`, 9 `emergency-state-projection`

---

## Linked Open Decisions

- **OD-3 (decided in documents, developer, 2026-11-24):** Kafka retains `triaje.eventos` for 30 days; `EventoHistorico` is the accepted source for full reconstruction (OPEN-09/OPEN-10 closed as Option A on 2026-11-25).
- **OD-5 (open):** retention period of `EventoHistorico`. The durable record is decided; its clinical retention period is not. No time-based purge until the developer sets it. It must be set before production use and does not block the 3-day evaluation scope.
- **OPEN-09/OPEN-10 (closed 2026-11-25) and G-T29 (closed, 7 days):** Option A accepted; `EventoHistorico` is the durable record and `OutboxEvento` is operational. See `docs/decisions/OPEN-09-10-outbox-durability-recovery.md`. OD-5 remains open.
