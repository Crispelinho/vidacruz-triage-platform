# ADR-04: Vector store and JDBC isolation for the Clinical Assistant

- **Status:** Proposed. Not accepted. Option A is the proposed route; the spike in §Verification is still required.
- **Date:** 2026-11-25
- **Deciders:** developer (pending)
- **Scope:** C-06 retrieval path of `POST /asistente/consulta`. This ADR chooses the vector store access model and how the blocking JDBC call is isolated. It does not choose the LLM or embedding provider (ADR-05), and it does not define the relevance gate or citation validation (RN-05, R-10, C-06).
- **Related:** DER-06, TC-11, TC-12, NFR-01, NFR-08, R-03, G-14, G-15, G-19, CA-G-18, DEC-06, DEC-07, ADR-05, TST-RXN, TST-RAG in [clinical-assistant-service.md](../services/clinical-assistant-service.md) and [requirements.md](../requirements.md)

## Context

TC-11 fixes the Spring AI 1.x line, and DER-06 records that its pgvector store is JDBC-based. The service analysis reads the 1.1.1 `VectorStore` methods (`similaritySearch`, `add`, `delete`) as synchronous, without `Mono` or `Flux`; the API signatures must still be verified against the pinned artifacts in the spike. Calling them on a Netty event-loop thread would violate NFR-01. R-03 rates this as a medium-probability performance risk.

The skeleton has an R2DBC stack and a pgvector starter, but no JDBC URL or JDBC driver. The Assistant database also holds `DocumentoClinico` through R2DBC, so two connection pools would coexist on the same PostgreSQL database. There is no cross-store transaction between the document and vector tables, which makes ingestion idempotency necessary.

The schema owner is undecided: Flyway under TC-12 or Spring AI's table creation at startup (G-14). The table layout, metadata keys and ANN index type are also undecided (G-15). The embedding dimension and distance metric are decided jointly with ADR-05, because changing the embedding model later requires re-ingesting the whole corpus.

## Decision drivers

- NFR-01: no blocking call on the request path, demonstrated with BlockHound (TST-RXN).
- A bounded queue: overload fails fast instead of queueing without limit.
- A JDBC pool sized independently from the R2DBC pool.
- Flyway owns the schema; ingestion is idempotent and off the request path.
- The vector data stays in the Assistant database under DEC-10.
- The choice is reversible without rewriting the domain port.

## Alternatives considered

| Option | Description | Pros | Cons | Status / why not chosen |
|---|---|---|---|---|
| **A. Spring AI `PgVectorStore` behind a reactive port on a dedicated bounded scheduler** | The domain depends on `Mono<List<RetrievedChunk>>`; an adapter calls the store inside `Mono.fromCallable(...)` on a dedicated bounded elastic scheduler with a separate JDBC pool if the pinned API is synchronous; Flyway owns the schema, and Spring AI auto-creation is the rejected counterpart under TC-12 | Keeps the Spring AI abstraction and ingestion helpers; satisfies NFR-01 by isolation; small custom surface | One blocking thread per in-flight retrieval; pool and queue sizing must be measured; needs mapping between Spring AI's table and `FragmentoDocumental` | **Proposed**, not rejected. Acceptance depends on the spike and BlockHound evidence |
| **B. Custom R2DBC retrieval adapter with pgvector SQL** | Run `embedding <=> :query` directly through R2DBC against the `FragmentoDocumental` table | No JDBC on the request path; one pool; explicit control of table and query | Loses the Spring AI store abstraction and ingestion helpers; requires custom vector serialization and result mapping; more code to maintain | Kept as the escape hatch if the spike fails. Not selected now because the cost is higher than isolating the existing store |
| **C. External vector service** | Move pgvector storage and search to a separate service or managed vector database | Removes JDBC from this service entirely; independent scaling | Adds a network boundary, a second data store and a new failure mode; conflicts with DEC-10, which keeps vector data with the Assistant, and complicates the Azure perimeter | Rejected: the boundary is not justified for this scope |

## Decision

**Proposed: Option A.** The retrieval port has a reactive signature, for example `Mono<List<RetrievedChunk>> retrieve(String query, int k)`. `PgVectorRetrievalAdapter` calls the store inside `Mono.fromCallable(...)` on a dedicated bounded elastic scheduler named for pgvector JDBC if the pinned API is synchronous; the spike verifies the signature before implementation. The JDBC pool is sized to the scheduler thread count and is monitored separately from the R2DBC pool. Flyway owns the schema; Spring AI table auto-creation is disabled; the table is mapped to `FragmentoDocumental` with the column names, metadata keys and ANN index defined under G-15. Ingestion runs at startup or as an operator action on its own scheduler, and re-running it must not duplicate chunks. The embedding model and its dimension come from ADR-05; the ANN index type and distance metric are defined here together with that dimension under G-15. Queue saturation is a retrieval failure owned by C-06 and G-05; ADR-09 covers only the LLM breaker and timeout. This is a proposal, not an accepted decision.

## Consequences

- Positive: NFR-01 is preserved without rewriting the domain around a synchronous API; the Spring AI ingestion helpers remain available.
- Negative: the design still consumes one blocking thread per retrieval. Under load the queue fills and requests fail, so the failure behavior must be explicit.
- C-06 depends only on the reactive retrieval port, not on Spring AI classes.
- C-01 and DEC-10 keep vector data in the Assistant database; no new store is introduced.
- ADR-05 fixes the embedding dimension used by the Flyway column and the ANN index.
- Retrieval failure responses stay with C-06 and G-05; ADR-09 owns only the LLM breaker and timeout.

## Risks and mitigations

- **R-03 (JDBC blocking):** isolate on the bounded scheduler, keep the queue bounded, and measure latency. Residual risk remains until TST-RXN passes.
- **R-05 (version drift):** pin Spring AI 1.1.1 under DEC-12 and verify compatibility before the first build (CA-G-18).
- **G-14 (startup coexistence):** spike the R2DBC and JDBC auto-configuration on the same database before implementation.
- **G-15 (table layout):** define columns, metadata keys, ANN index and distance metric in the migration, and verify the `VectorStore` signatures against the pinned 1.1.1 artifacts.
- **G-19 (test infrastructure):** add Testcontainers and BlockHound before the verification tests can run.

## Verification

- **TST-RXN positive:** retrieval through the adapter passes BlockHound on the request path.
- **TST-RXN negative:** if the pinned API is synchronous, a direct `VectorStore` call from a reactor thread fails the test.
- **TST-RAG retrieval subset:** real pgvector in a Testcontainers PostgreSQL database returns the expected chunks for PROTO-001 and PROTO-002 (NFR-17); citation validation itself stays in C-06.
- **Load evidence:** the bounded queue and separate JDBC pool are measured against the proposed threshold p95 < 200 ms at 20 concurrent retrievals, to be confirmed in T-14; saturation fails fast and is visible in metrics.
- The evidence belongs in the `assistant-vector-store-isolation` change of `implementation-roadmap.md` (T-14, T-15).
