# Architecture Decision Records

Start here. This index lists the **canonical** record for each decision, where it is implemented, and what its status means. Superseded drafts and supporting annexes are listed separately so they are not mistaken for open decisions.

## How to read the status

| Status | Meaning in this repository |
|---|---|
| **Accepted** | Decision closed and its verification gates passed. |
| **Proposed** | The decision is **chosen and implemented** (see the *Implemented in* column), but one or more of its own verification gates is still open. Under rule D-ADR-FORMAL an ADR is not marked `Accepted` until those gates pass; the open gates per ADR are listed in [`adr-acceptance-acta-20261127.md`](../audits/20261127/adr-acceptance-acta-20261127.md). |
| **Superseded** | Replaced by the record named in its header. Kept for traceability only; do not implement it. |

`Proposed` therefore does **not** mean "undecided". It means the gates for formal acceptance are still open.

## Canonical decisions

| ADR | Decision | Status | Implemented in |
|---|---|---|---|
| [ADR-01](ADR-01-service-decomposition.md) | Two services: Triage and Clinical Assistant, no synchronous call between them | Proposed | `triage-service/`, `clinical-assistant-service/` |
| [ADR-02](ADR-02-assistant-kafka-consumer.md) | Assistant consumes `triaje.eventos` with reactor-kafka, all four events | Proposed | `TriageEventsConsumer` |
| [ADR-03](ADR-03-transactional-outbox.md) | Transactional outbox for event publication (lease + `SKIP LOCKED`) | Proposed | `OutboxEventWriter`, `OutboxPublisher` |
| [ADR-04](ADR-04-pgvector-jdbc-isolation.md) | pgvector as vector store; JDBC isolated on a bounded scheduler | Proposed | `PgvectorVectorStoreAdapter`, `VectorRepository` |
| [ADR-05](ADR-05-assistant-llm-provider.md) | LLM chain (Ollama, Gemini, OpenCode) over reactive `WebClient`; embedding model | Proposed | `ResilientLLMRouter`, `*HttpLLMAdapter`, `LexicalEmbeddingAdapter` |
| [ADR-07](ADR-07-authentication-api-key.md) | Authentication with `X-API-Key`; JWT rejected | **Accepted** | `ApiKeyAuthenticationFilter` (both services) |
| [ADR-08](ADR-08-urgency-projection-store.md) | Urgency projection in Redis with atomic Lua scripts | **Accepted** (amended) | `RedisUrgencyStateRepository`, `redis/*.lua` |
| [ADR-09](ADR-09-circuit-breaker-degraded-mode.md) | Resilience4j circuit breaker per LLM node; 503 `LLM_UNAVAILABLE` degraded mode | Proposed | `ResilientLLMRouter`, `RAGExceptionHandler` |
| [ADR-10](ADR-10-spring-boot-platform-and-java-baseline.md) | Spring Boot 3.5.x / Java 21 baseline (non-production restriction) | **Accepted** (evaluation scope) | `build.gradle.kts` (both services) |
| [ADR-11](ADR-11-azure-target.md) | Azure target: AKS (amended from the original option) | Proposed | `infra/azure/main.bicep` |
| [ADR-12 (AKS)](ADR-12-aks-deployment.md) | AKS deployment topology and Gateway | **Accepted** | `infra/k8s/`, `*/deployment/k8s/`, `.github/workflows/deploy.yml` |
| [ADR-12 (Idempotency)](ADR-12-idempotency-key.md) | Optional `Idempotency-Key` replay on `POST /triage/pacientes` | Proposed | `TriageRegistrationService`, migration `V3__idempotency_key.sql` |
| [ADR-13](ADR-13-sla-evaluation.md) | SLA evaluation: scheduled job + conditional `UPDATE` (RN-03) | Proposed | `SlaEvaluator`, `SlaEvaluationScheduler` |
| [ADR-14](ADR-14-database-ownership-one-db-per-service.md) | One PostgreSQL database per service + Redis projection | Proposed | `docker-compose.yml`, Flyway migrations per service |
| [ADR-15](ADR-15-persisted-sse-alerts.md) | RF-06 alerts: persisted `medical_alert` rows streamed over SSE | **Accepted** | `SlaEvaluator`, `AlertController`, `AlertChangeListener` |

> Two records share the number 12 for historical reasons. Audit documents dated before this index refer to them as "ADR-12 — Idempotency key" and "ADR-12 — AKS deployment". They are not renumbered, so those references stay valid.

## Superseded records (do not implement)

| File | Replaced by |
|---|---|
| [ADR-05-llm-provider-spring-ai.md](ADR-05-llm-provider-spring-ai.md) | [ADR-05](ADR-05-assistant-llm-provider.md) |
| [ADR-06-escalation-notification-channel-webhook.md](ADR-06-escalation-notification-channel-webhook.md) | [ADR-15](ADR-15-persisted-sse-alerts.md) |
| [ADR-06-notification-channel.md](ADR-06-notification-channel.md) | [ADR-15](ADR-15-persisted-sse-alerts.md) |
| [ADR-07-authentication.md](ADR-07-authentication.md) | [ADR-07](ADR-07-authentication-api-key.md) |

## Annexes (supporting material, not decisions)

| File | Supports | Content |
|---|---|---|
| [ADR-09-circuit-breaker-proposal.md](ADR-09-circuit-breaker-proposal.md) | ADR-09 | Earlier draft of the degraded-mode decision |
| [ADR-09-architecture-diagram.md](ADR-09-architecture-diagram.md) | ADR-09 | Primary and fallback flow diagrams |
| [ADR-09-failure-scenario-matrix.md](ADR-09-failure-scenario-matrix.md) | ADR-09 | Failure scenarios and expected behavior |
| [ADR-09-model-equivalence-validation.md](ADR-09-model-equivalence-validation.md) | ADR-09 | Validation criteria for an alternative model |

The system-level diagrams are in [`docs/architecture/diagrams.md`](../architecture/diagrams.md).
